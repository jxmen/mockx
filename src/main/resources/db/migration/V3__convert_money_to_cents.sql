-- 금액 단위를 정수 달러에서 정수 센트로 바꾼다. ($1 → 100)
UPDATE account
SET balance     = balance * 100,
    hold_amount = hold_amount * 100;

UPDATE ledger
SET amount        = amount * 100,
    balance_after = balance_after * 100;
