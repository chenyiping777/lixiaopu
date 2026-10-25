-- 运维重建：调用方持有该券排他锁，暂停该券受理和消费。
-- ARGV[1] JSON 快照：stock、beginTime、endTime、buyers、reservations。
local snapshot = cjson.decode(ARGV[1])
redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
redis.call('HSET', KEYS[1], 'stock', snapshot.stock,
    'begin', snapshot.beginTime, 'end', snapshot.endTime, 'ready', '1')
for _, userId in ipairs(snapshot.buyers) do redis.call('SADD', KEYS[2], userId) end
for orderId, userId in pairs(snapshot.reservations) do
    redis.call('HSET', KEYS[3], orderId, userId)
end
return 1
