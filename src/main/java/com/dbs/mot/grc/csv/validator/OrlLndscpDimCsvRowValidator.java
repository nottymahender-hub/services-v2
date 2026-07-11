package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.csv.CsvRowValidator;
import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.OrlLndscpDimCsvRow;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Cross-row and DB-reference validation for the orl_lndscp_dim CSV batch.
 *
 * <p>Rules enforced here (after per-row Bean Validation has already passed):
 * <ul>
 *   <li>CONFIG_ID and LNDSCP_NM must not contain commas.</li>
 *   <li>CONFIG_ID must be unique within the CSV batch.</li>
 *   <li>BIZ_UNIT_LVL must be the same across all rows and within range (1, MAX_HIER).</li>
 *   <li>RISK_AREA must be valid JSON; duplicate JSON keys are rejected;
 *       each value must be a non-empty array.</li>
 *   <li>BIZ_UNITS (optional): each token must exist in orl_biz_unit at BIZ_UNIT_LVL;
 *       no duplicates within a cell.</li>
 *   <li>LOCATIONS: each token must exist in orl_entity_mstr.orl_location; no duplicates.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrlLndscpDimCsvRowValidator implements CsvRowValidator<OrlLndscpDimCsvRow> {

    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ValidationErrorDetail> validate(List<OrlLndscpDimCsvRow> rows) {
        log.debug("Starting cross-row validation for {} orl_lndscp_dim row(s)", rows.size());

        List<ValidationErrorDetail> errors = new ArrayList<>();

        Set<String> validLocations = loadLocations();
        int maxBizUnitLvl          = loadMaxBizUnitLevel();

        Integer batchBizUnitLvl = resolveBatchBizUnitLvl(rows, errors);
        Set<String> validBizUnits = (batchBizUnitLvl != null)
                ? loadBizUnitsAtLevel(batchBizUnitLvl)
                : Collections.emptySet();

        Map<String, Integer> seenConfigIds = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            OrlLndscpDimCsvRow row = rows.get(i);
            int rowNum = i + 1;

            checkNoComma(rowNum, "CONFIG_ID", row.getConfigId(), errors);
            checkNoComma(rowNum, "LNDSCP_NM", row.getLndscpNm(), errors);
            checkDuplicate(rowNum, "CONFIG_ID", row.getConfigId(), seenConfigIds, errors);
            checkEffectDates(rowNum, row.getEffectStartDt(), row.getEffectEndDt(), errors);

            validateBizUnitLvlRange(rowNum, row.getBizUnitLvl(), maxBizUnitLvl, errors);
            validateRiskAreaJson(rowNum, row.getRiskArea(), errors);

            if (row.getBizUnits() != null) {
                validateMultiValues(rowNum, "BIZ_UNITS", row.getBizUnits(),
                        validBizUnits,
                        "orl_biz_unit.BU_NM at LVL_OF_HIER=" + batchBizUnitLvl,
                        errors);
            }

            validateMultiValues(rowNum, "LOCATIONS", row.getLocations(),
                    validLocations, "orl_entity_mstr.orl_location", errors);
        }

        if (errors.isEmpty()) {
            log.info("Validation passed for all {} orl_lndscp_dim row(s)", rows.size());
        } else {
            log.warn("Validation failed for orl_lndscp_dim batch — {} error(s)", errors.size());
        }

        return errors;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Validates that RISK_AREA is valid JSON, has no duplicate keys,
     * and each value is a non-empty array.
     */
    private void validateRiskAreaJson(int rowNum, String json,
                                       List<ValidationErrorDetail> errors) {
        if (json == null || json.isBlank()) return;
        Map<String, List<String>> parsed;
        try {
            parsed = STRICT_MAPPER.readValue(json,
                    new TypeReference<LinkedHashMap<String, List<String>>>() {});
        } catch (Exception e) {
            errors.add(error(rowNum, "RISK_AREA",
                    "RISK_AREA must be valid JSON with no duplicate keys. Error: " + e.getMessage()));
            return;
        }
        for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                errors.add(error(rowNum, "RISK_AREA",
                        "RISK_AREA key '" + entry.getKey()
                                + "' must map to a non-empty array of risk type codes."));
            }
        }
    }

    private Integer resolveBatchBizUnitLvl(List<OrlLndscpDimCsvRow> rows,
                                            List<ValidationErrorDetail> errors) {
        if (rows.isEmpty()) return null;
        Integer primaryLvl = null;
        int primaryRow = -1;
        for (int i = 0; i < rows.size(); i++) {
            Integer lvl = rows.get(i).getBizUnitLvl();
            if (lvl == null) continue;
            if (primaryLvl == null) {
                primaryLvl = lvl;
                primaryRow = i + 1;
            } else if (!lvl.equals(primaryLvl)) {
                errors.add(error(i + 1, "BIZ_UNIT_LVL",
                        "All rows must have the same BIZ_UNIT_LVL. Expected "
                                + primaryLvl + " (from row " + primaryRow + ") but got " + lvl + "."));
            }
        }
        log.debug("Batch BIZ_UNIT_LVL resolved to {}", primaryLvl);
        return primaryLvl;
    }

    private void validateBizUnitLvlRange(int rowNum, Integer bizUnitLvl,
                                          int maxBizUnitLvl,
                                          List<ValidationErrorDetail> errors) {
        if (bizUnitLvl == null) return;
        if (maxBizUnitLvl > 0 && bizUnitLvl >= maxBizUnitLvl) {
            errors.add(error(rowNum, "BIZ_UNIT_LVL",
                    "BIZ_UNIT_LVL must be less than the maximum hierarchy level ("
                            + maxBizUnitLvl + "), but got: " + bizUnitLvl + "."));
        }
    }

    private void validateMultiValues(int rowNum, String field, String rawValue,
                                      Set<String> validValues, String refEntity,
                                      List<ValidationErrorDetail> errors) {
        if (rawValue == null || rawValue.isBlank()) return;
        Set<String> seen = new LinkedHashSet<>();
        for (String part : rawValue.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            if (!seen.add(token)) {
                errors.add(error(rowNum, field,
                        "Duplicate value '" + token + "' in " + field + "."));
            } else if (!validValues.isEmpty() && !validValues.contains(token)) {
                errors.add(error(rowNum, field,
                        "'" + token + "' does not exist in " + refEntity + "."));
            }
        }
    }

    private void checkEffectDates(int rowNum, java.time.LocalDate startDt, java.time.LocalDate endDt,
                                   List<ValidationErrorDetail> errors) {
        if (startDt == null || endDt == null) return;
        if (!startDt.isBefore(endDt)) {
            errors.add(error(rowNum, "EFFECT_END_DT", "EFFECT_END_DT must be after EFFECT_START_DT."));
        }
    }

    private void checkNoComma(int rowNum, String field, String value,
                               List<ValidationErrorDetail> errors) {
        if (value != null && value.contains(",")) {
            errors.add(error(rowNum, field,
                    "Multiple values are not allowed in '" + field + "'. Got: '" + value + "'."));
        }
    }

    private void checkDuplicate(int rowNum, String field, String value,
                                 Map<String, Integer> seen,
                                 List<ValidationErrorDetail> errors) {
        if (value == null) return;
        if (seen.containsKey(value)) {
            errors.add(error(rowNum, field,
                    "'" + field + "' value '" + value + "' is duplicated (first seen at row "
                            + seen.get(value) + ")."));
        } else {
            seen.put(value, rowNum);
        }
    }

    private Set<String> loadLocations() {
        log.debug("Loading valid orl_location values from orl_entity_mstr");
        List<String> locs = jdbcTemplate.queryForList(
                "SELECT DISTINCT orl_location FROM orl_entity_mstr", String.class);
        log.debug("Loaded {} distinct location(s)", locs.size());
        return new HashSet<>(locs);
    }

    private int loadMaxBizUnitLevel() {
        log.debug("Loading MAX(LVL_OF_HIER) from orl_biz_unit");
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(LVL_OF_HIER), 0) FROM orl_biz_unit", Integer.class);
        return (max != null) ? max : 0;
    }

    private Set<String> loadBizUnitsAtLevel(int level) {
        log.debug("Loading BU_NM values at LVL_OF_HIER={}", level);
        List<String> names = jdbcTemplate.queryForList(
                "SELECT BU_NM FROM orl_biz_unit WHERE LVL_OF_HIER = ?", String.class, level);
        return new HashSet<>(names);
    }

    private ValidationErrorDetail error(int row, String field, String message) {
        return ValidationErrorDetail.builder().row(row).field(field).message(message).build();
    }
}
