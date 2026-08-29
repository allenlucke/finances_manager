package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * An instrument — a stock, fund, money-market sweep or cash line.
 *
 * <p>Shared across accounts: the same index fund held in three accounts is one row here and three
 * {@link Holding}s. That is what makes "how much of this do I own in total" answerable.
 *
 * <p>Named {@code Security} after the financial instrument, which unavoidably collides with the
 * everyday sense of the word in a codebase that also has a {@code security} package. The package
 * qualifies it wherever both are in scope.
 */
@Entity
@Table(name = "security")
public class Security extends UserOwned {

    /** Ticker, with footnote markers already stripped — Fidelity writes {@code SPAXX**}. */
    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "security_type", nullable = false, length = 20)
    private String securityType = "unknown";

    /**
     * Whether this is cash rather than an investment.
     *
     * <p>Decided by symbol, never by a positions export's {@code Type} column: that column carries
     * the account's registration (Cash or Margin), and reading it as "is this row cash" classified
     * AAPL as a cash holding. A bug invisible without a real file.
     */
    @Column(name = "is_cash", nullable = false)
    private boolean cash;

    protected Security() {
    }

    public Security(Long userId, String symbol, String name, String securityType, boolean cash) {
        super(userId);
        this.symbol = symbol;
        this.name = name;
        this.securityType = securityType == null ? "unknown" : securityType;
        this.cash = cash;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }

    public boolean isCash() {
        return cash;
    }

    public void setCash(boolean cash) {
        this.cash = cash;
    }
}
