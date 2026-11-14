use std::sync::Arc;

use crate::config::Config;

#[derive(Clone)]
pub struct AppState {
    pub pool: sqlx::SqlitePool,
    pub cfg: Arc<Config>,
    pub fts: bool,
    pub client: reqwest::Client,
}
