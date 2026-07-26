package com.dbs.mot.grc.dto;

/**
 * Projection of the current maximum {@code config_version} per natural-key group in
 * {@code feature_score_band}. Populated by a grouped {@code @Query} on the repository and fed to
 * {@link com.dbs.mot.grc.util.ConfigVersionResolver} to compute each group's next version.
 *
 * @param featureName the feature name
 * @param featureBin  the feature bin
 * @param module      the module
 * @param maxVersion  the highest {@code config_version} currently stored for this group
 */
public record FeatureScoreBandVersionGroup(
        String featureName,
        Integer featureBin,
        String module,
        Integer maxVersion) {
}
