package com.dbs.mot.grc.service;

import com.dbs.mot.grc.exception.ConflictException;
import com.dbs.mot.grc.exception.NoFactDataException;
import com.dbs.mot.grc.dto.AssmtGenerationResponse;
import com.dbs.mot.grc.dto.AssmtGenerationResult;
import com.dbs.mot.grc.dto.AssmtGenerationStatus;
import com.dbs.mot.grc.dto.BulkAssmtGenerationResponse;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Bulk assessment generation for all active landscapes
 * ({@code POST /landscape/assessments/generate}).
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Load all {@code orl_lndscp_dim} rows and keep only those that are {@code ACTIVE}
 *       and whose effective window contains the caller-supplied as-of date (filtering in the
 *       service layer — the repository query stays a plain {@code findAll()}).</li>
 *   <li>Group the qualifying rows by landscape name.</li>
 *   <li>Per landscape: skip with {@code SKIPPED_AMBIGUOUS_CONFIG} when more than one
 *       config qualifies, skip with {@code SKIPPED_ALREADY_EXISTS} when the reported month's
 *       assessment already exists, skip with {@code SKIPPED_NO_DATA} when {@code fact_orl} has
 *       no data anywhere for the reported month, otherwise generate via
 *       {@link LandscapeAssmtGenerationService#generateForDim}.</li>
 * </ol>
 *
 * <p><b>Transactions:</b> this orchestrator is intentionally <em>not</em> transactional.
 * Each landscape is generated in its own transaction (started by
 * {@code generateForDim} on the injected service proxy), so a skip or failure for one
 * landscape never rolls back assessments already generated for the others.
 *
 * <p>Only {@link ConflictException} (duplicate period) and {@link NoFactDataException} (no
 * underlying data) are caught per landscape — both are expected, reportable outcomes. Any
 * unexpected exception propagates and fails the whole request with HTTP 500.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkAssmtGenerationService {

    private final OrlLndscpDimRepository dimRepository;
    private final LandscapeAssmtGenerationService generationService;

    /**
     * Generates the reported month's assessment for every active landscape, reporting a
     * per-landscape outcome instead of failing fast.
     *
     * @param asOfDate the as-of date driving both the effectivity filter and the reported period
     *                 (M-1 of this date's month); see {@link LandscapeAssmtGenerationService}
     * @param userId   caller identity, stored as {@code CREATED_BY} on generated rows
     * @return per-landscape results plus generated/skipped counts
     */
    public BulkAssmtGenerationResponse generateForAllActiveLandscapes(LocalDate asOfDate, String userId) {
        // Group the ACTIVE, as-of-effective configs by landscape name,
        // preserving encounter order for a stable, readable response.
        Map<String, List<OrlLndscpDim>> configsByName =
                StreamSupport.stream(dimRepository.findAll().spliterator(), false)
                .filter(dim -> dim.isActiveAndEffectiveOn(asOfDate))
                .collect(Collectors.groupingBy(
                        OrlLndscpDim::getLndscpNm, LinkedHashMap::new, Collectors.toList()));
        log.info("Bulk generation requested by '{}' — {} active landscape(s) effective on {}",
                userId, configsByName.size(), asOfDate);

        List<AssmtGenerationResult> results = new ArrayList<>();
        for (Map.Entry<String, List<OrlLndscpDim>> entry : configsByName.entrySet()) {
            results.add(generateOne(entry.getKey(), entry.getValue(), asOfDate, userId));
        }

        int generated = (int) results.stream()
                .filter(r -> r.getStatus() == AssmtGenerationStatus.GENERATED)
                .count();
        log.info("Bulk generation finished: {} generated, {} skipped (of {} landscape(s))",
                generated, results.size() - generated, results.size());

        return BulkAssmtGenerationResponse.builder()
                .totalLandscapes(results.size())
                .generated(generated)
                .skipped(results.size() - generated)
                .results(results)
                .build();
    }

    /**
     * Generates the assessment for one landscape name, translating the two expected
     * skip conditions (ambiguous config, duplicate period) into result entries.
     */
    private AssmtGenerationResult generateOne(String lndscpNm, List<OrlLndscpDim> configs,
                                              LocalDate asOfDate, String userId) {
        if (configs.size() > 1) {
            String ids = configs.stream()
                    .map(dim -> String.valueOf(dim.getId()))
                    .collect(Collectors.joining(", "));
            log.warn("Skipping landscape '{}' — {} active configs effective today (ids: {})",
                    lndscpNm, configs.size(), ids);
            return AssmtGenerationResult.builder()
                    .lndscpNm(lndscpNm)
                    .status(AssmtGenerationStatus.SKIPPED_AMBIGUOUS_CONFIG)
                    .message("Multiple active configs (ids: " + ids
                            + ") are effective today — cannot determine which one to generate from.")
                    .build();
        }

        OrlLndscpDim dim = configs.getFirst();
        try {
            AssmtGenerationResponse generatedAssmt = generationService.generateForDim(dim, asOfDate, userId);
            return AssmtGenerationResult.builder()
                    .lndscpNm(lndscpNm)
                    .lndscpNum(dim.getId())
                    .status(AssmtGenerationStatus.GENERATED)
                    .message("Assessment generated with " + generatedAssmt.getDetailRowCount()
                            + " detail row(s) for period '" + generatedAssmt.getAssmtPeriod() + "'.")
                    .lndscpAssmtId(generatedAssmt.getLndscpAssmtId())
                    .detailRowCount(generatedAssmt.getDetailRowCount())
                    .build();
        } catch (ConflictException ex) {
            // Expected when this month's assessment was already generated — report and move on.
            log.info("Skipping landscape '{}' — {}", lndscpNm, ex.getMessage());
            return AssmtGenerationResult.builder()
                    .lndscpNm(lndscpNm)
                    .lndscpNum(dim.getId())
                    .status(AssmtGenerationStatus.SKIPPED_ALREADY_EXISTS)
                    .message(ex.getMessage())
                    .build();
        } catch (NoFactDataException ex) {
            // Expected when there is no fact_orl data yet for the reported month — report and move on.
            log.info("Skipping landscape '{}' — {}", lndscpNm, ex.getMessage());
            return AssmtGenerationResult.builder()
                    .lndscpNm(lndscpNm)
                    .lndscpNum(dim.getId())
                    .status(AssmtGenerationStatus.SKIPPED_NO_DATA)
                    .message(ex.getMessage())
                    .build();
        }
    }
}
