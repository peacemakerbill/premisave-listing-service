package com.premisave.listing.service;

import com.premisave.listing.client.WalletServiceClient;
import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.wallet_service.EscrowDetails;
import com.premisave.listing.dto.wallet_service.EscrowHoldRequest;
import com.premisave.listing.dto.wallet_service.EscrowHoldResponse;
import com.premisave.listing.dto.wallet_service.EscrowPayoutRecord;
import com.premisave.listing.dto.wallet_service.EscrowPayoutRequest;
import com.premisave.listing.dto.wallet_service.EscrowPayoutResponse;
import com.premisave.listing.service.WalletTransferService.Result;
import com.premisave.listing.service.WalletTransferService.Status;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Talks to wallet-service's escrow endpoints and turns every possible answer into
 * PAID, DECLINED or UNKNOWN, the same three results WalletTransferService uses.
 *
 * PAID     the hold was made, or the payout reached the recipient (possibly on an earlier attempt).
 * DECLINED wallet-service refused it and nothing moved (insufficient funds, frozen or missing wallet,
 *          bad request), or its record says FAILED.
 * UNKNOWN  we cannot tell yet (timeout, outage, 5xx, a record still PENDING). Never treated as failed.
 *
 * A reference is an idempotency key on the wallet side: a repeat of one that already went through is
 * answered with 409 and moves nothing. A 409, a timeout or an outage is therefore checked against
 * GET /internal/escrow/{holdReference}. A reference whose earlier attempt ended FAILED may be sent again.
 * Wallet-service settles a PENDING record on its own within a few minutes, so UNKNOWN is temporary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletEscrowService {

    /** Outcome of a hold. The amounts are filled in when the hold exists. */
    public record HoldResult(Status status, String message, BigDecimal commission, BigDecimal totalDebited) {
        public boolean paid() {
            return status == Status.PAID;
        }

        static HoldResult paid(BigDecimal commission, BigDecimal totalDebited) {
            return new HoldResult(Status.PAID, null, commission, totalDebited);
        }

        static HoldResult declined(String message) {
            return new HoldResult(Status.DECLINED, message, null, null);
        }

        static HoldResult unknown(String message) {
            return new HoldResult(Status.UNKNOWN, message, null, null);
        }
    }

    private final WalletServiceClient walletServiceClient;

    // ====================== HOLD ======================

    public HoldResult hold(EscrowHoldRequest request) {
        try {
            ApiResponse<EscrowHoldResponse> response = walletServiceClient.holdEscrow(request);
            EscrowHoldResponse data = response != null ? response.getData() : null;
            if (response == null || !response.isSuccess() || data == null) {
                return HoldResult.declined(response != null ? response.getMessage() : "no response from wallet-service");
            }
            return holdFromStatus(data.getStatus(), data.getCommission(), data.getTotalDebited(), null);
        } catch (FeignException.Conflict e) {
            HoldResult found = lookupHold(request.getReference());
            if (found.status() != Status.UNKNOWN) {
                log.info("Escrow hold {} was already processed by wallet-service.", request.getReference());
                return found;
            }
            return HoldResult.unknown("wallet-service says reference " + request.getReference()
                    + " is already used, but its hold is not completed yet");
        } catch (FeignException.FeignClientException e) {
            return HoldResult.declined(WalletTransferService.describe(e));
        } catch (RuntimeException e) {
            // Timeout, outage or 5xx: the money may or may not have moved. Ask before giving up.
            HoldResult found = lookupHold(request.getReference());
            if (found.status() != Status.UNKNOWN) {
                log.info("Escrow hold {} resolved to {} although the call failed ({}).",
                        request.getReference(), found.status(), e.getClass().getSimpleName());
                return found;
            }
            return HoldResult.unknown(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** What happened to the hold with this reference. UNKNOWN covers both "no record" and "could not ask". */
    public HoldResult lookupHold(String reference) {
        try {
            ApiResponse<EscrowDetails> response = walletServiceClient.getEscrow(reference);
            EscrowDetails details = response != null ? response.getData() : null;
            if (response == null || !response.isSuccess() || details == null) {
                return HoldResult.unknown("empty answer when looking up escrow " + reference);
            }
            return holdFromStatus(details.getStatus(), details.getCommission(), details.getTotalDebited(),
                    details.getFailureReason());
        } catch (FeignException.NotFound e) {
            return HoldResult.unknown("no escrow hold recorded for " + reference);
        } catch (RuntimeException e) {
            return HoldResult.unknown("could not look up escrow " + reference + ": " + e.getClass().getSimpleName());
        }
    }

    private static HoldResult holdFromStatus(String status, BigDecimal commission, BigDecimal totalDebited,
                                             String failureReason) {
        if (status == null) {
            return HoldResult.unknown("escrow hold has no status");
        }
        return switch (status.toUpperCase()) {
            case "HELD", "PARTIALLY_PAID", "PAID_OUT" -> HoldResult.paid(commission, totalDebited);
            case "FAILED" -> HoldResult.declined(failureReason != null ? failureReason : "escrow hold failed");
            default -> HoldResult.unknown("escrow hold has status " + status);
        };
    }

    // ====================== PAYOUT ======================

    public Result payout(String holdReference, EscrowPayoutRequest request) {
        try {
            ApiResponse<EscrowPayoutResponse> response = walletServiceClient.payoutEscrow(holdReference, request);
            EscrowPayoutResponse data = response != null ? response.getData() : null;
            if (response == null || !response.isSuccess() || data == null) {
                return Result.declined(response != null ? response.getMessage() : "no response from wallet-service");
            }
            return payoutFromStatus(data.getStatus(), null);
        } catch (FeignException.Conflict e) {
            // Either this payout reference was already used, or the hold cannot be paid out right now.
            Result found = lookupPayout(holdReference, request.getReference());
            if (found.status() != Status.UNKNOWN) {
                log.info("Escrow payout {} on {} was already processed by wallet-service.",
                        request.getReference(), holdReference);
                return found;
            }
            return Result.unknown("wallet-service refused payout " + request.getReference() + " on " + holdReference
                    + " with a conflict and no completed payout was found: " + WalletTransferService.describe(e));
        } catch (FeignException.FeignClientException e) {
            return Result.declined(WalletTransferService.describe(e));
        } catch (RuntimeException e) {
            Result found = lookupPayout(holdReference, request.getReference());
            if (found.status() != Status.UNKNOWN) {
                log.info("Escrow payout {} on {} resolved to {} although the call failed ({}).",
                        request.getReference(), holdReference, found.status(), e.getClass().getSimpleName());
                return found;
            }
            return Result.unknown(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** What happened to one payout of a hold, found by the payout's own reference. */
    public Result lookupPayout(String holdReference, String payoutReference) {
        try {
            ApiResponse<EscrowDetails> response = walletServiceClient.getEscrow(holdReference);
            EscrowDetails details = response != null ? response.getData() : null;
            if (response == null || !response.isSuccess() || details == null) {
                return Result.unknown("empty answer when looking up escrow " + holdReference);
            }
            if (details.getPayouts() != null) {
                for (EscrowPayoutRecord payout : details.getPayouts()) {
                    if (payoutReference.equals(payout.getReference())) {
                        return payoutFromStatus(payout.getStatus(), payout.getFailureReason());
                    }
                }
            }
            return Result.unknown("no payout " + payoutReference + " recorded on escrow " + holdReference
                    + " (hold status " + details.getStatus() + ")");
        } catch (FeignException.NotFound e) {
            return Result.unknown("no escrow hold recorded for " + holdReference);
        } catch (RuntimeException e) {
            return Result.unknown("could not look up escrow " + holdReference + ": " + e.getClass().getSimpleName());
        }
    }

    private static Result payoutFromStatus(String status, String failureReason) {
        if (status == null) {
            return Result.unknown("escrow payout has no status");
        }
        return switch (status.toUpperCase()) {
            case "SUCCESS" -> Result.paid(null);
            case "FAILED" -> Result.declined(failureReason != null ? failureReason : "escrow payout failed");
            default -> Result.unknown("escrow payout has status " + status);
        };
    }
}
