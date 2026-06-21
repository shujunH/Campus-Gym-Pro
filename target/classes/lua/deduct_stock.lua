-- ============================================
-- 库存扣减 Lua 脚本
-- KEYS[1]: slot stock key, e.g. "slot:stock:{slotId}"
-- ARGV[1]: 预留参数（预留用于后续扩展，如扣减数量）
-- 返回: 1=扣减成功, 0=库存不足, -1=KEY不存在
-- ============================================

local stockKey = KEYS[1]
local currentStock = redis.call('GET', stockKey)

if currentStock == false then
    return -1
end

currentStock = tonumber(currentStock)

if currentStock <= 0 then
    return 0
end

redis.call('DECR', stockKey)
return 1