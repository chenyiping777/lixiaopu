-- Only WeChat payment is supported; payment state uses status and pay_time.
-- Safe to rerun, including on a database created from the current full schema.
SET @drop_order_pay_type = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'order' AND column_name = 'pay_type'),
  'ALTER TABLE `order` DROP COLUMN pay_type',
  'SELECT 1'
);
PREPARE drop_order_pay_type_stmt FROM @drop_order_pay_type;
EXECUTE drop_order_pay_type_stmt;
DEALLOCATE PREPARE drop_order_pay_type_stmt;
