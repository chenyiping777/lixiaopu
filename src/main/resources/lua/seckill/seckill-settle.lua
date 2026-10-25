-- KEYS：活动、用户集合、预扣记录；ARGV：用户、订单、处理结果。
-- 删除预扣凭据和退库存同属原子操作，补偿重复执行也只退一次。
local userId = ARGV[1]
local orderId = ARGV[2]
local outcome = ARGV[3]
if redis.call('HGET', KEYS[3], orderId) ~= userId then return 0 end
redis.call('HDEL', KEYS[3], orderId)
if outcome ~= 'SUCCESS' then
    -- Redis 重建会从 DB 重新计算库存；旧预扣记录不存在时不执行补偿。
    if redis.call('HGET', KEYS[1], 'ready') == '1' then
        redis.call('HINCRBY', KEYS[1], 'stock', 1)
    end
    -- 用户已在数据库有订单时保持快速挡板，其他业务失败允许重新参与。
    if outcome ~= 'ALREADY_ORDERED' then redis.call('SREM', KEYS[2], userId) end
end
return 1
