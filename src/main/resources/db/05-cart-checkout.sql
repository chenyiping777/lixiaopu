-- 历史迁移：当前已恢复栗子商城直接创建订单流程，不再需要执行此脚本。
-- 已有数据库执行一次；CREATE IF NOT EXISTS 不删除已有购物车/订单。
CREATE TABLE IF NOT EXISTS cart_checkout_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  item_keys TEXT NOT NULL,
  cleared_at DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cart_checkout_request(user_id,request_id),
  KEY idx_cart_checkout_pending(cleared_at,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车结算幂等记录和待清理条目';
