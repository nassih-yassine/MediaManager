use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::Arc;
use std::time::{Duration, Instant};

use anyhow::{Context, Result};
use futures_util::StreamExt;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::process::Command;
use tokio::sync::Mutex;

use crate::config::Config;
use crate::db;
use crate::media;
use crate::models::{self, Item, Job};
use crate::state::AppState;

const MEDIA_EXTENSIONS: [&str; 10] = [
    "mp3", "m4a", "opus", "flac", "wav", "mp4", "webm", "mkv", "mov", "aac",
];
const IMAGE_EXTENSIONS: [&str; 5] = ["jpg", "jpeg", "png", "webp", "avif"];

pub fn spawn_workers(state: AppState) -> Vec<tokio::task::JoinHandle<()>> {
    let n = state.cfg.max_concurrent.max(1);
    (0..n)
        .map(|idx| {
            let state = state.clone();
            tokio::spawn(async move {
                let worker_id = format!("worker-{idx}-{}", std::process::id());
                worker_loop(state, worker_id).await;
            })
        })
        .collect()
}

pub fn spawn_reaper(state: AppState) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let mut tick = tokio::time::interval(Duration::from_secs(15));
        loop {
            tick.tick().await;
            let lease_ms = (state.cfg.lease_s * 1000) as i64;
            let cutoff = db::now_ms() - lease_ms;
            let res = sqlx::query(
                "UPDATE jobs
                    SET status = 'queued', claimed_by = NULL, claimed_at = NULL,
                        heartbeat_at = NULL, updated_at = ?, error = 'worker lost; retrying'
                  WHERE status = 'running' AND COALESCE(heartbeat_at, claimed_at, 0) < ?",
            )
            .bind(db::now_ms())
            .bind(cutoff)
            .execute(&state.pool)
            .await;
            match res {
                Ok(done) if done.rows_affected() > 0 => {
                    tracing::warn!(count = done.rows_affected(), "reaped stale jobs")
                }
                Ok(_) => {}
                Err(err) => tracing::error!(%err, "reaper query failed"),
            }

            // Fail jobs that exhausted their attempts while queued.
            let _ = sqlx::query(
                "UPDATE jobs SET status = 'failed', error = COALESCE(error, 'retry limit reached'), updated_at = ?
                  WHERE status = 'queued' AND attempts >= max_attempts",
            )
            .bind(db::now_ms())
            .execute(&state.pool)
            .await;
        }
    })
}

async fn worker_loop(state: AppState, worker_id: String) {
    let mut tick = tokio::time::interval(Duration::from_millis(500));
    loop {
        tick.tick().await;
        match claim(&state.pool, &worker_id).await {
            Ok(Some(job)) => {
                let job_id = job.id.clone();
                tracing::info!(job = %job_id, kind = %job.kind, "claimed job");
                if let Err(err) = run_job(&state, job).await {
                    tracing::error!(job = %job_id, %err, "job crashed");
                }
            }
            Ok(None) => {}
            Err(err) => tracing::warn!(%err, "claim failed"),
        }
    }
}

async fn claim(pool: &sqlx::SqlitePool, worker_id: &str) -> Result<Option<Job>> {
    let now = db::now_ms();
    let job = sqlx::query_as::<_, Job>(
        "UPDATE jobs
            SET status = 'running', claimed_by = ?, claimed_at = ?, heartbeat_at = ?,
                attempts = attempts + 1, updated_at = ?
          WHERE id = (
            SELECT id FROM jobs
             WHERE status = 'queued' AND scheduled_at <= ? AND attempts < max_attempts
             ORDER BY priority DESC, created_at
             LIMIT 1
          )
          RETURNING *",
    )
    .bind(worker_id)
    .bind(now)
    .bind(now)
    .bind(now)
    .bind(now)
    .fetch_optional(pool)
    .await?;
    Ok(job)
}

