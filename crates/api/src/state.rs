use std::sync::Arc;

use tokio::sync::broadcast;

use crate::config::Config;

#[derive(Clone)]
pub struct AppState {
    pub pool: sqlx::SqlitePool,
    pub cfg: Arc<Config>,
    pub fts: bool,
    pub client: reqwest::Client,
    pub events: broadcast::Sender<String>,
    pub default_gallery: String,
}

impl AppState {
    /// Broadcast a lightweight change event. Payload is the SSE data line.
    pub fn emit(&self, kind: &str, id: &str, collection_id: Option<&str>) {
        let payload = serde_json::json!({
            "type": kind,
            "id": id,
            "collectionId": collection_id,
        })
        .to_string();
        let _ = self.events.send(payload);
    }
}
