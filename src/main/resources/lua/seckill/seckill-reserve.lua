-- KEYS：活动 Hash、已抢用户 Set、预扣记录 Hash，使用相同券号 hash tag。
-- ARGV：用户 ID、订单 ID。订单 ID 始终作为字符串保存，不能 tonumber。
local ACCEPTED = 0
local DUPLICATE = 1
local SOLD_OUT = 2
local NOT_OPEN = 3
local NOT_READY = 4
if redis.call('HGET', KEYS[1], 'ready') ~= '1' then return NOT_READY end
local redisTime = redis.call('TIME')
local now = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)
if now < tonumber(redis.call('HGET', KEYS[1], 'begin'))
    or now >= tonumber(redis.call('HGET', KEYS[1], 'end')) then return NOT_OPEN end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return DUPLICATE end
if tonumber(redis.call('HGET', KEYS[1], 'stock')) <= 0 then return SOLD_OUT end

redis.call('HINCRBY', KEYS[1], 'stock', -1)
redis.call('SADD', KEYS[2], ARGV[1])
-- 同时留存预扣凭据，覆盖 Lua 成功但 PENDING 尚未写入时的进程退出。
redis.call('HSET', KEYS[3], ARGV[2], ARGV[1])
return ACCEPTED
