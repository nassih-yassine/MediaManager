use std::path::{Component, Path, PathBuf};

use anyhow::{Context, Result};
use tokio::process::Command;

use crate::config::Config;
use crate::db;
use crate::models::{self, Item};

/// Join a DB-relative path under the library root, rejecting anything that
/// could escape it.
pub fn safe_join(root: &Path, rel: &str) -> Option<PathBuf> {
    let rel_path = Path::new(rel);
    if rel_path.is_absolute() {
        return None;
    }
    if rel_path.components().any(|c| {
        matches!(
            c,
            Component::ParentDir | Component::RootDir | Component::Prefix(_)
        )
    }) {
        return None;
    }
    Some(root.join(rel_path))
}

pub fn extension_of(path: &Path) -> String {
    path.extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_ascii_lowercase()
}

/// Convert an image to a JPEG thumbnail (no upscaling). Returns the written path.
pub async fn make_jpeg_thumb(cfg: &Config, src: &Path, dest: &Path) -> Result<()> {
    if extension_of(src) == "jpg" || extension_of(src) == "jpeg" {
        tokio::fs::rename(src, dest).await?;
        return Ok(());
    }
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let status = Command::new(&cfg.ffmpeg)
        .arg("-y")
        .arg("-i")
        .arg(src)
        .args([
            "-vf",
            "scale='max(2,min(640,iw))':-2",
            "-frames:v",
            "1",
            "-update",
            "1",
            "-q:v",
            "3",
        ])
        .arg(dest)
        .status()
        .await
        .with_context(|| format!("spawning {}", cfg.ffmpeg))?;
    if !status.success() {
        anyhow::bail!("ffmpeg thumbnail conversion failed for {}", src.display());
    }
    Ok(())
}

/// First page of a PDF (or an EPUB's cover image) as a JPEG. Best effort: a
/// book with no cover is still a valid item.
pub async fn book_cover(cfg: &Config, src: &Path, dest: &Path) -> Result<()> {
    match extension_of(src).as_str() {
        "pdf" => pdf_cover(src, dest).await,
        "epub" => epub_cover(cfg, src, dest).await,
        other => anyhow::bail!("no cover extractor for .{other}"),
    }
}

async fn pdf_cover(src: &Path, dest: &Path) -> Result<()> {
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    // pdftoppm writes "<stem>.jpg" with -singlefile.
    let stem = dest.with_extension("");
    let status = Command::new("pdftoppm")
        .args([
            "-jpeg",
            "-f",
            "1",
            "-l",
            "1",
            "-singlefile",
            "-scale-to",
            "800",
        ])
        .arg(src)
        .arg(&stem)
        .status()
        .await;

    if let Ok(status) = status {
        if status.success() {
            let produced = stem.with_extension("jpg");
            if produced != dest && produced.exists() {
                tokio::fs::rename(produced, dest).await?;
            }
            return Ok(());
        }
    }

    // Fallback for machines without poppler.
    let status = Command::new("magick")
        .arg("-density")
        .arg("150")
        .arg(format!("{}[0]", src.display()))
        .args(["-resize", "800x", "-quality", "85"])
        .arg(dest)
        .status()
        .await
        .context("spawning magick for pdf cover")?;
    if !status.success() {
        anyhow::bail!("could not render a PDF cover");
    }
    Ok(())
}

fn is_image_entry(name: &str) -> bool {
    matches!(
        extension_of(Path::new(name)).as_str(),
        "jpg" | "jpeg" | "png" | "webp" | "gif" | "avif"
    )
}

async fn epub_cover(cfg: &Config, src: &Path, dest: &Path) -> Result<()> {
    let listing = Command::new("unzip")
        .arg("-Z1")
        .arg(src)
        .output()
        .await
        .context("spawning unzip (is it installed?)")?;
    if !listing.status.success() {
        anyhow::bail!("could not list the epub archive");
    }
    let names = String::from_utf8_lossy(&listing.stdout);
    let images: Vec<&str> = names.lines().filter(|n| is_image_entry(n)).collect();
    let entry = images
        .iter()
        .find(|n| n.to_ascii_lowercase().contains("cover"))
        .or_else(|| images.first())
        .copied()
        .context("epub contains no images")?;

    let tmp = std::env::temp_dir().join(format!("mm-epub-{}", uuid::Uuid::now_v7()));
    tokio::fs::create_dir_all(&tmp).await?;
    let status = Command::new("unzip")
        .arg("-o")
        .arg("-j")
        .arg(src)
        .arg(entry)
        .arg("-d")
        .arg(&tmp)
        .status()
        .await?;
    if !status.success() {
        let _ = tokio::fs::remove_dir_all(&tmp).await;
        anyhow::bail!("could not extract the epub cover");
    }
    let file_name = Path::new(entry)
        .file_name()
        .context("epub entry has no file name")?;
    let extracted = tmp.join(file_name);
    let result = make_jpeg_thumb(cfg, &extracted, dest).await;
    let _ = tokio::fs::remove_dir_all(&tmp).await;
    result
}

