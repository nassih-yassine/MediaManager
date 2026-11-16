//! Local authentication: password hashing, sessions, extractors, bootstrap.

use argon2::password_hash::{
    rand_core::OsRng, PasswordHash, PasswordHasher, PasswordVerifier, SaltString,
};
use argon2::Argon2;
use axum::extract::FromRequestParts;
use axum::http::header::COOKIE;
use axum::http::request::Parts;
use axum::http::HeaderMap;
use rand::RngCore;
use sha2::{Digest, Sha256};
use sqlx::SqlitePool;

use crate::config::Config;
use crate::state::AppState;

pub const COOKIE_NAME: &str = "mm_session";
const SLIDE_AFTER_MS: i64 = 24 * 60 * 60 * 1000;

#[derive(Clone, Debug, sqlx::FromRow)]
#[allow(dead_code)]
pub struct User {
    pub id: String,
    pub email: String,
    pub name: String,
    pub role: String,
    pub disabled: i64,
    pub theme: String,
    pub theme_mode: String,
    pub created_at: i64,
    pub updated_at: i64,
}

impl User {
    pub fn is_admin(&self) -> bool {
        self.role == "admin"
    }
}

#[derive(Clone, Debug)]
pub struct AuthUser(pub User);

#[derive(Clone, Debug)]
pub struct AdminUser(pub User);

pub fn hash_password(password: &str) -> anyhow::Result<String> {
    let salt = SaltString::generate(&mut OsRng);
    let hash = Argon2::default()
        .hash_password(password.as_bytes(), &salt)
        .map_err(|err| anyhow::anyhow!("hash password: {err}"))?;
    Ok(hash.to_string())
}

pub fn verify_password(hash: &str, password: &str) -> bool {
    match PasswordHash::new(hash) {
        Ok(parsed) => Argon2::default()
            .verify_password(password.as_bytes(), &parsed)
            .is_ok(),
        Err(_) => false,
    }
}

pub fn new_token() -> String {
    let mut bytes = [0u8; 32];
    rand::rngs::OsRng.fill_bytes(&mut bytes);
    hex::encode(bytes)
}

pub fn hash_token(token: &str) -> String {
    hex::encode(Sha256::digest(token.as_bytes()))
}

pub fn validate_email(email: &str) -> Result<String, &'static str> {
    let email = email.trim().to_ascii_lowercase();
    let valid = email.len() <= 254
        && email.contains('@')
        && !email.starts_with('@')
        && !email.ends_with('@')
        && !email.contains(' ');
    if !valid {
        return Err("enter a valid email address");
    }
    Ok(email)
}

pub fn validate_password(password: &str) -> Result<(), &'static str> {
    if password.chars().count() < 8 {
        return Err("password must be at least 8 characters");
    }
    Ok(())
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

pub async fn create_user(
    pool: &SqlitePool,
    email: &str,
    name: &str,
    password: &str,
    role: &str,
) -> anyhow::Result<User> {
    let email = validate_email(email).map_err(anyhow::Error::msg)?;
    validate_password(password).map_err(anyhow::Error::msg)?;
    if role != "admin" && role != "user" {
        anyhow::bail!("role must be admin or user");
    }
    let name = name.trim();
    if name.is_empty() {
        anyhow::bail!("name is required");
    }
    let hash = hash_password(password)?;
    let now = now_ms();
    let id = uuid::Uuid::now_v7().to_string();
    sqlx::query(
        "INSERT INTO users (id, email, name, password_hash, role, disabled, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
    )
    .bind(&id)
    .bind(&email)
    .bind(name)
    .bind(&hash)
    .bind(role)
    .bind(now)
    .bind(now)
    .execute(pool)
    .await?;
    find_user(pool, &id)
        .await?
        .ok_or_else(|| anyhow::anyhow!("created user missing"))
}

/// Create the first admin only if the users table is still empty. One
/// `INSERT ... WHERE NOT EXISTS` makes concurrent setup attempts safe.
pub async fn create_first_admin(
    pool: &SqlitePool,
    email: &str,
    name: &str,
    password: &str,
) -> anyhow::Result<Option<User>> {
    let email = validate_email(email).map_err(anyhow::Error::msg)?;
    validate_password(password).map_err(anyhow::Error::msg)?;
    let name = name.trim();
    if name.is_empty() {
        anyhow::bail!("name is required");
    }
    let hash = hash_password(password)?;
    let now = now_ms();
    let id = uuid::Uuid::now_v7().to_string();
    let result = sqlx::query(
        "INSERT INTO users (id, email, name, password_hash, role, disabled, created_at, updated_at)
         SELECT ?, ?, ?, ?, 'admin', 0, ?, ?
          WHERE NOT EXISTS (SELECT 1 FROM users)",
    )
    .bind(&id)
    .bind(&email)
    .bind(name)
    .bind(&hash)
    .bind(now)
    .bind(now)
    .execute(pool)
    .await?;
    if result.rows_affected() == 0 {
        return Ok(None);
    }
    find_user(pool, &id).await
}

