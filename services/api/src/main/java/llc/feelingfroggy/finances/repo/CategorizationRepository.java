package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Categorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategorizationRepository extends JpaRepository<Categorization, Long> {

    /** The one unresolved suggestion, if any. A partial unique index guarantees at most one. */
    @Query("select c from Categorization c where c.transaction.id = :txId and c.resolution is null")
    Optional<Categorization> findOpenFor(@Param("txId") Long transactionId);

    /** Resolved history for a transaction, newest first. */
    List<Categorization> findByTransactionIdOrderByIdDesc(Long transactionId);

    /** The open suggestions for a page of transactions, for the review queue. */
    @Query("select c from Categorization c left join fetch c.suggestedCategory "
        + "where c.transaction.id in :ids and c.resolution is null")
    List<Categorization> findOpenForAll(@Param("ids") java.util.Collection<Long> ids);

    Optional<Categorization> findTopByTransactionIdOrderByIdDesc(Long transactionId);
}
