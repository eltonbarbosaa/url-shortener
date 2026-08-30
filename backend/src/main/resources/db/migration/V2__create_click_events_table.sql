CREATE TABLE click_events (
    id          BIGSERIAL PRIMARY KEY,
    link_id     BIGINT NOT NULL REFERENCES links (id),
    clicked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_hash     VARCHAR(64),
    country     VARCHAR(100),
    city        VARCHAR(100),
    device_type VARCHAR(50),
    browser     VARCHAR(100),
    os          VARCHAR(100),
    referrer    TEXT
);

CREATE INDEX idx_click_events_link_id ON click_events (link_id);
CREATE INDEX idx_click_events_clicked_at ON click_events (clicked_at);
