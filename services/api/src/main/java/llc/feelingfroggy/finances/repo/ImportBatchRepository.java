package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    List<ImportBatch> findByUserIdOrderByStartedAtDesc(Long userId);

    Optional<ImportBatch> findByIdAndUserId(Long id, Long userId);
}
