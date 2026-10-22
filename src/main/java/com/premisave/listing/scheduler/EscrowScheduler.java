package com.premisave.listing.scheduler;

import com.premisave.listing.service.BookingReconciliationService;
import com.premisave.listing.service.EscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscrowScheduler {

    private final EscrowService escrowService;
    private final BookingReconciliationService reconciliationService;

    @Scheduled(cron = "${escrow.release-check-cron:0 */5 * * * *}")
    public void releaseDueEscrow() {
        escrowService.releaseDueEscrow();
    }

    @Scheduled(cron = "${escrow.settlement-retry-cron:0 */3 * * * *}")
    public void retryPendingSettlements() {
        escrowService.retryPendingSettlements();
    }

    @Scheduled(cron = "${booking.reconciliation.cron:0 */2 * * * *}")
    public void reconcilePendingBookings() {
        reconciliationService.reconcilePendingBookings();
    }
}