async fn run_job(state: &AppState, job: Job) -> Result<()> {
    let heartbeat = spawn_heartbeat(state.clone(), job.id.clone());

    let outcome = match job.kind.as_str() {
        "music" | "video" => download_ytdlp(state, &job).await,
        _ => download_direct(state, &job).await,
    };
    heartbeat.abort();

    match outcome {
        Ok(item) => {
            // A cancel that landed mid-download wins over the finished file.
            let status = sqlx::query_scalar::<_, String>("SELECT status FROM jobs WHERE id = ?")
                .bind(&job.id)
                .fetch_optional(&state.pool)
                .await
                .unwrap_or(None);
            if status.as_deref() == Some("cancelled") {
                let _ = media::remove_item(&state.pool, &state.cfg, &item, true).await;
                state.emit("item.deleted", &item.id, None, job.owner_id.as_deref());
                return Ok(());
            }
            if let Some(collection_id) = &job.collection_id {
                if let Err(err) =
                    media::attach_to_collection(&state.pool, collection_id, &item.id).await
                {
                    tracing::warn!(%err, %collection_id, "could not attach finished item");
                }
            }
            state.emit(
                "item.created",
                &item.id,
                job.collection_id.as_deref(),
                job.owner_id.as_deref(),
            );
            if let Some(collection_id) = &job.collection_id {
                state.emit(
                    "collection.changed",
                    collection_id,
                    None,
                    job.owner_id.as_deref(),
                );
            }
            if item.kind == "picture" {
                if let Some(owner) = item.owner_id.as_deref() {
                    if let Ok(default) = media::ensure_default_gallery(&state.pool, owner).await {
                        let _ = media::attach_to_collection(&state.pool, &default, &item.id).await;
                        state.emit("collection.changed", &default, None, Some(owner));
                    }
                }
            }
            sqlx::query(
                "UPDATE jobs
                    SET status = 'done', progress = 1, stage = 'done', item_id = ?, updated_at = ?
                  WHERE id = ?",
            )
            .bind(&item.id)
            .bind(db::now_ms())
            .bind(&job.id)
            .execute(&state.pool)
            .await?;
            match notify_job_done(&state.pool, &job, &item).await {
                Ok(Some(id)) => {
                    state.emit("notification.created", &id, None, job.owner_id.as_deref())
                }
                Ok(None) => {}
                Err(err) => tracing::warn!(%err, "could not notify finished download"),
            }
            cleanup_incoming(state, &job.id).await;
            tracing::info!(job = %job.id, item = %item.id, "job done");
        }
        Err(err) => {
            let message = truncate(&format!("{err:#}"), 2000);
            let retry = !is_permanent_error(&message) && job.attempts < job.max_attempts;
            let backoff_s = 2i64.pow(job.attempts.clamp(1, 8) as u32).min(300);
            sqlx::query(
                "UPDATE jobs
                    SET status = ?, error = ?, updated_at = ?, scheduled_at = ?,
                        claimed_by = NULL, claimed_at = NULL, heartbeat_at = NULL
                  WHERE id = ?",
            )
            .bind(if retry { "queued" } else { "failed" })
            .bind(&message)
            .bind(db::now_ms())
            .bind(db::now_ms() + backoff_s * 1000)
            .bind(&job.id)
            .execute(&state.pool)
            .await?;
            match notify_job_failed(&state.pool, &job, retry, &message).await {
                Ok(Some(id)) => {
                    state.emit("notification.created", &id, None, job.owner_id.as_deref())
                }
                Ok(None) => {}
                Err(err) => tracing::warn!(%err, "could not notify failed download"),
            }
            tracing::warn!(job = %job.id, retry, %message, "job failed");
        }
    }

    Ok(())
}

fn spawn_heartbeat(state: AppState, job_id: String) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let every = Duration::from_secs(state.cfg.heartbeat_s.max(1));
        let mut tick = tokio::time::interval(every);
        loop {
            tick.tick().await;
            let _ =
                sqlx::query("UPDATE jobs SET heartbeat_at = ? WHERE id = ? AND status = 'running'")
                    .bind(db::now_ms())
                    .bind(&job_id)
                    .execute(&state.pool)
                    .await;
        }
    })
}

