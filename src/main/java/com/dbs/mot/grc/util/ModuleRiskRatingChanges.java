package com.dbs.mot.grc.util;

import com.dbs.mot.grc.entity.ModuleFact;
import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.enums.PersistableEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds and reads the {@code orl_lndscp_assmt_details.MODULE_RISK_RTNG_CHGE} JSON — the per-module
 * risk-rating change persisted at generation and surfaced by the drill-down.
 *
 * <p><strong>Single source of truth for module-change derivation.</strong> For each module the JSON
 * object carries:
 * <ul>
 *   <li>{@code riskRatingChange} — the module's NRR-based change
 *       ({@code Improved/Deteriorated/Stable/N.A}) via {@link RiskRatingChanges}, comparing the
 *       previous vs. current month's module {@code NET_RISK_RATING};</li>
 *   <li>one entry per module metric — a <em>neutral</em> numeric change
 *       ({@code Increased/Decreased/Stable/N.A}) comparing the previous vs. current metric value.</li>
 * </ul>
 * Example:
 * <pre>{"RCSA":{"riskRatingChange":"Improved","combined_count_high_risk":"Decreased",...}, ...}</pre>
 *
 * <p>The drill-down reads the whole document via {@link #parse(String)} — both the module-level
 * {@code riskRatingChange} and the per-metric labels feed the current/previous GRC blocks. The
 * <em>live</em> block instead computes its per-metric changes on the fly with
 * {@link #metricChanges(ModuleFact, ModuleFact)} (the same neutral rule), comparing the live vs. the
 * current snapshot.
 */
@Slf4j
@Component
public class ModuleRiskRatingChanges {

    /** JSON key under each module holding the NRR-based module risk-rating change. */
    public static final String RISK_RATING_CHANGE_KEY = "riskRatingChange";

    /** Default label when a change cannot be determined (no data on one side). */
    public static final String NOT_APPLICABLE = "N.A";

    private static final String INCREASED = "Increased";
    private static final String DECREASED = "Decreased";
    private static final String STABLE = "Stable";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * One module's parsed change document: the module-level risk-rating change plus a neutral change
     * per metric. Missing entries resolve to {@code "N.A"} via the accessors.
     *
     * @param riskRatingChange the module-level change, or {@code null} when absent
     * @param metricChanges    metric field name → neutral change label (may be empty)
     */
    public record ModuleChange(String riskRatingChange, Map<String, String> metricChanges) {

        /** An empty change: module-level and every metric resolve to {@code "N.A"}. */
        public static final ModuleChange NONE = new ModuleChange(null, Map.of());

        /** Module-level change, defaulting to {@code "N.A"} when absent. */
        public String riskRatingChangeOrNa() {
            return riskRatingChange != null ? riskRatingChange : NOT_APPLICABLE;
        }

        /** Neutral change for a metric, defaulting to {@code "N.A"} when absent. */
        public String metricChangeOrNa(String metricName) {
            String change = metricChanges.get(metricName);
            return change != null ? change : NOT_APPLICABLE;
        }
    }

    /**
     * Builds the MODULE_RISK_RTNG_CHGE JSON by comparing current vs. previous module facts.
     * Modules with no current fact are omitted; a module with no previous fact yields
     * {@code N.A} everywhere.
     *
     * @param currentByModule  module key → current-month fact (values may be {@code null})
     * @param previousByModule module key → previous-month fact (values may be {@code null})
     * @return the JSON string, or {@code null} when no module has a current fact
     */
    public String build(Map<String, ? extends ModuleFact> currentByModule,
                        Map<String, ? extends ModuleFact> previousByModule) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends ModuleFact> entry : currentByModule.entrySet()) {
            ModuleFact current = entry.getValue();
            if (current == null) {
                continue; // no current snapshot for this module → nothing to record
            }
            root.put(entry.getKey(), moduleNode(current, previousByModule.get(entry.getKey())));
        }
        if (root.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // Serialising a plain map of strings should never fail; treat as an unexpected 500.
            throw new IllegalStateException("Failed to serialise MODULE_RISK_RTNG_CHGE JSON", e);
        }
    }

    /** One module's node: NRR-based riskRatingChange + a neutral change per metric. */
    private Map<String, Object> moduleNode(ModuleFact current, ModuleFact previous) {
        Map<String, Object> node = new LinkedHashMap<>();
        NetRiskRating previousNrr = previous != null ? previous.getNetRiskRtng() : null;
        node.put(RISK_RATING_CHANGE_KEY,
                PersistableEnum.dbValue(RiskRatingChanges.derive(previousNrr, current.getNetRiskRtng())));
        node.putAll(metricChanges(current, previous));
        return node;
    }

    /**
     * The neutral per-metric change between two module snapshots, keyed by metric field name in the
     * current fact's canonical order. Reused for both the persisted JSON and the live drill-down
     * block, so the rule lives in exactly one place.
     *
     * @param current  the current snapshot (its metric order/keys drive the result); required
     * @param previous the baseline snapshot, or {@code null} → every metric is {@code "N.A"}
     * @return metric field name → neutral change label
     */
    public Map<String, String> metricChanges(ModuleFact current, ModuleFact previous) {
        Map<String, Object> previousMetrics = previous != null ? previous.metrics() : Map.of();
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> metric : current.metrics().entrySet()) {
            changes.put(metric.getKey(), metricChange(previousMetrics.get(metric.getKey()), metric.getValue()));
        }
        return changes;
    }

    /** Neutral numeric change label (no risk-direction judgement) for a metric value pair. */
    private String metricChange(Object previous, Object current) {
        BigDecimal p = toBigDecimal(previous);
        BigDecimal c = toBigDecimal(current);
        if (p == null || c == null) {
            return NOT_APPLICABLE;
        }
        int cmp = c.compareTo(p);
        if (cmp > 0) {
            return INCREASED;
        }
        return cmp < 0 ? DECREASED : STABLE;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return null;
    }

    /**
     * Parses a stored MODULE_RISK_RTNG_CHGE document into a per-module {@link ModuleChange} (the
     * module-level change plus every per-metric label). Degrades to an empty map on a blank or
     * unparseable document so one bad row never fails the request; a module absent from the map
     * resolves to {@link ModuleChange#NONE} at the call site.
     *
     * @param json the stored JSON (may be {@code null}/blank)
     * @return module key → its parsed change (empty when absent/unparseable)
     */
    public Map<String, ModuleChange> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = mapper.readTree(json);
            Map<String, ModuleChange> changes = new LinkedHashMap<>();
            root.fields().forEachRemaining(module -> changes.put(module.getKey(), toModuleChange(module.getValue())));
            return changes;
        } catch (JsonProcessingException e) {
            log.warn("Ignoring unparseable MODULE_RISK_RTNG_CHGE JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Splits one module node into its module-level change and the per-metric labels. */
    private ModuleChange toModuleChange(JsonNode moduleNode) {
        String moduleChange = null;
        Map<String, String> metricChanges = new LinkedHashMap<>();
        var fields = moduleNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String value = field.getValue().isNull() ? null : field.getValue().asText();
            if (RISK_RATING_CHANGE_KEY.equals(field.getKey())) {
                moduleChange = value;
            } else {
                metricChanges.put(field.getKey(), value);
            }
        }
        return new ModuleChange(moduleChange, metricChanges);
    }
}
