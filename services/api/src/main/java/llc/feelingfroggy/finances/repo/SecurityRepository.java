package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Security;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityRepository extends JpaRepository<Security, Long> {

    /** Matches the {@code (user_id, symbol)} unique index, so an import reuses rather than duplicates. */
    Optional<Security> findByUserIdAndSymbol(Long userId, String symbol);

    List<Security> findByUserIdOrderBySymbolAsc(Long userId);
}
