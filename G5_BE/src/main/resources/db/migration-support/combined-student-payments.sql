ALTER TABLE student_payment_submissions ADD CONSTRAINT ck_student_payment_kind CHECK(kind IN ('TUITION','DEPOSIT','COMBINED'));
ALTER TABLE student_payment_submissions ADD COLUMN tuition_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(tuition_amount>=0 AND tuition_amount=FLOOR(tuition_amount));
ALTER TABLE student_payment_submissions ADD COLUMN deposit_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(deposit_amount>=0 AND deposit_amount=FLOOR(deposit_amount));
ALTER TABLE student_payment_submissions ADD COLUMN deposit_transaction_id UUID UNIQUE REFERENCES financial_transactions(id);

UPDATE student_payment_submissions SET tuition_amount=CASE WHEN kind='TUITION' THEN amount ELSE 0 END,
    deposit_amount=CASE WHEN kind='DEPOSIT' THEN amount ELSE 0 END;

ALTER TABLE student_payment_submissions ADD CONSTRAINT ck_student_payment_allocations CHECK(
    tuition_amount+deposit_amount=amount AND
    ((kind='TUITION' AND tuition_amount>0 AND deposit_amount=0)
     OR (kind='DEPOSIT' AND deposit_amount>0 AND tuition_amount=0)
     OR (kind='COMBINED' AND tuition_amount>0 AND deposit_amount>0))
);
ALTER TABLE student_payment_submissions ADD CONSTRAINT ck_student_payment_deposit_confirmation CHECK(
    (kind='COMBINED' AND status='CONFIRMED' AND deposit_transaction_id IS NOT NULL)
    OR (kind='COMBINED' AND status<>'CONFIRMED' AND deposit_transaction_id IS NULL)
    OR (kind<>'COMBINED' AND deposit_transaction_id IS NULL)
);
