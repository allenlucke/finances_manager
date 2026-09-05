package llc.feelingfroggy.finances.api;

import java.time.Instant;
import llc.feelingfroggy.finances.domain.DomainRuleViolation;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the failures this API actually produces into useful status codes.
 *
 * <p>Deliberately terse about database errors. Constraint names and SQL fragments describe the
 * schema to whoever triggered them, and per docs/SECURITY.md an error message is one of the places
 * account identifiers must never leak. The full cause is logged, not returned.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException exception) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                error -> error.getField(),
                error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                (first, second) -> first));

        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setProperty("fields", fields);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * A constraint the database refused. Most of these are meaningful business rules rather than
     * bugs — a duplicate dedupe key is a re-import, an exclusion violation is an overlapping
     * target — so 409 is more accurate than 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onConstraintViolation(DataIntegrityViolationException exception) {
        log.warn("Constraint violation", exception);
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflicts with existing data");
        problem.setDetail("The change violates a data rule. Nothing was saved.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Two edits to the same row; the @Version column caught the second. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail onStaleWrite(OptimisticLockingFailureException exception) {
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Modified by someone else");
        problem.setDetail("This record changed after you loaded it. Reload and try again.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** A rule of the domain said no, in words meant for the person. Returned verbatim. */
    @ExceptionHandler(DomainRuleViolation.class)
    ProblemDetail onRuleViolation(DomainRuleViolation exception) {
        var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Not allowed");
        problem.setDetail(exception.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * An {@code IllegalStateException} is a fault, not a refusal.
     *
     * <p>This used to echo its message as a 422, on the assumption that only domain rules threw
     * it. The JDK, Hibernate and Spring throw it for internal faults, and every one of those
     * reached the client as a 422 carrying an internal message — while the handler above was
     * carefully hiding constraint names. The cause is logged; the client gets a sentence with
     * nothing in it.
     *
     * <p>Deliberately this one type and not {@code RuntimeException}: a blanket handler would take
     * Spring MVC's own exceptions away from it — an unreadable request body is a 400 and must stay
     * one. Everything not listed here falls through to Boot's default error handling, which does
     * not include exception messages in responses.
     */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail onFault(IllegalStateException exception) {
        log.error("Unhandled fault", exception);
        var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Something went wrong");
        problem.setDetail("The request could not be completed. Nothing was saved.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
