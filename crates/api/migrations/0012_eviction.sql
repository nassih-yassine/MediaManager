-- 0012_eviction.sql — remove idle media files while keeping metadata.

ALTER TABLE items ADD COLUMN evicted_at INTEGER;
ALTER TABLE jobs ADD COLUMN restore_item_id TEXT REFERENCES items(id) ON DELETE SET NULL;

CREATE INDEX idx_items_evicted ON items(evicted_at);
CREATE INDEX idx_jobs_restore ON jobs(restore_item_id);

-- Keep the sync payload aware of eviction. Recreate the item triggers with the
-- new column (0011's versions predate it).
DROP TRIGGER IF EXISTS sync_items_ai;
DROP TRIGGER IF EXISTS sync_items_au;

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
      'evictedAt', NEW.evicted_at,
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
      'evictedAt', NEW.evicted_at,
      'createdAt', NEW.created_at,
      'updatedAt', NEW.updated_at
    ),
    strftime('%s', 'now') * 1000
  );
END;
