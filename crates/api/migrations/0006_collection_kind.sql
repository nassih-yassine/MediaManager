-- 0006_collection_kind.sql — playlists gain a kind so pictures can live in
-- galleries while everything else stays a playlist.

ALTER TABLE playlists ADD COLUMN kind TEXT NOT NULL DEFAULT 'playlist'
  CHECK(kind IN ('playlist', 'gallery'));
