package com.dbs.mot.grc.util;

import com.dbs.mot.grc.exception.RiskAreaParseException;
import com.dbs.mot.grc.dto.RiskAreaEntry;
import com.dbs.mot.grc.dto.RiskAreaGroup;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Single source of truth for reading the {@code orl_lndscp_dim.RISK_AREA} JSON document.
 *
 * <p>The document is a JSON array of {@link RiskAreaGroup} objects, each holding one or more
 * {@link RiskAreaEntry} risk areas with their risk-cluster codes:
 * <pre>
 * [
 *   { "groupName": "IT", "isGroup": true,
 *     "riskAreas": [ { "riskArea": "IT Resiliency and Continuity", "riskClusters": ["OR","LCS"] } ] }
 * ]
 * </pre>
 *
 * <p>Two parsing modes are offered so each caller gets the right failure behaviour:
 * <ul>
 *   <li>{@link #parseStrict(String)} — throws {@link RiskAreaParseException}; used by CSV
 *       validation so malformed input is reported as a precise HTTP 400.</li>
 *   <li>{@link #parseQuietly(String)} — logs and returns an empty list; used by read APIs so
 *       one bad stored document degrades gracefully rather than failing the whole response.</li>
 * </ul>
 *
 * <p>This is the single place risk-area JSON is parsed: the generation, callout and
 * assessment-details services all delegate here rather than parsing it themselves.
 */
@Slf4j
@Component
public class RiskAreaParser {

    private static final TypeReference<List<RiskAreaGroup>> GROUP_LIST_TYPE =
            new TypeReference<>() {};

    /** Strict duplicate-key detection rejects documents with repeated JSON keys. */
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    /**
     * Parses the RISK_AREA document, throwing on any problem.
     *
     * @param json the raw RISK_AREA JSON string
     * @return the parsed groups (never {@code null})
     * @throws RiskAreaParseException if the value is blank or not valid RISK_AREA JSON
     */
    public List<RiskAreaGroup> parseStrict(String json) {
        if (json == null || json.isBlank()) {
            throw new RiskAreaParseException("RISK_AREA must not be blank.", null);
        }
        try {
            List<RiskAreaGroup> groups = mapper.readValue(json, GROUP_LIST_TYPE);
            log.debug("Parsed RISK_AREA document into {} group(s)", groups.size());
            return groups;
        } catch (JsonProcessingException e) {
            throw new RiskAreaParseException(
                    "RISK_AREA must be a valid JSON array of risk-area groups: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * Parses the RISK_AREA document, degrading to an empty list on any problem.
     * Intended for read paths where a single bad stored document must not fail the request.
     *
     * @param json the raw RISK_AREA JSON string
     * @return the parsed groups, or an empty list when blank or unparseable
     */
    public List<RiskAreaGroup> parseQuietly(String json) {
        try {
            return parseStrict(json);
        } catch (RiskAreaParseException e) {
            log.warn("Ignoring unparseable RISK_AREA document: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds a lookup from risk area name to its group and risk clusters, for enriching
     * assessment detail rows. When the same name appears more than once (which CSV validation
     * rejects on upload) the first occurrence wins.
     *
     * @param json the raw RISK_AREA JSON string
     * @return an ordered map keyed by risk area name; empty when blank/unparseable
     */
    public Map<String, AreaLookup> lookupByRiskArea(String json) {
        Map<String, AreaLookup> lookup = new LinkedHashMap<>();
        for (RiskAreaGroup group : parseQuietly(json)) {
            for (RiskAreaEntry entry : safeEntries(group)) {
                if (entry.riskArea() != null) {
                    lookup.putIfAbsent(entry.riskArea(),
                            new AreaLookup(group.groupName(), group.isGroup(),
                                    entry.riskClusters() != null ? entry.riskClusters() : List.of()));
                }
            }
        }
        return lookup;
    }

    /**
     * Flattens the document to an ordered map of risk area name → its risk clusters. When the
     * same name appears more than once (rejected by CSV validation) the first occurrence wins.
     *
     * @param json the raw RISK_AREA JSON string
     * @return an ordered map keyed by risk area name; empty when blank/unparseable
     */
    public Map<String, List<String>> riskAreaClusterMap(String json) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (RiskAreaGroup group : parseQuietly(json)) {
            for (RiskAreaEntry entry : safeEntries(group)) {
                if (entry.riskArea() != null) {
                    map.putIfAbsent(entry.riskArea(),
                            entry.riskClusters() != null ? entry.riskClusters() : List.of());
                }
            }
        }
        return map;
    }

    /**
     * Returns the distinct risk cluster codes across all risk areas, in first-seen order.
     *
     * @param json the raw RISK_AREA JSON string
     * @return distinct risk cluster codes; empty when blank/unparseable
     */
    public List<String> distinctRiskClusters(String json) {
        Set<String> clusters = new LinkedHashSet<>();
        for (RiskAreaGroup group : parseQuietly(json)) {
            for (RiskAreaEntry entry : safeEntries(group)) {
                if (entry.riskClusters() != null) {
                    entry.riskClusters().stream().filter(Objects::nonNull).forEach(clusters::add);
                }
            }
        }
        return new ArrayList<>(clusters);
    }

    /**
     * Re-serialises the RISK_AREA document to canonical, compact JSON for storage, so stored
     * values are normalised regardless of the whitespace/formatting in the uploaded CSV cell.
     *
     * @param json the raw RISK_AREA JSON string (already validated by the caller)
     * @return the minified JSON document
     * @throws RiskAreaParseException if the value cannot be parsed or re-serialised
     */
    public String normalizeCompact(String json) {
        List<RiskAreaGroup> groups = parseStrict(json);
        try {
            return mapper.writeValueAsString(groups);
        } catch (JsonProcessingException e) {
            throw new RiskAreaParseException("Could not normalise RISK_AREA JSON: " + e.getOriginalMessage(), e);
        }
    }

    private List<RiskAreaEntry> safeEntries(RiskAreaGroup group) {
        return group.riskAreas() != null ? group.riskAreas() : List.of();
    }

    /**
     * The group context of a single risk area, used to enrich assessment detail rows.
     *
     * @param groupName    the owning group's name
     * @param isGroup      whether the owning group represents several risk areas
     * @param riskClusters the risk clusters mapped to the risk area (never {@code null})
     */
    public record AreaLookup(String groupName, boolean isGroup, List<String> riskClusters) {
    }
}
