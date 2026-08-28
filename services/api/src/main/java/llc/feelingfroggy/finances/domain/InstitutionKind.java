package llc.feelingfroggy.finances.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * What kind of source this is. BUSINESS_SYSTEM is Feeling Froggy, which generates
 * its own financials and pushes them over (D-14/M5) rather than being modelled here.
 */
public enum InstitutionKind implements CodedEnum {

    BANK("bank"),
    CREDIT_CARD_ISSUER("credit_card_issuer"),
    BROKERAGE("brokerage"),
    BUSINESS_SYSTEM("business_system"),
    MANUAL("manual");

    private final String code;

    InstitutionKind(String code) {
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
    public static InstitutionKind fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (InstitutionKind candidate : values()) {
            if (candidate.code.equalsIgnoreCase(value) || candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown InstitutionKind: " + value);
    }

    @Converter(autoApply = true)
    public static class Conv extends CodedEnumConverter<InstitutionKind> {
        public Conv() {
            super(InstitutionKind.class);
        }
    }
}
