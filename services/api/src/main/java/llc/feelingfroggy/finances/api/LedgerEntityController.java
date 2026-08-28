package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import llc.feelingfroggy.finances.domain.EntityKind;
import llc.feelingfroggy.finances.domain.LedgerEntity;
import llc.feelingfroggy.finances.repo.LedgerEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Personal vs. Feeling Froggy LLC. Everything else hangs off one of these. */
@RestController
@RequestMapping("/api/v1/entities")
public class LedgerEntityController {

    private final LedgerEntityRepository entities;
    private final CurrentUser currentUser;

    public LedgerEntityController(LedgerEntityRepository entities, CurrentUser currentUser) {
        this.entities = entities;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<EntityView> list() {
        return entities.findByUserIdOrderByName(currentUser.id())
            .stream().map(EntityView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntityView create(@Valid @RequestBody CreateEntity request) {
        return EntityView.of(entities.save(
            new LedgerEntity(currentUser.id(), request.name(), request.kind())));
    }

    public record EntityView(Long id, String name, String kind, boolean active) {
        static EntityView of(LedgerEntity entity) {
            return new EntityView(entity.getId(), entity.getName(), entity.getKind().code(),
                entity.isActive());
        }
    }

    public record CreateEntity(@NotBlank @Size(max = 120) String name, @NotNull EntityKind kind) {
    }
}
