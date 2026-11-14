use serde::Serialize;

use percent_encoding::{utf8_percent_encode, AsciiSet, NON_ALPHANUMERIC};

const SEGMENT: &AsciiSet = &NON_ALPHANUMERIC
    .remove(b'-')
    .remove(b'_')
    .remove(b'.')
    .remove(b'~');

#[derive(Debug, Clone, sqlx::FromRow)]
#[allow(dead_code)]
pub struct Item {
    pub rowid: i64,
    pub id: String,
    pub kind: String,
    pub title: String,
    pub source_url: Option<String>,
    pub path: String,
    pub thumb_path: Option<String>,
    pub mime: String,
    pub bytes: i64,
    pub duration_ms: Option<i64>,
    pub width: Option<i64>,
    pub height: Option<i64>,
    pub sha256: Option<String>,
    pub position_ms: i64,
    pub last_played_at: Option<i64>,
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ItemDto {
    pub id: String,
    pub kind: String,
    pub title: String,
    pub source_url: Option<String>,
    pub mime: String,
    pub bytes: i64,
    pub duration_ms: Option<i64>,
    pub width: Option<i64>,
    pub height: Option<i64>,
    pub position_ms: i64,
    pub last_played_at: Option<i64>,
    pub created_at: i64,
    pub file_url: String,
    pub thumb_url: Option<String>,
}

fn encode_path(path: &str) -> String {
    path.split('/')
        .map(|seg| utf8_percent_encode(seg, SEGMENT).to_string())
        .collect::<Vec<_>>()
        .join("/")
}

pub fn thumb_url(path: &str) -> String {
    let rel = path.strip_prefix(".thumbs/").unwrap_or(path);
    format!("/thumbs/{}", encode_path(rel))
}

impl Item {
    pub fn to_dto(&self) -> ItemDto {
        let thumb_url = self.thumb_path.as_deref().map(thumb_url);
        ItemDto {
            id: self.id.clone(),
            kind: self.kind.clone(),
            title: self.title.clone(),
            source_url: self.source_url.clone(),
            mime: self.mime.clone(),
            bytes: self.bytes,
            duration_ms: self.duration_ms,
            width: self.width,
            height: self.height,
            position_ms: self.position_ms,
            last_played_at: self.last_played_at,
            created_at: self.created_at,
            file_url: format!("/files/{}", encode_path(&self.path)),
            thumb_url,
        }
    }
}

#[derive(Debug, Clone, sqlx::FromRow)]
#[allow(dead_code)]
pub struct Job {
    pub id: String,
    pub dedupe_key: String,
    pub url: String,
    pub kind: String,
    pub mode: String,
    pub status: String,
    pub stage: Option<String>,
    pub progress: f64,
    pub error: Option<String>,
    pub item_id: Option<String>,
    pub collection_id: Option<String>,
    pub priority: i64,
    pub attempts: i64,
    pub max_attempts: i64,
    pub scheduled_at: i64,
    pub claimed_by: Option<String>,
    pub claimed_at: Option<i64>,
    pub heartbeat_at: Option<i64>,
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JobDto {
    pub id: String,
    pub url: String,
    pub kind: String,
    pub mode: String,
    pub status: String,
    pub stage: Option<String>,
    pub progress: f64,
    pub error: Option<String>,
    pub item_id: Option<String>,
    pub attempts: i64,
    pub max_attempts: i64,
    pub created_at: i64,
    pub updated_at: i64,
}

impl Job {
    pub fn to_dto(&self) -> JobDto {
        JobDto {
            id: self.id.clone(),
            url: self.url.clone(),
            kind: self.kind.clone(),
            mode: self.mode.clone(),
            status: self.status.clone(),
            stage: self.stage.clone(),
            progress: self.progress,
            error: self.error.clone(),
            item_id: self.item_id.clone(),
            attempts: self.attempts,
            max_attempts: self.max_attempts,
            created_at: self.created_at,
            updated_at: self.updated_at,
        }
    }
}

pub const KINDS: [&str; 4] = ["music", "video", "picture", "book"];

pub fn valid_kind(kind: &str) -> bool {
    KINDS.contains(&kind)
}

pub fn kind_dir(kind: &str) -> &'static str {
    match kind {
        "music" => "music",
        "video" => "video",
        "picture" => "picture",
        "book" => "book",
        _ => "book",
    }
}

pub fn mime_for_ext(ext: &str) -> &'static str {
    match ext.to_ascii_lowercase().as_str() {
        "mp3" => "audio/mpeg",
        "m4a" => "audio/mp4",
        "opus" => "audio/opus",
        "flac" => "audio/flac",
        "wav" => "audio/wav",
        "mp4" => "video/mp4",
        "webm" => "video/webm",
        "mkv" => "video/x-matroska",
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "webp" => "image/webp",
        "gif" => "image/gif",
        "avif" => "image/avif",
        "pdf" => "application/pdf",
        "epub" => "application/epub+zip",
        "txt" => "text/plain",
        _ => "application/octet-stream",
    }
}

pub fn ext_for_mime(mime: &str) -> Option<&'static str> {
    let base = mime.split(';').next()?.trim().to_ascii_lowercase();
    Some(match base.as_str() {
        "audio/mpeg" => "mp3",
        "audio/mp4" => "m4a",
        "audio/opus" => "opus",
        "audio/flac" => "flac",
        "audio/wav" | "audio/x-wav" => "wav",
        "video/mp4" => "mp4",
        "video/webm" => "webm",
        "video/x-matroska" => "mkv",
        "image/jpeg" => "jpg",
        "image/png" => "png",
        "image/webp" => "webp",
        "image/gif" => "gif",
        "image/avif" => "avif",
        "application/pdf" => "pdf",
        "application/epub+zip" => "epub",
        "text/plain" => "txt",
        _ => return None,
    })
}

pub fn default_mode(kind: &str) -> &'static str {
    match kind {
        "music" => "audio+thumb",
        _ => "full",
    }
}

#[derive(Debug, Clone, sqlx::FromRow)]
#[allow(dead_code)]
pub struct Playlist {
    pub id: String,
    pub name: String,
    pub kind: String,
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(Debug, Clone, sqlx::FromRow)]
#[allow(dead_code)]
pub struct PlaylistSummaryRow {
    pub id: String,
    pub name: String,
    pub kind: String,
    pub created_at: i64,
    pub updated_at: i64,
    pub item_count: i64,
    pub thumb_path: Option<String>,
    pub thumbs_json: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaylistSummaryDto {
    pub id: String,
    pub name: String,
    pub kind: String,
    pub item_count: i64,
    pub thumb_url: Option<String>,
    pub thumb_urls: Vec<String>,
    pub created_at: i64,
    pub updated_at: i64,
}

impl PlaylistSummaryRow {
    pub fn to_dto(&self) -> PlaylistSummaryDto {
        let thumb_urls = self
            .thumbs_json
            .as_deref()
            .and_then(|raw| serde_json::from_str::<Vec<String>>(raw).ok())
            .unwrap_or_default()
            .iter()
            .map(|path| thumb_url(path))
            .collect();
        PlaylistSummaryDto {
            id: self.id.clone(),
            name: self.name.clone(),
            kind: self.kind.clone(),
            item_count: self.item_count,
            thumb_url: self.thumb_path.as_deref().map(thumb_url),
            thumb_urls,
            created_at: self.created_at,
            updated_at: self.updated_at,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaylistDetailDto {
    pub id: String,
    pub name: String,
    pub kind: String,
    pub created_at: i64,
    pub updated_at: i64,
    pub items: Vec<ItemDto>,
}
