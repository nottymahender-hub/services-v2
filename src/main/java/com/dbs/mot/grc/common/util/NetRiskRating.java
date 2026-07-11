package com.dbs.mot.grc.common.util;

import java.util.Optional;

/**
 * The four net-risk-rating bands, ordered by severity so the "worst" of a set can be
 * computed. Enum {@link #ordinal()} is the severity rank: {@code LOW} (lowest) →
 * {@code HIGH} (highest).
 *
 * <p>The stored/display label ({@code "Med Low"}, etc.) is kept alongside the constant so
 * callers can round-trip between the DB string value and the severity ordering without
 * embedding the mapping in multiple places.
 */
public enum NetRiskRating {

    LOW("Low"),
    MED_LOW("Med Low"),
    MED_HIGH("Med High"),
    HIGH("High");

    private final String label;

    NetRiskRating(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Resolves a stored rating label (e.g. {@code "Med High"}) to its enum constant.
     * Returns empty for null/blank or unrecognised values so callers can decide how to
     * handle bad data rather than this throwing.
     */
    public static Optional<NetRiskRating> fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        for (NetRiskRating rating : values()) {
            if (rating.label.equalsIgnoreCase(label.trim())) {
                return Optional.of(rating);
            }
        }
        return Optional.empty();
    }
}