/// Move a finished file into the sharded library layout, generate its thumbnail
/// if one was produced, and insert the item row.
#[allow(clippy::too_many_arguments)]
pub async fn finalize(
    pool: &sqlx::SqlitePool,
    cfg: &Config,
    kind: &str,
    title: &str,
    media_src: &Path,
    thumb_src: Option<&Path>,
    mime: &str,
    source_url: Option<&str>,
    duration_ms: Option<i64>,
    width: Option<i64>,
    height: Option<i64>,
) -> Result<Item> {
    let id = uuid::Uuid::now_v7().to_string();
    let prefix = &id[..2];
    let kind_dir = models::kind_dir(kind);

    let ext = {
        let e = extension_of(media_src);
        if e.is_empty() {
            models::ext_for_mime(mime).unwrap_or("bin").to_string()
        } else {
            e
        }
    };

    let media_rel = format!("{kind_dir}/{prefix}/{id}.{ext}");
    let media_dest = cfg.library_dir.join(&media_rel);
    tokio::fs::create_dir_all(media_dest.parent().unwrap()).await?;
    tokio::fs::rename(media_src, &media_dest)
        .await
        .with_context(|| format!("moving {} into library", media_src.display()))?;

    let thumb_rel = if let Some(src) = thumb_src {
        let rel = format!(".thumbs/{prefix}/{id}.jpg");
        let dest = cfg.library_dir.join(&rel);
        tokio::fs::create_dir_all(dest.parent().unwrap()).await?;
        match make_jpeg_thumb(cfg, src, &dest).await {
            Ok(()) => Some(rel),
            Err(err) => {
                tracing::warn!(%err, "thumbnail generation failed; item kept without thumb");
                None
            }
        }
    } else if kind == "picture" {
        let rel = format!(".thumbs/{prefix}/{id}.jpg");
        let dest = cfg.library_dir.join(&rel);
        tokio::fs::create_dir_all(dest.parent().unwrap()).await?;
        match make_jpeg_thumb(cfg, &media_dest, &dest).await {
            Ok(()) => Some(rel),
            Err(err) => {
                tracing::warn!(%err, "picture thumbnail failed");
                None
            }
        }
    } else if kind == "book" {
        let rel = format!(".thumbs/{prefix}/{id}.jpg");
        let dest = cfg.library_dir.join(&rel);
        tokio::fs::create_dir_all(dest.parent().unwrap()).await?;
        match book_cover(cfg, &media_dest, &dest).await {
            Ok(()) => Some(rel),
            Err(err) => {
                tracing::warn!(%err, "book cover extraction failed");
                None
            }
        }
    } else {
        None
    };

    let bytes = tokio::fs::metadata(&media_dest)
        .await
        .map(|m| m.len() as i64)
        .unwrap_or(0);
    let now = db::now_ms();

    sqlx::query(
        "INSERT INTO items
           (id, kind, title, source_url, path, thumb_path, mime, bytes,
            duration_ms, width, height, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    )
    .bind(&id)
    .bind(kind)
    .bind(title)
    .bind(source_url)
    .bind(&media_rel)
    .bind(&thumb_rel)
    .bind(mime)
    .bind(bytes)
    .bind(duration_ms)
    .bind(width)
    .bind(height)
    .bind(now)
    .bind(now)
    .execute(pool)
    .await?;

    let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = ?")
        .bind(&id)
        .fetch_one(pool)
        .await?;

    Ok(item)
}

/// Existing book rows added before cover support get a cover on the next start.
pub async fn backfill_book_covers(pool: &sqlx::SqlitePool, cfg: &Config) -> Result<usize> {
    let rows = sqlx::query_as::<_, (String, String)>(
        "SELECT id, path FROM items
          WHERE kind = 'book' AND (thumb_path IS NULL OR thumb_path = '')",
    )
    .fetch_all(pool)
    .await?;

    let mut done = 0;
    for (id, path) in rows {
        let Some(src) = safe_join(&cfg.library_dir, &path) else {
            continue;
        };
        if !src.exists() || id.len() < 2 {
            continue;
        }
        let rel = format!(".thumbs/{}/{id}.jpg", &id[..2]);
        let dest = cfg.library_dir.join(&rel);
        if let Some(parent) = dest.parent() {
            tokio::fs::create_dir_all(parent).await?;
        }
        match book_cover(cfg, &src, &dest).await {
            Ok(()) => {
                sqlx::query("UPDATE items SET thumb_path = ?, updated_at = ? WHERE id = ?")
                    .bind(&rel)
                    .bind(db::now_ms())
                    .bind(&id)
                    .execute(pool)
                    .await?;
                done += 1;
            }
            Err(err) => tracing::warn!(item = %id, %err, "book cover backfill skipped"),
        }
    }
    Ok(done)
}

/// Attach an item to a playlist or gallery at the end. Idempotent.
pub async fn attach_to_collection(
    pool: &sqlx::SqlitePool,
    collection_id: &str,
    item_id: &str,
) -> Result<()> {
    let now = db::now_ms();
    let mut tx = pool.begin().await?;
    let start: i64 = sqlx::query_scalar(
        "SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlist_id = ?",
    )
    .bind(collection_id)
    .fetch_one(&mut *tx)
    .await?;
    sqlx::query(
        "INSERT OR IGNORE INTO playlist_items
           (playlist_id, item_id, position, added_at)
         VALUES (?, ?, ?, ?)",
    )
    .bind(collection_id)
    .bind(item_id)
    .bind(start + 1)
    .bind(now)
    .execute(&mut *tx)
    .await?;
    sqlx::query("UPDATE playlists SET updated_at = ? WHERE id = ?")
        .bind(now)
        .bind(collection_id)
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;
    Ok(())
}

/// Remove an item's files (best effort) and its row. FTS triggers handle search.
pub async fn remove_item(
    pool: &sqlx::SqlitePool,
    cfg: &Config,
    item: &Item,
    delete_file: bool,
) -> Result<()> {
    if delete_file {
        if let Some(path) = safe_join(&cfg.library_dir, &item.path) {
            let _ = tokio::fs::remove_file(path).await;
        }
        if let Some(thumb) = &item.thumb_path {
            if let Some(path) = safe_join(&cfg.library_dir, thumb) {
                let _ = tokio::fs::remove_file(path).await;
            }
        }
    }
    sqlx::query("DELETE FROM items WHERE id = ?")
        .bind(&item.id)
        .execute(pool)
        .await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::Role;
    use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};

    async fn test_db(dir: &Path) -> sqlx::SqlitePool {
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect_with(
                SqliteConnectOptions::new()
                    .filename(dir.join("test.db"))
                    .create_if_missing(true)
                    .foreign_keys(true),
            )
            .await
            .unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        pool
    }

    fn test_config(dir: &Path) -> Config {
        Config {
            library_dir: dir.to_path_buf(),
            db_path: dir.join("test.db"),
            bind: "127.0.0.1:0".into(),
            web_origin: "http://localhost:3000".into(),
            max_concurrent: 1,
            max_queued: 10,
            max_download_mb: 10,
            heartbeat_s: 30,
            lease_s: 60,
            role: Role::Combined,
            ytdlp: "yt-dlp".into(),
            ffmpeg: "ffmpeg".into(),
            opener: "xdg-open".into(),
        }
    }

    #[test]
    fn rejects_path_traversal() {
        let root = Path::new("/library");
        assert!(safe_join(root, "../etc/passwd").is_none());
        assert!(safe_join(root, "music/../../etc/passwd").is_none());
        assert!(safe_join(root, "/etc/passwd").is_none());
    }

    #[test]
    fn accepts_library_relative_paths() {
        let root = Path::new("/library");
        assert_eq!(
            safe_join(root, "music/ab/id.mp3").unwrap(),
            Path::new("/library/music/ab/id.mp3")
        );
    }

    #[test]
    fn extension_lowercases() {
        assert_eq!(extension_of(Path::new("a/b/X.MP4")), "mp4");
        assert_eq!(extension_of(Path::new("noext")), "");
    }

    /// Regression: deleting a downloaded item used to fail with a foreign-key
    /// error because `jobs.item_id` had no ON DELETE action.
    #[tokio::test]
    async fn deletes_an_item_that_has_a_job() {
        let dir = std::env::temp_dir().join(format!("mm-del-{}", uuid::Uuid::now_v7()));
        std::fs::create_dir_all(&dir).unwrap();
        let pool = test_db(&dir).await;
        let cfg = test_config(&dir);
        let now = db::now_ms();

        let rel = "music/ab/test.mp3";
        let file = dir.join(rel);
        std::fs::create_dir_all(file.parent().unwrap()).unwrap();
        std::fs::write(&file, b"audio").unwrap();

        sqlx::query(
            "INSERT INTO items (id, kind, title, path, mime, bytes, created_at, updated_at)
             VALUES ('item-1', 'music', 'Track', ?, 'audio/mpeg', 5, ?, ?)",
        )
        .bind(rel)
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO jobs
               (id, dedupe_key, url, kind, mode, status, scheduled_at, created_at, updated_at, item_id)
             VALUES ('job-1', 'k', 'https://example.com', 'music', 'full', 'done', ?, ?, ?, 'item-1')",
        )
        .bind(now)
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();

        let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = 'item-1'")
            .fetch_one(&pool)
            .await
            .unwrap();
        let outcome = remove_item(&pool, &cfg, &item, true).await;
        assert!(outcome.is_ok(), "delete failed: {:?}", outcome.err());

        let items: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM items")
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(items, 0);
        assert!(!file.exists(), "file should be gone from disk");

        let job_item: Option<String> =
            sqlx::query_scalar("SELECT item_id FROM jobs WHERE id = 'job-1'")
                .fetch_one(&pool)
                .await
                .unwrap();
        assert_eq!(job_item, None, "the job should survive with no item link");

        let _ = std::fs::remove_dir_all(&dir);
    }

    /// Attaching a finished download to a collection is idempotent.
    #[tokio::test]
    async fn attaches_an_item_to_a_collection_once() {
        let dir = std::env::temp_dir().join(format!("mm-attach-{}", uuid::Uuid::now_v7()));
        std::fs::create_dir_all(&dir).unwrap();
        let pool = test_db(&dir).await;
        let now = db::now_ms();

        sqlx::query(
            "INSERT INTO items (id, kind, title, path, mime, bytes, created_at, updated_at)
             VALUES ('item-a', 'music', 'Track', 'music/aa/x.mp3', 'audio/mpeg', 1, ?, ?)",
        )
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO playlists (id, name, kind, created_at, updated_at)
             VALUES ('col-1', 'Mix', 'playlist', ?, ?)",
        )
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();

        attach_to_collection(&pool, "col-1", "item-a").await.unwrap();
        attach_to_collection(&pool, "col-1", "item-a").await.unwrap();

        let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM playlist_items")
            .fetch_one(&pool)
            .await
            .unwrap();
        let position: i64 =
            sqlx::query_scalar("SELECT position FROM playlist_items LIMIT 1")
                .fetch_one(&pool)
                .await
                .unwrap();
        assert_eq!(count, 1);
        assert_eq!(position, 0);

        let _ = std::fs::remove_dir_all(&dir);
    }

    /// Deleting an item must also remove it from every playlist.
    #[tokio::test]
    async fn deleting_an_item_cascades_out_of_playlists() {
        let dir = std::env::temp_dir().join(format!("mm-pl-{}", uuid::Uuid::now_v7()));
        std::fs::create_dir_all(&dir).unwrap();
        let pool = test_db(&dir).await;
        let cfg = test_config(&dir);
        let now = db::now_ms();

        let rel = "music/ab/pl.mp3";
        let file = dir.join(rel);
        std::fs::create_dir_all(file.parent().unwrap()).unwrap();
        std::fs::write(&file, b"audio").unwrap();

        sqlx::query(
            "INSERT INTO items (id, kind, title, path, mime, bytes, created_at, updated_at)
             VALUES ('item-2', 'music', 'In playlist', ?, 'audio/mpeg', 5, ?, ?)",
        )
        .bind(rel)
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO playlists (id, name, created_at, updated_at) VALUES ('p1','Mix',?,?)",
        )
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO playlist_items (playlist_id, item_id, position, added_at)
             VALUES ('p1','item-2',0,?)",
        )
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();

        let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = 'item-2'")
            .fetch_one(&pool)
            .await
            .unwrap();
        remove_item(&pool, &cfg, &item, true).await.unwrap();

        let left: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM playlist_items")
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(left, 0);
        let playlists: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM playlists")
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(playlists, 1, "the playlist itself stays");

        let _ = std::fs::remove_dir_all(&dir);
    }
}