pub async fn find_user(pool: &SqlitePool, id: &str) -> anyhow::Result<Option<User>> {
    Ok(sqlx::query_as::<_, User>(
        "SELECT id, email, name, role, disabled, theme, theme_mode, created_at, updated_at FROM users WHERE id = ?",
    )
    .bind(id)
    .fetch_optional(pool)
    .await?)
}

pub async fn find_user_by_email(pool: &SqlitePool, email: &str) -> anyhow::Result<Option<User>> {
    Ok(sqlx::query_as::<_, User>(
        "SELECT id, email, name, role, disabled, theme, theme_mode, created_at, updated_at FROM users WHERE email = ?",
    )
    .bind(email.trim())
    .fetch_optional(pool)
    .await?)
}

pub async fn authenticate(
    pool: &SqlitePool,
    email: &str,
    password: &str,
) -> anyhow::Result<Option<User>> {
    let Some(user) = find_user_by_email(pool, email).await? else {
        // Flatten timing for unknown emails by verifying against a real hash.
        static DUMMY: std::sync::OnceLock<String> = std::sync::OnceLock::new();
        let dummy = DUMMY
            .get_or_init(|| hash_password("not-a-real-password").unwrap_or_else(|_| String::new()));
        let _ = verify_password(dummy, password);
        return Ok(None);
    };
    let hash: Option<String> = sqlx::query_scalar("SELECT password_hash FROM users WHERE id = ?")
        .bind(&user.id)
        .fetch_optional(pool)
        .await?;
    let Some(hash) = hash else { return Ok(None) };
    if !verify_password(&hash, password) {
        return Ok(None);
    }
    Ok(Some(user))
}

pub async fn check_password(
    pool: &SqlitePool,
    user_id: &str,
    password: &str,
) -> anyhow::Result<bool> {
    let hash: Option<String> = sqlx::query_scalar("SELECT password_hash FROM users WHERE id = ?")
        .bind(user_id)
        .fetch_optional(pool)
        .await?;
    Ok(hash
        .map(|hash| verify_password(&hash, password))
        .unwrap_or(false))
}

pub async fn set_password(pool: &SqlitePool, user_id: &str, password: &str) -> anyhow::Result<()> {
    validate_password(password).map_err(anyhow::Error::msg)?;
    let hash = hash_password(password)?;
    sqlx::query("UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?")
        .bind(&hash)
        .bind(now_ms())
        .bind(user_id)
        .execute(pool)
        .await?;
    Ok(())
}

pub async fn create_session(pool: &SqlitePool, user_id: &str, days: i64) -> anyhow::Result<String> {
    let token = new_token();
    let now = now_ms();
    let expires = now + days.max(1) * 24 * 60 * 60 * 1000;
    sqlx::query(
        "INSERT INTO sessions (token_hash, user_id, created_at, expires_at, last_seen_at)
         VALUES (?, ?, ?, ?, ?)",
    )
    .bind(hash_token(&token))
    .bind(user_id)
    .bind(now)
    .bind(expires)
    .bind(now)
    .execute(pool)
    .await?;
    Ok(token)
}

pub async fn delete_session(pool: &SqlitePool, token: &str) -> anyhow::Result<()> {
    sqlx::query("DELETE FROM sessions WHERE token_hash = ?")
        .bind(hash_token(token))
        .execute(pool)
        .await?;
    Ok(())
}

/// Revoke all sessions for a user, optionally sparing the current one.
pub async fn delete_user_sessions(
    pool: &SqlitePool,
    user_id: &str,
    keep_token: Option<&str>,
) -> anyhow::Result<()> {
    match keep_token {
        Some(token) => {
            sqlx::query("DELETE FROM sessions WHERE user_id = ? AND token_hash != ?")
                .bind(user_id)
                .bind(hash_token(token))
                .execute(pool)
                .await?;
        }
        None => {
            sqlx::query("DELETE FROM sessions WHERE user_id = ?")
                .bind(user_id)
                .execute(pool)
                .await?;
        }
    }
    Ok(())
}

