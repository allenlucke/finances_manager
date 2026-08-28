package llc.feelingfroggy.finances.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Target;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TargetRepository extends JpaRepository<Target, Long> {

    List<Target> findByUserIdOrderByEffectiveFromDesc(Long userId);

    Optional<Target> findByIdAndUserId(Long id, Long userId);

    /**
     * The single target governing a category and entity on a date. At most one can exist — the
     * {@code ex_target_no_overlap} exclusion constraint makes overlapping ranges unstorable — so
     * this is a genuine {@code Optional}, not a "first of several".
     */
    @Query("""
        select t from Target t
        where t.userId = :userId
          and t.category.id = :categoryId
          and t.ledgerEntity.id = :entityId
          and t.effectiveFrom <= :on
          and (t.effectiveTo is null or t.effectiveTo > :on)
        """)
    Optional<Target> findEffective(@Param("userId") Long userId,
                                   @Param("categoryId") Long categoryId,
                                   @Param("entityId") Long entityId,
                                   @Param("on") LocalDate on);

    /** The currently-open target, i.e. the one a new value would have to close first. */
    @Query("""
        select t from Target t
        where t.userId = :userId
          and t.category.id = :categoryId
          and t.ledgerEntity.id = :entityId
          and t.effectiveTo is null
        """)
    Optional<Target> findOpen(@Param("userId") Long userId,
                              @Param("categoryId") Long categoryId,
                              @Param("entityId") Long entityId);
}