async fn update_progress(
    pool: &sqlx::SqlitePool,
    job_id: &str,
    progress: f64,
    stage: &str,
    last_emit: &Arc<Mutex<Instant>>,
) {
    let mut guard = last_emit.lock().await;
    if guard.elapsed() < Duration::from_millis(700) {
        return;
    }
    *guard = Instant::now();
    drop(guard);
    let _ = sqlx::query(
        "UPDATE jobs SET progress = ?, stage = ?, updated_at = ? WHERE id = ? AND status = 'running'",
    )
    .bind(progress.clamp(0.0, 1.0))
    .bind(stage)
    .bind(db::now_ms())
    .bind(job_id)
    .execute(pool)
    .await;
}

async fn cleanup_incoming(state: &AppState, job_id: &str) {
    let dir = state.cfg.incoming_dir().join(job_id);
    let _ = tokio::fs::remove_dir_all(dir).await;
}

async fn download_ytdlp(state: &AppState, job: &Job) -> Result<Item> {
    ensure_public_url(&job.url).await?;
    let dir = state.cfg.incoming_dir().join(&job.id);
    tokio::fs::create_dir_all(&dir).await?;
    let out_tpl = dir.join("%(title).180B.%(ext)s");
    let out = out_tpl.to_string_lossy().to_string();

    let mut args: Vec<String> = Vec::new();
    if job.kind == "music" {
        args.extend(
            [
                "-x",
                "--audio-format",
                "mp3",
                "--audio-quality",
                "0",
                "--embed-metadata",
            ]
            .map(String::from),
        );
    } else {
        args.extend(["-f", "bv*+ba/b", "--merge-output-format", "mp4"].map(String::from));
    }
    args.extend(
        [
            "--write-thumbnail",
            "--convert-thumbnails",
            "jpg",
            "--write-info-json",
            "--no-playlist",
            "--no-js-runtimes",
            "--js-runtimes",
            "node",
            "--newline",
            "--progress-template",
            "MMPROG %(progress._percent_str)s",
            "-o",
            &out,
            &job.url,
        ]
        .map(String::from),
    );

    let mut child = Command::new(&state.cfg.ytdlp)
        .args(&args)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true)
        .spawn()
        .with_context(|| format!("spawning {} (is it installed?)", state.cfg.ytdlp))?;

    let stdout = child.stdout.take().context("yt-dlp stdout")?;
    let stderr = child.stderr.take().context("yt-dlp stderr")?;
    let last_emit = Arc::new(Mutex::new(Instant::now() - Duration::from_secs(10)));
    let pool = state.pool.clone();
    let job_id = job.id.clone();

    let progress_task = {
        let pool = pool.clone();
        let job_id = job_id.clone();
        let last_emit = last_emit.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(stdout).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                if let Some(rest) = line.trim().strip_prefix("MMPROG") {
                    let pct = rest
                        .trim()
                        .trim_end_matches('%')
                        .trim()
                        .parse::<f64>()
                        .unwrap_or(0.0);
                    update_progress(&pool, &job_id, pct / 100.0, "fetching", &last_emit).await;
                }
            }
        })
    };

    let stderr_task = {
        let pool = pool.clone();
        let job_id = job_id.clone();
        let last_emit = last_emit.clone();
        tokio::spawn(async move {
            let mut tail: Vec<String> = Vec::new();
            let mut lines = BufReader::new(stderr).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                let lower = line.to_ascii_lowercase();
                if lower.contains("[extractaudio]")
                    || lower.contains("[merger]")
                    || lower.contains("[ffmpeg]")
                    || lower.contains("[videoconvertor]")
                {
                    update_progress(&pool, &job_id, 0.9, "converting", &last_emit).await;
                }
                if lower.contains("error") || tail.len() < 40 {
                    tail.push(line);
                }
                if tail.len() > 60 {
                    tail.remove(0);
                }
            }
            tail.join("\n")
        })
    };

    let status = child.wait().await?;
    let _ = progress_task.await;
    let tail = stderr_task.await.unwrap_or_default();
    if !status.success() {
        anyhow::bail!(
            "yt-dlp exited with {}: {}",
            status.code().unwrap_or(-1),
            truncate(&tail, 1200)
        );
    }

    update_progress(&state.pool, &job.id, 0.95, "finalizing", &last_emit).await;

    let (media_path, thumb_path, info_path) = collect_download(&dir, job.kind.as_str())?;
    let (title, duration_ms, width, height) = read_info(&info_path, &job.url).await;

    let mime = models::mime_for_ext(&media::extension_of(&media_path));
    let owner_id = job
        .owner_id
        .as_deref()
        .ok_or_else(|| anyhow::anyhow!("job has no owner"))?;
    let mut item = media::finalize(
        &state.pool,
        &state.cfg,
        owner_id,
        &job.kind,
        &title,
        &media_path,
        thumb_path.as_deref(),
        mime,
        Some(&job.url),
        duration_ms,
        width,
        height,
    )
    .await?;

    if job.kind == "music" {
        // Best effort: attach the sidecar cover art to the MP3 itself.
        if let Some(thumb) = &item.thumb_path {
            let cover = state.cfg.library_dir.join(thumb);
            let audio = state.cfg.library_dir.join(&item.path);
            if let Err(err) = embed_cover(&state.cfg, &audio, &cover).await {
                tracing::debug!(%err, "cover embed skipped");
            } else if let Ok(meta) = tokio::fs::metadata(&audio).await {
                // The embedded cover changes the file size after the row was written.
                item.bytes = meta.len() as i64;
                let _ = sqlx::query("UPDATE items SET bytes = ?, updated_at = ? WHERE id = ?")
                    .bind(item.bytes)
                    .bind(db::now_ms())
                    .bind(&item.id)
                    .execute(&state.pool)
                    .await;
            }
        }
    }

    Ok(item)
}

