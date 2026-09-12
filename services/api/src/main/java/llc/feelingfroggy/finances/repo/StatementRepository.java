package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByAccountIdOrderByPeriodEndDesc(Long accountId);

    Optional<Statement> findByIdAndUserId(Long id, Long userId);

    /** Mirrors {@code ux_statement_period}: one checkpoint per account per period end. */
    boolean existsByAccountIdAndPeriodEnd(Long accountId, java.time.LocalDate periodEnd);

    Optional<Statement> findByAccountIdAndPeriodEnd(Long accountId, java.time.LocalDate periodEnd);
}
