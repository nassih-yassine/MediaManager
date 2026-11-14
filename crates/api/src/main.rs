mod config;
mod db;
mod media;
mod models;
mod routes;
mod state;
mod worker;

use std::sync::Arc;

use config::{Config, Role};
use state::AppState;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let _ = dotenvy::dotenv();

    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "mm=info,tower_http=info".into()),
        )
        .init();

    let cfg = Arc::new(Config::from_env()?);
    std::fs::create_dir_all(&cfg.library_dir)?;
    std::fs::create_dir_all(cfg.incoming_dir())?;
    std::fs::create_dir_all(cfg.thumbs_dir())?;
    for kind in models::KINDS {
        std::fs::create_dir_all(cfg.library_dir.join(kind))?;
    }

    let pool = db::connect(&cfg).await?;
    db::migrate(&pool).await?;

    let fts = match db::ensure_fts(&pool).await {
        Ok(()) => true,
        Err(err) => {
            tracing::warn!(%err, "FTS5 unavailable, search falls back to LIKE");
            false
        }
    };
    db::recover(&pool, &cfg).await?;

    let state = AppState {
        pool,
        cfg: cfg.clone(),
        fts,
        client: reqwest::Client::builder()
            .user_agent(concat!("MediaManager/", env!("CARGO_PKG_VERSION")))
            .timeout(std::time::Duration::from_secs(60 * 60))
            .build()?,
    };

    let mut handles = Vec::new();
    if cfg.role != Role::Api {
        handles.extend(worker::spawn_workers(state.clone()));
        handles.push(worker::spawn_reaper(state.clone()));
        tracing::info!(
            workers = cfg.max_concurrent,
            role = ?cfg.role,
            "workers started"
        );
    }

    if cfg.role == Role::Worker {
        tracing::info!("worker-only mode: no HTTP server");
        tokio::signal::ctrl_c().await?;
        for handle in handles {
            handle.abort();
        }
        return Ok(());
    }

    let app = routes::router(state);
    let listener = tokio::net::TcpListener::bind(&cfg.bind).await?;
    tracing::info!(
        bind = %cfg.bind,
        library = %cfg.library_dir.display(),
        fts,
        "mm listening"
    );

    axum::serve(listener, app)
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
            tracing::info!("shutting down");
        })
        .await?;

    Ok(())
}
