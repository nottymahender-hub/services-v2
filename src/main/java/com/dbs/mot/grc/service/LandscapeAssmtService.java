package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.LandscapeAssmtProjection;
import com.dbs.mot.grc.dto.LandscapeAssmtSummary;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read logic for the landscape assessment listing API.
 *
 * <p>Backed by a single query, {@link OrlLndscpAssmtRepository#findAllSummaries()}, which joins
 * each assessment to its parent {@code orl_lndscp_dim} for the landscape name and returns a flat
 * projection ordered most-recently-modified first. The projection keeps the query to one table's
 * worth of columns and does not materialise each assessment's detail {@code MappedCollection}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeAssmtService {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrlLndscpAssmtRepository assmtRepository;

    /**
     * Returns all landscape assessments enriched with the landscape name, ordered by last
     * modified (UPDATE_DT_TM, else CREATE_DT_TM) descending — as sorted by the query.
     *
     * @return list of summaries; empty list when no assessments exist
     */
    public List<LandscapeAssmtSummary> fetchAll() {
        log.debug("Fetching landscape assessment summaries (joined with landscape configs)");

        List<LandscapeAssmtSummary> summaries = assmtRepository.findAllSummaries().stream()
                .map(this::toSummary)
                .toList();

        log.info("Returning {} landscape assessment summary(ies)", summaries.size());
        return summaries;
    }

    private LandscapeAssmtSummary toSummary(LandscapeAssmtProjection assmt) {
        LocalDateTime lastModifiedOn = assmt.updateDtTm() != null ? assmt.updateDtTm() : assmt.createDtTm();
        String lastModifiedBy = assmt.updatedBy() != null ? assmt.updatedBy() : assmt.createdBy();

        return LandscapeAssmtSummary.builder()
                .landscapeAssmtId(assmt.id())
                .landscapeName(assmt.landscapeName())
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
