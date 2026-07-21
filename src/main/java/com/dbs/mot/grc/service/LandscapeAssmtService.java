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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Read logic for the landscape assessment listing API.
 *
 * <p>Issues two plain queries — a summary projection ({@link OrlLndscpAssmtRepository#findAllSummaries()})
 * and a full {@code findAll()} of configs — and merges them in memory (fixed 2-query cost, no join,
 * no N+1). The projection is used instead of the {@code OrlLndscpAssmt} entity so Spring Data JDBC
 * does not eagerly load each assessment's detail {@code MappedCollection}. Ordering (most recently
 * modified first) is done in SQL.
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
     * Returns all landscape assessments enriched with the landscape name, ordered by last
     * modified (UPDATE_DT_TM, else CREATE_DT_TM) descending — as sorted by the query.
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
                .toList();

        log.info("Returning {} landscape assessment summary(ies)", summaries.size());
        return summaries;
    }

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
