package com.example.fridiet.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    // 데이터 저장 + 만료 시간(초 단위) 설정
    public void setDataExpire(String key, String value, long durationSeconds) {
        redisTemplate.opsForValue().set(key, value, durationSeconds, TimeUnit.SECONDS);
    }

    // 데이터 조회
    public String getData(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 읽기와 삭제를 하나의 Redis 명령으로 처리해 동시 재사용을 방지한다.
    public String consumeData(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    // 데이터 삭제
    public void deleteData(String key) {
        redisTemplate.delete(key);
    }

    // key 존재 여부 확인
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
