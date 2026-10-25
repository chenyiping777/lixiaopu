-- 已有库先完成15号迁移；备份后执行一次。新库仅使用01-full-schema.sql。
-- 普通券不限库存、不设购买活动时间、允许重复购买。没有领券/满减/库存字段。
USE lixiaopu;
ALTER TABLE voucher_order ADD COLUMN seckill_voucher_id BIGINT NULL;
-- 原有秒杀订单保留一人一单，普通订单不关联秒杀活动。
UPDATE voucher_order vo JOIN voucher v ON v.id=vo.voucher_id
SET vo.seckill_voucher_id=vo.voucher_id WHERE v.type=1;
ALTER TABLE voucher_order
  DROP INDEX uk_voucher_order_user,
  ADD UNIQUE KEY uk_voucher_order_user(user_id,seckill_voucher_id),
  ADD CONSTRAINT chk_voucher_order_activity CHECK
    (seckill_voucher_id IS NULL OR seckill_voucher_id=voucher_id);
