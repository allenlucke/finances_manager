package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * A bank, card, brokerage, or cash account.
 *
 * <p>There is no {@code isCredit} flag — the legacy one is replaced by {@link AccountType}, so
 * there is no second field to keep in sync. Whether a balance is a liability follows from the type,
 * and under the project's sign convention a liability's balance is simply negative.
 */
@Entity
@Table(name = "account")
public class Account extends UserOwned {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_entity_id", nullable = false)
    private LedgerEntity ledgerEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private SourceConnection connection;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Convert(converter = AccountType.Conv.class)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    /** Last four only, per docs/SECURITY.md. */
    @Column(name = "mask", length = 4)
    private String mask;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Account() {
    }

    public Account(Long userId, LedgerEntity ledgerEntity, String name, AccountType accountType) {
        super(userId);
        this.ledgerEntity = ledgerEntity;
        this.name = name;
        this.accountType = accountType;
    }

    public LedgerEntity getLedgerEntity() {
        return ledgerEntity;
    }

    public void setLedgerEntity(LedgerEntity ledgerEntity) {
        this.ledgerEntity = ledgerEntity;
    }

    public Institution getInstitution() {
        return institution;
    }

    public void setInstitution(Institution institution) {
        this.institution = institution;
    }

    public SourceConnection getConnection() {
        return connection;
    }

    public void setConnection(SourceConnection connection) {
        this.connection = connection;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public void setOpenedOn(LocalDate openedOn) {
        this.openedOn = openedOn;
    }

    public LocalDate getClosedOn() {
        return closedOn;
    }

    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