pub async fn user_for_token(pool: &SqlitePool, token: &str) -> anyhow::Result<Option<User>> {
    let token_hash = hash_token(token);
    let now = now_ms();
    let user = sqlx::query_as::<_, User>(
        "SELECT u.id, u.email, u.name, u.role, u.disabled, u.theme, u.theme_mode, u.created_at, u.updated_at
         FROM sessions s JOIN users u ON u.id = s.user_id
         WHERE s.token_hash = ? AND s.expires_at > ? AND u.disabled = 0",
    )
    .bind(&token_hash)
    .bind(now)
    .fetch_optional(pool)
    .await?;
    if user.is_some() {
        let _ = sqlx::query(
            "UPDATE sessions SET last_seen_at = ? WHERE token_hash = ? AND last_seen_at < ?",
        )
        .bind(now)
        .bind(&token_hash)
        .bind(now - SLIDE_AFTER_MS)
        .execute(pool)
        .await;
    }
    Ok(user)
}

pub fn session_token(headers: &HeaderMap) -> Option<String> {
    headers
        .get(COOKIE)?
        .to_str()
        .ok()?
        .split(';')
        .find_map(|part| part.trim().strip_prefix(&format!("{COOKIE_NAME}=")))
        .map(str::to_string)
}

pub fn session_cookie(token: &str, days: i64, secure: bool) -> String {
    let max_age = days.max(1) * 24 * 60 * 60;
    format!(
        "{COOKIE_NAME}={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age={max_age}{}",
        if secure { "; Secure" } else { "" }
    )
}

pub fn clear_cookie() -> String {
    format!("{COOKIE_NAME}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0")
}

pub async fn setup_needed(pool: &SqlitePool) -> anyhow::Result<bool> {
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM users")
        .fetch_one(pool)
        .await?;
    Ok(count == 0)
}

/// Create the first admin from MM_ADMIN_EMAIL / MM_ADMIN_PASSWORD, if unset.
pub async fn bootstrap_admin(pool: &SqlitePool, cfg: &Config) -> anyhow::Result<Option<User>> {
    if !setup_needed(pool).await? {
        return Ok(None);
    }
    let (Some(email), Some(password)) = (&cfg.admin_email, &cfg.admin_password) else {
        return Ok(None);
    };
    let name = email.split('@').next().unwrap_or("Admin");
    match create_first_admin(pool, email, name, password).await {
        Ok(Some(user)) => {
            tracing::info!(email = %user.email, "created bootstrap admin");
            Ok(Some(user))
        }
        Ok(None) => Ok(None),
        // Another process won the race; treat it as already bootstrapped.
        Err(err) if err.to_string().contains("UNIQUE") => Ok(None),
        Err(err) => Err(err),
    }
}

/// Adopt rows written before auth existed into the only admin account.
pub async fn adopt_legacy(pool: &SqlitePool) -> anyhow::Result<u64> {
    // The oldest admin owns the pre-auth library even if a second admin was
    // created concurrently; otherwise those rows would stay invisible forever.
    let owner: Option<String> = sqlx::query_scalar(
        "SELECT id FROM users WHERE role = 'admin' ORDER BY created_at, rowid LIMIT 1",
    )
    .fetch_optional(pool)
    .await?;
    let Some(owner) = owner else {
        return Ok(0);
    };
    let owner = &owner;
    let mut adopted = 0;
    for table in ["items", "playlists", "jobs"] {
        let result = sqlx::query(&format!(
            "UPDATE {table} SET owner_id = ? WHERE owner_id IS NULL"
        ))
        .bind(owner)
        .execute(pool)
        .await?;
        adopted += result.rows_affected();
    }
    // The pre-auth default gallery becomes this admin's default gallery.
    sqlx::query(
        "UPDATE playlists SET is_default = 1
         WHERE owner_id = ? AND kind = 'gallery' AND name = 'All pictures' AND is_default = 0",
    )
    .bind(owner)
    .execute(pool)
    .await?;
    Ok(adopted)
}

#[derive(Default)]
pub struct LoginLimiter {
    inner: std::sync::Mutex<std::collections::HashMap<String, (u32, std::time::Instant)>>,
}

impl LoginLimiter {
    /// Returns false when the caller is over the limit.
    pub fn check(&self, key: &str) -> bool {
        let mut map = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        match map.get(key) {
            Some((count, first)) if first.elapsed().as_secs() < 900 => *count < 10,
            _ => {
                map.insert(key.to_string(), (0, std::time::Instant::now()));
                true
            }
        }
    }

