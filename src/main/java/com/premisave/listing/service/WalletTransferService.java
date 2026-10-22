package com.premisave.listing.service;

import com.premisave.listing.client.WalletServiceClient;
import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.wallet_service.WalletTransferRecord;
import com.premisave.listing.dto.wallet_service.WalletTransferRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes wallet-service transfers and turns every possible answer into one of
 * three results, so callers never have to interpret HTTP errors:
 *
 * PAID     the money moved (possibly on an earlier attempt).
 * DECLINED wallet-service refused it and nothing moved (insufficient funds,
 *          frozen or missing wallet, bad request).
 * UNKNOWN  we cannot tell yet (timeout, outage, 5xx). Do not treat as failed.
 *
 * Wallet-service rejects a repeated reference with 409 instead of repeating
 * the original answer. A 409, a timeout or an outage is therefore checked
 * against GET /internal/transfer/{reference}: a record there means the money
 * moved. A transfer record is written only after the balances moved.
 *
 * Never send the same reference twice at the same moment: wallet-service has
 * no unique index on it. Callers retry only after the first call has ended.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTransferService {

    public enum Status { PAID, DECLINED, UNKNOWN }

    public record Result(Status status, String message) {
        public boolean paid() {
            return status == Status.PAID;
        }

        static Result paid(String message) {
            return new Result(Status.PAID, message);
        }

        static Result declined(String message) {
            return new Result(Status.DECLINED, message);
        }

        static Result unknown(String message) {
            return new Result(Status.UNKNOWN, message);
        }
    }

    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final WalletServiceClient walletServiceClient;

    public Result transfer(WalletTransferRequest request) {
        try {
            ApiResponse<Object> response = walletServiceClient.transferFunds(request);
            if (response != null && response.isSuccess()) {
                return Result.paid(null);
            }
            return Result.declined(response != null ? response.getMessage() : "no response from wallet-service");
        } catch (FeignException.Conflict e) {
            Result found = lookup(request.getReference());
            if (found.paid()) {
                log.info("Transfer {} was already processed by wallet-service.", request.getReference());
                return found;
            }
            if (found.status() == Status.DECLINED) {
                return found;
            }
            return Result.unknown("wallet-service says reference " + request.getReference()
                    + " is already used, but no completed transfer was found yet");
        } catch (FeignException.FeignClientException e) {
            return Result.declined(describe(e));
        } catch (RuntimeException e) {
            // Timeout, outage or 5xx: the money may or may not have moved. Ask before giving up.
            Result found = lookup(request.getReference());
            if (found.paid()) {
                log.info("Transfer {} went through although the call failed ({}).",
                        request.getReference(), e.getClass().getSimpleName());
                return found;
            }
            return Result.unknown(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** What happened to the transfer with this reference. UNKNOWN covers both "no record" and "could not ask". */
    public Result lookup(String reference) {
        try {
            ApiResponse<WalletTransferRecord> response = walletServiceClient.getTransfer(reference);
            WalletTransferRecord record = response != null ? response.getData() : null;
            if (response == null || !response.isSuccess() || record == null) {
                return Result.unknown("empty answer when looking up " + reference);
            }
            if ("SUCCESS".equalsIgnoreCase(record.getStatus())) {
                return Result.paid(null);
            }
            if ("FAILED".equalsIgnoreCase(record.getStatus())) {
                return Result.declined(record.getFailureReason() != null ? record.getFailureReason() : "transfer failed");
            }
            return Result.unknown("transfer " + reference + " has status " + record.getStatus());
        } catch (FeignException.NotFound e) {
            return Result.unknown("no transfer recorded for " + reference);
        } catch (RuntimeException e) {
            return Result.unknown("could not look up " + reference + ": " + e.getClass().getSimpleName());
        }
    }

    /** The reason wallet-service gave for a 4xx, or a plain default for the status. Shared with WalletEscrowService. */
    static String describe(FeignException e) {
        String body = e.contentUTF8();
        if (body != null) {
            Matcher m = MESSAGE.matcher(body);
            if (m.find() && !m.group(1).isBlank()) {
                return m.group(1);
            }
        }
        return switch (e.status()) {
            case 422 -> "Insufficient wallet balance.";
            case 403 -> "A wallet involved in this payment is frozen.";
            case 404 -> "A wallet involved in this payment was not found.";
            case 400 -> "The payment request was rejected.";
            default -> "The payment was refused (" + e.status() + ").";
        };
    }
}
