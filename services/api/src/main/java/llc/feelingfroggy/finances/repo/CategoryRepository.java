package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Category;
import llc.feelingfroggy.finances.domain.CategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIdOrderByName(Long userId);

    List<Category> findByUserIdAndKindOrderByName(Long userId, CategoryKind kind);

    Optional<Category> findByIdAndUserId(Long id, Long userId);
}