    pub fn fail(&self, key: &str) {
        let mut map = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        let entry = map
            .entry(key.to_string())
            .or_insert((0, std::time::Instant::now()));
        entry.0 += 1;
    }

    pub fn reset(&self, key: &str) {
        let mut map = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        map.remove(key);
    }
}

impl FromRequestParts<AppState> for AuthUser {
    type Rejection = crate::routes::ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let token =
            session_token(&parts.headers).ok_or_else(|| crate::routes::ApiError::unauthorized())?;
        let user = user_for_token(&state.pool, &token)
            .await
            .map_err(crate::routes::ApiError::from)?
            .ok_or_else(crate::routes::ApiError::unauthorized)?;
        Ok(AuthUser(user))
    }
}

impl FromRequestParts<AppState> for AdminUser {
    type Rejection = crate::routes::ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let AuthUser(user) = AuthUser::from_request_parts(parts, state).await?;
        if !user.is_admin() {
            return Err(crate::routes::ApiError::forbidden());
        }
        Ok(AdminUser(user))
    }
}

/// Used by the login/setup handlers.
pub fn set_cookie_header(cfg: &Config, token: &str) -> String {
    session_cookie(token, cfg.session_days, cfg.cookie_secure)
}

#[cfg(test)]
mod tests {
    use super::*;

