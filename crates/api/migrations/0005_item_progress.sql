-- 0005_item_progress.sql — remember where playback stopped per item.

ALTER TABLE items ADD COLUMN position_ms INTEGER NOT NULL DEFAULT 0;
ALTER TABLE items ADD COLUMN last_played_at INTEGER;
