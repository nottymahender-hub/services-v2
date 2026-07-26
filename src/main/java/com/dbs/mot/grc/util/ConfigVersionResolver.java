package com.dbs.mot.grc.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the {@code config_version} to assign to each distinct grouping key in a CSV batch for
 * the scoring tables ({@code feature_score_band}, {@code train_stats}, {@code net_risk_band}).
 *
 * <h3>Business rule</h3>
 * Each group's {@code config_version} is {@code MAX(existing config_version for the group) + 1}
 * (or {@code 1} if the group has never been uploaded), where "group" is the table-specific
 * natural key (e.g. {@code (feature_name, feature_bin, module)}).
 *
 * <h3>Data access</h3>
 * This class performs no database access. Each caller fetches the current per-group maxima via a
 * grouped repository query and passes them in as {@link GroupMax} values, so all DB access stays
 * in Spring Data JDBC repositories and this class is pure, easily-unit-tested logic.
 */
@Slf4j
@Component
public class ConfigVersionResolver {

    /**
     * Delimiter used to join group-by column values into a single map key. {@code U+0001}
     * (start-of-heading) is a control character that cannot occur in CSV data, so joined keys
     * can never collide.
     */
    private static final String KEY_DELIM = "\u0001";

    /**
     * Resolves {@code MAX + 1} (or {@code 1}) for every key in {@code batchKeys}.
     *
     * @param existingGroups the current maximum version per already-stored group
     * @param batchKeys      distinct group keys present in the current batch, each an ordered
     *                       list of values in the same column order used to build
     *                       {@code existingGroups}
     * @return map from joined group key (see {@link #groupKey(String...)}) to next version
     */
    public Map<String, Integer> resolveNextVersions(List<GroupMax> existingGroups,
                                                    Set<List<String>> batchKeys) {
        Map<String, Integer> currentMax = new HashMap<>();
        for (GroupMax group : existingGroups) {
            currentMax.put(groupKey(group.groupValues().toArray(new String[0])), group.maxVersion());
        }

        Map<String, Integer> resolved = new HashMap<>();
        for (List<String> key : batchKeys) {
            String joined = groupKey(key.toArray(new String[0]));
            resolved.put(joined, currentMax.getOrDefault(joined, 0) + 1);
        }

        log.debug("Resolved config_version for {} group(s); {} group(s) already existed",
                resolved.size(), currentMax.size());
        return resolved;
    }

    /** Joins ordered group-by column values into a single lookup key. */
    public static String groupKey(String... values) {
        return String.join(KEY_DELIM, values);
    }

    /**
     * The current maximum {@code config_version} for one already-stored group.
     *
     * @param groupValues the group's natural-key values, in the caller's fixed column order
     * @param maxVersion  the highest version currently stored for the group
     */
    public record GroupMax(List<String> groupValues, int maxVersion) {
    }
}
