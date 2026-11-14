-- 0002_playlists.sql — ordered user playlists. Items are deleted with the
-- playlist and with the item itself (ON DELETE CASCADE).

CREATE TABLE playlists (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE playlist_items (
  playlist_id TEXT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
  item_id     TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  position    INTEGER NOT NULL,
  added_at    INTEGER NOT NULL,
  PRIMARY KEY (playlist_id, item_id)
);

CREATE INDEX idx_playlist_items_order ON playlist_items(playlist_id, position);
