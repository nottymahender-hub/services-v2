package com.dbs.mot.grc.common.exception;

/**
 * Thrown when the {@code orl_lndscp_dim.RISK_AREA} JSON document cannot be parsed into the
 * expected grouped structure (see {@link com.dbs.mot.grc.dto.RiskAreaGroup}).
 *
 * <p>Write paths (CSV upload validation) catch this to report a precise per-row
 * validation error (HTTP 400). Read paths degrade gracefully and never surface it.
 */
public class RiskAreaParseException extends RuntimeException {

    public RiskAreaParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