fn collect_download(dir: &Path, kind: &str) -> Result<(PathBuf, Option<PathBuf>, PathBuf)> {
    let mut best_media: Option<PathBuf> = None;
    let mut thumb: Option<PathBuf> = None;
    let mut info: Option<PathBuf> = None;

    let entries = std::fs::read_dir(dir)?;
    for entry in entries.flatten() {
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().to_string();
        if name.ends_with(".info.json") {
            info = Some(path);
            continue;
        }
        let ext = media::extension_of(&path);
        if IMAGE_EXTENSIONS.contains(&ext.as_str()) {
            // Prefer jpg over whatever conversion left behind.
            if thumb.is_none() || ext == "jpg" {
                thumb = Some(path);
            }
            continue;
        }
        if MEDIA_EXTENSIONS.contains(&ext.as_str()) {
            let preferred = if kind == "music" { "mp3" } else { "mp4" };
            let replace = match &best_media {
                None => true,
                Some(existing) => media::extension_of(existing) != preferred && ext == preferred,
            };
            if replace {
                best_media = Some(path);
            }
        }
    }

    let media_path = best_media.context("yt-dlp finished but produced no media file")?;
    let info_path = info.context("yt-dlp produced no info json")?;
    Ok((media_path, thumb, info_path))
}

async fn read_info(
    path: &Path,
    fallback_url: &str,
) -> (String, Option<i64>, Option<i64>, Option<i64>) {
    let parsed = tokio::fs::read(path)
        .await
        .ok()
        .and_then(|bytes| serde_json::from_slice::<serde_json::Value>(&bytes).ok());

    let Some(info) = parsed else {
        return (fallback_url.to_string(), None, None, None);
    };
    let title = info
        .get("title")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .unwrap_or_else(|| fallback_url.to_string());
    let duration_ms = info
        .get("duration")
        .and_then(|v| v.as_f64())
        .map(|s| (s * 1000.0) as i64);
    let width = info.get("width").and_then(|v| v.as_i64());
    let height = info.get("height").and_then(|v| v.as_i64());
    (title, duration_ms, width, height)
}

