-- 0009_auth.sql — local users, sessions, and per-owner content.
-- owner_id stays nullable so an upgrade can adopt the existing single-user
-- library into the first admin (see auth::adopt_legacy).

CREATE TABLE users (
  id            TEXT PRIMARY KEY,
  email         TEXT NOT NULL COLLATE NOCASE UNIQUE,
  name          TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'user' CHECK(role IN ('admin','user')),
  disabled      INTEGER NOT NULL DEFAULT 0,
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL
);

CREATE TABLE sessions (
  token_hash   TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at   INTEGER NOT NULL,
  expires_at   INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL
);

CREATE INDEX idx_sessions_user ON sessions(user_id);

ALTER TABLE items ADD COLUMN owner_id TEXT REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE playlists ADD COLUMN owner_id TEXT REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE jobs ADD COLUMN owner_id TEXT REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE playlists ADD COLUMN is_default INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_items_owner_created ON items(owner_id, kind, created_at DESC, rowid DESC);
CREATE INDEX idx_playlists_owner_kind ON playlists(owner_id, kind);
CREATE INDEX idx_jobs_owner_status ON jobs(owner_id, status);
