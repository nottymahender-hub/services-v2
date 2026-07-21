package com.dbs.mot.grc.common.config;

import com.dbs.mot.grc.common.enums.PersistableEnum;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.data.convert.ReadingConverter;

/**
 * Reads a DB String back into the matching {@link PersistableEnum} constant of the target
 * enum type. A {@link ConverterFactory} lets one registration serve every persistable enum.
 */
@ReadingConverter
public class StringToPersistableEnumConverterFactory implements ConverterFactory<String, PersistableEnum> {

    @Override
    public <T extends PersistableEnum> Converter<String, T> getConverter(Class<T> targetType) {
        return new StringToPersistableEnum<>(targetType);
    }

    private record StringToPersistableEnum<T extends PersistableEnum>(Class<T> enumType)
            implements Converter<String, T> {

        @Override
        public T convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            for (T constant : enumType.getEnumConstants()) {
                if (constant.getDbValue().equals(source)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException(
                    "No " + enumType.getSimpleName() + " constant for database value '" + source + "'");
        }
    }
}