    async fn pool() -> SqlitePool {
        let pool = SqlitePool::connect("sqlite::memory:").await.unwrap();
        sqlx::query(
            "CREATE TABLE users (
                id TEXT PRIMARY KEY, email TEXT NOT NULL COLLATE NOCASE UNIQUE,
                name TEXT NOT NULL, password_hash TEXT NOT NULL,
                role TEXT NOT NULL, disabled INTEGER NOT NULL DEFAULT 0,
                theme TEXT NOT NULL DEFAULT 'slate',
                theme_mode TEXT NOT NULL DEFAULT 'dark',
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
        )
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "CREATE TABLE sessions (
                token_hash TEXT PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL)",
        )
        .execute(&pool)
        .await
        .unwrap();
        pool
    }

    #[test]
    fn password_hash_roundtrip() {
        let hash = hash_password("correct horse battery").unwrap();
        assert!(verify_password(&hash, "correct horse battery"));
        assert!(!verify_password(&hash, "wrong horse battery"));
    }

    #[test]
    fn tokens_are_random_and_hashed() {
        let a = new_token();
        let b = new_token();
        assert_eq!(a.len(), 64);
        assert_ne!(a, b);
        assert_ne!(hash_token(&a), a);
        assert_eq!(hash_token(&a), hash_token(&a));
    }

    #[test]
    fn email_and_password_validation() {
        assert_eq!(
            validate_email("  Me@Example.COM ").unwrap(),
            "me@example.com"
        );
        assert!(validate_email("nope").is_err());
        assert!(validate_password("short").is_err());
        assert!(validate_password("long enough").is_ok());
    }

    #[tokio::test]
    async fn creates_and_authenticates_a_user() {
        let pool = pool().await;
        let user = create_user(&pool, "A@Example.com", "Ann", "secret123", "admin")
            .await
            .unwrap();
        assert_eq!(user.email, "a@example.com");
        assert!(user.is_admin());
        assert!(authenticate(&pool, "a@example.com", "secret123")
            .await
            .unwrap()
            .is_some());
        assert!(authenticate(&pool, "a@example.com", "nope")
            .await
            .unwrap()
            .is_none());
    }

    #[tokio::test]
    async fn sessions_resolve_and_revoke() {
        let pool = pool().await;
        let user = create_user(&pool, "a@example.com", "Ann", "secret123", "user")
            .await
            .unwrap();
        let token = create_session(&pool, &user.id, 30).await.unwrap();
        assert_eq!(
            user_for_token(&pool, &token).await.unwrap().unwrap().id,
            user.id
        );
        delete_session(&pool, &token).await.unwrap();
        assert!(user_for_token(&pool, &token).await.unwrap().is_none());
    }

    #[tokio::test]
    async fn disabled_users_lose_sessions() {
        let pool = pool().await;
        let user = create_user(&pool, "a@example.com", "Ann", "secret123", "user")
            .await
            .unwrap();
        let token = create_session(&pool, &user.id, 30).await.unwrap();
        sqlx::query("UPDATE users SET disabled = 1 WHERE id = ?")
            .bind(&user.id)
            .execute(&pool)
            .await
            .unwrap();
        assert!(user_for_token(&pool, &token).await.unwrap().is_none());
    }

    #[test]
    fn cookie_has_the_security_attributes() {
        let cookie = session_cookie("abc", 30, false);
        assert!(cookie.contains("HttpOnly"));
        assert!(cookie.contains("SameSite=Lax"));
        assert!(cookie.contains("Max-Age=2592000"));
        assert!(!cookie.contains("Secure"));
        assert!(session_cookie("abc", 30, true).contains("; Secure"));
    }

    /// Only one setup request may create the first admin, and adoption must
    /// still find an owner even if a second admin raced in.
    #[tokio::test]
    async fn first_admin_creation_is_atomic() {
        let pool = SqlitePool::connect("sqlite::memory:").await.unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        let now = now_ms();
        sqlx::query(
            "INSERT INTO items (id, kind, title, path, mime, bytes, created_at, updated_at)
             VALUES ('orphan', 'music', 'Old', 'music/aa/orphan.mp3', 'audio/mpeg', 1, ?, ?)",
        )
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();

        let first = create_first_admin(&pool, "one@example.com", "One", "secret123")
            .await
            .unwrap();
        let second = create_first_admin(&pool, "two@example.com", "Two", "secret123")
            .await
            .unwrap();
        assert!(first.is_some());
        assert!(second.is_none(), "a second setup must not create an admin");

        // A second admin can only exist by later promotion; adoption still
        // assigns orphan rows to the oldest admin rather than nobody.
        create_user(&pool, "later@example.com", "Later", "secret123", "admin")
            .await
            .unwrap();
        adopt_legacy(&pool).await.unwrap();
        let owner: String = sqlx::query_scalar("SELECT owner_id FROM items WHERE id = 'orphan'")
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(owner, first.unwrap().id);
    }

    #[tokio::test]
    async fn session_cleanup_removes_every_session_for_a_user() {
        let pool = SqlitePool::connect("sqlite::memory:").await.unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        let user = create_user(&pool, "a@example.com", "Ann", "secret123", "user")
            .await
            .unwrap();
        let keep = create_session(&pool, &user.id, 30).await.unwrap();
        let drop = create_session(&pool, &user.id, 30).await.unwrap();
        delete_user_sessions(&pool, &user.id, Some(&keep))
            .await
            .unwrap();
        assert!(user_for_token(&pool, &keep).await.unwrap().is_some());
        assert!(user_for_token(&pool, &drop).await.unwrap().is_none());
    }

    #[tokio::test]
    async fn adopts_pre_auth_rows_into_the_only_admin() {
        let pool = SqlitePool::connect("sqlite::memory:").await.unwrap();
        sqlx::migrate!("./migrations").run(&pool).await.unwrap();
        let admin = create_user(&pool, "admin@example.com", "Admin", "secret123", "admin")
            .await
            .unwrap();
        let now = now_ms();
        sqlx::query(
            "INSERT INTO items (id, kind, title, path, mime, bytes, created_at, updated_at)
             VALUES ('old-item', 'music', 'Old', 'music/aa/old-item.mp3', 'audio/mpeg', 1, ?, ?)",
        )
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO playlists (id, name, kind, is_default, created_at, updated_at)
             VALUES ('old-gallery', 'All pictures', 'gallery', 0, ?, ?)",
        )
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO jobs (id, dedupe_key, url, kind, mode, scheduled_at, created_at, updated_at)
             VALUES ('old-job', 'k', 'https://example.com/a', 'music', 'audio+thumb', ?, ?, ?)",
        )
        .bind(now)
        .bind(now)
        .bind(now)
        .execute(&pool)
        .await
        .unwrap();

        let adopted = adopt_legacy(&pool).await.unwrap();
        assert_eq!(adopted, 3);

        let owner: String = sqlx::query_scalar("SELECT owner_id FROM items WHERE id = 'old-item'")
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(owner, admin.id);
        let is_default: i64 =
            sqlx::query_scalar("SELECT is_default FROM playlists WHERE id = 'old-gallery'")
                .fetch_one(&pool)
                .await
                .unwrap();
        assert_eq!(is_default, 1);
    }

    #[test]
    fn login_limiter_blocks_after_ten_failures() {
        let limiter = LoginLimiter::default();
        for _ in 0..10 {
            assert!(limiter.check("a@example.com|127.0.0.1"));
            limiter.fail("a@example.com|127.0.0.1");
        }
        assert!(!limiter.check("a@example.com|127.0.0.1"));
        limiter.reset("a@example.com|127.0.0.1");
        assert!(limiter.check("a@example.com|127.0.0.1"));
    }
}
