CREATE INDEX idx_wafer_image_created_at ON wafer_image (created_at);
CREATE INDEX idx_wafer_image_prediction_created_at ON wafer_image (prediction, created_at);
