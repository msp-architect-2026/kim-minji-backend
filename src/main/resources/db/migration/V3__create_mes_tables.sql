CREATE TABLE equipment (
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    code   VARCHAR(50) NOT NULL,
    type   VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_code (code)
);

CREATE TABLE process_step (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    code VARCHAR(50)  NOT NULL,
    name VARCHAR(100) NOT NULL,
    seq  INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_step_code (code)
);

CREATE TABLE lot (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    lot_no     VARCHAR(50) NOT NULL,
    product    VARCHAR(50) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    version    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot_lot_no (lot_no),
    KEY idx_lot_created_at (created_at)
);

CREATE TABLE wafer (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    lot_id  BIGINT      NOT NULL,
    slot_no INT         NOT NULL,
    status  VARCHAR(20) NOT NULL,
    version BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wafer_lot_slot (lot_id, slot_no),
    CONSTRAINT fk_wafer_lot FOREIGN KEY (lot_id) REFERENCES lot (id)
);

CREATE TABLE wafer_process_history (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    wafer_id     BIGINT      NOT NULL,
    step_id      BIGINT      NOT NULL,
    equipment_id BIGINT      NOT NULL,
    recipe       VARCHAR(50) NOT NULL,
    started_at   DATETIME(6) NOT NULL,
    ended_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_wph_wafer (wafer_id),
    KEY idx_wph_equipment_started (equipment_id, started_at),
    CONSTRAINT fk_wph_wafer FOREIGN KEY (wafer_id) REFERENCES wafer (id),
    CONSTRAINT fk_wph_step FOREIGN KEY (step_id) REFERENCES process_step (id),
    CONSTRAINT fk_wph_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id)
);

CREATE TABLE inspection (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    wafer_id        BIGINT,
    filename        VARCHAR(255),
    image_key       VARCHAR(255),
    status          VARCHAR(20)  NOT NULL,
    prediction      VARCHAR(255),
    confidence      DOUBLE,
    model_version   VARCHAR(100),
    error_code      VARCHAR(50),
    trace_id        VARCHAR(64),
    idempotency_key VARCHAR(120),
    attempt         INT          NOT NULL,
    version         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inspection_idempotency_key (idempotency_key),
    KEY idx_inspection_wafer (wafer_id),
    KEY idx_inspection_status (status),
    KEY idx_inspection_created_at (created_at),
    KEY idx_inspection_prediction_created_at (prediction, created_at),
    CONSTRAINT fk_inspection_wafer FOREIGN KEY (wafer_id) REFERENCES wafer (id)
);

CREATE TABLE inspection_event (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT      NOT NULL,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20) NOT NULL,
    error_code    VARCHAR(50),
    trace_id      VARCHAR(64),
    occurred_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_inspection_event_inspection (inspection_id),
    CONSTRAINT fk_inspection_event_inspection FOREIGN KEY (inspection_id) REFERENCES inspection (id)
);
