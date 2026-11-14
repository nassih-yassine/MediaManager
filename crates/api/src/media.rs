use std::path::{Component, Path, PathBuf};

use anyhow::{Context, Result};
use tokio::process::Command;

use crate::config::Config;
use crate::db;
use crate::models::{self, Item};

/// Join a DB-relative path under the library root, rejecting anything that
/// could escape it.
pub fn safe_join(root: &Path, rel: &str) -> Option<PathBuf> {
    let rel_path = Path::new(rel);
    if rel_path.is_absolute() {
        return None;
    }
    if rel_path.components().any(|c| {
        matches!(
            c,
            Component::ParentDir | Component::RootDir | Component::Prefix(_)
        )
    }) {
        return None;
    }
    Some(root.join(rel_path))
}

pub fn extension_of(path: &Path) -> String {
    path.extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_ascii_lowercase()
}

/// Convert an image to a JPEG thumbnail (no upscaling). Returns the written path.
pub async fn make_jpeg_thumb(cfg: &Config, src: &Path, dest: &Path) -> Result<()> {
    if extension_of(src) == "jpg" || extension_of(src) == "jpeg" {
        tokio::fs::rename(src, dest).await?;
        return Ok(());
    }
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let status = Command::new(&cfg.ffmpeg)
        .arg("-y")
        .arg("-i")
        .arg(src)
        .args(["-vf", "scale='min(640,iw)':-2", "-frames:v", "1", "-update", "1", "-q:v", "3"])
        .arg(dest)
        .status()
        .await
        .with_context(|| format!("spawning {}", cfg.ffmpeg))?;
    if !status.success() {
        anyhow::bail!("ffmpeg thumbnail conversion failed for {}", src.display());
    }
    Ok(())
}

/// First page of a PDF (or an EPUB's cover image) as a JPEG. Best effort: a
/// book with no cover is still a valid item.
pub async fn book_cover(cfg: &Config, src: &Path, dest: &Path) -> Result<()> {
    match extension_of(src).as_str() {
        "pdf" => pdf_cover(src, dest).await,
        "epub" => epub_cover(cfg, src, dest).await,
        other => anyhow::bail!("no cover extractor for .{other}"),
    }
}

async fn pdf_cover(src: &Path, dest: &Path) -> Result<()> {
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    // pdftoppm writes "<stem>.jpg" with -singlefile.
    let stem = dest.with_extension("");
    let status = Command::new("pdftoppm")
        .args([
            "-jpeg", "-f", "1", "-l", "1", "-singlefile", "-scale-to", "800",
        ])
        .arg(src)
        .arg(&stem)
        .status()
        .await;

    if let Ok(status) = status {
        if status.success() {
            let produced = stem.with_extension("jpg");
            if produced != dest && produced.exists() {
                tokio::fs::rename(produced, dest).await?;
            }
            return Ok(());
        }
    }

    // Fallback for machines without poppler.
    let status = Command::new("magick")
        .arg("-density")
        .arg("150")
        .arg(format!("{}[0]", src.display()))
        .args(["-resize", "800x", "-quality", "85"])
        .arg(dest)
        .status()
        .await
        .context("spawning magick for pdf cover")?;
    if !status.success() {
        anyhow::bail!("could not render a PDF cover");
    }
    Ok(())
}

fn is_image_entry(name: &str) -> bool {
    matches!(
        extension_of(Path::new(name)).as_str(),
        "jpg" | "jpeg" | "png" | "webp" | "gif" | "avif"
    )
}

async fn epub_cover(cfg: &Config, src: &Path, dest: &Path) -> Result<()> {
    let listing = Command::new("unzip")
        .arg("-Z1")
        .arg(src)
        .output()
        .await
        .context("spawning unzip (is it installed?)")?;
    if !listing.status.success() {
        anyhow::bail!("could not list the epub archive");
    }
    let names = String::from_utf8_lossy(&listing.stdout);
    let images: Vec<&str> = names.lines().filter(|n| is_image_entry(n)).collect();
    let entry = images
        .iter()
        .find(|n| n.to_ascii_lowercase().contains("cover"))
        .or_else(|| images.first())
        .copied()
        .context("epub contains no images")?;

    let tmp = std::env::temp_dir().join(format!(
        "mm-epub-{}",
        uuid::Uuid::now_v7()
    ));
    tokio::fs::create_dir_all(&tmp).await?;
    let status = Command::new("unzip")
        .arg("-o")
        .arg("-j")
        .arg(src)
        .arg(entry)
        .arg("-d")
        .arg(&tmp)
        .status()
        .await?;
    if !status.success() {
        let _ = tokio::fs::remove_dir_all(&tmp).await;
        anyhow::bail!("could not extract the epub cover");
    }
    let file_name = Path::new(entry)
        .file_name()
        .context("epub entry has no file name")?;
    let extracted = tmp.join(file_name);
    let result = make_jpeg_thumb(cfg, &extracted, dest).await;
    let _ = tokio::fs::remove_dir_all(&tmp).await;
    result
}

