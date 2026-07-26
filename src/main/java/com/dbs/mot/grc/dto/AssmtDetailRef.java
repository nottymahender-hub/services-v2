package com.dbs.mot.grc.dto;

/**
 * Lightweight projection of an assessment detail row's owning assessment id and status,
 * used by the overlay-save flow to authorise the update without loading the full row.
 *
 * @param lndscpAssmtId the owning {@code orl_lndscp_assmt.id}
 * @param status        the detail's {@code STATUS}
 */
public record AssmtDetailRef(Long lndscpAssmtId, String status) {
}
