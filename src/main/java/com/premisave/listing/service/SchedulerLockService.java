package com.premisave.listing.service;

import com.premisave.listing.entity.SchedulerLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Lets only one instance run a scheduled job at a time. The lock is a
 * document in the scheduler_locks collection, taken with a single atomic
 * update. It expires by itself after lockAtMost, so a crashed instance never
 * blocks a job for long, and it stays held for at least lockAtLeast so a
 * second instance whose clock ticks a moment later does not run the same
 * job again.
 */
@Slf4j
@Service
public class SchedulerLockService {

    private final MongoTemplate mongoTemplate;
    private final String instanceId = UUID.randomUUID().toString();

    public SchedulerLockService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Runs the task if the lock could be taken. Returns whether it ran. */
    public boolean runLocked(String name, Duration lockAtMost, Duration lockAtLeast, Runnable task) {
        Date start = new Date();
        if (!tryAcquire(name, start, lockAtMost)) {
            log.debug("Job '{}' is running on another instance, skipping.", name);
            return false;
        }
        try {
            task.run();
        } catch (Exception e) {
            log.error("Job '{}' failed: {}: {}", name, e.getClass().getSimpleName(), e.getMessage());
        } finally {
            release(name, new Date(start.getTime() + lockAtLeast.toMillis()));
        }
        return true;
    }

    private boolean tryAcquire(String name, Date now, Duration lockAtMost) {
        Date until = new Date(now.getTime() + lockAtMost.toMillis());

        Query expired = new Query(Criteria.where("_id").is(name).and("lockedUntil").lte(now));
        Update take = new Update().set("lockedUntil", until).set("lockedBy", instanceId).set("lockedAt", now);
        if (mongoTemplate.updateFirst(expired, take, SchedulerLock.class).getModifiedCount() > 0) {
            return true;
        }

        try {
            mongoTemplate.insert(new SchedulerLock(name, until, instanceId, now));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private void release(String name, Date keepLockedUntil) {
        Date now = new Date();
        Date until = keepLockedUntil.after(now) ? keepLockedUntil : now;
        try {
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(name).and("lockedBy").is(instanceId)),
                    new Update().set("lockedUntil", until),
                    SchedulerLock.class);
        } catch (Exception e) {
            log.warn("Could not release lock '{}': {}", name, e.getMessage());
        }
    }
}
