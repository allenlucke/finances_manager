package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.LedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntityRepository extends JpaRepository<LedgerEntity, Long> {

    List<LedgerEntity> findByUserIdOrderByName(Long userId);

    /**
     * Always look up by id AND userId. Filtering on the owner in the query rather than checking it
     * after the fetch means a wrong id returns empty instead of another user's row.
     */
    Optional<LedgerEntity> findByIdAndUserId(Long id, Long userId);
}
