CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_user_invoice_number_active
  ON invoices(user_id, invoice_number)
  WHERE deleted = 0 AND invoice_number <> '';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_invoices_amounts_nonnegative'
  ) THEN
    ALTER TABLE invoices
      ADD CONSTRAINT chk_invoices_amounts_nonnegative
      CHECK (amount >= 0 AND tax_amount >= 0 AND total_amount >= 0) NOT VALID;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_invoices_total_matches_parts'
  ) THEN
    ALTER TABLE invoices
      ADD CONSTRAINT chk_invoices_total_matches_parts
      CHECK (ABS(total_amount - amount - tax_amount) <= 0.05) NOT VALID;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_voucher_entries_amounts_nonnegative'
  ) THEN
    ALTER TABLE voucher_entries
      ADD CONSTRAINT chk_voucher_entries_amounts_nonnegative
      CHECK (debit_amount >= 0 AND credit_amount >= 0) NOT VALID;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_voucher_entries_one_side_amount'
  ) THEN
    ALTER TABLE voucher_entries
      ADD CONSTRAINT chk_voucher_entries_one_side_amount
      CHECK (
        (debit_amount > 0 AND credit_amount = 0)
        OR (credit_amount > 0 AND debit_amount = 0)
      ) NOT VALID;
  END IF;
END $$;
