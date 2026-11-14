use std::path::Path;

use axum::{
    extract::{DefaultBodyLimit, Multipart, Path as AxumPath, Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use serde::Deserialize;
use serde_json::json;
use sha2::{Digest, Sha256};
use sqlx::{QueryBuilder, Sqlite};
use tower_http::cors::CorsLayer;

use crate::db;
use crate::media;
use crate::models::{self, Item, ItemDto, Job};
use crate::state::AppState;

pub struct ApiError {
    status: StatusCode,
    code: &'static str,
    message: String,
}

impl ApiError {
    fn bad_request(code: &'static str, message: impl Into<String>) -> Self {
        Self {
            status: StatusCode::BAD_REQUEST,
            code,
            message: message.into(),
        }
    }
    fn not_found(message: impl Into<String>) -> Self {
        Self {
            status: StatusCode::NOT_FOUND,
            code: "not_found",
            message: message.into(),
        }
    }
    fn conflict(code: &'static str, message: impl Into<String>) -> Self {
        Self {
            status: StatusCode::CONFLICT,
            code,
            message: message.into(),
        }
    }
    fn internal(err: impl std::fmt::Display) -> Self {
        tracing::error!(%err, "internal error");
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            code: "internal",
            message: "internal error".to_string(),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (
            self.status,
            Json(json!({ "error": self.message, "code": self.code })),
        )
            .into_response()
    }
}

impl From<sqlx::Error> for ApiError {
    fn from(err: sqlx::Error) -> Self {
        ApiError::internal(err)
    }
}

impl From<anyhow::Error> for ApiError {
    fn from(err: anyhow::Error) -> Self {
        ApiError::internal(err)
    }
}

pub fn router(state: AppState) -> Router {
    let origin: axum::http::HeaderValue = state
        .cfg
        .web_origin
        .parse()
        .unwrap_or_else(|_| "http://localhost:3000".parse().unwrap());

    let cors = CorsLayer::new()
        .allow_origin(origin)
        .allow_methods(tower_http::cors::Any)
        .allow_headers(tower_http::cors::Any);

    let files = Router::new()
        .nest_service(
            "/music",
            tower_http::services::ServeDir::new(state.cfg.library_dir.join("music")),
        )
        .nest_service(
            "/video",
            tower_http::services::ServeDir::new(state.cfg.library_dir.join("video")),
        )
        .nest_service(
            "/picture",
            tower_http::services::ServeDir::new(state.cfg.library_dir.join("picture")),
        )
        .nest_service(
            "/book",
            tower_http::services::ServeDir::new(state.cfg.library_dir.join("book")),
        );

    Router::new()
        .route("/api/health", get(health))
        .route("/api/metrics", get(metrics))
        .route("/api/items", get(list_items))
        .route("/api/items/{id}", get(get_item).patch(patch_item).delete(delete_item))
        .route("/api/jobs", get(list_jobs).post(create_job))
        .route("/api/jobs/{id}", get(get_job).delete(cancel_job))
        .route(
            "/api/uploads",
            post(upload).layer(DefaultBodyLimit::max(
                state.cfg.max_download_bytes() as usize,
            )),
        )
        .nest("/files", files)
        .nest_service(
            "/thumbs",
            tower_http::services::ServeDir::new(state.cfg.thumbs_dir()),
        )
        .layer(cors)
        .layer(tower_http::trace::TraceLayer::new_for_http())
        .with_state(state)
}

#[derive(Deserialize)]
struct ListParams {
    kind: Option<String>,
    q: Option<String>,
    limit: Option<i64>,
    cursor: Option<String>,
}

fn fts_terms(q: &str) -> Vec<String> {
    q.split_whitespace()
        .map(|t| {
            t.chars()
                .filter(|c| c.is_alphanumeric() || *c == '\'' )
                .collect::<String>()
        })
        .filter(|t| !t.is_empty())
        .map(|t| format!("\"{t}\"*"))
        .collect()
}

fn parse_cursor(cursor: &Option<String>) -> Option<(i64, i64)> {
    let raw = cursor.as_deref()?;
    let (ts, rowid) = raw.split_once(':')?;
    Some((ts.parse().ok()?, rowid.parse().ok()?))
}

async fn list_items(
    State(state): State<AppState>,
    Query(params): Query<ListParams>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let limit = params.limit.unwrap_or(60).clamp(1, 200);
    let cursor = parse_cursor(&params.cursor);
    let terms = params.q.as_deref().map(fts_terms).unwrap_or_default();
    let use_fts = state.fts && !terms.is_empty();
    let like = params
        .q
        .as_deref()
        .filter(|q| !q.trim().is_empty())
        .map(|q| format!("%{}%", q.trim()));

    let mut count: QueryBuilder<Sqlite> = QueryBuilder::new("SELECT COUNT(*) FROM items i");
    if use_fts {
        count.push(" JOIN items_fts ON items_fts.rowid = i.rowid");
    }
    count.push(" WHERE 1 = 1");
    if let Some(kind) = params.kind.as_deref().filter(|k| models::valid_kind(k)) {
        count.push(" AND i.kind = ").push_bind(kind.to_string());
    }
    if use_fts {
        count
            .push(" AND items_fts MATCH ")
            .push_bind(terms.join(" AND "));
    } else if let Some(like) = &like {
        count.push(" AND i.title LIKE ").push_bind(like.clone());
    }
    let total: i64 = count.build_query_scalar().fetch_one(&state.pool).await?;

    let mut rows: QueryBuilder<Sqlite> =
        QueryBuilder::new("SELECT i.* FROM items i");
    if use_fts {
        rows.push(" JOIN items_fts ON items_fts.rowid = i.rowid");
    }
    rows.push(" WHERE 1 = 1");
    if let Some(kind) = params.kind.as_deref().filter(|k| models::valid_kind(k)) {
        rows.push(" AND i.kind = ").push_bind(kind.to_string());
    }
    if use_fts {
        rows.push(" AND items_fts MATCH ")
            .push_bind(terms.join(" AND "));
    } else if let Some(like) = &like {
        rows.push(" AND i.title LIKE ").push_bind(like.clone());
    }
    if let Some((ts, rowid)) = cursor {
        rows.push(" AND (i.created_at, i.rowid) < (")
            .push_bind(ts)
            .push(", ")
            .push_bind(rowid)
            .push(")");
    }
    rows.push(" ORDER BY i.created_at DESC, i.rowid DESC LIMIT ")
        .push_bind(limit + 1);

    let mut items: Vec<Item> = rows.build_query_as().fetch_all(&state.pool).await?;
    let has_more = items.len() as i64 > limit;
    if has_more {
        items.truncate(limit as usize);
    }
    let next_cursor = if has_more {
        items
            .last()
            .map(|i| format!("{}:{}", i.created_at, i.rowid))
    } else {
        None
    };

    let dtos: Vec<ItemDto> = items.iter().map(|i| i.to_dto()).collect();
    Ok(Json(json!({
        "items": dtos,
        "total": total,
        "nextCursor": next_cursor,
    })))
}

async fn get_item(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
) -> Result<Json<ItemDto>, ApiError> {
    let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found("item not found"))?;
    Ok(Json(item.to_dto()))
}

#[derive(Deserialize)]
struct PatchItem {
    title: String,
}

async fn patch_item(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Json(body): Json<PatchItem>,
) -> Result<Json<ItemDto>, ApiError> {
    let title = body.title.trim();
    if title.is_empty() {
        return Err(ApiError::bad_request("invalid_title", "title cannot be empty"));
    }
    let result = sqlx::query("UPDATE items SET title = ?, updated_at = ? WHERE id = ?")
        .bind(title)
        .bind(db::now_ms())
        .bind(&id)
        .execute(&state.pool)
        .await?;
    if result.rows_affected() == 0 {
        return Err(ApiError::not_found("item not found"));
    }
    let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = ?")
        .bind(&id)
        .fetch_one(&state.pool)
        .await?;
    Ok(Json(item.to_dto()))
}

#[derive(Deserialize)]
struct DeleteParams {
    delete_file: Option<bool>,
}

async fn delete_item(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Query(params): Query<DeleteParams>,
) -> Result<StatusCode, ApiError> {
    let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found("item not found"))?;
    media::remove_item(
        &state.pool,
        &state.cfg,
        &item,
        params.delete_file.unwrap_or(false),
    )
    .await?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
struct NewJob {
    url: String,
    kind: String,
    mode: Option<String>,
}

fn dedupe_key(url: &str, kind: &str, mode: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(url.trim().as_bytes());
    hasher.update(b"|");
    hasher.update(kind.as_bytes());
    hasher.update(b"|");
    hasher.update(mode.as_bytes());
    hex::encode(hasher.finalize())
}

async fn create_job(
    State(state): State<AppState>,
    Json(body): Json<NewJob>,
) -> Result<(StatusCode, Json<serde_json::Value>), ApiError> {
    let url = body.url.trim();
    if !(url.starts_with("http://") || url.starts_with("https://")) {
        return Err(ApiError::bad_request("invalid_url", "url must start with http:// or https://"));
    }
    if !models::valid_kind(&body.kind) {
        return Err(ApiError::bad_request(
            "invalid_kind",
            "kind must be music, video, picture or book",
        ));
    }
    let mode = body
        .mode
        .clone()
        .filter(|m| m == "audio+thumb" || m == "full")
        .unwrap_or_else(|| models::default_mode(&body.kind).to_string());
    if mode == "audio+thumb" && body.kind != "music" {
        return Err(ApiError::bad_request(
            "invalid_mode",
            "audio+thumb only applies to music",
        ));
    }

    let key = dedupe_key(url, &body.kind, &mode);
    if let Some(existing) = sqlx::query_as::<_, Job>(
        "SELECT * FROM jobs WHERE dedupe_key = ? AND status IN ('queued','running') LIMIT 1",
    )
    .bind(&key)
    .fetch_optional(&state.pool)
    .await?
    {
        return Err(ApiError::conflict(
            "duplicate_job",
            format!("already downloading (job {})", existing.id),
        ));
    }

    let active = db::count_by_status(&state.pool, "queued").await
        + db::count_by_status(&state.pool, "running").await;
    if active >= state.cfg.max_queued {
        return Err(ApiError {
            status: StatusCode::TOO_MANY_REQUESTS,
            code: "queue_full",
            message: format!("queue is full ({} jobs)", state.cfg.max_queued),
        });
    }

    let id = uuid::Uuid::now_v7().to_string();
    let now = db::now_ms();
    sqlx::query(
        "INSERT INTO jobs (id, dedupe_key, url, kind, mode, status, scheduled_at, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, 'queued', ?, ?, ?)",
    )
    .bind(&id)
    .bind(&key)
    .bind(url)
    .bind(&body.kind)
    .bind(&mode)
    .bind(now)
    .bind(now)
    .bind(now)
    .execute(&state.pool)
    .await?;

    let job = sqlx::query_as::<_, Job>("SELECT * FROM jobs WHERE id = ?")
        .bind(&id)
        .fetch_one(&state.pool)
        .await?;
    Ok((StatusCode::CREATED, Json(json!(job.to_dto()))))
}

#[derive(Deserialize)]
struct JobListParams {
    status: Option<String>,
    active: Option<u8>,
    limit: Option<i64>,
}

async fn list_jobs(
    State(state): State<AppState>,
    Query(params): Query<JobListParams>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let limit = params.limit.unwrap_or(50).clamp(1, 200);
    let mut qb: QueryBuilder<Sqlite> = QueryBuilder::new("SELECT * FROM jobs WHERE 1 = 1");
    if params.active.unwrap_or(0) == 1 {
        qb.push(" AND status IN ('queued','running')");
    } else if let Some(status) = params.status.as_deref() {
        qb.push(" AND status = ").push_bind(status.to_string());
    }
    qb.push(" ORDER BY created_at DESC LIMIT ").push_bind(limit);

    let jobs: Vec<Job> = qb.build_query_as().fetch_all(&state.pool).await?;
    let dtos: Vec<_> = jobs.iter().map(|j| j.to_dto()).collect();
    Ok(Json(json!({ "jobs": dtos })))
}

async fn get_job(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let job = sqlx::query_as::<_, Job>("SELECT * FROM jobs WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found("job not found"))?;
    Ok(Json(json!(job.to_dto())))
}

async fn cancel_job(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let result = sqlx::query(
        "UPDATE jobs SET status = 'cancelled', updated_at = ?
          WHERE id = ? AND status IN ('queued','running')",
    )
    .bind(db::now_ms())
    .bind(&id)
    .execute(&state.pool)
    .await?;
    if result.rows_affected() == 0 {
        return Err(ApiError::conflict(
            "not_cancellable",
            "job does not exist or already finished",
        ));
    }
    let job = sqlx::query_as::<_, Job>("SELECT * FROM jobs WHERE id = ?")
        .bind(&id)
        .fetch_one(&state.pool)
        .await?;
    Ok(Json(json!(job.to_dto())))
}

#[derive(Deserialize)]
struct UploadParams {
    kind: String,
    title: Option<String>,
}

async fn upload(
    State(state): State<AppState>,
    Query(params): Query<UploadParams>,
    mut multipart: Multipart,
) -> Result<Json<ItemDto>, ApiError> {
    if !models::valid_kind(&params.kind) {
        return Err(ApiError::bad_request("invalid_kind", "unknown kind"));
    }

    let dir = state.cfg.incoming_dir().join(format!("upload-{}", uuid::Uuid::now_v7()));
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(ApiError::internal)?;

    let mut saved: Option<std::path::PathBuf> = None;
    let mut original_name = String::new();
    let mut mime = "application/octet-stream".to_string();
    let max = state.cfg.max_download_bytes();

    while let Some(mut field) = multipart.next_field().await.map_err(ApiError::internal)? {
        if field.name() != Some("file") {
            continue;
        }
        original_name = field.file_name().unwrap_or("upload.bin").to_string();
        if let Some(ct) = field.content_type() {
            mime = ct.to_string();
        }
        let ext = Path::new(&original_name)
            .extension()
            .and_then(|e| e.to_str())
            .map(|e| e.to_ascii_lowercase())
            .filter(|e| !e.is_empty())
            .unwrap_or_else(|| models::ext_for_mime(&mime).unwrap_or("bin").to_string());
        let dest = dir.join(format!("source.{ext}"));
        let mut file = tokio::fs::File::create(&dest)
            .await
            .map_err(ApiError::internal)?;
        let mut written: u64 = 0;
        while let Some(chunk) = field.chunk().await.map_err(ApiError::internal)? {
            written += chunk.len() as u64;
            if written > max {
                return Err(ApiError {
                    status: StatusCode::PAYLOAD_TOO_LARGE,
                    code: "too_large",
                    message: format!("file exceeds {} MB", state.cfg.max_download_mb),
                });
            }
            use tokio::io::AsyncWriteExt;
            file.write_all(&chunk).await.map_err(ApiError::internal)?;
        }
        use tokio::io::AsyncWriteExt;
        file.flush().await.map_err(ApiError::internal)?;
        saved = Some(dest);
    }

    let src = saved.ok_or_else(|| ApiError::bad_request("no_file", "expected a `file` field"))?;
    let title = params.title.unwrap_or_else(|| {
        Path::new(&original_name)
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("upload")
            .to_string()
    });
    let ext = media::extension_of(&src);
    let mime = if mime == "application/octet-stream" {
        models::mime_for_ext(&ext).to_string()
    } else {
        mime
    };

    let item = media::finalize(
        &state.pool,
        &state.cfg,
        &params.kind,
        &title,
        &src,
        None,
        &mime,
        None,
        None,
        None,
        None,
    )
    .await?;
    let _ = tokio::fs::remove_dir_all(&dir).await;

    Ok(Json(item.to_dto()))
}

async fn health(State(state): State<AppState>) -> Result<Json<serde_json::Value>, ApiError> {
    let db_ok = sqlx::query_scalar::<_, i64>("SELECT 1")
        .fetch_one(&state.pool)
        .await
        .is_ok();
    let items: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM items")
        .fetch_one(&state.pool)
        .await
        .unwrap_or(0);
    let queued = db::count_by_status(&state.pool, "queued").await;
    let running = db::count_by_status(&state.pool, "running").await;

    Ok(Json(json!({
        "status": if db_ok { "ok" } else { "degraded" },
        "db": if db_ok { "ok" } else { "unavailable" },
        "items": items,
        "queueDepth": queued,
        "active": running,
        "fts": state.fts,
    })))
}

async fn metrics(State(state): State<AppState>) -> Result<String, ApiError> {
    let items: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM items")
        .fetch_one(&state.pool)
        .await
        .unwrap_or(0);
    let queued = db::count_by_status(&state.pool, "queued").await;
    let running = db::count_by_status(&state.pool, "running").await;
    let done = db::count_by_status(&state.pool, "done").await;
    let failed = db::count_by_status(&state.pool, "failed").await;

    Ok(format!(
        "# TYPE mm_items_total gauge\nmm_items_total {items}\n\
         # TYPE mm_jobs_active gauge\nmm_jobs_active {running}\n\
         # TYPE mm_jobs_queued gauge\nmm_jobs_queued {queued}\n\
         # TYPE mm_jobs_done_total counter\nmm_jobs_done_total {done}\n\
         # TYPE mm_jobs_failed_total counter\nmm_jobs_failed_total {failed}\n"
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fts_terms_are_quoted_prefixes() {
        assert_eq!(fts_terms("dark side"), vec!["\"dark\"*", "\"side\"*"]);
        assert!(fts_terms("   ").is_empty());
        let injected = fts_terms("\"; DROP TABLE items; --");
        assert!(injected.iter().all(|t| t.starts_with('"') && t.ends_with('*')));
        assert!(injected.iter().all(|t| !t.contains(';')));
    }

    #[test]
    fn dedupe_key_is_stable_and_input_sensitive() {
        assert_eq!(dedupe_key("u", "music", "full"), dedupe_key("u", "music", "full"));
        assert_ne!(dedupe_key("u", "music", "full"), dedupe_key("u", "music", "audio+thumb"));
    }
}
