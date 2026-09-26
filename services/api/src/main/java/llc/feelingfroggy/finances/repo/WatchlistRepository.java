package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Long> {

    /** Fetch-joined: open-in-view is off, and the view needs the symbol. */
    @Query("select w from WatchlistEntry w join fetch w.security where w.userId = :userId "
        + "order by w.security.symbol")
    List<WatchlistEntry> findAllForUser(@Param("userId") Long userId);

    Optional<WatchlistEntry> findByIdAndUserId(Long id, Long userId);

    Optional<WatchlistEntry> findByUserIdAndSecurityId(Long userId, Long securityId);
}
