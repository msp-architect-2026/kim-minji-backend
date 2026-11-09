-- id를 유지해 기존 /wafer/records/{id} 링크가 그대로 동작하게 한다. Lot 없이 올린 과거 건은 wafer_id가 NULL이다
INSERT INTO inspection (id, filename, image_key, status, prediction, confidence, attempt, version, created_at, updated_at)
SELECT id,
       filename,
       filepath,
       CASE WHEN prediction IS NULL THEN 'FAILED' ELSE 'DONE' END,
       prediction,
       confidence,
       1,
       0,
       COALESCE(created_at, CURRENT_TIMESTAMP(6)),
       COALESCE(created_at, CURRENT_TIMESTAMP(6))
FROM wafer_image;

INSERT INTO inspection_event (inspection_id, from_status, to_status, occurred_at)
SELECT id, NULL, status, created_at FROM inspection;

DROP TABLE wafer_image;
