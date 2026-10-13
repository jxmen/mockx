CREATE TABLE account
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT   NOT NULL,
    number      CHAR(12) NOT NULL,
    balance     BIGINT   NOT NULL DEFAULT 0,
    hold_amount BIGINT   NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,

    CONSTRAINT uk_account_member_id UNIQUE (member_id),
    CONSTRAINT uk_account_number UNIQUE (number),
    KEY idx_account_member_id (member_id)
);

CREATE TABLE account_number_sequence
(
    product_code CHAR(3) PRIMARY KEY,
    next_value   BIGINT NOT NULL
);


CREATE TABLE member
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(10)  NOT NULL COLLATE utf8mb4_0900_ai_ci,
    nickname   VARCHAR(10)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_member_username UNIQUE (username)
);

INSERT INTO account_number_sequence (product_code, next_value)
VALUES ('100', 0);
