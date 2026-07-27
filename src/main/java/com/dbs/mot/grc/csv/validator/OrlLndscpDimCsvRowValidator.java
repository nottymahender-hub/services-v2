package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.csv.CsvRowValidator;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.exception.RiskAreaParseException;
import com.dbs.mot.grc.util.RiskAreaParser;
import com.dbs.mot.grc.dto.OrlLndscpDimCsvRow;
import com.dbs.mot.grc.dto.RiskAreaEntry;
import com.dbs.mot.grc.dto.RiskAreaGroup;
import com.dbs.mot.grc.entity.OrlBizUnit;
import com.dbs.mot.grc.entity.OrlEntityMstr;
import com.dbs.mot.grc.repository.OrlBizUnitRepository;
import com.dbs.mot.grc.repository.OrlEntityMstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * Cross-row and DB-reference validation for the orl_lndscp_dim CSV batch.
 *
 * <p>Rules enforced here (after per-row Bean Validation has already passed):
 * <ul>
 *   <li>CONFIG_ID and LNDSCP_NM must not contain commas.</li>
 *   <li>CONFIG_ID must be unique within the CSV batch.</li>
 *   <li>BIZ_UNIT_LVL must be the same across all rows and within range (1, MAX_HIER).</li>
 *   <li>RISK_AREA must be a valid JSON array of risk-area groups (see
 *       {@link RiskAreaParser}); duplicate JSON keys are rejected; a grouped entry
 *       ({@code isGroup=true}) requires a non-empty {@code groupName} (a standalone
 *       {@code isGroup=false} entry may leave it blank); each group must have a non-empty
 *       risk-area list; each risk area must have a name and a non-empty cluster list; and
 *       risk-area names must be unique across the whole document.</li>
 *   <li>BIZ_UNITS (optional): each token must exist among the distinct
 *       {@code orl_biz_unit.ORL_BU_NM_L{level}} values matching the batch's BIZ_UNIT_LVL
 *       (level 2 → {@code ORL_BU_NM_L2}, 3 → {@code ORL_BU_NM_L3}, 4 → {@code ORL_BU_NM_L4});
 *       no duplicates within a cell.</li>
 *   <li>LOCATIONS: each token must exist in {@code orl_entity_mstr.orl_location} OR
 *       {@code orl_entity_mstr.orl_location_ic}; no duplicates.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrlLndscpDimCsvRowValidator implements CsvRowValidator<OrlLndscpDimCsvRow> {

    private final OrlEntityMstrRepository entityMstrRepository;
    private final OrlBizUnitRepository bizUnitRepository;
    private final RiskAreaParser riskAreaParser;

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
                        "orl_biz_unit.ORL_BU_NM_L" + batchBizUnitLvl,
                        errors);
            }

            validateMultiValues(rowNum, "LOCATIONS", row.getLocations(),
                    validLocations, "orl_entity_mstr.orl_location / orl_location_ic", errors);
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
     * Validates the RISK_AREA grouped-JSON document: it must parse; a grouped entry
     * ({@code isGroup=true}) must have a non-empty {@code groupName} while a standalone entry
     * ({@code isGroup=false}) may leave it blank; every group must have at least one risk area;
     * every risk area must have a name and at least one risk cluster; and risk-area names must
     * be unique across the document (they become the dimension key on generated detail rows).
     */
    private void validateRiskAreaJson(int rowNum, String json,
                                       List<ValidationErrorDetail> errors) {
        if (json == null || json.isBlank()) return;

        List<RiskAreaGroup> groups;
        try {
            groups = riskAreaParser.parseStrict(json);
        } catch (RiskAreaParseException e) {
            errors.add(error(rowNum, "RISK_AREA", e.getMessage()));
            return;
        }

        if (groups.isEmpty()) {
            errors.add(error(rowNum, "RISK_AREA", "RISK_AREA must contain at least one group."));
            return;
        }

        Set<String> seenRiskAreas = new HashSet<>();
        for (RiskAreaGroup group : groups) {
            validateGroup(rowNum, group, seenRiskAreas, errors);
        }
    }

    private void validateGroup(int rowNum, RiskAreaGroup group, Set<String> seenRiskAreas,
                               List<ValidationErrorDetail> errors) {
        // groupName is mandatory only for a real group; a standalone risk area may leave it blank.
        if (group.isGroup() && isBlank(group.groupName())) {
            errors.add(error(rowNum, "RISK_AREA",
                    "A grouped risk area (isGroup=true) must have a non-empty 'groupName'."));
        } else if (!group.isGroup() && isBlank(group.groupName())) {
            log.debug("Row {}: standalone risk-area group (isGroup=false) with blank groupName — allowed", rowNum);
        }

        if (group.riskAreas() == null || group.riskAreas().isEmpty()) {
            errors.add(error(rowNum, "RISK_AREA", "Each RISK_AREA group must contain at least one risk area."));
            return;
        }
        for (RiskAreaEntry entry : group.riskAreas()) {
            validateRiskAreaEntry(rowNum, entry, seenRiskAreas, errors);
        }
    }

    private void validateRiskAreaEntry(int rowNum, RiskAreaEntry entry, Set<String> seenRiskAreas,
                                       List<ValidationErrorDetail> errors) {
        if (isBlank(entry.riskArea())) {
            errors.add(error(rowNum, "RISK_AREA", "Every risk area must have a non-empty 'riskArea' name."));
            return;
        }
        if (!seenRiskAreas.add(entry.riskArea())) {
            errors.add(error(rowNum, "RISK_AREA",
                    "Duplicate risk area '" + entry.riskArea() + "'. Risk area names must be unique."));
        }
        if (entry.riskClusters() == null || entry.riskClusters().isEmpty()) {
            errors.add(error(rowNum, "RISK_AREA",
                    "Risk area '" + entry.riskArea() + "' must map to a non-empty list of risk clusters."));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    /**
     * The set of valid location tokens: the distinct union of {@code orl_location} and
     * {@code orl_location_ic} across {@code orl_entity_mstr} (blank values ignored). An uploaded
     * LOCATIONS token is valid when it matches a value in <em>either</em> column.
     */
    private Set<String> loadLocations() {
        log.debug("Loading valid location values (orl_location + orl_location_ic) from orl_entity_mstr");
        Set<String> locations = new HashSet<>();
        for (OrlEntityMstr entity : entityMstrRepository.findAll()) {
            addIfPresent(locations, entity.getOrlLocation());
            addIfPresent(locations, entity.getOrlLocationIc());
        }
        log.debug("Loaded {} distinct location value(s) across both columns", locations.size());
        return locations;
    }

    private int loadMaxBizUnitLevel() {
        log.debug("Loading MAX(LVL_OF_HIER) from orl_biz_unit");
        return bizUnitRepository.findMaxLvlOfHier();
    }

    /**
     * The distinct {@code ORL_BU_NM_L{level}} values for the batch's BIZ_UNIT_LVL: level 2 →
     * {@code ORL_BU_NM_L2}, 3 → {@code ORL_BU_NM_L3}, 4 → {@code ORL_BU_NM_L4}. Any other level
     * has no defined BU column, so an empty set is returned (BIZ_UNITS reference-check is skipped,
     * only the duplicate-within-cell check still applies).
     */
    private Set<String> loadBizUnitsAtLevel(int level) {
        log.debug("Loading distinct ORL_BU_NM_L{} values from orl_biz_unit", level);
        Function<OrlBizUnit, String> columnForLevel = switch (level) {
            case 2 -> OrlBizUnit::getOrlBuNmL2;
            case 3 -> OrlBizUnit::getOrlBuNmL3;
            case 4 -> OrlBizUnit::getOrlBuNmL4;
            default -> null;
        };
        if (columnForLevel == null) {
            log.debug("BIZ_UNIT_LVL={} has no ORL_BU_NM_L column — BIZ_UNITS reference-check skipped", level);
            return Collections.emptySet();
        }
        Set<String> names = new HashSet<>();
        for (OrlBizUnit bu : bizUnitRepository.findAll()) {
            addIfPresent(names, columnForLevel.apply(bu));
        }
        log.debug("Loaded {} distinct ORL_BU_NM_L{} value(s)", names.size(), level);
        return names;
    }

    /** Adds a trimmed value to the set when it is non-null and non-blank. */
    private void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.trim());
        }
    }

    private ValidationErrorDetail error(int row, String field, String message) {
        return ValidationErrorDetail.builder().row(row).field(field).message(message).build();
    }
}
