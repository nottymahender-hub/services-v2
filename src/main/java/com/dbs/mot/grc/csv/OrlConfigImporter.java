package com.dbs.mot.grc.csv;

/**
 * Contract for importing and exporting one ORL configuration table: the union of
 * {@link ConfigUploader} and {@link ConfigExporter}, which every {@code *ConfigImportService}
 * implements. The two are declared separately so each of their actual callers —
 * {@link com.dbs.mot.grc.service.OrlConfigurationService} for upload,
 * {@link OrlConfigImporterRegistry} for download — depends only on the half it calls.
 *
 * <p>The upload payload happens to be CSV, but each implementation represents a
 * <em>configuration</em> concern (business units, entity master, scoring bands, …), not a
 * CSV concern — hence the name. Each table has its own {@code *ConfigImportService}
 * implementation in the service layer, wired explicitly into
 * {@link com.dbs.mot.grc.controller.OrlConfigurationController}.
 */
public interface OrlConfigImporter extends ConfigUploader, ConfigExporter {
}
