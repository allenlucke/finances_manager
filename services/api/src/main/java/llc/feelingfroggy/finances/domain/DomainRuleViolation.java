package llc.feelingfroggy.finances.domain;

/**
 * A rule of the domain said no, in words written for the person who asked.
 *
 * <p>Thrown for the deliberate refusals — categorizing a transfer, a transfer with one account,
 * a file that names no account — and for nothing else. Its message is safe to hand straight back
 * to a client, and {@code ApiExceptionHandler} does exactly that, as a 422.
 *
 * <p>It exists because these used to be {@code IllegalStateException}, which the handler also
 * turned into a 422 with its message — and {@code IllegalStateException} is thrown by the JDK,
 * Hibernate, Spring and half the ecosystem for internal faults. Any of those reached the client
 * as a 422 carrying an internal message, while two methods up the same handler was carefully
 * hiding constraint names. A rule violation and a server fault are different things and need
 * different types.
 */
public class DomainRuleViolation extends RuntimeException {

    public DomainRuleViolation(String message) {
        super(message);
    }
}
