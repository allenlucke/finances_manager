package llc.feelingfroggy.finances.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Replaces the legacy split into separate expenseCategory and incomeCategory tables.
 */
public enum CategoryKind implements CodedEnum {

    EXPENSE("expense"),
    INCOME("income");

    private final String code;

    CategoryKind(String code) {
        this.code = code;
    }

    /**
     * Also the JSON representation. Without {@code @JsonValue}, Jackson would emit and expect the
     * Java constant name, so the REST API would speak a different spelling from Postgres, the
     * Python service and the TypeScript client — which is exactly what {@link CodedEnum} exists to
     * prevent, and which showed up as a 400 on every transaction the UI tried to create.
     */
    @Override
    @JsonValue
    public String code() {
        return code;
    }

    /**
     * Accepts the code, case-insensitively.
     *
     * <p>Lenient on the way in and canonical on the way out: responses always carry the lowercase
     * code, but a caller sending {@code DEBIT} is understood rather than rejected.
     */
    @JsonCreator
    public static CategoryKind fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (CategoryKind candidate : values()) {
            if (candidate.code.equalsIgnoreCase(value) || candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown CategoryKind: " + value);
    }

    @Converter(autoApply = true)
    public static class Conv extends CodedEnumConverter<CategoryKind> {
        public Conv() {
            super(CategoryKind.class);
        }
    }
}
