CREATE TABLE balance_snapshots (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    user_id            BIGINT         NOT NULL,
    snapshot_date      DATE           NOT NULL,
    total_assets       DECIMAL(14, 2) NOT NULL,
    total_liabilities  DECIMAL(14, 2) NOT NULL,
    net_worth          DECIMAL(14, 2) NOT NULL,
    created_at         DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_balance_snapshots_user_date UNIQUE (user_id, snapshot_date),
    CONSTRAINT fk_balance_snapshots_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
