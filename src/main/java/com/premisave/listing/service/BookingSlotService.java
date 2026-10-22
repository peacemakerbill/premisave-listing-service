package com.premisave.listing.service;

import com.premisave.listing.entity.BookingSlot;
import com.premisave.listing.exception.BookingConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The atomic guard against double booking. A booking (or an owner's blocked
 * dates) claims one slot per night before any money moves; MongoDB's unique
 * _id turns a second claim on the same night into a duplicate-key error, so
 * two simultaneous requests cannot both win.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSlotService {

    public static final String BLOCK_PREFIX = "BLOCK-";

    private final MongoTemplate mongoTemplate;

    /** Nights covered by a stay: check-in date up to, not including, the check-out date. */
    public static List<LocalDate> nights(LocalDateTime checkIn, LocalDateTime checkOut) {
        return nights(checkIn.toLocalDate(), checkOut.toLocalDate());
    }

    public static List<LocalDate> nights(LocalDate from, LocalDate toExclusive) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; d.isBefore(toExclusive); d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }

    public void claim(String listingId, String bookingId, LocalDateTime checkIn, LocalDateTime checkOut) {
        claimNights(listingId, bookingId, nights(checkIn, checkOut), checkOut.toLocalDate().plusDays(7));
    }

    /** Owner blocks nights from (inclusive) to (exclusive). */
    public void claimBlock(String listingId, String blockId, LocalDate from, LocalDate toExclusive) {
        claimNights(listingId, BLOCK_PREFIX + blockId, nights(from, toExclusive), toExclusive.plusDays(7));
    }

    private void claimNights(String listingId, String holderId, List<LocalDate> days, LocalDate expireOn) {
        Date expireAt = Date.from(expireOn.atStartOfDay(ZoneId.systemDefault()).toInstant());
        for (LocalDate day : days) {
            BookingSlot slot = new BookingSlot(listingId + "|" + day, holderId, listingId, day.toString(), expireAt);
            try {
                mongoTemplate.insert(slot);
            } catch (DuplicateKeyException e) {
                release(holderId);
                throw new BookingConflictException(
                        "This listing is already booked or blocked for part or all of the requested dates.");
            }
        }
    }

    public void release(String holderId) {
        try {
            mongoTemplate.remove(new Query(Criteria.where("bookingId").is(holderId)), BookingSlot.class);
        } catch (Exception e) {
            log.error("Could not release date slots for {}: {}", holderId, e.getMessage());
        }
    }

    /** Dates in [from, to] that cannot be booked, for the availability calendar. */
    public List<String> unavailableDays(String listingId, LocalDate from, LocalDate to) {
        String prefix = listingId + "|";
        Query query = new Query(Criteria.where("_id").gte(prefix + from).lte(prefix + to));
        return mongoTemplate.find(query, BookingSlot.class).stream()
                .map(BookingSlot::getDay)
                .sorted()
                .toList();
    }

    /** True if the block exists and belongs to the listing. */
    public boolean blockExists(String listingId, String blockId) {
        Query query = new Query(Criteria.where("bookingId").is(BLOCK_PREFIX + blockId).and("listingId").is(listingId));
        return mongoTemplate.exists(query, BookingSlot.class);
    }
}
