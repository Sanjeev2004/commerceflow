-- Redis time prevents gateway clock skew. The script atomically refills and consumes.
local nowParts = redis.call('TIME')
local now = tonumber(nowParts[1]) + tonumber(nowParts[2]) / 1000000
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local state = redis.call('HMGET', KEYS[1], 'tokens', 'last')
local tokens = tonumber(state[1]) or capacity
local last = tonumber(state[2]) or now
tokens = math.min(capacity, tokens + math.max(0, now - last) * rate)
local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end
redis.call('HSET', KEYS[1], 'tokens', tokens, 'last', now)
redis.call('EXPIRE', KEYS[1], math.ceil(capacity / rate) * 2)
return {allowed, math.floor(tokens)}
