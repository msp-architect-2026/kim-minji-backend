CREATE INDEX idx_inspection_event_to_status_occurred ON inspection_event (to_status, occurred_at);
CREATE INDEX idx_inspection_status_updated ON inspection (status, updated_at);
