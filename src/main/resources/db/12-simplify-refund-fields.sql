-- 仅用于曾执行旧版 11（含两个原因列和 wechat_refund_id）的数据库。
-- 先停止应用；合并当前有效原因后移除冗余列。保留 refund_no，避免改变原退款请求。
-- 尚未执行 11 的库直接执行新版 11；新库直接使用 01-full-schema.sql。
SET @has_fail_reason = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='order' AND column_name='refund_fail_reason');
SET @refund_ddl = IF(@has_fail_reason=0,
  'ALTER TABLE `order` ADD COLUMN refund_fail_reason VARCHAR(500) NULL', 'SELECT 1');
PREPARE refund_stmt FROM @refund_ddl;
EXECUTE refund_stmt;
DEALLOCATE PREPARE refund_stmt;

SET @has_reject_reason = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='order' AND column_name='refund_reject_reason');
SET @has_refund_error = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='order' AND column_name='refund_error');
SET @has_wechat_refund_id = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='order' AND column_name='wechat_refund_id');

SET @legacy_reason = CASE
  WHEN @has_reject_reason>0 AND @has_refund_error>0 THEN
    'CASE WHEN pay_status=5 THEN COALESCE(NULLIF(refund_reject_reason,''''),refund_error)
          WHEN pay_status=3 THEN COALESCE(NULLIF(refund_error,''''),refund_reject_reason) ELSE NULL END'
  WHEN @has_reject_reason>0 THEN 'IF(pay_status IN (3,5),refund_reject_reason,NULL)'
  WHEN @has_refund_error>0 THEN 'IF(pay_status IN (3,5),refund_error,NULL)'
  ELSE 'NULL' END;
SET @refund_dml = CONCAT('UPDATE `order` SET refund_fail_reason=', @legacy_reason,
  ' WHERE refund_fail_reason IS NULL');
PREPARE refund_stmt FROM @refund_dml;
EXECUTE refund_stmt;
DEALLOCATE PREPARE refund_stmt;

SET @drop_refund_columns = CONCAT_WS(', ',
  IF(@has_reject_reason>0,'DROP COLUMN refund_reject_reason',NULL),
  IF(@has_refund_error>0,'DROP COLUMN refund_error',NULL),
  IF(@has_wechat_refund_id>0,'DROP COLUMN wechat_refund_id',NULL));
SET @refund_ddl = IF(@drop_refund_columns='', 'SELECT 1', CONCAT('ALTER TABLE `order` ',@drop_refund_columns));
PREPARE refund_stmt FROM @refund_ddl;
EXECUTE refund_stmt;
DEALLOCATE PREPARE refund_stmt;
