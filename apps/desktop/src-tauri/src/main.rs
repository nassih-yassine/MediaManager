#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

use tauri::{WebviewUrl, WebviewWindowBuilder};

fn server_reachable(server: &str) -> bool {
    let (scheme, rest) = if let Some(rest) = server.strip_prefix("https://") {
        ("https", rest)
    } else if let Some(rest) = server.strip_prefix("http://") {
        ("http", rest)
    } else {
        ("http", server)
    };
    let host_port = rest.split('/').next().unwrap_or(rest);
    let default_port = if scheme == "https" { 443 } else { 80 };
    let address = if host_port.rsplit_once(':').is_some() {
        host_port.to_string()
    } else {
        format!("{host_port}:{default_port}")
    };
    let Ok(mut addresses) = address.to_socket_addrs() else {
        return false;
    };
    addresses
        .next()
        .map(|addr| TcpStream::connect_timeout(&addr, Duration::from_millis(800)).is_ok())
        .unwrap_or(false)
}

fn main() {
    tauri::Builder::default()
        .setup(|app| {
            let server = std::env::var("MM_DESKTOP_URL")
                .unwrap_or_else(|_| "http://localhost:3001".to_string());
            let reachable = server_reachable(&server);
            let target = if reachable {
                WebviewUrl::External(server.parse()?)
            } else {
                WebviewUrl::App("error.html".into())
            };
            let init_script = format!(
                "window.__MM_SERVER__ = {};",
                serde_json::to_string(&server)?
            );

            WebviewWindowBuilder::new(app, "main", target)
                .title("MediaManager")
                .initialization_script(&init_script)
                .inner_size(1440.0, 900.0)
                .min_inner_size(360.0, 560.0)
                .build()?;
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running MediaManager");
}
