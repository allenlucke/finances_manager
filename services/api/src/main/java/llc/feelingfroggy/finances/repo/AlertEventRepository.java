package llc.feelingfroggy.finances.repo;

import java.util.List;
import llc.feelingfroggy.finances.domain.AlertEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    @Query("select e from AlertEvent e join fetch e.alert a join fetch a.security left join fetch e.quote "
        + "where e.userId = :userId order by e.firedAt desc")
    List<AlertEvent> findRecentForUser(@Param("userId") Long userId, Pageable page);
}
