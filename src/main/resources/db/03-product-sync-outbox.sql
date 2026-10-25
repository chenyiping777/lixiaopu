CREATE TABLE IF NOT EXISTS product_sync_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_product_sync_pending(completed_at, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品事务同步事件；完成后可按保留期归档';
