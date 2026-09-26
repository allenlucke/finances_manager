package llc.feelingfroggy.finances.repo;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.domain.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByUserIdOrderByActiveDescDueOnAsc(Long userId);

    List<Reminder> findByUserIdAndActiveTrueOrderByDueOnAsc(Long userId);

    Optional<Reminder> findByIdAndUserId(Long id, Long userId);
}
