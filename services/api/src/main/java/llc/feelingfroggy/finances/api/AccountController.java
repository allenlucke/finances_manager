package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.AccountType;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.repo.LedgerEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountRepository accounts;
    private final LedgerEntityRepository entities;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public AccountController(AccountRepository accounts, LedgerEntityRepository entities,
                             JdbcTemplate jdbc, CurrentUser currentUser) {
        this.accounts = accounts;
        this.entities = entities;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    /** Accounts with their current balance, read from {@code v_account_balance}. */
    @GetMapping
    public List<AccountView> list() {
        return jdbc.query("""
            SELECT account_id, account_name, account_type, ledger_entity_id, currency,
                   is_active, balance, transaction_count, last_activity,
                   balance_source, balance_as_of, cost_basis, mask
            FROM v_account_balance
            WHERE user_id = ?
            ORDER BY is_active DESC, account_name
            """,
            this::toView,
            currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(@Valid @RequestBody CreateAccount request) {
        Long userId = currentUser.id();
        var entity = entities.findByIdAndUserId(request.ledgerEntityId(), userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown entity"));

        var account = new Account(userId, entity, request.name(), request.accountType());
        account.setMask(request.mask());
        // The stable link an import reported. Setting it here means the very next import of that
        // file matches exactly, with nobody typing an account number.
        account.setExternalId(request.externalId());
        if (request.currency() != null) {
            account.setCurrency(request.currency());
        }
        var saved = accounts.save(account);

        return new AccountView(saved.getId(), saved.getName(), saved.getAccountType().code(),
            entity.getId(), saved.getCurrency(), saved.isActive(), BigDecimal.ZERO, 0, null,
            "transactions", null, null, saved.getMask());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountView> get(@PathVariable Long id) {
        // One row. This used to run the whole balance report and filter in memory — trivial at
        // ten accounts, and the kind of thing that stops being trivial without anyone touching
        // this file again.
        var rows = jdbc.query("""
            SELECT account_id, account_name, account_type, ledger_entity_id, currency,
                   is_active, balance, transaction_count, last_activity,
                   balance_source, balance_as_of, cost_basis, mask
            FROM v_account_balance
            WHERE user_id = ? AND account_id = ?
            """, this::toView, currentUser.id(), id);
        return rows.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(rows.getFirst());
    }

    private AccountView toView(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AccountView(
            rs.getLong("account_id"),
            rs.getString("account_name"),
            rs.getString("account_type"),
            rs.getLong("ledger_entity_id"),
            rs.getString("currency"),
            rs.getBoolean("is_active"),
            rs.getBigDecimal("balance"),
            rs.getInt("transaction_count"),
            rs.getObject("last_activity", LocalDate.class),
            rs.getString("balance_source"),
            rs.getObject("balance_as_of", LocalDate.class),
            rs.getBigDecimal("cost_basis"),
            rs.getString("mask"));
    }

    /**
     * @param balanceSource {@code holdings} when the balance is the market value of a positions
     *     snapshot, {@code transactions} when it is the sum of the ledger. A brokerage switches to
     *     the former the moment a positions file is imported, because summing the cash paid in
     *     understates it by every dollar of growth.
     * @param balanceAsOf the snapshot's date, null for a transaction-derived balance. Surfaced so
     *     a stale market value is visibly stale rather than quietly wrong.
     * @param costBasis what the holdings cost, when known — the other half of a gain figure.
     */
    public record AccountView(Long id, String name, String accountType, Long ledgerEntityId,
                              String currency, boolean active, BigDecimal balance,
                              int transactionCount, LocalDate lastActivity,
                              String balanceSource, LocalDate balanceAsOf, BigDecimal costBasis,
                              /** Last four, for recognising the account. Stored on creation and,
                               * until now, never returned — the UI had a "Number" column that
                               * rendered a dash on every row, forever. */
                              String mask) {
    }

    public record CreateAccount(
        @NotBlank @Size(max = 160) String name,
        @NotNull AccountType accountType,
        @NotNull Long ledgerEntityId,
        @Size(max = 4) String mask,
        @Size(min = 3, max = 3) String currency,
        /** Opaque link reported by an import; never an account number. */
        @Size(max = 255) String externalId) {
    }
}
