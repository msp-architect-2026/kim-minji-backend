-- TrackIn 후 TrackOut 전까지는 종료 시각이 없다
ALTER TABLE wafer_process_history MODIFY COLUMN ended_at DATETIME(6) NULL;
