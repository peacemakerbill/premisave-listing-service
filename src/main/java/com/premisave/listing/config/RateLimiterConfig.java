package com.premisave.listing.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Distributed, Redis-backed rate limiting via Bucket4j's Lettuce
 * integration — bucket4j_jdk17-lettuce was already a pom.xml dependency,
 * just never actually wired up. This replaces the previous implementation,
 * which built a single in-memory Bucket shared by the entire JVM: every
 * user and every endpoint drew from the same 100-requests-per-minute
 * budget, and running more than one instance behind a load balancer would
 * have multiplied the effective limit per instance rather than enforcing
 * it — the opposite of what a rate limiter is for.
 *
 * Buckets now live in Redis, keyed by tier + caller identity
 * (see RateLimiterInterceptor), so the limit is correctly shared across
 * however many instances of this service are running, and each user/IP
 * has their own independent budget per tier.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiterConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> redisConnection;

    @SuppressWarnings("deprecation")
	@Bean
    public ProxyManager<String> bucketProxyManager() {
        redisClient = RedisClient.create(RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build());

        redisConnection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return LettuceBasedProxyManager.builderFor(redisConnection)
                // Lets a bucket's Redis key expire shortly after it would
                // naturally have refilled to full capacity, rather than
                // living forever — avoids an ever-growing set of stale
                // per-user/per-IP keys for callers who stop making requests.
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }

    @PreDestroy
    public void shutdown() {
        if (redisConnection != null) {
            redisConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    public static BucketConfiguration toBucketConfiguration(RateLimitProperties.Tier tier) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(tier.getCapacity())
                        .refillGreedy(tier.getRefillTokens(), Duration.ofSeconds(tier.getRefillPeriodSeconds()))
                        .build())
                .build();
    }
}