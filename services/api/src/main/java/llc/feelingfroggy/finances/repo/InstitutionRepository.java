package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    List<Institution> findByUserIdOrderByName(Long userId);

    Optional<Institution> findByIdAndUserId(Long id, Long userId);
}
