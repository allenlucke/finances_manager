package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.BacktestRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {

    @Query("select r from BacktestRun r join fetch r.security where r.userId = :userId "
        + "order by r.createdAt desc")
    List<BacktestRun> findRecentForUser(@Param("userId") Long userId, Pageable page);

    @Query("select r from BacktestRun r join fetch r.security where r.id = :id and r.userId = :userId")
    Optional<BacktestRun> findForUser(@Param("id") Long id, @Param("userId") Long userId);
}
