CREATE TABLE ledger
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id    BIGINT      NOT NULL,
    amount        BIGINT      NOT NULL,
    balance_after BIGINT      NOT NULL,
    type          VARCHAR(20) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,

    INDEX idx_ledger_account_id (account_id)
);
