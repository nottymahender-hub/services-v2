package com.dbs.mot.grc.util;

import com.dbs.mot.grc.exception.RiskAreaParseException;
import com.dbs.mot.grc.dto.RiskAreaGroup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RiskAreaParser} — the single place that reads the grouped
 * {@code orl_lndscp_dim.RISK_AREA} JSON document.
 */
class RiskAreaParserTest {

    private final RiskAreaParser parser = new RiskAreaParser();

    private static final String VALID = """
            [
              { "groupName": "IT", "isGroup": true, "riskAreas": [
                  { "riskArea": "IT Resiliency and Continuity", "riskClusters": ["OR", "LCS"] },
                  { "riskArea": "IT Change and Release Management", "riskClusters": ["LCS"] } ] },
              { "groupName": "Transaction Capture and Execution", "isGroup": false, "riskAreas": [
                  { "riskArea": "Transaction Capture and Execution", "riskClusters": ["OR"] } ] }
            ]
            """;

    // ── parseStrict ──────────────────────────────────────────────────────────────

    @Test
    void parseStrict_validDocument_returnsGroups() {
        List<RiskAreaGroup> groups = parser.parseStrict(VALID);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).groupName()).isEqualTo("IT");
        assertThat(groups.get(0).isGroup()).isTrue();
        assertThat(groups.get(0).riskAreas()).hasSize(2);
        assertThat(groups.get(1).isGroup()).isFalse();
    }

    @Test
    void parseStrict_blank_throws() {
        assertThatThrownBy(() -> parser.parseStrict("  "))
                .isInstanceOf(RiskAreaParseException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void parseStrict_notJson_throws() {
        assertThatThrownBy(() -> parser.parseStrict("NOT_JSON"))
                .isInstanceOf(RiskAreaParseException.class)
                .hasMessageContaining("valid JSON");
    }

    @Test
    void parseStrict_duplicateKeys_throws() {
        String dup = "[{\"groupName\":\"A\",\"groupName\":\"B\",\"isGroup\":true,"
                + "\"riskAreas\":[{\"riskArea\":\"X\",\"riskClusters\":[\"OR\"]}]}]";
        assertThatThrownBy(() -> parser.parseStrict(dup))
                .isInstanceOf(RiskAreaParseException.class);
    }

    // ── parseQuietly ─────────────────────────────────────────────────────────────

    @Test
    void parseQuietly_invalid_returnsEmpty() {
        assertThat(parser.parseQuietly("NOT_JSON")).isEmpty();
        assertThat(parser.parseQuietly(null)).isEmpty();
    }

    // ── lookupByRiskArea ─────────────────────────────────────────────────────────

    @Test
    void lookupByRiskArea_mapsGroupAndClusters() {
        Map<String, RiskAreaParser.AreaLookup> lookup = parser.lookupByRiskArea(VALID);

        RiskAreaParser.AreaLookup itResiliency = lookup.get("IT Resiliency and Continuity");
        assertThat(itResiliency.groupName()).isEqualTo("IT");
        assertThat(itResiliency.isGroup()).isTrue();
        assertThat(itResiliency.riskClusters()).containsExactly("OR", "LCS");

        RiskAreaParser.AreaLookup txn = lookup.get("Transaction Capture and Execution");
        assertThat(txn.isGroup()).isFalse();
        assertThat(txn.riskClusters()).containsExactly("OR");
    }

    @Test
    void lookupByRiskArea_unknownName_absent() {
        assertThat(parser.lookupByRiskArea(VALID)).doesNotContainKey("Unknown");
    }

    // ── riskAreaClusterMap / distinctRiskClusters ────────────────────────────────

    @Test
    void riskAreaClusterMap_flattensNameToClusters() {
        Map<String, List<String>> map = parser.riskAreaClusterMap(VALID);

        assertThat(map).containsOnlyKeys(
                "IT Resiliency and Continuity",
                "IT Change and Release Management",
                "Transaction Capture and Execution");
        assertThat(map.get("IT Resiliency and Continuity")).containsExactly("OR", "LCS");
        assertThat(map.get("IT Change and Release Management")).containsExactly("LCS");
    }

    @Test
    void riskAreaClusterMap_blank_returnsEmpty() {
        assertThat(parser.riskAreaClusterMap("")).isEmpty();
    }

    @Test
    void distinctRiskClusters_returnsUnionInFirstSeenOrder() {
        assertThat(parser.distinctRiskClusters(VALID)).containsExactly("OR", "LCS");
    }

    @Test
    void distinctRiskClusters_blank_returnsEmpty() {
        assertThat(parser.distinctRiskClusters(null)).isEmpty();
    }

    // ── normalizeCompact ─────────────────────────────────────────────────────────

    @Test
    void normalizeCompact_removesWhitespace_andRoundTrips() {
        String compact = parser.normalizeCompact(VALID);

        assertThat(compact).doesNotContain("\n").doesNotContain(": ");
        // The compact form still parses to the same risk areas.
        assertThat(parser.riskAreaClusterMap(compact)).isEqualTo(parser.riskAreaClusterMap(VALID));
    }

    @Test
    void normalizeCompact_invalid_throws() {
        assertThatThrownBy(() -> parser.normalizeCompact("NOT_JSON"))
                .isInstanceOf(RiskAreaParseException.class);
    }
}
