#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use tauri::{WebviewUrl, WebviewWindowBuilder};

fn main() {
    tauri::Builder::default()
        .setup(|app| {
            // Point at a running MediaManager web server. The desktop app keeps
            // the exact web design while running in a native window.
            let url = std::env::var("MM_DESKTOP_URL")
                .unwrap_or_else(|_| "http://localhost:3001".to_string());
            WebviewWindowBuilder::new(app, "main", WebviewUrl::External(url.parse()?))
                .title("MediaManager")
                .inner_size(1440.0, 900.0)
                .min_inner_size(960.0, 640.0)
                .build()?;
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running MediaManager");
}
