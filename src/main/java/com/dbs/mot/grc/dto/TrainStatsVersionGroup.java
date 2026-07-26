package com.dbs.mot.grc.dto;

/**
 * Projection of the current maximum {@code config_version} per natural-key group in
 * {@code train_stats}. Fed to {@link com.dbs.mot.grc.util.ConfigVersionResolver}.
 *
 * @param lvl        the training level
 * @param module     the module
 * @param maxVersion the highest {@code config_version} currently stored for this group
 */
public record TrainStatsVersionGroup(
        String lvl,
        String module,
        Integer maxVersion) {
}
