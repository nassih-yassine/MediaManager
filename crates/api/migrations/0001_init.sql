-- 0001_init.sql — core schema. FTS5 is created at runtime (see src/db.rs)
-- because some SQLite builds omit FTS5; search degrades to LIKE without it.

CREATE TABLE items (
  rowid       INTEGER PRIMARY KEY AUTOINCREMENT,
  id          TEXT NOT NULL UNIQUE,
  kind        TEXT NOT NULL CHECK(kind IN ('music','video','picture','book')),
  title       TEXT NOT NULL,
  source_url  TEXT,
  path        TEXT NOT NULL UNIQUE,
  thumb_path  TEXT,
  mime        TEXT NOT NULL,
  bytes       INTEGER NOT NULL,
  duration_ms INTEGER,
  width       INTEGER,
  height      INTEGER,
  sha256      TEXT,
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);

CREATE INDEX idx_items_kind_created ON items(kind, created_at DESC, rowid DESC);
CREATE INDEX idx_items_title        ON items(title);

CREATE TABLE jobs (
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
  item_id       TEXT REFERENCES items(id),
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

CREATE INDEX idx_jobs_dequeue ON jobs(status, priority DESC, scheduled_at, created_at);
CREATE INDEX idx_jobs_dedupe  ON jobs(dedupe_key);
CREATE INDEX idx_jobs_item    ON jobs(item_id);
