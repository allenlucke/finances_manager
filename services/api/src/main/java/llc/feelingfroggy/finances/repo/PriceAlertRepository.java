package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    @Query("select a from PriceAlert a join fetch a.security where a.userId = :userId "
        + "order by a.createdAt desc")
    List<PriceAlert> findAllForUser(@Param("userId") Long userId);

    @Query("select a from PriceAlert a join fetch a.security where a.userId = :userId and a.active = true")
    List<PriceAlert> findActiveForUser(@Param("userId") Long userId);

    Optional<PriceAlert> findByIdAndUserId(Long id, Long userId);
}
