use std::path::PathBuf;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Role {
    Combined,
    Api,
    Worker,
}

#[derive(Clone, Debug)]
pub struct Config {
    pub library_dir: PathBuf,
    pub db_path: PathBuf,
    pub bind: String,
    pub web_origin: String,
    pub max_concurrent: usize,
    pub max_queued: i64,
    pub max_download_mb: u64,
    pub heartbeat_s: u64,
    pub lease_s: u64,
    pub role: Role,
    pub ytdlp: String,
    pub ffmpeg: String,
    pub opener: String,
    pub admin_email: Option<String>,
    pub admin_password: Option<String>,
    pub session_days: i64,
    pub cookie_secure: bool,
}

fn env_or(name: &str, default: &str) -> String {
    std::env::var(name)
        .ok()
        .filter(|v| !v.trim().is_empty())
        .unwrap_or_else(|| default.to_string())
}

fn expand_home(raw: &str) -> PathBuf {
    let path = if let Some(rest) = raw.strip_prefix("~/") {
        match std::env::var("HOME") {
            Ok(home) => PathBuf::from(home).join(rest),
            Err(_) => PathBuf::from(raw),
        }
    } else {
        PathBuf::from(raw)
    };
    path
}

impl Config {
    pub fn from_env() -> anyhow::Result<Self> {
        let library_dir = expand_home(&env_or("MM_LIBRARY_DIR", "~/MediaManager/library"));
        let db_path = match std::env::var("MM_DB_PATH") {
            Ok(v) if !v.trim().is_empty() => expand_home(&v),
            _ => library_dir.join(".mm.db"),
        };

        let role = match env_or("MM_ROLE", "combined").as_str() {
            "api" => Role::Api,
            "worker" => Role::Worker,
            "combined" => Role::Combined,
            other => anyhow::bail!("MM_ROLE must be combined|api|worker, got {other}"),
        };

        Ok(Self {
            library_dir,
            db_path,
            bind: env_or("MM_BIND", "127.0.0.1:8787"),
            web_origin: env_or("MM_WEB_ORIGIN", "http://localhost:3000"),
            max_concurrent: env_or("MM_MAX_CONCURRENT", "8").parse().unwrap_or(8),
            max_queued: env_or("MM_MAX_QUEUED", "500").parse().unwrap_or(500),
            max_download_mb: env_or("MM_MAX_DOWNLOAD_MB", "2048").parse().unwrap_or(2048),
            heartbeat_s: env_or("MM_HEARTBEAT_S", "30").parse().unwrap_or(30),
            lease_s: env_or("MM_LEASE_S", "60").parse().unwrap_or(60),
            role,
            ytdlp: env_or("MM_YTDLP", "yt-dlp"),
            ffmpeg: env_or("MM_FFMPEG", "ffmpeg"),
            opener: env_or("MM_OPENER", "xdg-open"),
            admin_email: std::env::var("MM_ADMIN_EMAIL")
                .ok()
                .filter(|v| !v.trim().is_empty()),
            admin_password: std::env::var("MM_ADMIN_PASSWORD")
                .ok()
                .filter(|v| !v.trim().is_empty()),
            session_days: env_or("MM_SESSION_DAYS", "30").parse().unwrap_or(30),
            cookie_secure: env_or("MM_COOKIE_SECURE", "0") == "1",
        })
    }

    pub fn incoming_dir(&self) -> PathBuf {
        self.library_dir.join(".incoming")
    }

    pub fn thumbs_dir(&self) -> PathBuf {
        self.library_dir.join(".thumbs")
    }

    pub fn max_download_bytes(&self) -> u64 {
        self.max_download_mb.saturating_mul(1024 * 1024)
    }
}
