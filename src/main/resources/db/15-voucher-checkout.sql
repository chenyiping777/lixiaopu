-- 已有库先完成 14 号迁移，再停应用执行本文件一次；新库使用 01-full-schema.sql。
-- 复用 voucher_order，不新增券钱包或流水表。
USE lixiaopu;

ALTER TABLE voucher ADD COLUMN validity_days INT NOT NULL DEFAULT 30;

ALTER TABLE voucher_order
  ADD COLUMN pay_value BIGINT NULL,
  ADD COLUMN actual_value BIGINT NULL,
  ADD COLUMN validity_days INT NULL,
  ADD COLUMN transaction_id VARCHAR(64) NULL,
  ADD COLUMN pay_expire_time DATETIME NULL,
  ADD COLUMN valid_from DATETIME NULL,
  ADD COLUMN valid_until DATETIME NULL,
  ADD COLUMN use_status VARCHAR(16) NULL,
  ADD COLUMN bound_order_id BIGINT NULL,
  ADD UNIQUE KEY uk_voucher_payment_transaction(transaction_id),
  ADD KEY idx_voucher_order_wallet(user_id,id);

-- 旧实现只生成待支付订单。按当前模板冻结快照，不推断已付款、不自动发券。
-- 历史支付期限已结束时仍保持不可支付；有异常旧状态须先人工核对。
UPDATE voucher_order vo JOIN voucher v ON v.id = vo.voucher_id
SET vo.pay_value = v.pay_value,
    vo.actual_value = v.actual_value,
    vo.validity_days = v.validity_days,
    vo.pay_expire_time = DATE_ADD(vo.create_time, INTERVAL 1 HOUR)
WHERE vo.status = 1 AND vo.pay_value IS NULL;

ALTER TABLE `order`
  ADD COLUMN voucher_order_id BIGINT NULL,
  ADD COLUMN voucher_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  ADD KEY idx_order_voucher(voucher_order_id),
  ADD CONSTRAINT chk_order_voucher_discount CHECK
    (voucher_discount_amount >= 0 AND voucher_discount_amount <= total_goods_amount);
