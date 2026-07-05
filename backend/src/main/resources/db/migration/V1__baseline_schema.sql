-- Baseline schema matching the entities as of the switch from ddl-auto=update
-- to Flyway. Existing databases are baselined at version 1 (this file is
-- skipped); fresh databases are created from it.

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    created_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE transactions (
    id                    BIGINT                   NOT NULL AUTO_INCREMENT,
    description           VARCHAR(255)             NOT NULL,
    amount                DECIMAL(10, 2)           NOT NULL,
    type                  ENUM ('INCOME','EXPENSE') NOT NULL,
    category              VARCHAR(255)             NOT NULL,
    date                  DATE                     NOT NULL,
    notes                 VARCHAR(255),
    plaid_transaction_id  VARCHAR(255),
    plaid_account_id      VARCHAR(255),
    merchant_name         VARCHAR(255),
    pending               BIT(1),
    created_at            DATETIME(6),
    user_id               BIGINT                   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transactions_plaid_transaction_id UNIQUE (plaid_transaction_id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE budgets (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    category      VARCHAR(255)   NOT NULL,
    limit_amount  DECIMAL(10, 2) NOT NULL,
    budget_month  INT            NOT NULL,
    budget_year   INT            NOT NULL,
    created_at    DATETIME(6),
    user_id       BIGINT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_budgets_user_category_month_year UNIQUE (user_id, category, budget_month, budget_year),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE plaid_items (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT        NOT NULL,
    plaid_item_id           VARCHAR(255)  NOT NULL,
    access_token_encrypted  VARCHAR(1024) NOT NULL,
    institution_id          VARCHAR(255),
    institution_name        VARCHAR(255),
    sync_cursor             VARCHAR(1024),
    last_synced_at          DATETIME(6),
    sync_error              VARCHAR(255),
    created_at              DATETIME(6),
    updated_at              DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_plaid_items_plaid_item_id UNIQUE (plaid_item_id),
    CONSTRAINT fk_plaid_items_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE plaid_accounts (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    plaid_item_id      BIGINT         NOT NULL,
    plaid_account_id   VARCHAR(255)   NOT NULL,
    name               VARCHAR(255)   NOT NULL,
    account_mask       VARCHAR(255),
    account_type       VARCHAR(255),
    account_subtype    VARCHAR(255),
    current_balance    DECIMAL(14, 2),
    available_balance  DECIMAL(14, 2),
    iso_currency_code  VARCHAR(255),
    created_at         DATETIME(6),
    updated_at         DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_plaid_accounts_plaid_account_id UNIQUE (plaid_account_id),
    CONSTRAINT fk_plaid_accounts_item FOREIGN KEY (plaid_item_id) REFERENCES plaid_items (id)
) ENGINE = InnoDB;