async fn embed_cover(cfg: &Config, audio: &Path, cover: &Path) -> Result<()> {
    let tmp = audio.with_extension("cover.mp3");
    let status = Command::new(&cfg.ffmpeg)
        .arg("-y")
        .arg("-i")
        .arg(audio)
        .arg("-i")
        .arg(cover)
        .args([
            "-map",
            "0",
            "-map",
            "1",
            "-c",
            "copy",
            "-id3v2_version",
            "3",
            "-metadata:s:v",
            "title=Album cover",
            "-metadata:s:v",
            "comment=Cover (front)",
        ])
        .arg(&tmp)
        .status()
        .await?;
    if status.success() {
        tokio::fs::rename(&tmp, audio).await?;
    } else {
        let _ = tokio::fs::remove_file(&tmp).await;
        anyhow::bail!("ffmpeg could not embed cover");
    }
    Ok(())
}

async fn download_direct(state: &AppState, job: &Job) -> Result<Item> {
    ensure_public_url(&job.url).await?;
    let dir = state.cfg.incoming_dir().join(&job.id);
    tokio::fs::create_dir_all(&dir).await?;

    let resp = state
        .client
        .get(&job.url)
        .send()
        .await
        .with_context(|| format!("GET {}", job.url))?;
    if !resp.status().is_success() {
        anyhow::bail!("server returned {}", resp.status());
    }

    let header_len = resp.content_length().unwrap_or(0);
    if header_len > state.cfg.max_download_bytes() {
        anyhow::bail!(
            "file is {} MB, over the {} MB limit",
            header_len / 1_048_576,
            state.cfg.max_download_mb
        );
    }

    let content_type = resp
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_string();
    let url_ext = Path::new(job.url.split('?').next().unwrap_or(&job.url))
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_ascii_lowercase())
        .filter(|e| !e.is_empty() && e.len() <= 5)
        .unwrap_or_else(|| {
            models::ext_for_mime(&content_type)
                .unwrap_or("bin")
                .to_string()
        });

    let dest = dir.join(format!("source.{url_ext}"));
    let title = title_from_url(&job.url);
    let mut file = tokio::fs::File::create(&dest).await?;
    let mut stream = resp.bytes_stream();
    let mut written: u64 = 0;
    let max = state.cfg.max_download_bytes();
    let last_emit = Arc::new(Mutex::new(Instant::now() - Duration::from_secs(10)));

    while let Some(chunk) = stream.next().await {
        let chunk = chunk.context("reading response body")?;
        written += chunk.len() as u64;
        if written > max {
            let _ = tokio::fs::remove_file(&dest).await;
            anyhow::bail!(
                "download exceeded the {} MB limit",
                state.cfg.max_download_mb
            );
        }
        file.write_all(&chunk).await?;
        if header_len > 0 {
            update_progress(
                &state.pool,
                &job.id,
                written as f64 / header_len as f64,
                "fetching",
                &last_emit,
            )
            .await;
        }
    }
    file.flush().await?;
    drop(file);

    update_progress(&state.pool, &job.id, 0.95, "finalizing", &last_emit).await;

    let mime = models::mime_for_ext(&url_ext);
    let owner_id = job
        .owner_id
        .as_deref()
        .ok_or_else(|| anyhow::anyhow!("job has no owner"))?;
    let item = media::finalize(
        &state.pool,
        &state.cfg,
        owner_id,
        &job.kind,
        &title,
        &dest,
        None,
        mime,
        Some(&job.url),
        None,
        None,
        None,
    )
    .await?;

    Ok(item)
}

/// One `job_done` row for the owner; `None` when the job has no owner.
async fn notify_job_done(
    pool: &sqlx::SqlitePool,
    job: &Job,
    item: &Item,
) -> anyhow::Result<Option<String>> {
    let Some(owner) = job.owner_id.as_deref() else {
        return Ok(None);
    };
    Ok(Some(
        crate::notifications::create(
            pool,
            owner,
            "job_done",
            &format!("Downloaded: {}", item.title),
            Some(&job.url),
            Some(&format!("/watch/{}", item.id)),
        )
        .await?,
    ))
}

