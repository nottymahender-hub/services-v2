package com.dbs.mot.grc.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.util.List;

/**
 * Spring Data JDBC configuration registering the custom {@link com.dbs.mot.grc.common.enums.PersistableEnum}
 * read/write converters, so entity enum fields persist as their {@code dbValue} String and are
 * read back into the correct constant.
 */
@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return List.of(
                new PersistableEnumToStringConverter(),
                new StringToPersistableEnumConverterFactory());
    }
}
