//! Persistent per-user notifications and their fan-out helpers.

use sqlx::SqlitePool;

use crate::db;

#[derive(Clone, Debug, sqlx::FromRow)]
#[allow(dead_code)]
pub struct Notification {
    pub id: String,
    pub user_id: String,
    pub kind: String,
    pub title: String,
    pub body: Option<String>,
    pub href: Option<String>,
    pub read_at: Option<i64>,
    pub created_at: i64,
}

pub async fn create(
    pool: &SqlitePool,
    user_id: &str,
    kind: &str,
    title: &str,
    body: Option<&str>,
    href: Option<&str>,
) -> anyhow::Result<String> {
    let id = uuid::Uuid::now_v7().to_string();
    sqlx::query(
        "INSERT INTO notifications (id, user_id, kind, title, body, href, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)",
    )
    .bind(&id)
    .bind(user_id)
    .bind(kind)
    .bind(title)
    .bind(body)
    .bind(href)
    .bind(db::now_ms())
    .execute(pool)
    .await?;
    Ok(id)
}

async fn recipients(pool: &SqlitePool, admins_only: bool) -> anyhow::Result<Vec<String>> {
    let sql = if admins_only {
        "SELECT id FROM users WHERE role = 'admin' AND disabled = 0 ORDER BY created_at"
    } else {
        "SELECT id FROM users WHERE disabled = 0 ORDER BY created_at"
    };
    Ok(sqlx::query_scalar(sql).fetch_all(pool).await?)
}

async fn fan_out(
    pool: &SqlitePool,
    user_ids: Vec<String>,
    kind: &str,
    title: &str,
    body: Option<&str>,
    href: Option<&str>,
) -> anyhow::Result<Vec<String>> {
    let now = db::now_ms();
    let mut ids = Vec::with_capacity(user_ids.len());
    let mut tx = pool.begin().await?;
    for user_id in user_ids {
        let id = uuid::Uuid::now_v7().to_string();
        sqlx::query(
            "INSERT INTO notifications (id, user_id, kind, title, body, href, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)",
        )
        .bind(&id)
        .bind(&user_id)
        .bind(kind)
        .bind(title)
        .bind(body)
        .bind(href)
        .bind(now)
        .execute(&mut *tx)
        .await?;
        ids.push(id);
    }
    tx.commit().await?;
    Ok(ids)
}

pub async fn notify_admins(
    pool: &SqlitePool,
    kind: &str,
    title: &str,
    body: Option<&str>,
    href: Option<&str>,
) -> anyhow::Result<Vec<String>> {
    let admins = recipients(pool, true).await?;
    fan_out(pool, admins, kind, title, body, href).await
}

pub async fn notify_all_users(
    pool: &SqlitePool,
    kind: &str,
    title: &str,
    body: Option<&str>,
    href: Option<&str>,
) -> anyhow::Result<Vec<String>> {
    let users = recipients(pool, false).await?;
    fan_out(pool, users, kind, title, body, href).await
}

pub async fn unread_count(pool: &SqlitePool, user_id: &str) -> anyhow::Result<i64> {
    Ok(sqlx::query_scalar(
        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
    )
    .bind(user_id)
    .fetch_one(pool)
    .await?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};

    async fn pool() -> SqlitePool {
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect_with(
                SqliteConnectOptions::new()
                    .filename(":memory:")
                    .create_if_missing(true)
                    .foreign_keys(true),
            )
            .await
            .unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        pool
    }

    async fn user(pool: &SqlitePool, email: &str, role: &str, disabled: i64) -> String {
        let id = uuid::Uuid::now_v7().to_string();
        let now = db::now_ms();
        sqlx::query(
            "INSERT INTO users (id, email, name, password_hash, role, disabled, created_at, updated_at)
             VALUES (?, ?, ?, 'x', ?, ?, ?, ?)",
        )
        .bind(&id)
        .bind(email)
        .bind(email)
        .bind(role)
        .bind(disabled)
        .bind(now)
        .bind(now)
        .execute(pool)
        .await
        .unwrap();
        id
    }

    #[tokio::test]
    async fn creates_a_row_and_counts_it_for_its_owner() {
        let pool = pool().await;
        let a = user(&pool, "a@example.com", "user", 0).await;
        let b = user(&pool, "b@example.com", "user", 0).await;

        create(
            &pool,
            &a,
            "job_done",
            "Downloaded",
            Some("Track"),
            Some("/watch/1"),
        )
        .await
        .unwrap();
        create(&pool, &a, "job_failed", "Failed", None, None)
            .await
            .unwrap();
        assert_eq!(unread_count(&pool, &a).await.unwrap(), 2);
        assert_eq!(unread_count(&pool, &b).await.unwrap(), 0);

        let kinds: Vec<String> =
            sqlx::query_scalar("SELECT kind FROM notifications WHERE user_id = ? ORDER BY rowid")
                .bind(&a)
                .fetch_all(&pool)
                .await
                .unwrap();
        assert_eq!(kinds, vec!["job_done", "job_failed"]);
    }

    #[tokio::test]
    async fn notify_admins_reaches_enabled_admins_only() {
        let pool = pool().await;
        let admin = user(&pool, "admin@example.com", "admin", 0).await;
        let off = user(&pool, "off@example.com", "admin", 1).await;
        let user_id = user(&pool, "u@example.com", "user", 0).await;

        let ids = notify_admins(
            &pool,
            "report_new",
            "New report",
            Some("bug"),
            Some("/admin"),
        )
        .await
        .unwrap();
        assert_eq!(ids.len(), 1);
        assert_eq!(unread_count(&pool, &admin).await.unwrap(), 1);
        assert_eq!(unread_count(&pool, &off).await.unwrap(), 0);
        assert_eq!(unread_count(&pool, &user_id).await.unwrap(), 0);
    }

    #[tokio::test]
    async fn notify_all_reaches_enabled_users_only() {
        let pool = pool().await;
        let a = user(&pool, "a@example.com", "user", 0).await;
        let b = user(&pool, "b@example.com", "admin", 0).await;
        let off = user(&pool, "off@example.com", "user", 1).await;

        let ids = notify_all_users(&pool, "announcement", "v2", Some("New feature"), None)
            .await
            .unwrap();
        assert_eq!(ids.len(), 2);
        assert_eq!(unread_count(&pool, &a).await.unwrap(), 1);
        assert_eq!(unread_count(&pool, &b).await.unwrap(), 1);
        assert_eq!(unread_count(&pool, &off).await.unwrap(), 0);
    }
}
