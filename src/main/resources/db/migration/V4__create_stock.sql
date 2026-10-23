CREATE TABLE stock
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol     VARCHAR(10)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,

    CONSTRAINT uk_stock_symbol UNIQUE (symbol)
);

-- Auditing이 동작하지 않으므로 시각을 직접 넣는다. CURRENT_TIMESTAMP는 세션 time_zone을 따르므로 UTC_TIMESTAMP를 쓴다.
INSERT INTO stock (symbol, name, created_at, updated_at)
VALUES ('SPY', 'SPDR S&P 500 ETF Trust', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('QQQM', 'Invesco NASDAQ 100 ETF', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('QLD', 'ProShares Ultra QQQ', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TQQQ', 'ProShares UltraPro QQQ', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('SOXL', 'Direxion Daily Semiconductor Bull 3X Shares', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('SGOV', 'iShares 0-3 Month Treasury Bond ETF', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('MU', 'Micron Technology', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('SKHY', 'SK hynix ADR', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