/// Move a finished file into the sharded library layout, generate its thumbnail
/// if one was produced, and insert the item row.
#[allow(clippy::too_many_arguments)]
pub async fn finalize(
    pool: &sqlx::SqlitePool,
    cfg: &Config,
    kind: &str,
    title: &str,
    media_src: &Path,
    thumb_src: Option<&Path>,
    mime: &str,
    source_url: Option<&str>,
    duration_ms: Option<i64>,
    width: Option<i64>,
    height: Option<i64>,
) -> Result<Item> {
    let id = uuid::Uuid::now_v7().to_string();
    let prefix = &id[..2];
    let kind_dir = models::kind_dir(kind);

    let ext = {
        let e = extension_of(media_src);
        if e.is_empty() {
            models::ext_for_mime(mime).unwrap_or("bin").to_string()
        } else {
            e
        }
    };

    let media_rel = format!("{kind_dir}/{prefix}/{id}.{ext}");
    let media_dest = cfg.library_dir.join(&media_rel);
    tokio::fs::create_dir_all(media_dest.parent().unwrap()).await?;
    tokio::fs::rename(media_src, &media_dest)
        .await
        .with_context(|| format!("moving {} into library", media_src.display()))?;

    let thumb_rel = if let Some(src) = thumb_src {
        let rel = format!(".thumbs/{prefix}/{id}.jpg");
        let dest = cfg.library_dir.join(&rel);
        tokio::fs::create_dir_all(dest.parent().unwrap()).await?;
        match make_jpeg_thumb(cfg, src, &dest).await {
            Ok(()) => Some(rel),
            Err(err) => {
                tracing::warn!(%err, "thumbnail generation failed; item kept without thumb");
                None
            }
        }
    } else if kind == "picture" {
        let rel = format!(".thumbs/{prefix}/{id}.jpg");
        let dest = cfg.library_dir.join(&rel);
        tokio::fs::create_dir_all(dest.parent().unwrap()).await?;
        match make_jpeg_thumb(cfg, &media_dest, &dest).await {
            Ok(()) => Some(rel),
            Err(err) => {
                tracing::warn!(%err, "picture thumbnail failed");
                None
            }
        }
    } else if kind == "book" {
        let rel = format!(".thumbs/{prefix}/{id}.jpg");
        let dest = cfg.library_dir.join(&rel);
        tokio::fs::create_dir_all(dest.parent().unwrap()).await?;
        match book_cover(cfg, &media_dest, &dest).await {
            Ok(()) => Some(rel),
            Err(err) => {
                tracing::warn!(%err, "book cover extraction failed");
                None
            }
        }
    } else {
        None
    };

    let bytes = tokio::fs::metadata(&media_dest)
        .await
        .map(|m| m.len() as i64)
        .unwrap_or(0);
    let now = db::now_ms();

    sqlx::query(
        "INSERT INTO items
           (id, kind, title, source_url, path, thumb_path, mime, bytes,
            duration_ms, width, height, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    )
    .bind(&id)
    .bind(kind)
    .bind(title)
    .bind(source_url)
    .bind(&media_rel)
    .bind(&thumb_rel)
    .bind(mime)
    .bind(bytes)
    .bind(duration_ms)
    .bind(width)
    .bind(height)
    .bind(now)
    .bind(now)
    .execute(pool)
    .await?;

    let item = sqlx::query_as::<_, Item>("SELECT * FROM items WHERE id = ?")
        .bind(&id)
        .fetch_one(pool)
        .await?;

    Ok(item)
}

/// Remove an item's files (best effort) and its row. FTS triggers handle search.
pub async fn remove_item(pool: &sqlx::SqlitePool, cfg: &Config, item: &Item, delete_file: bool) -> Result<()> {
    if delete_file {
        if let Some(path) = safe_join(&cfg.library_dir, &item.path) {
            let _ = tokio::fs::remove_file(path).await;
        }
        if let Some(thumb) = &item.thumb_path {
            if let Some(path) = safe_join(&cfg.library_dir, thumb) {
                let _ = tokio::fs::remove_file(path).await;
            }
        }
    }
    sqlx::query("DELETE FROM items WHERE id = ?")
        .bind(&item.id)
        .execute(pool)
        .await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_path_traversal() {
        let root = Path::new("/library");
        assert!(safe_join(root, "../etc/passwd").is_none());
        assert!(safe_join(root, "music/../../etc/passwd").is_none());
        assert!(safe_join(root, "/etc/passwd").is_none());
    }

    #[test]
    fn accepts_library_relative_paths() {
        let root = Path::new("/library");
        assert_eq!(
            safe_join(root, "music/ab/id.mp3").unwrap(),
            Path::new("/library/music/ab/id.mp3")
        );
    }

    #[test]
    fn extension_lowercases() {
        assert_eq!(extension_of(Path::new("a/b/X.MP4")), "mp4");
        assert_eq!(extension_of(Path::new("noext")), "");
    }
}
