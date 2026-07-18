package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.LandscapeAssmtProjection;
import com.dbs.mot.grc.dto.LandscapeAssmtSummary;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Business logic for the landscape assessment listing API.
 *
 * <h3>No hand-written joins, no unnecessary aggregate loading</h3>
 * <p>This service issues two plain, independent queries — a summary projection from
 * {@link OrlLndscpAssmtRepository#findAllSummaries()} and a full {@code findAll()} from
 * {@link OrlLndscpDimRepository} — and merges them in memory. This is a fixed 2-query
 * cost regardless of row count (avoiding both hand-written join SQL and an N+1
 * per-assessment lookup pattern).
 *
 * <p>Using the projection here (rather than the {@code OrlLndscpAssmt} entity, which
 * carries a {@code @MappedCollection} of detail rows) is deliberate: Spring Data JDBC
 * eagerly loads every {@code @MappedCollection} whenever the entity type itself is
 * fetched, and this listing endpoint never needs the detail rows. The landscape name
 * lookup, the {@code lastModifiedOn}/{@code lastModifiedBy} fallback, and the sort
 * order are all resolved here rather than in SQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeAssmtService {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrlLndscpAssmtRepository assmtRepository;
    private final OrlLndscpDimRepository   dimRepository;

    /**
     * Returns all landscape assessments enriched with the landscape name,
     * ordered by landscape name then assessment period.
     *
     * @return list of summaries; empty list when no assessments exist
     */
    public List<LandscapeAssmtSummary> fetchAll() {
        log.debug("Fetching landscape assessment summaries and landscape configs");

        List<LandscapeAssmtProjection> assmts = assmtRepository.findAllSummaries();
        Map<Long, OrlLndscpDim> dimsById = StreamSupport
                .stream(dimRepository.findAll().spliterator(), false)
                .collect(Collectors.toMap(OrlLndscpDim::getId, Function.identity()));

        log.debug("Fetched {} assessment(s) and {} landscape config(s)", assmts.size(), dimsById.size());

        List<LandscapeAssmtSummary> summaries = assmts.stream()
                .map(assmt -> toSummary(assmt, dimsById.get(assmt.lndscpNum())))
                .sorted(Comparator
                        .comparing(LandscapeAssmtSummary::getLandscapeName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LandscapeAssmtSummary::getAssessmentPeriod, Comparator.nullsLast(String::compareTo)))
                .toList();

        log.info("Returning {} landscape assessment summary(ies)", summaries.size());
        return summaries;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private LandscapeAssmtSummary toSummary(LandscapeAssmtProjection assmt, OrlLndscpDim dim) {
        LocalDateTime lastModifiedOn = assmt.updateDtTm() != null ? assmt.updateDtTm() : assmt.createDtTm();
        String lastModifiedBy = assmt.updatedBy() != null ? assmt.updatedBy() : assmt.createdBy();

        return LandscapeAssmtSummary.builder()
                .landscapeAssmtId(assmt.id())
                .landscapeName(dim != null ? dim.getLndscpNm() : null)
                .assessmentPeriod(assmt.assmtPeriod())
                .lastModifiedOn(format(lastModifiedOn))
                .lastModifiedBy(lastModifiedBy)
                .status(assmt.status())
                .build();
    }

    private String format(LocalDateTime dt) {
        return (dt != null) ? dt.format(DT_FMT) : null;
    }
}
