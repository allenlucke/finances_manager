package llc.feelingfroggy.finances.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query here filters {@code deletedAt is null}. Deletion is soft (the dedupe key stays
 * claimed so a re-import cannot resurrect a row), which means a query that forgets the filter
 * silently counts deleted money.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
        select t from Transaction t
        where t.userId = :userId and t.deletedAt is null
          and t.transactionDate >= :from and t.transactionDate <= :to
        order by t.transactionDate desc, t.id desc
        """)
    Page<Transaction> findInRange(@Param("userId") Long userId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  Pageable pageable);

    @Query("select t from Transaction t where t.id = :id and t.userId = :userId and t.deletedAt is null")
    Optional<Transaction> findLive(@Param("id") Long id, @Param("userId") Long userId);

    /** One deleted row, for restoring it. The only finder here that wants {@code deletedAt}. */
    @Query("select t from Transaction t where t.id = :id and t.userId = :userId and t.deletedAt is not null")
    Optional<Transaction> findDeleted(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * The recycle bin: recently deleted rows, newest deletion first.
     *
     * <p>Exists so a delete is reviewable rather than merely reversible-in-principle. Undo is not
     * much use if there is no way to see what was removed.
     */
    @Query("""
        select t from Transaction t
        where t.userId = :userId and t.deletedAt is not null
        order by t.deletedAt desc, t.id desc
        """)
    Page<Transaction> findDeletedFor(@Param("userId") Long userId, Pageable pageable);

    /** Import idempotency check, mirroring the {@code (account_id, dedupe_key)} unique index. */
    Optional<Transaction> findByAccountIdAndDedupeKey(Long accountId, String dedupeKey);

    /**
     * Every leg of one transfer. Includes soft-deleted rows so a partially deleted transfer can be
     * detected rather than silently half-applied.
     */
    List<Transaction> findByTransferGroupId(java.util.UUID transferGroupId);

    /**
     * The review queue: live, uncategorized, and not a transfer.
     *
     * <p>Transfers are excluded because an uncategorized transfer is <em>correct</em>, not pending
     * — categorizing a card payment would double-count the spend.
     */
    @Query("""
        select t from Transaction t
        where t.userId = :userId and t.deletedAt is null
          and t.category is null and t.transfer = false
        order by t.transactionDate desc, t.id desc
        """)
    Page<Transaction> findNeedingReview(@Param("userId") Long userId, Pageable pageable);
}