/// Retries stay silent; only the final failure notifies the owner.
async fn notify_job_failed(
    pool: &sqlx::SqlitePool,
    job: &Job,
    retry: bool,
    message: &str,
) -> anyhow::Result<Option<String>> {
    if retry {
        return Ok(None);
    }
    let Some(owner) = job.owner_id.as_deref() else {
        return Ok(None);
    };
    Ok(Some(
        crate::notifications::create(
            pool,
            owner,
            "job_failed",
            "Download failed",
            Some(message),
            Some("/add"),
        )
        .await?,
    ))
}

/// Reject loopback, private, link-local and other non-public targets so a
/// non-admin account cannot use the worker as a network proxy.
pub(crate) fn host_is_forbidden(host: &str) -> bool {
    let host = host.trim_end_matches('.').to_ascii_lowercase();
    if host.is_empty()
        || host == "localhost"
        || host.ends_with(".localhost")
        || host == "metadata.google.internal"
    {
        return true;
    }
    host.parse::<std::net::IpAddr>()
        .map(ip_is_forbidden)
        .unwrap_or(false)
}

fn ip_is_forbidden(ip: std::net::IpAddr) -> bool {
    match ip {
        std::net::IpAddr::V4(v4) => {
            v4.is_loopback()
                || v4.is_private()
                || v4.is_link_local()
                || v4.is_unspecified()
                || v4.is_broadcast()
                || v4.is_documentation()
                || v4.octets()[0] == 100 && (64..128).contains(&v4.octets()[1])
        }
        std::net::IpAddr::V6(v6) => {
            v6.is_loopback()
                || v6.is_unspecified()
                || v6.is_unique_local()
                || v6.is_unicast_link_local()
        }
    }
}

async fn ensure_public_url(raw: &str) -> Result<()> {
    let url = reqwest::Url::parse(raw).with_context(|| format!("invalid url {raw}"))?;
    if !matches!(url.scheme(), "http" | "https") {
        anyhow::bail!("only http and https urls are supported");
    }
    let host = url.host_str().context("url has no host")?;
    if host_is_forbidden(host) {
        anyhow::bail!("url target is not allowed");
    }
    let port = url.port_or_known_default().unwrap_or(80);
    if let Ok(addrs) = tokio::net::lookup_host((host, port)).await {
        if addrs.into_iter().any(|addr| ip_is_forbidden(addr.ip())) {
            anyhow::bail!("url resolves to a private address");
        }
    }
    Ok(())
}

fn title_from_url(url: &str) -> String {
    let trimmed = url.split('?').next().unwrap_or(url).trim_end_matches('/');
    let last = trimmed.rsplit('/').next().unwrap_or(trimmed);
    let decoded = percent_encoding::percent_decode_str(last)
        .decode_utf8_lossy()
        .to_string();
    let stem = decoded
        .rsplit_once('.')
        .map(|(s, _)| s.to_string())
        .unwrap_or(decoded);
    if stem.is_empty() {
        url.to_string()
    } else {
        stem.replace(['_', '-'], " ")
    }
}

fn truncate(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        s.to_string()
    } else {
        s.chars().take(max).collect::<String>() + "…"
    }
}

/// Errors that will not fix themselves on retry (dead links, private videos).
fn is_permanent_error(message: &str) -> bool {
    let m = message.to_ascii_lowercase();
    [
        "video is unavailable",
        "this video is private",
        "has been removed",
        "not available in your country",
        "unsupported url",
        "http error 404",
        "http error 403",
    ]
    .iter()
    .any(|needle| m.contains(needle))
}

#[cfg(test)]
mod tests {
    use super::*;

    async fn notify_pool() -> sqlx::SqlitePool {
        let pool = sqlx::SqlitePool::connect("sqlite::memory:").await.unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        sqlx::query(
            "INSERT INTO users (id, email, name, password_hash, role, created_at, updated_at)
             VALUES ('owner-1', 'o@example.com', 'Owner', 'x', 'user', 1, 1)",
        )
        .execute(&pool)
        .await
        .unwrap();
        pool
    }

