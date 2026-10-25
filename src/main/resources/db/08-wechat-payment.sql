-- 已有库执行一次；新库直接使用 01-full-schema.sql。
ALTER TABLE `order`
  ADD COLUMN transaction_id VARCHAR(64) NULL COMMENT '微信支付交易号',
  ADD COLUMN pay_expire_time DATETIME NULL COMMENT '固定支付截止时间';
-- 迁移前默认待支付有效期为 redis.cache.ttl.order-ttl=3600 秒。
UPDATE `order` SET pay_expire_time = DATE_ADD(create_time, INTERVAL 3600 SECOND)
WHERE status = 1 AND pay_expire_time IS NULL;
