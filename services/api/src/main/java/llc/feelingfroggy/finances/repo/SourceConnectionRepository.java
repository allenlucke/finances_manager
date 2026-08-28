package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.SourceConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceConnectionRepository extends JpaRepository<SourceConnection, Long> {

    List<SourceConnection> findByUserIdOrderByName(Long userId);

    Optional<SourceConnection> findByIdAndUserId(Long id, Long userId);
}
