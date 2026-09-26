package llc.feelingfroggy.finances.repo;

import java.time.Instant;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    /** Mirrors {@code ux_quote_moment}: refreshing between trades re-reads the same last trade. */
    boolean existsBySecurityIdAndAsOf(Long securityId, Instant asOf);

    Optional<Quote> findTopBySecurityIdOrderByAsOfDescIdDesc(Long securityId);
}