    fn job(owner: Option<&str>) -> Job {
        Job {
            id: "job-1".into(),
            owner_id: owner.map(str::to_string),
            dedupe_key: "d".into(),
            url: "https://example.com/a".into(),
            kind: "music".into(),
            mode: "audio+thumb".into(),
            status: "running".into(),
            stage: None,
            progress: 0.0,
            error: None,
            item_id: None,
            collection_id: None,
            priority: 0,
            attempts: 1,
            max_attempts: 3,
            scheduled_at: 0,
            claimed_by: None,
            claimed_at: None,
            heartbeat_at: None,
            created_at: 1,
            updated_at: 1,
        }
    }

    fn item() -> Item {
        Item {
            rowid: 1,
            owner_id: Some("owner-1".into()),
            id: "item-1".into(),
            kind: "music".into(),
            title: "Track".into(),
            source_url: None,
            path: "music/aa/item-1.mp3".into(),
            thumb_path: None,
            mime: "audio/mpeg".into(),
            bytes: 1,
            duration_ms: None,
            width: None,
            height: None,
            sha256: None,
            position_ms: 0,
            last_played_at: None,
            created_at: 1,
            updated_at: 1,
        }
    }

    #[tokio::test]
    async fn download_outcomes_notify_once_and_retries_stay_silent() {
        let pool = notify_pool().await;
        let done = notify_job_done(&pool, &job(Some("owner-1")), &item())
            .await
            .unwrap()
            .unwrap();
        assert!(!done.is_empty());

        assert!(
            notify_job_failed(&pool, &job(Some("owner-1")), true, "timeout")
                .await
                .unwrap()
                .is_none()
        );
        let failed = notify_job_failed(&pool, &job(Some("owner-1")), false, "permanent")
            .await
            .unwrap()
            .unwrap();
        assert!(!failed.is_empty());

        let kinds: Vec<String> = sqlx::query_scalar(
            "SELECT kind FROM notifications WHERE user_id = 'owner-1' ORDER BY rowid",
        )
        .fetch_all(&pool)
        .await
        .unwrap();
        assert_eq!(kinds, vec!["job_done", "job_failed"]);
        assert!(notify_job_done(&pool, &job(None), &item())
            .await
            .unwrap()
            .is_none());
    }

    #[test]
    fn private_targets_are_blocked() {
        for host in [
            "localhost",
            "app.localhost",
            "127.0.0.1",
            "10.0.0.5",
            "192.168.1.1",
            "172.16.0.1",
            "169.254.169.254",
            "100.64.0.1",
            "0.0.0.0",
            "::1",
            "fd00::1",
            "fe80::1",
            "metadata.google.internal",
        ] {
            assert!(host_is_forbidden(host), "{host} should be blocked");
        }
        for host in ["example.com", "youtube.com", "8.8.8.8"] {
            assert!(!host_is_forbidden(host), "{host} should be allowed");
        }
    }

    #[tokio::test]
    async fn ensure_public_url_rejects_loopback_and_other_schemes() {
        assert!(ensure_public_url("http://127.0.0.1/x").await.is_err());
        assert!(ensure_public_url("http://localhost:8787/api/health")
            .await
            .is_err());
        assert!(ensure_public_url("file:///etc/passwd").await.is_err());
        assert!(ensure_public_url("http://169.254.169.254/latest")
            .await
            .is_err());
    }

    #[test]
    fn permanent_errors_are_detected() {
        assert!(is_permanent_error(
            "ERROR: [youtube] abc: This video is unavailable"
        ));
        assert!(is_permanent_error("ERROR: HTTP Error 404: Not Found"));
        assert!(!is_permanent_error("ERROR: unable to download: timed out"));
    }

    #[test]
    fn titles_come_from_urls() {
        assert_eq!(
            title_from_url("https://example.com/files/My_Photo-01.jpg?x=1"),
            "My Photo 01"
        );
        assert_eq!(title_from_url("https://example.com"), "example");
    }
}
