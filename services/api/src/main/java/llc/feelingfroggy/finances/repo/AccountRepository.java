package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByName(Long userId);

    List<Account> findByUserIdAndActiveTrueOrderByName(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    /**
     * The stable link between an imported row and an account: the parser's one-way hash of the
     * institution's account number, stored here rather than the number itself (docs/SECURITY.md).
     */
    Optional<Account> findByUserIdAndExternalId(Long userId, String externalId);

    /**
     * Fallback for the first import, before any account has been linked. Returns a list rather than
     * an Optional on purpose — two accounts can share the last four digits, and picking one
     * arbitrarily would file a child's brokerage activity against a joint account.
     */
    List<Account> findByUserIdAndMask(Long userId, String mask);
}
