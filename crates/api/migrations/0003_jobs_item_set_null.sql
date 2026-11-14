-- 0003_jobs_item_set_null.sql
-- Deleting a downloaded item used to fail with FOREIGN KEY constraint failed
-- because jobs.item_id had no ON DELETE action. SQLite cannot alter a foreign
-- key in place, so rebuild the jobs table with ON DELETE SET NULL.

CREATE TABLE jobs_new (
  id            TEXT PRIMARY KEY,
  dedupe_key    TEXT NOT NULL,
  url           TEXT NOT NULL,
  kind          TEXT NOT NULL CHECK(kind IN ('music','video','picture','book')),
  mode          TEXT NOT NULL CHECK(mode IN ('audio+thumb','full')),
  status        TEXT NOT NULL DEFAULT 'queued'
                  CHECK(status IN ('queued','running','done','failed','cancelled')),
  stage         TEXT,
  progress      REAL NOT NULL DEFAULT 0,
  error         TEXT,
  item_id       TEXT REFERENCES items(id) ON DELETE SET NULL,
  priority      INTEGER NOT NULL DEFAULT 0,
  attempts      INTEGER NOT NULL DEFAULT 0,
  max_attempts  INTEGER NOT NULL DEFAULT 3,
  scheduled_at  INTEGER NOT NULL,
  claimed_by    TEXT,
  claimed_at    INTEGER,
  heartbeat_at  INTEGER,
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL
);

INSERT INTO jobs_new
  (id, dedupe_key, url, kind, mode, status, stage, progress, error, item_id,
   priority, attempts, max_attempts, scheduled_at, claimed_by, claimed_at,
   heartbeat_at, created_at, updated_at)
SELECT
  id, dedupe_key, url, kind, mode, status, stage, progress, error, item_id,
  priority, attempts, max_attempts, scheduled_at, claimed_by, claimed_at,
  heartbeat_at, created_at, updated_at
FROM jobs;

DROP TABLE jobs;
ALTER TABLE jobs_new RENAME TO jobs;

CREATE INDEX idx_jobs_dequeue ON jobs(status, priority DESC, scheduled_at, created_at);
CREATE INDEX idx_jobs_dedupe  ON jobs(dedupe_key);
CREATE INDEX idx_jobs_item    ON jobs(item_id);
