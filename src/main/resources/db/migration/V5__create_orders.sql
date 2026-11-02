-- order는 MySQL 예약어라 테이블만 복수형으로 쓴다.
CREATE TABLE orders
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT      NOT NULL,
    stock_id    BIGINT      NOT NULL,
    side        VARCHAR(10) NOT NULL,
    price       BIGINT      NOT NULL,
    quantity    BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    canceled_at DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,

    INDEX idx_orders_account_id (account_id)
);
