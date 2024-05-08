-- 从输入参数中获取普通令牌的容量（capacity）、高等级令牌的容量（h_capacity）、当前时间（now）、扣减的令牌数（deduct）和请求的令牌数（request）
local capacity = tonumber(ARGV[1])
local h_capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local deduct = tonumber(ARGV[4])
local request = tonumber(ARGV[5])

-- 初始化允许的数量为0
local allowed_num = 0

-- 从Redis中获取上次刷新高等级令牌的时间（h_last_refreshed），如果不存在则设置为0
local h_last_refreshed = tonumber(redis.call('get', KEYS[3]))
if h_last_refreshed == nil then
    h_last_refreshed = 0
end

-- 从Redis中获取当前的高等级令牌数（h_tokens），如果不存在则设置为高等级令牌的容量
local h_tokens = tonumber(redis.call('hget', KEYS[1], 'h_token'))
if h_tokens == nil then
    h_tokens = h_capacity
end

-- 计算填充的高等级令牌数，通过当前时间和上次刷新时间的差值乘以高等级令牌的容量，然后取这个值和高等级令牌的容量的最小值
local delta = math.max(0, now - h_last_refreshed)
local h_filled_tokens = math.min(h_capacity, h_tokens + (delta * h_capacity))

-- 如果普通令牌的容量为0，那么只考虑高等级令牌
if capacity == 0 then

    -- 判断是否允许，如果填充的高等级令牌数大于或等于请求的令牌数，那么允许
    local allowed = h_filled_tokens >= request
    local new_tokens = h_filled_tokens

    -- 如果允许，那么允许的数量为1，新的高等级令牌数为填充的高等级令牌数减去请求的令牌数
    if allowed then
        allowed_num = 1
        new_tokens = h_filled_tokens - request
    end

    -- 将新的高等级令牌数存入Redis，并设置键的过期时间为1秒
    redis.call('hset', KEYS[1], 'h_token', new_tokens)
    redis.call('setex', KEYS[3], '1', now)

-- 如果普通令牌的容量不为0，那么同时考虑普通令牌和高等级令牌
else

    -- 从Redis中获取当前的普通令牌数，如果不存在则设置为普通令牌的容量
    local tokens = tonumber(redis.call('hget', KEYS[1], 'token'))
    if tokens == nil then
        tokens = capacity
    end

    -- 从Redis中获取上次刷新普通令牌的时间，如果不存在则设置为0
    local last_refreshed = tonumber(redis.call('get', KEYS[2]))
    if last_refreshed == nil then
        last_refreshed = 0
    end

    -- 计算填充的普通令牌数，通过当前时间和上次刷新时间的差值乘以普通令牌的容量，然后取这个值和普通令牌的容量的最小值
    local delta = math.max(0, now - last_refreshed)
    local filled_tokens = math.min(capacity, tokens + (delta * capacity))

    -- 判断是否允许，如果填充的高等级令牌数和填充的普通令牌数的总和大于请求的令牌数，那么允许
    local allowed = h_filled_tokens + filled_tokens > request
    local new_tokens = filled_tokens

    -- 如果允许，那么允许的数量为1，新的普通令牌数为填充的普通令牌数减去扣减的令牌数
    if allowed then
        allowed_num = 1
        new_tokens = filled_tokens - deduct
    end

    -- 将新的普通令牌数存入Redis，并设置键的过期时间为1秒
    redis.call('hset', KEYS[1], 'token', new_tokens)
    redis.call('setex', KEYS[2], '1', now)

end

-- 设置键的过期时间为1秒
redis.call('expire', KEYS[1], '1')

-- 返回允许的数量
return allowed_num