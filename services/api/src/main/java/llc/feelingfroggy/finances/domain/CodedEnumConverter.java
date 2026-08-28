package llc.feelingfroggy.finances.domain;

import jakarta.persistence.AttributeConverter;

/**
 * Base for the small {@code @Converter} classes nested inside each {@link CodedEnum}.
 *
 * <p>Unknown codes fail loudly rather than yielding null. A value that reaches the database without
 * matching a constant means the schema and the enum have drifted apart, and silently reading it as
 * null would turn that into a wrong number somewhere downstream.
 */
public abstract class CodedEnumConverter<E extends Enum<E> & CodedEnum>
        implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected CodedEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.code();
    }

    @Override
    public E convertToEntityAttribute(String code) {
        if (code == null) {
            return null;
        }
        for (E candidate : type.getEnumConstants()) {
            if (candidate.code().equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
            "No %s constant for database value '%s'".formatted(type.getSimpleName(), code));
    }
}
