package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    @Query("select s from Strategy s join fetch s.security where s.userId = :userId order by s.name")
    List<Strategy> findAllForUser(@Param("userId") Long userId);

    @Query("select s from Strategy s join fetch s.security where s.userId = :userId and s.active = true "
        + "order by s.id")
    List<Strategy> findActiveForUser(@Param("userId") Long userId);

    @Query("select s from Strategy s join fetch s.security where s.id = :id and s.userId = :userId")
    Optional<Strategy> findForUser(@Param("id") Long id, @Param("userId") Long userId);
}
