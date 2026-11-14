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

/// FTS5 is optional: builds without it fall back to `LIKE` search.
pub async fn ensure_fts(pool: &SqlitePool) -> anyhow::Result<()> {
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
        CREATE TRIGGER IF NOT EXISTS items_fts_au AFTER UPDATE ON items BEGIN
            INSERT INTO items_fts(items_fts, rowid, title) VALUES ('delete', old.rowid, old.title);
            INSERT INTO items_fts(rowid, title) VALUES (new.rowid, new.title);
        END;
    "#;
    sqlx::raw_sql(ddl).execute(pool).await?;

    // Backfill anything inserted before the FTS table existed.
    sqlx::raw_sql(
        "INSERT INTO items_fts(items_fts) VALUES ('rebuild');",
    )
    .execute(pool)
    .await
    .ok();

    Ok(())
}

pub fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Requeue jobs a previous process left running, and clear partial work that
/// belongs to terminal jobs. Safe to call on every start.
pub async fn recover(pool: &SqlitePool, cfg: &Config) -> anyhow::Result<()> {
    let now = now_ms();
    sqlx::query(
        "UPDATE jobs
            SET status = 'queued', claimed_by = NULL, claimed_at = NULL,
                heartbeat_at = NULL, updated_at = ?
          WHERE status = 'running'",
    )
    .bind(now)
    .execute(pool)
    .await?;

    let incoming = cfg.incoming_dir();
    if let Ok(mut entries) = tokio::fs::read_dir(&incoming).await {
        while let Ok(Some(entry)) = entries.next_entry().await {
            let name = entry.file_name().to_string_lossy().to_string();
            let keep = sqlx::query_scalar::<_, String>(
                "SELECT status FROM jobs WHERE id = ?",
            )
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
