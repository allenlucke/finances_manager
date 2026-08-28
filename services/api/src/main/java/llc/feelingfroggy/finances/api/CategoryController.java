package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import llc.feelingfroggy.finances.domain.Category;
import llc.feelingfroggy.finances.domain.CategoryKind;
import llc.feelingfroggy.finances.repo.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryRepository categories;
    private final CurrentUser currentUser;

    public CategoryController(CategoryRepository categories, CurrentUser currentUser) {
        this.categories = categories;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<CategoryView> list(@RequestParam(required = false) CategoryKind kind) {
        Long userId = currentUser.id();
        var found = kind == null
            ? categories.findByUserIdOrderByName(userId)
            : categories.findByUserIdAndKindOrderByName(userId, kind);
        return found.stream().map(CategoryView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@Valid @RequestBody CreateCategory request) {
        Long userId = currentUser.id();
        var category = new Category(userId, request.name(), request.kind());

        if (request.parentId() != null) {
            var parent = categories.findByIdAndUserId(request.parentId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown parent"));
            if (parent.getKind() != request.kind()) {
                // An expense nested under an income category would make every roll-up meaningless.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A category must have the same kind as its parent");
            }
            category.setParent(parent);
        }

        return CategoryView.of(categories.save(category));
    }

    public record CategoryView(Long id, String name, String kind, Long parentId, boolean active) {
        static CategoryView of(Category category) {
            return new CategoryView(category.getId(), category.getName(), category.getKind().code(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.isActive());
        }
    }

    public record CreateCategory(
        @NotBlank @Size(max = 160) String name,
        @NotNull CategoryKind kind,
        Long parentId) {
    }
}
