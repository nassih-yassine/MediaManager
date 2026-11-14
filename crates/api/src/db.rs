use std::time::Duration;

use anyhow::Context;
use sqlx::sqlite::{
    SqliteConnectOptions, SqliteJournalMode, SqlitePool, SqlitePoolOptions, SqliteSynchronous,
};

use crate::config::Config;

pub async fn connect(cfg: &Config) -> anyhow::Result<SqlitePool> {
    let opts = SqliteConnectOptions::new()
        .filename(&cfg.db_path)
        .create_if_missing(true)
        .journal_mode(SqliteJournalMode::Wal)
        .synchronous(SqliteSynchronous::Normal)
        .foreign_keys(true)
        .busy_timeout(Duration::from_secs(5));

    let pool = SqlitePoolOptions::new()
        .max_connections(8)
        .acquire_timeout(Duration::from_secs(10))
        .connect_with(opts)
        .await
        .with_context(|| format!("opening sqlite db at {}", cfg.db_path.display()))?;

    Ok(pool)
}

pub async fn migrate(pool: &SqlitePool) -> anyhow::Result<()> {
    sqlx::migrate!("./migrations")
        .run(pool)
        .await
        .context("running migrations")?;
    Ok(())
}

/// FTS5 is optional: builds without it fall back to `LIKE` search. The index is
/// only rebuilt when it is created; rebuilding on every boot is O(library).
pub async fn ensure_fts(pool: &SqlitePool) -> anyhow::Result<()> {
    let existed: bool = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'items_fts'",
    )
    .fetch_one(pool)
    .await?
        > 0;

    let ddl = r#"
        CREATE VIRTUAL TABLE IF NOT EXISTS items_fts USING fts5(
            title, content='items', content_rowid='rowid'
        );
        CREATE TRIGGER IF NOT EXISTS items_fts_ai AFTER INSERT ON items BEGIN
            INSERT INTO items_fts(rowid, title) VALUES (new.rowid, new.title);
        END;
        CREATE TRIGGER IF NOT EXISTS items_fts_ad AFTER DELETE ON items BEGIN
            INSERT INTO items_fts(items_fts, rowid, title) VALUES ('delete', old.rowid, old.title);
        END;
        -- Recreated with UPDATE OF title so position updates do not reindex.
        DROP TRIGGER IF EXISTS items_fts_au;
        CREATE TRIGGER items_fts_au AFTER UPDATE OF title ON items BEGIN
            INSERT INTO items_fts(items_fts, rowid, title) VALUES ('delete', old.rowid, old.title);
            INSERT INTO items_fts(rowid, title) VALUES (new.rowid, new.title);
        END;
    "#;
    sqlx::raw_sql(ddl).execute(pool).await?;

    if !existed {
        sqlx::raw_sql("INSERT INTO items_fts(items_fts) VALUES ('rebuild');")
            .execute(pool)
            .await
            .ok();
    }

    Ok(())
}

pub fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Requeue only jobs whose lease is stale, so starting a second worker does
/// not steal jobs a live worker is still running. Also clears partial work for
/// terminal jobs. Safe to call on every start.
pub async fn recover(pool: &SqlitePool, cfg: &Config) -> anyhow::Result<()> {
    let now = now_ms();
    let cutoff = now - (cfg.lease_s as i64) * 1000;
    sqlx::query(
        "UPDATE jobs
            SET status = 'queued', claimed_by = NULL, claimed_at = NULL,
                heartbeat_at = NULL, updated_at = ?, error = 'worker lost; retrying'
          WHERE status = 'running'
            AND COALESCE(heartbeat_at, claimed_at, 0) < ?",
    )
    .bind(now)
    .bind(cutoff)
    .execute(pool)
    .await?;

    let incoming = cfg.incoming_dir();
    if let Ok(mut entries) = tokio::fs::read_dir(&incoming).await {
        while let Ok(Some(entry)) = entries.next_entry().await {
            let name = entry.file_name().to_string_lossy().to_string();
            let keep = sqlx::query_scalar::<_, String>("SELECT status FROM jobs WHERE id = ?")
                .bind(&name)
                .fetch_optional(pool)
                .await?
                .map(|s| s == "queued" || s == "running")
                .unwrap_or(false);

            if !keep {
                let _ = tokio::fs::remove_dir_all(entry.path()).await;
            }
        }
    }

    Ok(())
}

pub async fn count_by_status(pool: &SqlitePool, status: &str) -> i64 {
    sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM jobs WHERE status = ?")
        .bind(status)
        .fetch_one(pool)
        .await
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::Role;
    use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};

    fn config(dir: &std::path::Path) -> Config {
        Config {
            library_dir: dir.to_path_buf(),
            db_path: dir.join("test.db"),
            bind: "127.0.0.1:0".into(),
            web_origin: "http://localhost:3000".into(),
            max_concurrent: 2,
            max_queued: 10,
            max_download_mb: 10,
            heartbeat_s: 30,
            lease_s: 60,
            role: Role::Combined,
            ytdlp: "yt-dlp".into(),
            ffmpeg: "ffmpeg".into(),
            opener: "xdg-open".into(),
            admin_email: None,
            admin_password: None,
            session_days: 30,
            cookie_secure: false,
            evict_after_days: 30,
            evict_check_hours: 24,
        }
    }

    async fn pool(dir: &std::path::Path) -> SqlitePool {
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

    async fn insert_job(pool: &SqlitePool, id: &str, heartbeat: i64) {
        sqlx::query(
            "INSERT INTO jobs
               (id, dedupe_key, url, kind, mode, status, scheduled_at,
                claimed_by, claimed_at, heartbeat_at, created_at, updated_at)
             VALUES (?, 'k', 'https://example.com', 'music', 'audio+thumb',
                     'running', ?, 'worker-1', ?, ?, ?, ?)",
        )
        .bind(id)
        .bind(heartbeat)
        .bind(heartbeat)
        .bind(heartbeat)
        .bind(heartbeat)
        .bind(heartbeat)
        .execute(pool)
        .await
        .unwrap();
    }

    /// Regression: starting a second worker used to requeue jobs that a live
    /// worker was still running, causing duplicate downloads.
    #[tokio::test]
    async fn recover_requeues_only_stale_leases() {
        let dir = std::env::temp_dir().join(format!("mm-recover-{}", uuid::Uuid::now_v7()));
        std::fs::create_dir_all(&dir).unwrap();
        let pool = pool(&dir).await;
        let cfg = config(&dir);
        let now = now_ms();

        insert_job(&pool, "fresh", now).await;
        insert_job(&pool, "stale", now - 120_000).await;

        recover(&pool, &cfg).await.unwrap();

        let fresh: String = sqlx::query_scalar("SELECT status FROM jobs WHERE id = 'fresh'")
            .fetch_one(&pool)
            .await
            .unwrap();
        let stale: String = sqlx::query_scalar("SELECT status FROM jobs WHERE id = 'stale'")
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(fresh, "running", "a live worker keeps its job");
        assert_eq!(stale, "queued", "an expired lease is recovered");

        let _ = std::fs::remove_dir_all(&dir);
    }
}
