-- 目标库：lixiaopu。先备份并停止旧版本，再执行本脚本。
-- 删除五张未接入业务的旧优惠券表及其数据，按外键依赖顺序删除。
-- 不禁用外键检查：若存在预期外引用，立即失败，先核查依赖。
USE lixiaopu;
DROP TABLE IF EXISTS coupon_order_rel;
DROP TABLE IF EXISTS coupon_scope_detail;
DROP TABLE IF EXISTS coupon_user;
DROP TABLE IF EXISTS coupon;
DROP TABLE IF EXISTS coupon_mutex_group;

CREATE TABLE IF NOT EXISTS voucher (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  sub_title VARCHAR(255) NULL,
  rules VARCHAR(2000) NULL,
  pay_value BIGINT NOT NULL,
  actual_value BIGINT NOT NULL,
  type TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CHECK (pay_value > 0 AND actual_value >= pay_value),
  KEY idx_voucher_status(status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀代金券';

CREATE TABLE IF NOT EXISTS seckill_voucher (
  voucher_id BIGINT PRIMARY KEY,
  stock INT NOT NULL,
  begin_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CHECK (stock >= 0),
  CHECK (end_time > begin_time),
  FOREIGN KEY(voucher_id) REFERENCES voucher(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀库存及活动时间';

CREATE TABLE IF NOT EXISTS voucher_order (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  voucher_id BIGINT NOT NULL,
  pay_type TINYINT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  pay_time DATETIME NULL,
  use_time DATETIME NULL,
  refund_time DATETIME NULL,
  UNIQUE KEY uk_voucher_order_user(user_id,voucher_id),
  KEY idx_voucher_order_voucher(voucher_id),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(voucher_id) REFERENCES voucher(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购券订单，status=1为待支付';

CREATE TABLE IF NOT EXISTS seckill_request (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  voucher_id BIGINT NOT NULL,
  state VARCHAR(16) NOT NULL,
  reason VARCHAR(64) NULL,
  attempts INT NOT NULL DEFAULT 0,
  sent_at DATETIME NULL,
  next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_seckill_request_dispatch(state,next_attempt_at,id),
  KEY idx_seckill_request_activity(voucher_id,state),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(voucher_id) REFERENCES voucher(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀受理状态及可靠消息记录';
