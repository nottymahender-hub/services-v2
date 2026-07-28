package com.dbs.mot.grc.csv;

import com.dbs.mot.grc.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of every {@link OrlConfigImporter} bean, keyed by its {@link OrlConfigImporter#configName()}.
 *
 * <p>Spring injects all importer beans, so a new ORL configuration table becomes resolvable here
 * (and therefore downloadable through the single download endpoint) simply by adding its
 * {@code *ConfigImportService} — no controller or registry change required.
 *
 * <p>The registry is built once at startup and is immutable thereafter, so lookups are lock-free
 * and thread-safe.
 */
@Slf4j
@Component
public class OrlConfigImporterRegistry {

    /** Config name → importer. Insertion-ordered for stable {@link #knownNames()} output. */
    private final Map<String, OrlConfigImporter> importersByName;

    /**
     * Indexes the injected importers by config name, failing fast if two importers share a name
     * (a misconfiguration that would otherwise make the download route non-deterministic).
     *
     * @param importers all {@link OrlConfigImporter} beans discovered by Spring
     */
    public OrlConfigImporterRegistry(List<OrlConfigImporter> importers) {
        Map<String, OrlConfigImporter> byName = new LinkedHashMap<>();
        for (OrlConfigImporter importer : importers) {
            String name = importer.configName();
            OrlConfigImporter existing = byName.putIfAbsent(name, importer);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate OrlConfigImporter config name '" + name + "': "
                                + existing.getClass().getName() + " and " + importer.getClass().getName());
            }
        }
        this.importersByName = Map.copyOf(byName);
        log.info("Registered {} ORL config importer(s): {}", importersByName.size(), byName.keySet());
    }

    /**
     * Resolves the importer for a configuration name.
     *
     * @param configName the config identifier from the request path (e.g. {@code "biz-units"})
     * @return the matching importer
     * @throws NotFoundException when no importer is registered under {@code configName}
     */
    public OrlConfigImporter get(String configName) {
        OrlConfigImporter importer = importersByName.get(configName);
        if (importer == null) {
            log.warn("Unknown ORL configuration '{}' requested; known configs: {}",
                    configName, importersByName.keySet());
            throw new NotFoundException("Unknown ORL configuration: '" + configName
                    + "'. Valid values: " + importersByName.keySet() + ".");
        }
        return importer;
    }

    /** The set of registered config names, in registration order (used for docs and error messages). */
    public Set<String> knownNames() {
        return importersByName.keySet();
    }
}
