package com.premisave.listing.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Distributed, per-key rate limiting backed by Redis.
 *
 * Replaces the previous single shared in-memory Bucket bean, which had two
 * problems: every user on the instance shared ONE 100-req/min budget (so
 * one busy user could exhaust it for everyone else), and running more than
 * one instance multiplied the effective limit instead of sharing it, since
 * each instance's bucket lived only in its own JVM memory.
 *
 * This ProxyManager lets RateLimiterInterceptor build/reuse one bucket per
 * key (per authenticated user, or per IP for unauthenticated requests),
 * with the bucket's actual state stored in Redis so every instance is
 * counting against the same number.
 *
 * NOTE: this wiring hasn't been compiled/run in this environment (no access
 * to Maven Central here to pull the new bucket4j-redis/lettuce modules) —
 * run `mvn compile` yourself before deploying; this is the single piece of
 * new library surface most worth double-checking.
 */
@Configuration
public class RateLimiterConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient() {
        RedisURI uri = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();
        return RedisClient.create(uri);
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(RedisClient redisClient) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return redisClient.connect(codec);
    }

    @Bean
    public ProxyManager<String> bucketProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5)))
                .build();
    }
}