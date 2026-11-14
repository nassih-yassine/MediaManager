-- 0011_sync.sql — append-only, owner-scoped change log for offline clients.
-- Triggers keep it current without touching every handler. Clients pull by seq;
-- media bytes are never copied here, they stream on demand.
--
-- Legacy rows (owner_id IS NULL) are skipped: they are not visible to any
-- client until auth::adopt_legacy assigns them, and that UPDATE is logged.

CREATE TABLE sync_changes (
  seq        INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_id   TEXT NOT NULL,
  entity     TEXT NOT NULL,
  entity_id  TEXT NOT NULL,
  op         TEXT NOT NULL,              -- upsert | delete
  payload    TEXT,
  device_id  TEXT,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_sync_owner_seq ON sync_changes(owner_id, seq);

-- items ---------------------------------------------------------------------

CREATE TRIGGER sync_items_ai AFTER INSERT ON items
WHEN NEW.owner_id IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    NEW.owner_id, 'item', NEW.id, 'upsert',
    json_object(
      'id', NEW.id,
      'kind', NEW.kind,
      'title', NEW.title,
      'sourceUrl', NEW.source_url,
      'path', NEW.path,
      'thumbPath', NEW.thumb_path,
      'mime', NEW.mime,
      'bytes', NEW.bytes,
      'durationMs', NEW.duration_ms,
      'width', NEW.width,
      'height', NEW.height,
      'positionMs', NEW.position_ms,
      'lastPlayedAt', NEW.last_played_at,
      'createdAt', NEW.created_at,
      'updatedAt', NEW.updated_at
    ),
    strftime('%s', 'now') * 1000
  );
END;

CREATE TRIGGER sync_items_au AFTER UPDATE ON items
WHEN NEW.owner_id IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    NEW.owner_id, 'item', NEW.id, 'upsert',
    json_object(
      'id', NEW.id,
      'kind', NEW.kind,
      'title', NEW.title,
      'sourceUrl', NEW.source_url,
      'path', NEW.path,
      'thumbPath', NEW.thumb_path,
      'mime', NEW.mime,
      'bytes', NEW.bytes,
      'durationMs', NEW.duration_ms,
      'width', NEW.width,
      'height', NEW.height,
      'positionMs', NEW.position_ms,
      'lastPlayedAt', NEW.last_played_at,
      'createdAt', NEW.created_at,
      'updatedAt', NEW.updated_at
    ),
    strftime('%s', 'now') * 1000
  );
END;

CREATE TRIGGER sync_items_ad AFTER DELETE ON items
WHEN OLD.owner_id IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    OLD.owner_id, 'item', OLD.id, 'delete',
    json_object('id', OLD.id),
    strftime('%s', 'now') * 1000
  );
END;

-- playlists -----------------------------------------------------------------

CREATE TRIGGER sync_playlists_ai AFTER INSERT ON playlists
WHEN NEW.owner_id IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    NEW.owner_id, 'playlist', NEW.id, 'upsert',
    json_object(
      'id', NEW.id,
      'name', NEW.name,
      'kind', NEW.kind,
      'isDefault', NEW.is_default,
      'coverPath', NEW.cover_path,
      'createdAt', NEW.created_at,
      'updatedAt', NEW.updated_at
    ),
    strftime('%s', 'now') * 1000
  );
END;

CREATE TRIGGER sync_playlists_au AFTER UPDATE ON playlists
WHEN NEW.owner_id IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    NEW.owner_id, 'playlist', NEW.id, 'upsert',
    json_object(
      'id', NEW.id,
      'name', NEW.name,
      'kind', NEW.kind,
      'isDefault', NEW.is_default,
      'coverPath', NEW.cover_path,
      'createdAt', NEW.created_at,
      'updatedAt', NEW.updated_at
    ),
    strftime('%s', 'now') * 1000
  );
END;

CREATE TRIGGER sync_playlists_ad AFTER DELETE ON playlists
WHEN OLD.owner_id IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    OLD.owner_id, 'playlist', OLD.id, 'delete',
    json_object('id', OLD.id),
    strftime('%s', 'now') * 1000
  );
END;

-- playlist_items ------------------------------------------------------------
--
-- Ownership lives on the parent playlist. The subquery returns NULL when the
-- playlist is gone (cascade delete), and the WHEN clause skips that row.

CREATE TRIGGER sync_playlist_items_ai AFTER INSERT ON playlist_items
WHEN (SELECT owner_id FROM playlists WHERE id = NEW.playlist_id) IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    (SELECT owner_id FROM playlists WHERE id = NEW.playlist_id),
    'playlist_item',
    NEW.playlist_id || ':' || NEW.item_id,
    'upsert',
    json_object(
      'playlistId', NEW.playlist_id,
      'itemId', NEW.item_id,
      'position', NEW.position
    ),
    strftime('%s', 'now') * 1000
  );
END;

CREATE TRIGGER sync_playlist_items_au AFTER UPDATE ON playlist_items
WHEN (SELECT owner_id FROM playlists WHERE id = NEW.playlist_id) IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    (SELECT owner_id FROM playlists WHERE id = NEW.playlist_id),
    'playlist_item',
    NEW.playlist_id || ':' || NEW.item_id,
    'upsert',
    json_object(
      'playlistId', NEW.playlist_id,
      'itemId', NEW.item_id,
      'position', NEW.position
    ),
    strftime('%s', 'now') * 1000
  );
END;

CREATE TRIGGER sync_playlist_items_ad AFTER DELETE ON playlist_items
WHEN (SELECT owner_id FROM playlists WHERE id = OLD.playlist_id) IS NOT NULL
BEGIN
  INSERT INTO sync_changes (owner_id, entity, entity_id, op, payload, created_at)
  VALUES (
    (SELECT owner_id FROM playlists WHERE id = OLD.playlist_id),
    'playlist_item',
    OLD.playlist_id || ':' || OLD.item_id,
    'delete',
    json_object('playlistId', OLD.playlist_id, 'itemId', OLD.item_id),
    strftime('%s', 'now') * 1000
  );
END;
