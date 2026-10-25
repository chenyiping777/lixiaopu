-- 已有数据库执行一次；新库的完整建表脚本已包含该字段。
ALTER TABLE `order` ADD COLUMN address_snapshot TEXT NULL COMMENT '下单收货地址快照';
