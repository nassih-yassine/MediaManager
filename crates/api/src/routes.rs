use std::path::Path;
use std::process::Stdio;

use axum::{
    extract::{DefaultBodyLimit, Multipart, Path as AxumPath, Query, State},
    http::StatusCode,
    response::{
        sse::{Event, KeepAlive, Sse},
        IntoResponse, Response,
    },
    routing::{get, post, put},
    Json, Router,
};
use futures_util::{stream, Stream};
use serde::Deserialize;
use serde_json::json;
use sha2::{Digest, Sha256};
use sqlx::{QueryBuilder, Sqlite};
use std::convert::Infallible;
use tower_http::cors::CorsLayer;

use crate::db;
use crate::media;
use crate::models::{
    self, Item, ItemDto, Job, Playlist, PlaylistDetailDto, PlaylistSummaryDto, PlaylistSummaryRow,
};
use crate::state::AppState;

#[derive(Debug)]
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

impl From<std::io::Error> for ApiError {
    fn from(err: std::io::Error) -> Self {
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
        .route("/api/events", get(sse_events))
        .route("/api/metrics", get(metrics))
        .route("/api/items", get(list_items))
        .route(
            "/api/items/{id}",
            get(get_item).patch(patch_item).delete(delete_item),
        )
        .route("/api/items/{id}/open", post(open_item))
        .route("/api/items/{id}/progress", post(update_item_progress))
        .route("/api/items/{id}/playlists", get(item_playlists))
        .route("/api/jobs", get(list_jobs).post(create_job))
        .route("/api/jobs/clear", post(clear_jobs))
        .route("/api/jobs/{id}", get(get_job).delete(cancel_job))
        .route("/api/playlists", get(list_playlists).post(create_playlist))
        .route(
            "/api/playlists/{id}",
            get(get_playlist)
                .patch(rename_playlist)
                .delete(delete_playlist),
        )
        .route("/api/playlists/{id}/items", post(add_playlist_items))
        .route(
            "/api/playlists/{id}/items/{item_id}",
            axum::routing::delete(remove_playlist_item),
        )
        .route("/api/playlists/{id}/order", put(reorder_playlist))
        .route(
            "/api/playlists/{id}/cover",
            put(set_cover_from_item).delete(remove_cover),
        )
        .route(
            "/api/playlists/{id}/cover/upload",
            post(upload_cover).layer(DefaultBodyLimit::max(
                state.cfg.max_download_bytes() as usize
            )),
        )
        .route(
            "/api/uploads",
            post(upload).layer(DefaultBodyLimit::max(
                state.cfg.max_download_bytes() as usize
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
    sort: Option<String>,
    ungrouped: Option<u8>,
    hide_pictures: Option<u8>,
}

fn fts_terms(q: &str) -> Vec<String> {
    q.split_whitespace()
        .map(|t| {
            t.chars()
                .filter(|c| c.is_alphanumeric() || *c == '\'')
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
    if let Some(kind) = params.kind.as_deref() {
        if !models::valid_kind(kind) {
            return Err(ApiError::bad_request(
                "invalid_kind",
                "kind must be music, video, picture or book",
            ));
        }
    }
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
    if params.ungrouped.unwrap_or(0) == 1 {
        count.push(
            " AND NOT EXISTS (SELECT 1 FROM playlist_items pi
                JOIN playlists p ON p.id = pi.playlist_id
                WHERE pi.item_id = i.id AND p.kind = 'gallery')",
        );
    }
    if params.hide_pictures.unwrap_or(0) == 1 {
        count.push(" AND i.kind != 'picture'");
    }
    let total: i64 = count.build_query_scalar().fetch_one(&state.pool).await?;

    let mut rows: QueryBuilder<Sqlite> = QueryBuilder::new("SELECT i.* FROM items i");
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
    if params.ungrouped.unwrap_or(0) == 1 {
        rows.push(
            " AND NOT EXISTS (SELECT 1 FROM playlist_items pi
                JOIN playlists p ON p.id = pi.playlist_id
                WHERE pi.item_id = i.id AND p.kind = 'gallery')",
        );
    }
    if params.hide_pictures.unwrap_or(0) == 1 {
        rows.push(" AND i.kind != 'picture'");
    }

    let sort = params.sort.as_deref().unwrap_or("newest");
    let (order_sql, ascending) = match sort {
        "oldest" => ("i.created_at ASC, i.rowid ASC", true),
        "title" => ("i.title COLLATE NOCASE ASC", false),
        "largest" => ("i.bytes DESC", false),
        "duration" => ("i.duration_ms DESC", false),
        _ => ("i.created_at DESC, i.rowid DESC", false),
    };
    let cursor_sortable = sort == "newest" || sort == "oldest";

    if let Some((ts, rowid)) = cursor.filter(|_| cursor_sortable) {
        rows.push(if ascending {
            " AND (i.created_at, i.rowid) > ("
        } else {
            " AND (i.created_at, i.rowid) < ("
        })
        .push_bind(ts)
        .push(", ")
        .push_bind(rowid)
        .push(")");
    }
    rows.push(" ORDER BY ").push(order_sql);
    rows.push(" LIMIT ").push_bind(limit + 1);

    let mut items: Vec<Item> = rows.build_query_as().fetch_all(&state.pool).await?;
    let has_more = items.len() as i64 > limit;
    if has_more {
        items.truncate(limit as usize);
    }
    let next_cursor = if has_more && cursor_sortable {
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
        return Err(ApiError::bad_request(
            "invalid_title",
            "title cannot be empty",
        ));
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
    state.emit("item.updated", &item.id, None);
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
    state.emit("item.deleted", &item.id, None);
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProgressBody {
    position_ms: i64,
}
async fn update_item_progress(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Json(body): Json<ProgressBody>,
) -> Result<StatusCode, ApiError> {
    // Guard against a bogus client writing nonsense; 24h is a sane ceiling.
    let position = body.position_ms.clamp(0, 86_400_000);
    let result = sqlx::query("UPDATE items SET position_ms = ?, last_played_at = ? WHERE id = ?")
        .bind(position)
        .bind(db::now_ms())
        .bind(&id)
        .execute(&state.pool)
        .await?;
    if result.rows_affected() == 0 {
        return Err(ApiError::not_found("item not found"));
    }
    Ok(StatusCode::NO_CONTENT)
}

/// Playlists that could contain this item, with a membership flag. Powers the
/// add-to-playlist picker so it can show checkmarks without N requests.
async fn item_playlists(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Query(params): Query<PlaylistParams>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let kind_filter = params
        .kind
        .as_deref()
        .map(|value| collection_kind(Some(value)))
        .transpose()?;
    let exists = sqlx::query_scalar::<_, i64>("SELECT 1 FROM items WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .is_some();
    if !exists {
        return Err(ApiError::not_found("item not found"));
    }

    let rows: Vec<(String, String, String, i64, i64)> = sqlx::query_as(
        "SELECT p.id, p.name, p.kind,
                (SELECT COUNT(*) FROM playlist_items pi WHERE pi.playlist_id = p.id) AS item_count,
                EXISTS(SELECT 1 FROM playlist_items x
                        WHERE x.playlist_id = p.id AND x.item_id = ?) AS contains
           FROM playlists p
          ORDER BY p.name COLLATE NOCASE",
    )
    .bind(&id)
    .fetch_all(&state.pool)
    .await?;

    let playlists: Vec<serde_json::Value> = rows
        .into_iter()
        .filter(|(_, _, kind, _, _)| kind_filter.is_none_or(|filter| kind == filter))
        .map(|(pid, name, kind, count, contains)| {
            json!({
                "id": pid,
                "name": name,
                "kind": kind,
                "itemCount": count,
                "contains": contains != 0,
            })
        })
        .collect();

    Ok(Json(json!({ "playlists": playlists })))
}

async fn open_item(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
) -> Result<StatusCode, ApiError> {
    let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found("item not found"))?;

    let path = media::safe_join(&state.cfg.library_dir, &item.path)
        .ok_or_else(|| ApiError::not_found("file path is invalid"))?;
    if !path.exists() {
        return Err(ApiError::not_found("file is missing on disk"));
    }

    // The API is loopback-only; launching the user's default handler here is
    // what makes "open in native app" possible from the browser.
    std::process::Command::new(&state.cfg.opener)
        .arg(&path)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map_err(ApiError::internal)?;

    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct NewJob {
    url: String,
    kind: String,
    mode: Option<String>,
    collection_id: Option<String>,
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

/// Music is always audio+thumb; full video is its own kind. Rejecting the
/// mismatch here keeps the API honest even though the UI no longer offers it.
fn resolve_job_mode(
    kind: &str,
    requested: Option<&str>,
) -> Result<String, (&'static str, &'static str)> {
    if !models::valid_kind(kind) {
        return Err(("invalid_kind", "kind must be music, video, picture or book"));
    }
    let mode = requested.unwrap_or_else(|| models::default_mode(kind));
    match (kind, mode) {
        ("music", "audio+thumb") => Ok(mode.to_string()),
        ("music", _) => Err((
            "invalid_mode",
            "music is downloaded as audio; choose the video kind for the full file",
        )),
        (_, "full") => Ok(mode.to_string()),
        (_, "audio+thumb") => Err(("invalid_mode", "audio+thumb only applies to music")),
        _ => Err(("invalid_mode", "mode must be audio+thumb or full")),
    }
}

async fn create_job(
    State(state): State<AppState>,
    Json(body): Json<NewJob>,
) -> Result<(StatusCode, Json<serde_json::Value>), ApiError> {
    let url = body.url.trim();
    if !(url.starts_with("http://") || url.starts_with("https://")) {
        return Err(ApiError::bad_request(
            "invalid_url",
            "url must start with http:// or https://",
        ));
    }
    let mode = resolve_job_mode(&body.kind, body.mode.as_deref())
        .map_err(|(code, message)| ApiError::bad_request(code, message))?;

    if let Some(collection_id) = &body.collection_id {
        let target = sqlx::query_scalar::<_, String>("SELECT kind FROM playlists WHERE id = ?")
            .bind(collection_id)
            .fetch_optional(&state.pool)
            .await?;
        let Some(target_kind) = target else {
            return Err(ApiError::not_found("playlist not found"));
        };
        if target_kind == "gallery" && body.kind != "picture" {
            return Err(ApiError::bad_request(
                "gallery_pictures_only",
                "galleries can only contain pictures",
            ));
        }
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
        "INSERT INTO jobs (id, dedupe_key, url, kind, mode, status, scheduled_at, created_at, updated_at, collection_id)
         VALUES (?, ?, ?, ?, ?, 'queued', ?, ?, ?, ?)",
    )
    .bind(&id)
    .bind(&key)
    .bind(url)
    .bind(&body.kind)
    .bind(&mode)
    .bind(now)
    .bind(now)
    .bind(now)
    .bind(&body.collection_id)
    .execute(&state.pool)
    .await?;

    let job = sqlx::query_as::<_, Job>("SELECT * FROM jobs WHERE id = ?")
        .bind(&id)
        .fetch_one(&state.pool)
        .await?;
    Ok((StatusCode::CREATED, Json(json!(job.to_dto()))))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ClearJobsBody {
    scope: String,
}

/// `finished` removes done/failed/cancelled rows, `active` cancels queued and
/// running jobs, `all` does both. Active downloads keep running until their
/// worker notices the cancellation, then their partial files are discarded.
async fn clear_jobs(
    State(state): State<AppState>,
    Json(body): Json<ClearJobsBody>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let now = db::now_ms();
    let mut cancelled = 0u64;
    let mut deleted = 0u64;

    if body.scope == "active" || body.scope == "all" {
        let result = sqlx::query(
            "UPDATE jobs SET status = 'cancelled', updated_at = ?
              WHERE status IN ('queued','running')",
        )
        .bind(now)
        .execute(&state.pool)
        .await?;
        cancelled = result.rows_affected();
    }

    if body.scope == "finished" || body.scope == "all" {
        let result = sqlx::query("DELETE FROM jobs WHERE status IN ('done','failed','cancelled')")
            .execute(&state.pool)
            .await?;
        deleted = result.rows_affected();
    }

    if body.scope != "active" && body.scope != "finished" && body.scope != "all" {
        return Err(ApiError::bad_request(
            "invalid_scope",
            "scope must be active, finished or all",
        ));
    }

    Ok(Json(json!({ "cancelled": cancelled, "deleted": deleted })))
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

    let dir = state
        .cfg
        .incoming_dir()
        .join(format!("upload-{}", uuid::Uuid::now_v7()));
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
    if item.kind == "picture" {
        let _ = media::attach_to_collection(&state.pool, &state.default_gallery, &item.id).await;
        state.emit("collection.changed", &state.default_gallery, None);
    }
    state.emit("item.created", &item.id, None);

    Ok(Json(item.to_dto()))
}

fn collection_kind(raw: Option<&str>) -> Result<&'static str, ApiError> {
    match raw.unwrap_or("playlist") {
        "playlist" => Ok("playlist"),
        "gallery" => Ok("gallery"),
        other => Err(ApiError::bad_request(
            "invalid_kind",
            format!("collection kind must be playlist or gallery, got {other}"),
        )),
    }
}

fn clean_playlist_name(raw: &str) -> Result<String, ApiError> {
    let name = raw.trim();
    if name.is_empty() {
        return Err(ApiError::bad_request(
            "invalid_name",
            "playlist name cannot be empty",
        ));
    }
    if name.chars().count() > 80 {
        return Err(ApiError::bad_request(
            "invalid_name",
            "playlist name is longer than 80 characters",
        ));
    }
    Ok(name.to_string())
}

#[derive(Deserialize)]
struct PlaylistParams {
    kind: Option<String>,
}

async fn list_playlists(
    State(state): State<AppState>,
    Query(params): Query<PlaylistParams>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let kind = params
        .kind
        .as_deref()
        .map(|value| collection_kind(Some(value)))
        .transpose()?;
    let filter = if kind.is_some() {
        " WHERE p.kind = ?"
    } else {
        ""
    };
    let sql = format!(
        "SELECT p.id, p.name, p.kind, p.cover_path, p.created_at, p.updated_at,
                COUNT(pi.item_id) AS item_count,
                (SELECT i.thumb_path
                   FROM playlist_items pi2 JOIN items i ON i.id = pi2.item_id
                  WHERE pi2.playlist_id = p.id AND i.thumb_path IS NOT NULL
                  ORDER BY pi2.position LIMIT 1) AS thumb_path,
                (SELECT json_group_array(thumb_path) FROM (
                   SELECT i.thumb_path
                     FROM playlist_items pi2 JOIN items i ON i.id = pi2.item_id
                    WHERE pi2.playlist_id = p.id AND i.thumb_path IS NOT NULL
                    ORDER BY pi2.position LIMIT 4
                 )) AS thumbs_json
           FROM playlists p
           LEFT JOIN playlist_items pi ON pi.playlist_id = p.id
          {filter}
          GROUP BY p.id
          ORDER BY p.name COLLATE NOCASE"
    );
    let mut query = sqlx::query_as::<_, PlaylistSummaryRow>(&sql);
    if let Some(kind) = kind {
        query = query.bind(kind);
    }
    let rows = query.fetch_all(&state.pool).await?;
    let mut playlists: Vec<PlaylistSummaryDto> = rows.iter().map(|r| r.to_dto()).collect();
    for playlist in &mut playlists {
        playlist.is_default = playlist.id == state.default_gallery;
    }
    Ok(Json(json!({ "playlists": playlists })))
}

#[derive(Deserialize)]
struct NewPlaylist {
    name: String,
    kind: Option<String>,
}

async fn create_playlist(
    State(state): State<AppState>,
    Json(body): Json<NewPlaylist>,
) -> Result<(StatusCode, Json<PlaylistSummaryDto>), ApiError> {
    let name = clean_playlist_name(&body.name)?;
    let kind = collection_kind(body.kind.as_deref())?;
    let id = uuid::Uuid::now_v7().to_string();
    let now = db::now_ms();
    sqlx::query(
        "INSERT INTO playlists (id, name, kind, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?)",
    )
    .bind(&id)
    .bind(&name)
    .bind(kind)
    .bind(now)
    .bind(now)
    .execute(&state.pool)
    .await?;
    state.emit("collection.changed", &id, None);

    Ok((
        StatusCode::CREATED,
        Json(PlaylistSummaryDto {
            id,
            name,
            kind: kind.to_string(),
            is_default: false,
            item_count: 0,
            cover_url: None,
            thumb_url: None,
            thumb_urls: Vec::new(),
            created_at: now,
            updated_at: now,
        }),
    ))
}

async fn get_playlist(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
) -> Result<Json<PlaylistDetailDto>, ApiError> {
    let playlist = sqlx::query_as::<_, Playlist>("SELECT * FROM playlists WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found("playlist not found"))?;

    let items = sqlx::query_as::<_, Item>(
        "SELECT i.* FROM playlist_items pi
           JOIN items i ON i.id = pi.item_id
          WHERE pi.playlist_id = ?
          ORDER BY pi.position, pi.added_at",
    )
    .bind(&id)
    .fetch_all(&state.pool)
    .await?;

    let cover_url = playlist
        .cover_path
        .as_deref()
        .map(models::thumb_url)
        .or_else(|| {
            items
                .iter()
                .find_map(|item| item.thumb_path.as_deref().map(models::thumb_url))
        });
    let is_default = playlist.id == state.default_gallery;

    Ok(Json(PlaylistDetailDto {
        id: playlist.id,
        name: playlist.name,
        kind: playlist.kind,
        is_default,
        cover_url,
        created_at: playlist.created_at,
        updated_at: playlist.updated_at,
        items: items.iter().map(|i| i.to_dto()).collect(),
    }))
}

async fn rename_playlist(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Json(body): Json<NewPlaylist>,
) -> Result<StatusCode, ApiError> {
    let name = clean_playlist_name(&body.name)?;
    let result = sqlx::query("UPDATE playlists SET name = ?, updated_at = ? WHERE id = ?")
        .bind(&name)
        .bind(db::now_ms())
        .bind(&id)
        .execute(&state.pool)
        .await?;
    if result.rows_affected() == 0 {
        return Err(ApiError::not_found("playlist not found"));
    }
    state.emit("collection.changed", &id, None);
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
struct DeleteCollectionParams {
    mode: Option<String>,
}

async fn delete_playlist(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Query(params): Query<DeleteCollectionParams>,
) -> Result<StatusCode, ApiError> {
    let playlist = sqlx::query_as::<_, Playlist>("SELECT * FROM playlists WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found("playlist not found"))?;

    if playlist.id == state.default_gallery {
        return Err(ApiError::bad_request(
            "default_gallery",
            "the default gallery cannot be deleted",
        ));
    }

    let mode = params.mode.as_deref().unwrap_or("move");
    if mode != "move" && mode != "delete_items" {
        return Err(ApiError::bad_request(
            "invalid_mode",
            "mode must be move or delete_items",
        ));
    }

    if playlist.kind == "gallery" {
        let item_ids: Vec<String> =
            sqlx::query_scalar("SELECT item_id FROM playlist_items WHERE playlist_id = ?")
                .bind(&id)
                .fetch_all(&state.pool)
                .await?;

        if mode == "delete_items" {
            for item_id in &item_ids {
                if let Some(item) = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = ?")
                    .bind(item_id)
                    .fetch_optional(&state.pool)
                    .await?
                {
                    if let Err(err) = media::remove_item(&state.pool, &state.cfg, &item, true).await
                    {
                        tracing::warn!(%err, item = %item_id, "could not delete gallery picture");
                    } else {
                        state.emit("item.deleted", item_id, None);
                    }
                }
            }
        } else {
            for item_id in &item_ids {
                if let Err(err) =
                    media::attach_to_collection(&state.pool, &state.default_gallery, item_id).await
                {
                    tracing::warn!(%err, item = %item_id, "could not move picture to default gallery");
                }
            }
            state.emit("collection.changed", &state.default_gallery, None);
        }
    }

    let cover = playlist.cover_path;
    let result = sqlx::query("DELETE FROM playlists WHERE id = ?")
        .bind(&id)
        .execute(&state.pool)
        .await?;
    if result.rows_affected() == 0 {
        return Err(ApiError::not_found("playlist not found"));
    }
    if let Some(cover) = cover {
        if let Some(path) = media::safe_join(&state.cfg.library_dir, &cover) {
            let _ = tokio::fs::remove_file(path).await;
        }
    }
    state.emit("collection.changed", &id, None);
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct PlaylistItemsBody {
    item_ids: Vec<String>,
}

async fn add_playlist_items(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Json(body): Json<PlaylistItemsBody>,
) -> Result<StatusCode, ApiError> {
    let kind = sqlx::query_scalar::<_, String>("SELECT kind FROM playlists WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found("playlist not found"))?;

    if kind == "gallery" {
        for item_id in &body.item_ids {
            let item_kind = sqlx::query_scalar::<_, String>("SELECT kind FROM items WHERE id = ?")
                .bind(item_id)
                .fetch_optional(&state.pool)
                .await?;
            if let Some(item_kind) = item_kind {
                if item_kind != "picture" {
                    return Err(ApiError::bad_request(
                        "gallery_pictures_only",
                        "galleries can only contain pictures",
                    ));
                }
            }
        }
    }

    let now = db::now_ms();
    let mut tx = state.pool.begin().await?;
    let start: i64 = sqlx::query_scalar(
        "SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlist_id = ?",
    )
    .bind(&id)
    .fetch_one(&mut *tx)
    .await?;

    for (offset, item_id) in body.item_ids.iter().enumerate() {
        // SELECT ... FROM items ignores ids that no longer exist instead of
        // tripping the foreign key.
        sqlx::query(
            "INSERT OR IGNORE INTO playlist_items
               (playlist_id, item_id, position, added_at)
             SELECT ?, i.id, ?, ? FROM items i WHERE i.id = ?",
        )
        .bind(&id)
        .bind(start + 1 + offset as i64)
        .bind(now)
        .bind(item_id)
        .execute(&mut *tx)
        .await?;
    }

    sqlx::query("UPDATE playlists SET updated_at = ? WHERE id = ?")
        .bind(now)
        .bind(&id)
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;
    state.emit("collection.changed", &id, None);

    Ok(StatusCode::NO_CONTENT)
}

async fn remove_playlist_item(
    State(state): State<AppState>,
    AxumPath((id, item_id)): AxumPath<(String, String)>,
) -> Result<StatusCode, ApiError> {
    sqlx::query("DELETE FROM playlist_items WHERE playlist_id = ? AND item_id = ?")
        .bind(&id)
        .bind(&item_id)
        .execute(&state.pool)
        .await?;
    sqlx::query("UPDATE playlists SET updated_at = ? WHERE id = ?")
        .bind(db::now_ms())
        .bind(&id)
        .execute(&state.pool)
        .await?;
    state.emit("collection.changed", &id, None);
    Ok(StatusCode::NO_CONTENT)
}

async fn reorder_playlist(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Json(body): Json<PlaylistItemsBody>,
) -> Result<StatusCode, ApiError> {
    let now = db::now_ms();
    let mut tx = state.pool.begin().await?;
    for (position, item_id) in body.item_ids.iter().enumerate() {
        sqlx::query("UPDATE playlist_items SET position = ? WHERE playlist_id = ? AND item_id = ?")
            .bind(position as i64)
            .bind(&id)
            .bind(item_id)
            .execute(&mut *tx)
            .await?;
    }
    sqlx::query("UPDATE playlists SET updated_at = ? WHERE id = ?")
        .bind(now)
        .bind(&id)
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;
    state.emit("collection.changed", &id, None);
    Ok(StatusCode::NO_CONTENT)
}

fn cover_rel(id: &str) -> String {
    format!(".thumbs/covers/{id}.jpg")
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct CoverBody {
    item_id: String,
}

async fn set_cover_from_item(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    Json(body): Json<CoverBody>,
) -> Result<StatusCode, ApiError> {
    let exists = sqlx::query_scalar::<_, i64>("SELECT 1 FROM playlists WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .is_some();
    if !exists {
        return Err(ApiError::not_found("playlist not found"));
    }

    let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = ?")
        .bind(&body.item_id)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found("item not found"))?;

    let source = match item.thumb_path.as_deref() {
        Some(thumb) => state.cfg.library_dir.join(thumb),
        None if item.kind == "picture" => state.cfg.library_dir.join(&item.path),
        None => {
            return Err(ApiError::bad_request(
                "no_cover",
                "this item has no image that can be used as a cover",
            ))
        }
    };
    if !source.exists() {
        return Err(ApiError::not_found("cover image is missing on disk"));
    }

    let rel = cover_rel(&id);
    let dest = state.cfg.library_dir.join(&rel);
    tokio::fs::create_dir_all(dest.parent().unwrap()).await?;
    if media::extension_of(&source) == "jpg" {
        tokio::fs::copy(&source, &dest).await?;
    } else {
        media::make_jpeg_thumb(&state.cfg, &source, &dest).await?;
    }

    sqlx::query("UPDATE playlists SET cover_path = ?, updated_at = ? WHERE id = ?")
        .bind(&rel)
        .bind(db::now_ms())
        .bind(&id)
        .execute(&state.pool)
        .await?;
    state.emit("collection.changed", &id, None);
    Ok(StatusCode::NO_CONTENT)
}

async fn upload_cover(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
    mut multipart: Multipart,
) -> Result<StatusCode, ApiError> {
    let exists = sqlx::query_scalar::<_, i64>("SELECT 1 FROM playlists WHERE id = ?")
        .bind(&id)
        .fetch_optional(&state.pool)
        .await?
        .is_some();
    if !exists {
        return Err(ApiError::not_found("playlist not found"));
    }

    let dir = state
        .cfg
        .incoming_dir()
        .join(format!("cover-{}", uuid::Uuid::now_v7()));
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(ApiError::internal)?;

    let max = state.cfg.max_download_bytes();
    let mut saved: Option<std::path::PathBuf> = None;
    while let Some(mut field) = multipart.next_field().await.map_err(ApiError::internal)? {
        if field.name() != Some("file") {
            continue;
        }
        let name = field.file_name().unwrap_or("cover.jpg");
        let ext = Path::new(name)
            .extension()
            .and_then(|value| value.to_str())
            .map(|value| value.to_ascii_lowercase())
            .filter(|value| !value.is_empty())
            .unwrap_or_else(|| "jpg".to_string());
        let dest = dir.join(format!("source.{ext}"));
        let mut file = tokio::fs::File::create(&dest)
            .await
            .map_err(ApiError::internal)?;
        let mut written = 0u64;
        use tokio::io::AsyncWriteExt;
        while let Some(chunk) = field.chunk().await.map_err(ApiError::internal)? {
            written += chunk.len() as u64;
            if written > max {
                return Err(ApiError {
                    status: StatusCode::PAYLOAD_TOO_LARGE,
                    code: "too_large",
                    message: format!("cover exceeds {} MB", state.cfg.max_download_mb),
                });
            }
            file.write_all(&chunk).await.map_err(ApiError::internal)?;
        }
        file.flush().await.map_err(ApiError::internal)?;
        saved = Some(dest);
    }

    let source =
        saved.ok_or_else(|| ApiError::bad_request("no_file", "expected a `file` field"))?;
    let rel = cover_rel(&id);
    let dest = state.cfg.library_dir.join(&rel);
    tokio::fs::create_dir_all(dest.parent().unwrap()).await?;
    media::make_jpeg_thumb(&state.cfg, &source, &dest)
        .await
        .map_err(ApiError::internal)?;
    sqlx::query("UPDATE playlists SET cover_path = ?, updated_at = ? WHERE id = ?")
        .bind(&rel)
        .bind(db::now_ms())
        .bind(&id)
        .execute(&state.pool)
        .await?;
    let _ = tokio::fs::remove_dir_all(&dir).await;
    state.emit("collection.changed", &id, None);
    Ok(StatusCode::NO_CONTENT)
}

async fn remove_cover(
    State(state): State<AppState>,
    AxumPath(id): AxumPath<String>,
) -> Result<StatusCode, ApiError> {
    let result = sqlx::query("UPDATE playlists SET cover_path = NULL, updated_at = ? WHERE id = ?")
        .bind(db::now_ms())
        .bind(&id)
        .execute(&state.pool)
        .await?;
    if result.rows_affected() == 0 {
        return Err(ApiError::not_found("playlist not found"));
    }
    let dest = state.cfg.library_dir.join(cover_rel(&id));
    let _ = tokio::fs::remove_file(dest).await;
    state.emit("collection.changed", &id, None);
    Ok(StatusCode::NO_CONTENT)
}

async fn sse_events(
    State(state): State<AppState>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    let rx = state.events.subscribe();
    let stream = stream::unfold(rx, |mut rx| async move {
        loop {
            match rx.recv().await {
                Ok(payload) => return Some((Ok(Event::default().data(payload)), rx)),
                Err(tokio::sync::broadcast::error::RecvError::Lagged(_)) => continue,
                Err(tokio::sync::broadcast::error::RecvError::Closed) => return None,
            }
        }
    });
    Sse::new(stream).keep_alive(KeepAlive::default())
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
        assert!(injected
            .iter()
            .all(|t| t.starts_with('"') && t.ends_with('*')));
        assert!(injected.iter().all(|t| !t.contains(';')));
    }

    #[test]
    fn dedupe_key_is_stable_and_input_sensitive() {
        assert_eq!(
            dedupe_key("u", "music", "full"),
            dedupe_key("u", "music", "full")
        );
        assert_ne!(
            dedupe_key("u", "music", "full"),
            dedupe_key("u", "music", "audio+thumb")
        );
    }

    #[test]
    fn collection_kind_accepts_only_known_values() {
        assert_eq!(collection_kind(None).unwrap(), "playlist");
        assert_eq!(collection_kind(Some("playlist")).unwrap(), "playlist");
        assert_eq!(collection_kind(Some("gallery")).unwrap(), "gallery");
        assert!(collection_kind(Some("album")).is_err());
    }

    #[test]
    fn job_mode_matches_the_kind() {
        assert_eq!(resolve_job_mode("music", None).unwrap(), "audio+thumb");
        assert_eq!(resolve_job_mode("video", None).unwrap(), "full");
        assert_eq!(resolve_job_mode("book", Some("full")).unwrap(), "full");

        assert!(resolve_job_mode("music", Some("full")).is_err());
        assert!(resolve_job_mode("video", Some("audio+thumb")).is_err());
        assert!(resolve_job_mode("music", Some("nonsense")).is_err());
        assert!(resolve_job_mode("podcast", None).is_err());
    }
}
