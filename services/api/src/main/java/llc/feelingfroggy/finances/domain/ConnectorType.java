package llc.feelingfroggy.finances.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/**
 * Which {@code AccountConnector} implementation backs a connection (D-14).
 */
public enum ConnectorType implements CodedEnum {

    FILE_IMPORT("file_import"),
    PLAID("plaid"),
    SNAPTRADE("snaptrade"),
    MCP("mcp"),
    API("api");

    private final String code;

    ConnectorType(String code) {
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
    public static ConnectorType fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (ConnectorType candidate : values()) {
            if (candidate.code.equalsIgnoreCase(value) || candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown ConnectorType: " + value);
    }

    @Converter(autoApply = true)
    public static class Conv extends CodedEnumConverter<ConnectorType> {
        public Conv() {
            super(ConnectorType.class);
        }
    }
}
