package llc.feelingfroggy.finances.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.TradeOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    @Query("select o from TradeOrder o join fetch o.security where o.userId = :userId "
        + "order by o.createdAt desc")
    List<TradeOrder> findRecentForUser(@Param("userId") Long userId, Pageable page);

    @Query("select o from TradeOrder o join fetch o.security where o.id = :id and o.userId = :userId")
    Optional<TradeOrder> findForUser(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select o from TradeOrder o join fetch o.security where o.userId = :userId "
        + "and o.status in ('submitted', 'accepted', 'partially_filled') and o.brokerOrderId is not null")
    List<TradeOrder> findOpenAtBroker(@Param("userId") Long userId);

    /** The newest order a strategy proposed in any of the given states (M7c). */
    Optional<TradeOrder> findTopByStrategyIdAndStatusInOrderByIdDesc(Long strategyId,
                                                                    java.util.Collection<String> statuses);

    /** What the paper orders sent since a moment add up to — the figure the daily cap is checked against. */
    @Query("select coalesce(sum(o.notionalEstimate), 0) from TradeOrder o where o.userId = :userId "
        + "and o.venue = 'paper' and o.status in :statuses and o.submittedAt >= :since")
    BigDecimal sentNotionalSince(@Param("userId") Long userId,
                                 @Param("statuses") java.util.Collection<String> statuses,
                                 @Param("since") Instant since);
}
