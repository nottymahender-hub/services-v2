package com.dbs.mot.grc.util;

import com.dbs.mot.grc.exception.UnauthorizedException;

/**
 * The {@code X-EGRC-UserId} request header shared by every controller: its name, and the
 * missing/blank guard each controller runs before doing any work.
 */
public final class OperatorHeader {

    /** Header carrying the operator identity, required on every endpoint. */
    public static final String NAME = "X-EGRC-UserId";

    private OperatorHeader() {
        // Utility class — no instances.
    }

    /**
     * Returns the trimmed operator id, or throws {@link UnauthorizedException} when missing/blank.
     */
    public static String require(String username) {
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException(NAME + " header is required and must not be blank.");
        }
        return username.trim();
    }
}
