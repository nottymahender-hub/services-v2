package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlLndscpCalloutCommentHist;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JDBC repository for {@code orl_lndscp_callout_comment_hist}.
 * Append-only — the service inserts one row per callout comment version.
 */
@Repository
public interface OrlLndscpCalloutCommentHistRepository
        extends CrudRepository<OrlLndscpCalloutCommentHist, Long> {
}
