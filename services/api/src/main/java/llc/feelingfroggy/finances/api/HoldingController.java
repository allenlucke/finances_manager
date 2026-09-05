package llc.feelingfroggy.finances.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import llc.feelingfroggy.finances.domain.Holding;
import llc.feelingfroggy.finances.repo.HoldingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What is owned, from the most recent positions snapshot (M4).
 *
 * <p>Read-only. Holdings arrive by import; there is no hand-entry endpoint, because a position is
 * a fact the broker reports rather than a decision the user makes. Typing one in would create a
 * figure with nothing behind it.
 */
@RestController
@RequestMapping("/api/v1/holdings")
public class HoldingController {

    private final HoldingRepository holdings;
    private final CurrentUser currentUser;

    public HoldingController(HoldingRepository holdings, CurrentUser currentUser) {
        this.holdings = holdings;
        this.currentUser = currentUser;
    }

    /** Every account's latest snapshot, largest position first. */
    @GetMapping
    public List<HoldingView> latest() {
        return holdings.findLatestForUser(currentUser.id()).stream().map(HoldingView::of).toList();
    }

    @GetMapping("/account/{accountId}")
    public List<HoldingView> forAccount(@PathVariable Long accountId) {
        return holdings.findLatestForAccount(accountId, currentUser.id()).stream()
            .map(HoldingView::of)
            .toList();
    }

    /**
     * @param marketValue always positive — a holding is a magnitude, not a signed ledger amount
     * @param costBasis null where the export said not-applicable, which is not zero
     * @param asOf the snapshot's date. Exposed because a market value is only as current as the
     *     last file imported, and a stale figure you can see the date of beats a fresh-looking one
     */
    public record HoldingView(Long id, Long accountId, String symbol, String name,
                              boolean cash, String securityType, LocalDate asOf,
                              BigDecimal quantity, BigDecimal lastPrice, BigDecimal marketValue,
                              BigDecimal costBasis, BigDecimal totalGainLoss) {

        static HoldingView of(Holding h) {
            var security = h.getSecurity();
            return new HoldingView(h.getId(), h.getAccount().getId(), security.getSymbol(),
                security.getName(), security.isCash(), security.getSecurityType(), h.getAsOf(),
                h.getQuantity(), h.getLastPrice(), h.getMarketValue(), h.getCostBasis(),
                h.getTotalGainLoss());
        }
    }
}
