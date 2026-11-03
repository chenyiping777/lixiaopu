-- Existing databases: execute once before starting the migrated order module.
ALTER TABLE `order`
  ADD COLUMN logistics_company VARCHAR(128) NULL,
  ADD COLUMN logistics_no VARCHAR(128) NULL;

CREATE TABLE IF NOT EXISTS order_tracking (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  logistics_no VARCHAR(128) NULL,
  order_id BIGINT NOT NULL,
  logistics_status TINYINT NULL,
  location VARCHAR(255) NULL,
  description VARCHAR(1024) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tracking_order_time(order_id, create_time),
  FOREIGN KEY(order_id) REFERENCES `order`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
