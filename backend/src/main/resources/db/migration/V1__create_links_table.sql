CREATE TABLE links (
    id              BIGSERIAL PRIMARY KEY,
    short_code      VARCHAR(32) NOT NULL UNIQUE,
    original_url    TEXT NOT NULL,
    is_custom_alias BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_links_short_code ON links (short_code);
