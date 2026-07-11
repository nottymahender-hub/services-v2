package com.dbs.mot.grc.service;

import com.dbs.mot.grc.common.util.NetRiskRating;
import com.dbs.mot.grc.dto.DerivedDetailColumns;
import com.dbs.mot.grc.dto.MatchedFactRows;
import com.dbs.mot.grc.entity.InaFactOrl;
import com.dbs.mot.grc.entity.IncFactOrl;
import com.dbs.mot.grc.entity.KriFactOrl;
import com.dbs.mot.grc.entity.RcsaFactOrl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Derives the fact-sourced columns of an assessment detail row from its matched module
 * fact rows: {@code GRC_METRICS}, {@code CAL_NET_RISK_RTNG}, {@code COMMENTARY} and
 * {@code CTRL_EFF_RTN}.
 *
 * <p>Isolated as its own component so the (currently partial) derivation rules for
 * {@code COMMENTARY} and {@code CTRL_EFF_RTN} can be filled in later in exactly one
 * place, with the full matched fact rows already in hand. The generation service simply
 * calls {@link #derive(MatchedFactRows)} and stamps the returned values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactDerivationService {

    /**
     * Placeholder risk-rating-change value for every GRC metric entry.
     * The real per-metric derivation is pending; kept as a single constant so it changes
     * in one spot.
     */
    private static final String RISK_RATING_CHANGE_DEFAULT = "Improved";

    private static final String KEY_NRR = "nrr";
    private static final String KEY_COUNT = "count";
    private static final String KEY_RISK_RATING_CHANGE = "riskRatingChange";

    private final ObjectMapper objectMapper;

    /**
     * Computes all fact-derived detail columns for one generated row.
     *
     * @param matched the module fact rows that matched this detail row (any may be null)
     * @return the derived column values (never null; individual fields may be null)
     */
    public DerivedDetailColumns derive(MatchedFactRows matched) {
        String grcMetrics = buildGrcMetrics(matched);
        String calNetRiskRtng = worstNetRiskRating(matched);
        String commentary = deriveCommentary(matched);
        String ctrlEffRtn = deriveCtrlEffRtn(matched);

        log.debug("Derived detail columns: hasFacts={}, calNetRiskRtng={}",
                matched.hasAny(), calNetRiskRtng);

        return new DerivedDetailColumns(grcMetrics, calNetRiskRtng, commentary, ctrlEffRtn);
    }

    // ── GRC_METRICS ────────────────────────────────────────────────────────────

    /**
     * Builds the {@code GRC_METRICS} JSON. One top-level key per module that matched a
     * fact row ({@code INC}, {@code INA}, {@code KRI}, {@code RCSA}); each holds the
     * module {@code nrr} plus one entry per metric column of
     * {@code {count, riskRatingChange}}. Returns {@code null} when no module matched.
     */
    private String buildGrcMetrics(MatchedFactRows matched) {
        Map<String, Object> root = new LinkedHashMap<>();

        if (matched.inc() != null) {
            root.put("INC", incNode(matched.inc()));
        }
        if (matched.ina() != null) {
            root.put("INA", inaNode(matched.ina()));
        }
        if (matched.kri() != null) {
            root.put("KRI", kriNode(matched.kri()));
        }
        if (matched.rcsa() != null) {
            root.put("RCSA", rcsaNode(matched.rcsa()));
        }

        if (root.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // Serialising a plain Map of primitives should never fail; if it somehow
            // does, surface it as unexpected (→ HTTP 500) rather than silently dropping data.
            throw new IllegalStateException("Failed to serialise GRC_METRICS JSON", e);
        }
    }

    private Map<String, Object> incNode(IncFactOrl f) {
        Map<String, Object> node = newModuleNode(f.getNetRiskRtng());
        node.put("inc_is_sinp_count_l3m_mtd", metric(f.getIncIsSinpCountL3mMtd()));
        node.put("inc_is_mi_count_l3m_mtd", metric(f.getIncIsMiCountL3mMtd()));
        node.put("inc_is_gorc_count_l3m_mtd", metric(f.getIncIsGorcCountL3mMtd()));
        node.put("inc_is_min_reportable_count_l3m_mtd", metric(f.getIncIsMinReportableCountL3mMtd()));
        node.put("inc_time_to_detect_sum_l11m_mtd", metric(f.getIncTimeToDetectSumL11mMtd()));
        node.put("inc_count_l11m_mtd", metric(f.getIncCountL11mMtd()));
        return node;
    }

    private Map<String, Object> inaNode(InaFactOrl f) {
        Map<String, Object> node = newModuleNode(f.getNetRiskRtng());
        node.put("issue_rating_high_count", metric(f.getIssueRatingHighCount()));
        node.put("issue_rating_medium_count", metric(f.getIssueRatingMediumCount()));
        node.put("issue_type_regulatory_count", metric(f.getIssueTypeRegulatoryCount()));
        node.put("issue_type_audit_count", metric(f.getIssueTypeAuditCount()));
        node.put("issue_type_others_count", metric(f.getIssueTypeOthersCount()));
        node.put("issue_open_count", metric(f.getIssueOpenCount()));
        node.put("issue_closed_count_l3m_mtd", metric(f.getIssueClosedCountL3mMtd()));
        node.put("issue_repeated_count", metric(f.getIssueRepeatedCount()));
        return node;
    }

    private Map<String, Object> kriNode(KriFactOrl f) {
        Map<String, Object> node = newModuleNode(f.getNetRiskRtng());
        node.put("kri_sustained_red_3m_or_quarterly_red_count", metric(f.getKriSustainedRed3mOrQuarterlyRedCount()));
        node.put("kri_sustained_red_2m_count", metric(f.getKriSustainedRed2mCount()));
        node.put("kri_sustained_red_amber_4m_or_quarterly_amber_count", metric(f.getKriSustainedRedAmber4mOrQuarterlyAmberCount()));
        node.put("kri_amber_sustained_red_amber_3m_count", metric(f.getKriAmberSustainedRedAmber3mCount()));
        node.put("kri_red_count", metric(f.getKriRedCount()));
        node.put("kri_amber_count", metric(f.getKriAmberCount()));
        node.put("kri_green_count", metric(f.getKriGreenCount()));
        return node;
    }

    private Map<String, Object> rcsaNode(RcsaFactOrl f) {
        Map<String, Object> node = newModuleNode(f.getNetRiskRtng());
        node.put("rcsa_high_risk_proportion", metric(f.getRcsaHighRiskProportion()));
        node.put("rcsa_medhigh_risk_proportion", metric(f.getRcsaMedhighRiskProportion()));
        node.put("rcsa_medlow_risk_proportion", metric(f.getRcsaMedlowRiskProportion()));
        node.put("rcsa_low_risk_proportion", metric(f.getRcsaLowRiskProportion()));
        return node;
    }

    /** Module JSON object seeded with the {@code nrr} key (insertion-ordered first). */
    private Map<String, Object> newModuleNode(String netRiskRtng) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put(KEY_NRR, netRiskRtng);
        return node;
    }

    /** One metric entry: {@code {count, riskRatingChange}}. */
    private Map<String, Object> metric(Integer count) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(KEY_COUNT, count);
        entry.put(KEY_RISK_RATING_CHANGE, RISK_RATING_CHANGE_DEFAULT);
        return entry;
    }

    // ── CAL_NET_RISK_RTNG ──────────────────────────────────────────────────────

    /**
     * The worst (highest-severity) {@code NET_RISK_RTNG} across the matched modules.
     * Unrecognised rating labels are logged and ignored. Returns {@code null} when no
     * module matched or none carried a recognised rating.
     */
    private String worstNetRiskRating(MatchedFactRows matched) {
        return java.util.stream.Stream.of(
                        rating(matched.inc() != null ? matched.inc().getNetRiskRtng() : null),
                        rating(matched.ina() != null ? matched.ina().getNetRiskRtng() : null),
                        rating(matched.kri() != null ? matched.kri().getNetRiskRtng() : null),
                        rating(matched.rcsa() != null ? matched.rcsa().getNetRiskRtng() : null))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparingInt(Enum::ordinal))
                .map(NetRiskRating::label)
                .orElse(null);
    }

    private Optional<NetRiskRating> rating(String label) {
        Optional<NetRiskRating> parsed = NetRiskRating.fromLabel(label);
        if (label != null && !label.isBlank() && parsed.isEmpty()) {
            log.warn("Unrecognised NET_RISK_RTNG value '{}' ignored in worst-of calculation", label);
        }
        return parsed;
    }

    // ── COMMENTARY / CTRL_EFF_RTN (derivation pending) ─────────────────────────

    /**
     * Derivation rule pending — returns {@code null} for now. When defined, compute it
     * here from the matched fact rows.
     */
    private String deriveCommentary(MatchedFactRows matched) {
        return null;
    }

    /**
     * Derivation rule pending — returns {@code null} for now. When defined, compute it
     * here from the matched fact rows.
     */
    private String deriveCtrlEffRtn(MatchedFactRows matched) {
        return null;
    }
}
