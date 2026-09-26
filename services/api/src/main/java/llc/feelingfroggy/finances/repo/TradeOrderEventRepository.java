package llc.feelingfroggy.finances.repo;

import java.util.List;
import llc.feelingfroggy.finances.domain.TradeOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderEventRepository extends JpaRepository<TradeOrderEvent, Long> {

    List<TradeOrderEvent> findByOrderIdAndUserIdOrderByAtAscIdAsc(Long orderId, Long userId);
}
