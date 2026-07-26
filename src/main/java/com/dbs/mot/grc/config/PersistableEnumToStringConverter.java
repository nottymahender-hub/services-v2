package com.dbs.mot.grc.config;

import com.dbs.mot.grc.enums.PersistableEnum;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Writes any {@link PersistableEnum} to its {@code dbValue} String when persisting.
 */
@WritingConverter
public class PersistableEnumToStringConverter implements Converter<PersistableEnum, String> {

    @Override
    public String convert(PersistableEnum source) {
        return source.getDbValue();
    }
}
