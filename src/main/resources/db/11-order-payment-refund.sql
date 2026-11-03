-- 已有库执行一次；先停止旧版本退款任务，再迁移并部署新版本。
-- 新库直接使用 01-full-schema.sql，无需执行本脚本。
ALTER TABLE `order`
  ADD COLUMN pay_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未支付 1已支付 2申请退款 3退款中 4退款成功 5申请失败',
  ADD COLUMN refund_no VARCHAR(64) NULL,
  ADD COLUMN refund_reason VARCHAR(80) NULL,
  ADD COLUMN refund_fail_reason VARCHAR(500) NULL,
  ADD COLUMN refund_time DATETIME NULL,
  ADD UNIQUE KEY uk_order_refund_no(refund_no),
  ADD KEY idx_order_pay_status_time(pay_status, update_time);

UPDATE `order` SET pay_status = CASE
  WHEN status = 10 THEN 4
  WHEN pay_time IS NOT NULL AND transaction_id IS NOT NULL THEN 1
  ELSE 0 END;

-- 兼容曾执行旧版 09-order-refund.sql 的数据库：保留原退款单号，避免重复退款。
-- 旧表只作历史备份，新代码不再读写；本脚本不删除历史数据。
SET @has_old_refund = (SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'order_refund');
SET @migrate_refund = IF(@has_old_refund > 0,
  'UPDATE `order` o JOIN order_refund r ON r.order_id=o.id
   SET o.pay_status=CASE r.status WHEN ''PENDING'' THEN 2 WHEN ''REJECTED'' THEN 5
         WHEN ''SUCCESS'' THEN 4 ELSE 3 END,
       o.refund_no=r.refund_no, o.refund_reason=r.reason,
       o.refund_time=r.success_time,
       o.refund_fail_reason=CASE r.status WHEN ''REJECTED'' THEN r.review_reason
         WHEN ''SUCCESS'' THEN NULL WHEN ''CLOSED'' THEN ''微信退款已关闭，请在商户平台核实处理''
         WHEN ''ABNORMAL'' THEN ''微信退款异常，请在商户平台处理后同步结果'' ELSE r.last_error END,
       o.status=IF(o.status IN (7,10),r.original_status,o.status)
   WHERE o.refund_no IS NULL',
  'SELECT 1');
PREPARE migrate_refund_stmt FROM @migrate_refund;
EXECUTE migrate_refund_stmt;
DEALLOCATE PREPARE migrate_refund_stmt;
