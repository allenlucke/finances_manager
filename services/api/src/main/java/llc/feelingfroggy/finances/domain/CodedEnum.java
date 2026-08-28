package llc.feelingfroggy.finances.domain;

/**
 * An enum whose persisted form is an explicit lowercase code rather than its Java constant name.
 *
 * <p>The codes are not cosmetic. They are the same strings the database CHECK constraints accept
 * and the same strings the Python AI service puts on the wire (see
 * {@code services/ai/src/finances_ai/models.py}, e.g. {@code "debit"} / {@code "credit"}). Keeping
 * one spelling across Postgres, Java, and Python removes a translation layer that would otherwise
 * have to be correct in three places.
 *
 * <p>This is why {@code @Enumerated(EnumType.STRING)} is not used anywhere in this package: it
 * would persist {@code DEBIT}, which no CHECK constraint accepts.
 */
public interface CodedEnum {

    /** The value stored in the database. Must match the column's CHECK constraint exactly. */
    String code();
}
