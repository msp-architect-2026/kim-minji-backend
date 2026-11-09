-- 기존에 Hibernate ddl-auto=update가 만들던 스키마. 이미 테이블이 있는 DB는 baseline(version 1)으로 이 파일을 건너뛴다
CREATE TABLE wafer_image (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    filename   VARCHAR(255),
    filepath   VARCHAR(255),
    created_at DATETIME(6),
    prediction VARCHAR(255),
    confidence DOUBLE,
    PRIMARY KEY (id)
);
