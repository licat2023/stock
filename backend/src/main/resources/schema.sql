-- Defines the five inventory tables, with decimal scales shared by the API.
CREATE TABLE IF NOT EXISTS commodity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    spec VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS stock_in (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    commodity_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(24, 6) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    previous_quantity INT NOT NULL,
    previous_unit_price DECIMAL(24, 6) NOT NULL,
    previous_amount DECIMAL(20, 2) NOT NULL,
    INDEX ix_stock_in_history (commodity_id, warehouse_id, created_at, id),
    INDEX ix_stock_in_warehouse (warehouse_id),
    CONSTRAINT fk_stock_in_commodity FOREIGN KEY (commodity_id) REFERENCES commodity (id),
    CONSTRAINT fk_stock_in_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CHECK (quantity > 0 AND unit_price >= 0 AND amount >= 0),
    CHECK (previous_quantity >= 0 AND previous_unit_price >= 0 AND previous_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS stock_out (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    commodity_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(24, 6) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    previous_quantity INT NOT NULL,
    previous_unit_price DECIMAL(24, 6) NOT NULL,
    previous_amount DECIMAL(20, 2) NOT NULL,
    INDEX ix_stock_out_history (commodity_id, warehouse_id, created_at, id),
    INDEX ix_stock_out_warehouse (warehouse_id),
    CONSTRAINT fk_stock_out_commodity FOREIGN KEY (commodity_id) REFERENCES commodity (id),
    CONSTRAINT fk_stock_out_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CHECK (quantity > 0 AND unit_price >= 0 AND amount >= 0),
    CHECK (previous_quantity >= 0 AND previous_unit_price >= 0 AND previous_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS stock (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    commodity_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(24, 6) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    UNIQUE KEY uk_stock_pair (commodity_id, warehouse_id),
    CONSTRAINT fk_stock_commodity FOREIGN KEY (commodity_id) REFERENCES commodity (id),
    CONSTRAINT fk_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CHECK (quantity > 0 AND unit_price >= 0 AND amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
