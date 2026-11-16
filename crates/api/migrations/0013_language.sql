-- Per-account UI language. 'en' | 'ar'; the web client switches dir=rtl for ar.
ALTER TABLE users ADD COLUMN language TEXT NOT NULL DEFAULT 'en';
