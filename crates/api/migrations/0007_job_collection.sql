-- 0007_job_collection.sql — attach a finished download to a playlist/gallery.

ALTER TABLE jobs ADD COLUMN collection_id TEXT REFERENCES playlists(id) ON DELETE SET NULL;
