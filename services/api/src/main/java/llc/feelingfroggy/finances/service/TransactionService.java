package llc.feelingfroggy.finances.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.Category;
import llc.feelingfroggy.finances.domain.Direction;
import llc.feelingfroggy.finances.domain.Transaction;
import llc.feelingfroggy.finances.repo.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for transactions. Lives here rather than in a controller because
 * docs/ARCHITECTURE.md is explicit that if a calculation determines a number a human will act on,
 * it happens in this service.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactions;

    public TransactionService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    /** A plain, single-account transaction. */
    @Transactional
    public Transaction record(Long userId, Account account, LocalDate date, BigDecimal amount,
                              Direction direction, String description, Category category) {
        var transaction = new Transaction(userId, account, date, amount, direction, description,
            dedupeKey(account.getId(), date, amount, description));
        if (category != null) {
            transaction.setCategory(category);
        }
        return transactions.save(transaction);
    }

    /**
     * Records a movement between two of the user's own accounts as <strong>both</strong> legs.
     *
     * <p>This is the manual-entry counterpart to how import works, and the two differ on purpose:
     *
     * <ul>
     *   <li><b>Import is single-sided.</b> A statement describes one account, so each imported file
     *       supplies one leg. Importing both the checking and the card statement yields both legs
     *       naturally, each with its own dedupe key, and {@code transferGroupId} is what later
     *       matches them up.
     *   <li><b>Manual entry must write both.</b> Typing "I paid the card $696.31" and watching the
     *       card balance not move is simply wrong — the user described a complete movement, so both
     *       sides exist and the system should say so.
     * </ul>
     *
     * <p>The second leg carries the opposite direction. Paying a credit card is a debit on checking
     * (money leaves) and a credit on the card (the amount owed shrinks toward zero), which under the
     * project's sign convention is exactly what makes the pair net to zero and leave net worth
     * unchanged — a transfer moves money, it does not create or destroy it.
     *
     * <p>Neither leg is categorized. That is the credit-card double-count rule: the budget was
     * charged when the purchase happened, and a database CHECK enforces it independently of this
     * method.
     *
     * @return both legs, the originating account's first
     */
    @Transactional
    public List<Transaction> recordTransfer(Long userId, Account from, Account to, LocalDate date,
                                            BigDecimal amount, Direction direction,
                                            String description) {
        if (from.getId().equals(to.getId())) {
            throw new IllegalStateException("A transfer needs two different accounts");
        }

        UUID group = UUID.randomUUID();

        var outgoing = new Transaction(userId, from, date, amount, direction, description,
            dedupeKey(from.getId(), date, amount, description));
        outgoing.markAsTransfer(to);
        outgoing.setTransferGroupId(group);

        var incoming = new Transaction(userId, to, date, amount, opposite(direction), description,
            dedupeKey(to.getId(), date, amount, description));
        incoming.markAsTransfer(from);
        incoming.setTransferGroupId(group);

        return List.of(transactions.save(outgoing), transactions.save(incoming));
    }

    /**
     * Soft-deletes a transaction, and every other leg of the same transfer.
     *
     * <p>Deleting one half of a transfer would leave the books asymmetric: money would appear to
     * vanish from one account without arriving anywhere. A transfer is one event, so it is removed
     * as one event.
     */
    @Transactional
    public int softDelete(Transaction transaction) {
        Instant now = Instant.now();
        transaction.softDelete(now);
        transactions.save(transaction);
        int deleted = 1;

        if (transaction.getTransferGroupId() != null) {
            for (Transaction leg : transactions.findByTransferGroupId(transaction.getTransferGroupId())) {
                if (!leg.getId().equals(transaction.getId()) && !leg.isDeleted()) {
                    leg.softDelete(now);
                    transactions.save(leg);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    /**
     * Undoes a soft delete, bringing every leg of a transfer back with it.
     *
     * <p>Mirrors {@link #softDelete} exactly, and has to: that method removes the far leg of a
     * transfer along with the near one, so restoring only the row named here would leave money
     * arriving in an account it never left. Legs deleted at a different moment are left alone —
     * they were a separate decision, and reviving them would be this method inventing one.
     *
     * @return how many rows came back
     */
    @Transactional
    public int restore(Transaction transaction) {
        Instant deletedAt = transaction.getDeletedAt();
        transaction.restore();
        transactions.save(transaction);
        int restored = 1;

        if (transaction.getTransferGroupId() != null) {
            for (Transaction leg : transactions.findByTransferGroupId(transaction.getTransferGroupId())) {
                if (!leg.getId().equals(transaction.getId())
                        && leg.isDeleted()
                        && deletedAt.equals(leg.getDeletedAt())) {
                    leg.restore();
                    transactions.save(leg);
                    restored++;
                }
            }
        }
        return restored;
    }

    private static Direction opposite(Direction direction) {
        return direction == Direction.DEBIT ? Direction.CREDIT : Direction.DEBIT;
    }

    /**
     * Mirrors {@code finances_ai.ingest.csv_reader.dedupe_key}: account, ISO date, amount to four
     * decimals, and the normalized description, SHA-256'd and truncated to 32 hex characters.
     *
     * <p>The two implementations must agree, or a manually entered row and the same row later
     * imported from a statement will not collide and the ledger gains a duplicate. Because the
     * account id is part of the payload, the two legs of a transfer get different keys for free.
     */
    public static String dedupeKey(Long accountId, LocalDate date, BigDecimal amount,
                                   String description) {
        String payload = String.join("|",
            String.valueOf(accountId),
            date.toString(),
            amount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            description.trim().toUpperCase().replaceAll("\\s+", " "));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JDK", e);
        }
    }
}
