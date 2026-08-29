package llc.feelingfroggy.finances.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    /**
     * The row the unique index protects. Re-importing the same file updates the position rather
     * than adding a second copy of it.
     */
    @Query("""
        select h from Holding h
        where h.account.id = :accountId and h.security.id = :securityId and h.asOf = :asOf
        """)
    Optional<Holding> findSnapshot(@Param("accountId") Long accountId,
                                   @Param("securityId") Long securityId,
                                   @Param("asOf") LocalDate asOf);

    /**
     * Every holding in the most recent snapshot, across all accounts.
     *
     * <p>Both associations are fetch-joined. {@code open-in-view} is off, so a lazy proxy touched
     * while building the response throws — the endpoint answered 500 until this was added — and the
     * join also collapses what would otherwise be a query per row.
     *
     * <p>Per account, because snapshots arrive per file: importing one brokerage today and another
     * next week must not hide the first behind the second's newer date. The subquery therefore
     * takes the latest {@code as_of} <em>for that account</em>, not overall.
     */
    @Query("""
        select h from Holding h
        join fetch h.security
        join fetch h.account
        where h.userId = :userId
          and h.asOf = (select max(h2.asOf) from Holding h2 where h2.account.id = h.account.id)
        order by h.account.id, h.marketValue desc
        """)
    List<Holding> findLatestForUser(@Param("userId") Long userId);

    @Query("""
        select h from Holding h
        join fetch h.security
        join fetch h.account
        where h.account.id = :accountId
          and h.asOf = (select max(h2.asOf) from Holding h2 where h2.account.id = :accountId)
        order by h.marketValue desc
        """)
    List<Holding> findLatestForAccount(@Param("accountId") Long accountId);
}
