ALTER TABLE users
    ADD COLUMN email_verified BIT(1) NOT NULL DEFAULT 0;

-- Accounts that predate email verification are grandfathered in; forcing a
-- retroactive verification would lock existing users out of the banner-free UX.
UPDATE users SET email_verified = 1;

-- One-time tokens for email verification and password reset. Only SHA-256
-- hashes are stored, mirroring refresh_tokens.
CREATE TABLE account_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash  VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    purpose     ENUM ('VERIFY_EMAIL','RESET_PASSWORD') NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6),
    created_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_account_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
