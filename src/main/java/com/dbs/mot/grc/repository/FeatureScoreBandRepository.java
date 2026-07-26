package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.dto.FeatureScoreBandVersionGroup;
import com.dbs.mot.grc.entity.FeatureScoreBand;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeatureScoreBandRepository extends CrudRepository<FeatureScoreBand, Long> {

    /**
     * Current maximum {@code config_version} for every natural-key group
     * {@code (feature_name, feature_bin, module)}. Column aliases match the projection's
     * component names so Spring Data JDBC maps them directly.
     */
    @Query("""
            SELECT feature_name, feature_bin, module, MAX(config_version) AS max_version
            FROM feature_score_band
            GROUP BY feature_name, feature_bin, module
            """)
    List<FeatureScoreBandVersionGroup> findGroupedMaxVersions();
}
