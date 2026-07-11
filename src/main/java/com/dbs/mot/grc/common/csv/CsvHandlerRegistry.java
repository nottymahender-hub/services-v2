package com.dbs.mot.grc.common.csv;

import com.dbs.mot.grc.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Auto-discovers all {@link CsvHandler} beans registered in the Spring context
 * and provides a lookup by table name.
 *
 * <p>Handlers are registered on startup. Adding a new {@code @Component}
 * that implements {@link CsvHandler} is all that is needed to support a new table —
 * this registry and the controller pick it up automatically.
 */
@Slf4j
@Component
public class CsvHandlerRegistry {

    private final Map<String, CsvHandler> handlers;

    public CsvHandlerRegistry(List<CsvHandler> allHandlers) {
        this.handlers = allHandlers.stream()
                .collect(Collectors.toMap(CsvHandler::getTableName, h -> h));
        log.info("CsvHandlerRegistry: registered {} handler(s): {}",
                handlers.size(), handlers.keySet());
    }

    /**
     * Returns the handler for the given table name.
     *
     * @param tableName the URL path segment (e.g. {@code "biz-units"})
     * @throws BadRequestException if no handler is registered for {@code tableName}
     */
    public CsvHandler getHandler(String tableName) {
        CsvHandler handler = handlers.get(tableName);
        if (handler == null) {
            throw new BadRequestException(
                    "Unknown table '" + tableName + "'. Supported tables: " + handlers.keySet());
        }
        return handler;
    }
}
