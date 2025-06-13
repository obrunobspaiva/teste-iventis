-- Create partners table
CREATE TABLE partners (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create transaction_types enum
CREATE TYPE transaction_type AS ENUM ('CREDIT', 'DEBIT');

-- Create transaction_status enum
CREATE TYPE transaction_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED');

-- Create transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    partner_id BIGINT NOT NULL REFERENCES partners(id),
    type transaction_type NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    status transaction_status NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_idempotency_key UNIQUE (partner_id, idempotency_key)
);

-- Create index on partner_id for faster lookups
CREATE INDEX idx_transactions_partner_id ON transactions(partner_id);

-- Create index on status for reconciliation queries
CREATE INDEX idx_transactions_status ON transactions(status);

-- Create index on created_at for time-based queries
CREATE INDEX idx_transactions_created_at ON transactions(created_at);

-- Create partner_balances table to track current balances
CREATE TABLE partner_balances (
    partner_id BIGINT PRIMARY KEY REFERENCES partners(id),
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.0,
    version BIGINT NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Insert some sample partners
INSERT INTO partners (name, email) VALUES 
('Partner A', 'partner.a@example.com'),
('Partner B', 'partner.b@example.com'),
('Partner C', 'partner.c@example.com');

-- Initialize balances for sample partners
INSERT INTO partner_balances (partner_id, balance) VALUES 
(1, 0.0),
(2, 0.0),
(3, 0.0);
