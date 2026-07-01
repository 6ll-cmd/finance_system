CREATE TABLE invoice_attachments (
  id           UUID PRIMARY KEY,
  invoice_id   VARCHAR(32) NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  user_id      UUID NOT NULL REFERENCES users(id),
  file_name    VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  file_size    BIGINT NOT NULL,
  content      BYTEA NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_attachments_invoice ON invoice_attachments(invoice_id);
CREATE INDEX idx_invoice_attachments_user ON invoice_attachments(user_id);
