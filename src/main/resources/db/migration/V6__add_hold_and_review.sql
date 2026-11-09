ALTER TABLE inspection ADD COLUMN final_prediction VARCHAR(255);

-- 사람 판정 전까지는 비어 있다. 자동 확정된 과거 검사는 AI 판정이 곧 최종 판정이다
UPDATE inspection SET final_prediction = prediction WHERE status = 'DONE';

CREATE TABLE lot_hold (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    lot_id          BIGINT       NOT NULL,
    source          VARCHAR(20)  NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    held_by         VARCHAR(50)  NOT NULL,
    held_at         DATETIME(6)  NOT NULL,
    released_by     VARCHAR(50),
    release_comment VARCHAR(255),
    released_at     DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_lot_hold_lot (lot_id),
    CONSTRAINT fk_lot_hold_lot FOREIGN KEY (lot_id) REFERENCES lot (id)
);

CREATE TABLE inspection_review (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    inspection_id    BIGINT       NOT NULL,
    ai_prediction    VARCHAR(255) NOT NULL,
    final_prediction VARCHAR(255) NOT NULL,
    decision         VARCHAR(20)  NOT NULL,
    reviewer         VARCHAR(50)  NOT NULL,
    comment          VARCHAR(255),
    reviewed_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inspection_review_inspection (inspection_id),
    CONSTRAINT fk_inspection_review_inspection FOREIGN KEY (inspection_id) REFERENCES inspection (id)
);
