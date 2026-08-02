package com.dbs.mot.grc.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared {@link ObjectMapper} for the small hand-parsed JSON documents stored in a few columns
 * ({@code orl_lndscp_dim.RISK_AREA}, callout {@code LOCATIONS}/{@code BIZ_UNITS}): strict
 * duplicate-key detection rejects documents with repeated JSON keys.
 *
 * <p>Deliberately <strong>not</strong> a Spring bean: registering a second {@code ObjectMapper}
 * bean disables Spring Boot's autoconfigured one (used for HTTP request/response bodies, with
 * {@code JavaTimeModule} etc. registered), since that autoconfiguration only runs when no
 * {@code ObjectMapper} bean already exists. A plain shared instance avoids that pitfall entirely.
 */
public final class StrictJsonMapper {

    public static final ObjectMapper INSTANCE =
            new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private StrictJsonMapper() {
        // Utility class — no instances.
    }
}
