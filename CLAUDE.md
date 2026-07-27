# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`grc-orl-services-v2` — a Spring Boot 3.4 / Java 21 REST service (`com.dbs.mot.grc`) for GRC "Operational Risk Landscape" (ORL). It manages CSV-based maintenance of static/reference tables and serves landscape **assessments** (monthly snapshots of risk ratings by dimension), their drill-down details, and per-assessment **callouts**.

## Commands

The Maven wrapper (`./mvnw`, `mvnw.cmd` on Windows) is committed — use it.

```bash
./mvnw clean verify          # compile, run all tests, JaCoCo report + coverage gate
./mvnw test                  # run all tests (also produces JaCoCo report)
./mvnw spring-boot:run       # run the app (needs a MariaDB per application.properties)
./mvnw test -Dtest=BizUnitCsvHandlerTest              # single test class
./mvnw test -Dtest=BizUnitCsvHandlerTest#methodName   # single test method
```

- **Coverage gate:** JaCoCo enforces **≥90% line coverage** on the whole bundle during the `verify` phase (`jacoco:check`). A build can pass `test` but fail `verify` on coverage — keep new code covered. Report: `target/site/jacoco/index.html`.
- **Tests run against in-memory H2** (`src/test/resources/application-test.properties`), MySQL-compatibility mode, schema from `src/test/resources/schema-test.sql` (Flyway is disabled for tests). The production schema is Flyway migration `V1__create_all_tables.sql`. **These two schema files are maintained separately — when you change one, update the other**. H2 (MySQL mode) supports `DEFAULT CURRENT_TIMESTAMP` and `ON UPDATE CURRENT_TIMESTAMP` (used for the DB-managed audit timestamps), but not every MariaDB feature — notably not `ON DUPLICATE KEY UPDATE`, which is why the config upserts split insert/update in Java instead.
- Running the app locally requires MariaDB at `jdbc:mariadb://localhost:3306/grc_orl2_db` (root/root by default). Swagger UI is available at `/swagger-ui.html` when running.

## Architecture

### Package layout

There is **no `common` package** — all shared packages sit directly under `com.dbs.mot.grc`: `controller`, `service`, `repository`, `entity`, `dto`, `csv`, `exception`, `config`, `enums`, `validation`, `util`, `logging`.

### Two feature areas

1. **ORL configuration import/export** (`controller/OrlConfigurationController`, the `*ConfigImportService` beans in `service`, the shared engine in `csv`, plus `csv/mapper`, `csv/validator`) for the static/reference tables.
2. **Landscape assessment domain** (`controller/LandscapeAssmtController`, `service`, `entity`, `repository`, `dto`) — assessment read/drill-down, overlay save, and callouts.

### ORL configuration pipeline

`OrlConfigurationController` (`/api/orl-configurations`) exposes an **explicit upload + download endpoint per table** (e.g. `POST /api/orl-configurations/biz-units/upload`, `GET /api/orl-configurations/biz-units/download`). Each table has its own `service/XxxConfigImportService implements OrlConfigImporter` (the payload is CSV, but each represents a *configuration*, so the classes are not named "Csv"). The controller injects the importers directly and delegates the import+audit transaction to `OrlConfigurationService` (there is **no registry** and **no generic `{tableName}` dispatch**).

**To support a new table:**
- `service/XxxConfigImportService implements OrlConfigImporter` — config name, headers, persistence.
- `csv/mapper/XxxCsvRowMapper implements CsvRowMapper<T>` — one CSV row → DTO, collecting per-cell/type errors.
- `csv/validator/XxxCsvRowValidator implements CsvRowValidator<T>` — cross-row/dataset checks.
- add an explicit upload+download endpoint pair to `OrlConfigurationController`.

All importers delegate parsing to the shared `CsvImportProcessor.process(...)`: file validation → header validation (exact set, no duplicates, BOM-stripped) → per-row mapping → per-row Hibernate Bean Validation → dataset-level validation. Both mapping and cross-row phases collect **all** errors and throw `CsvValidationException` (→ HTTP 400 with per-row detail). `OrlConfigurationService.importConfig` is `@Transactional`, so the row import and the audit insert commit as one unit.

**Persistence is 100% Spring Data JDBC repositories — no `JdbcTemplate`/`NamedParameterJdbcTemplate`.** Append-only tables use `repository.saveAll`. The three **upsert** tables (`orl_biz_unit`, `orl_entity_mstr`, `orl_bu_loctn_headcount`) upsert *without* `ON DUPLICATE KEY UPDATE`: the service reads existing rows, splits incoming into inserts/updates, and calls `saveAll` on each (within-file duplicates keep the last occurrence). The two client-`@Id` entities (`OrlBizUnit`, `OrlEntityMstr`) implement `Persistable` with a transient `newRecord` flag so `saveAll` inserts new rows and updates existing ones; `OrlBuLctnHeadcount` matches on its composite unique key and reuses the surrogate id.

The three **scoring tables** (`feature_score_band`, `train_stats`, `net_risk_band`) compute `config_version` server-side via `ConfigVersionResolver` as `MAX(existing version for the natural-key group) + 1`. The current per-group maxima are read through a grouped `@Query` projection on each scoring repository (returning a `*VersionGroup` record); `ConfigVersionResolver` is pure in-memory logic. Open-ended range bounds use the sentinels in `RangeSentinels`.

**Table-specific validation notes:**
- `risk-type-risk-area-maps` upload carries `IS_OR_FA` (mandatory, `Y`/`N` only) and `RISK_CLUSTER` (optional). Its `CsvRowValidator` enforces two batch rules: `(RISK_AREA, RISK_TYPE_L4_NUM)` unique (mirrors the table key) and `RISK_CLUSTER` unique within each such pair (blank clusters ignored).
- `lndscp-dim` upload validates `BIZ_UNITS` against the distinct **`orl_biz_unit.ORL_BU_NM_L{level}`** matching the batch `BIZ_UNIT_LVL` (2→L2, 3→L3, 4→L4; other levels skip the ref-check) and `LOCATIONS` against the union of **`orl_entity_mstr.orl_location` and `orl_location_ic`**. Both sets are derived in `OrlLndscpDimCsvRowValidator` from `CrudRepository#findAll()` + in-memory distinct — Spring Data JDBC has no derived query for a single-/multi-column distinct scalar list, and both are small config tables.

The Flyway baseline `V1__create_all_tables.sql` is **fully idempotent** (`CREATE TABLE IF NOT EXISTS` on every table), so it can be re-applied safely after its history row is cleared (e.g. when a column is added to V1 rather than via a new versioned migration).

### Assessment domain model (Spring Data **JDBC**, not JPA)

This uses **Spring Data JDBC**. Key consequences that differ from JPA:
- FKs are modelled as `AggregateReference<T, Long>` — one-way, no cascade, no lazy navigation. Extract the id with `.getId()`.
- `OrlLndscpAssmt` owns its detail rows as a `@MappedCollection` (`orl_lndscp_assmt_details`). **Fetching the `OrlLndscpAssmt` entity eagerly loads all detail rows**, so use it only when you actually need them (the assessment listing). For existence use `existsById`; for header-only reads use the `findHeaderById` projection.
- **`save()` on an existing aggregate does a full delete+reinsert of its children.** So only call `save()` on the *collection-owning* `OrlLndscpAssmt` for a fresh insert, never to mutate an existing one. Child and childless rows, however, save cleanly: the overlay save loads the target `OrlLndscpAssmtDetails` by id (`findById`) and re-saves that single row via `detailsRepository.save(row.toBuilder()...)` — the detail is fetched and persisted as its own `CrudRepository` entity, not through the parent aggregate, so no delete+reinsert occurs. `OrlLndscpCallout` (no child collection) likewise updates/soft-deletes via `save()` on the loaded row (`toBuilder`). There is **no `@Modifying @Query`** and no `JdbcTemplate`/`NamedParameterJdbcTemplate` anywhere — all DB access is through repositories (`CrudRepository` methods + derived queries + a few read-only `@Query` projections).
- **Callouts:** field values are validated only by request-body Bean Validation (`@NotBlank`/`@NotEmpty`/`@Size(max=400)` on the comment) — there is no validation of values against the landscape dimensions, and the comment is rejected (400), not truncated, when too long. The active callouts are embedded in the assessment-details response as a plain `List<CalloutResponse>` (field `callouts`).
- **Timestamps are DB-managed.** `CREATE_DT_TM`/`CREATED_DT_TM`/`UPDATE_DT_TM`/`uploaded_dt_tm` columns are filled by the database (`DEFAULT current_timestamp()` on insert, `ON UPDATE current_timestamp()` on update) — never by the app. The entity fields carry `@ReadOnlyProperty` so Spring Data JDBC excludes them from INSERT/UPDATE; do not set them in builders. (H2 supports both `DEFAULT CURRENT_TIMESTAMP` and `ON UPDATE CURRENT_TIMESTAMP`, so the same behaviour holds in tests — keep the two schema files in step.)
- **Prefer single-table queries; join only to avoid a worse pattern.** Services mostly issue plain single-table queries (`findById`, `findByBizDt`, derived queries) and merge in Java. The one deliberate exception is the assessment **listing** projection (`findAllSummaries`), which `LEFT JOIN`s `orl_lndscp_dim` to carry the landscape name in a single query rather than separately loading every landscape config. The drill-down uses the `findHeaderById` projection + targeted single-row detail reads (`findById` for the row — its id is the PK, so it is not scoped to the path assessment — and `findByAssmtAndDimension` for the previous month) so the assessment's detail `@MappedCollection` is never loaded for a single-row view; that collection is loaded only by `fetchByAssmtId`, which returns every detail row. The single `fact_orl`/module lookups are plain **derived queries** on the `CrudRepository`s (`findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(...)`), not `SELECT *` `@Query`s. The lone remaining `@Query` on a fact/detail repo is `OrlLndscpAssmtDetailsRepository.findByAssmtAndDimension`, which **must** stay a named query because it filters on `lndscp_assmt_id` — the aggregate's `@MappedCollection` back-reference, intentionally unmapped on the child, so no derived-query property path can name it.

### The assessment/fact split (important)

Assessment detail rows (`orl_lndscp_assmt_details`) are **thin**: only dimensions, category, status, overlay fields, and audit columns. Row-level **computed** values (calculated NRR, rating change, control effectiveness, commentary, inherent risk) live in the separate `fact_orl` snapshot table (`CTRL_EFF_RTN` is a plain `VARCHAR` — surfaced as a raw string, not an enum), and the per-module **GRC metrics** live in the module snapshot tables (`rcsa_fact_orl`, `inc_fact_orl`, `ina_fact_orl`, `kri_fact_orl`, assembled by `GrcMetricsService` for the drill-down only). All are **matched at read time by dimension key** (`RISK_AREA`, `ORL_BU_NM_L2/L3/L4`, `LOCATION`) for a business date. The module tables are **standardized** on those same key column names plus `biz_dt`, `NET_RISK_RTNG`, and `RISK_RTNG_CHGE` (matching `fact_orl`), mapped in each entity. `GrcMetricsService` **always names all four modules** in its `grcMetrics` map, in `RCSA → INC → INA → KRI` order; a module with no matching snapshot row maps to `null` rather than being omitted, so the block's shape never varies. The business date used for each lookup is:
- current month → the assessment's own `biz_dt` (`orl_lndscp_assmt.biz_dt`)
- previous month → the previous assessment's `biz_dt` (followed via `PREV_ASSMT_NUM`)
- live → the latest business date across `fact_orl` (`findMaxBizDt()`), matched to the dimension via the same by-date lookup. That `MAX(biz_dt)` is read **once** and reused for both the live snapshot and the response's top-level `lastRefreshed` (so `live.lastRefreshed == lastRefreshed`, and there is no second `findMaxBizDt` call). If the dimension has no row on that global latest date, `liveNRRDetails` is `null`.

The displayed `nrr` is the analyst overlay (`OVRLY_NET_RISK_RTNG`) when present, otherwise it falls back to the calculated rating (`nrrCalculated`) — in both the list items and the current/previous drill-down blocks (`nrrOverlaid` records which applied).

**Proportions in the GRC metrics are percentages.** The KRI `KRI_RED_PROP`/`KRI_GREEN_PROP` (derived from the red/green counts over the active count) and the RCSA `rcsa_high_risk_proportion`/`rcsa_med_high_proportion`/`rcsa_med_low_proportion`/`rcsa_low_risk_proportion` (stored as 0..1 fractions) are all surfaced in the response as **percentages** — ×100, rounded to 2 dp, `HALF_UP` — via `util/Percentages` (e.g. `0.5433 → 54.33`, `2 of 4 → 50.00`). A `null` fraction or a zero KRI active-count stays `null` (distinct from `0.00`); the conversion happens in each module entity's `metrics()`.

Because MariaDB treats each NULL as distinct in a unique index, all dimension columns in `orl_lndscp_assmt_details` and `fact_orl` are `NOT NULL DEFAULT ''` — whatever populates these tables must write `''` (never null) for empty dimensions so the unique index actually enforces one row per dimension, and the read-time dimension-key match lines up between details and facts. Preserve this invariant.

> **Note:** assessment *generation* (`POST /landscape/assessments/generate`, `BulkAssmtGenerationService`, `LandscapeAssmtGenerationService`, and the `AssmtGeneration*` DTOs) has been **removed**. Assessment rows are now populated by an upstream data load, not by this service. The landscape domain is read/drill-down + overlay save + callouts only.

### Cross-cutting conventions

- **Landscape endpoints** (all on the single `LandscapeAssmtController`): `GET /landscape/assessments` (list), `GET /landscape/assessments/{lndscpAssmtId}` (details **with embedded dimensions + callouts**), `GET /landscape/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}` (drill-down), `POST .../assessmentDetail/{assmtDetailId}/overlay`, and `POST`/`PUT`/`DELETE /landscape/assessment/{lndscpAssmtId}/callouts[/{calloutId}]`. There is **no** generation endpoint, and **no** standalone `/dimensions` or `GET /callouts` endpoints — dimensions and callouts are embedded in the details response.
- **Auth:** every endpoint requires the `X-EGRC-UserId` header (used as `CREATED_BY`/operator identity); missing/blank → **HTTP 401**. Controllers read it with `required = false` and call a private `requireUser(...)` guard that throws `UnauthorizedException` (→ 401 via `GlobalExceptionHandler`) when blank; a genuinely absent required header becomes 401 via `MissingRequestHeaderException`.
- **Responses:** all endpoints return the `ApiResponse<T>` envelope. All error→HTTP mapping is centralized in `GlobalExceptionHandler` (`@RestControllerAdvice`) — throw the domain exceptions (`BadRequestException`→400, `NotFoundException`→404, `ConflictException`→409, `UnauthorizedException`→401, `CsvValidationException`→400 w/ details) rather than building error responses in controllers/services.
- **Nulls are always serialized.** No response DTO carries `@JsonInclude(NON_NULL)` and there is no global `default-property-inclusion` — every field of every response, including the envelope's `data`/`errors`, is always present so clients see one fixed shape. Do not add `NON_NULL` to a response DTO.
- **All DB access is through Spring Data JDBC repositories** — no `JdbcTemplate`/`NamedParameterJdbcTemplate`. Values are bound as `@Query` named parameters; a variable-length `IN` list is one named collection parameter (`IN (:configIds)`). No SQL is built by string concatenation.
- **Logging:** there is no request/access filter. `ServiceLoggingAspect` adds DEBUG entry/exit + timing around all `..service..` methods and importer `processAndImport`, **summarising `MultipartFile` args (name/size only)** — never log raw file content. Controllers log each request at DEBUG with the operator id. (Tomcat access logging can be enabled via `server.tomcat.accesslog.enabled=true` if needed.)
- **Enum columns:** DB `ENUM` columns map to Java enums implementing `enums/PersistableEnum` (constant + `dbValue`, since values like `"Med Low"`/`"Poor/Fail"` aren't identifiers). Spring Data JDBC read/write is handled by the converters registered in `config/JdbcConfig`; CSV boundary DTOs stay `String` and convert via `X.fromDbValue(...)`. Response DTOs stay `String` (populated via `PersistableEnum.dbValue(...)`); the exception is `NetRiskRating`, whose API fields use `getDisplayValue()` (e.g. `"Medium-Low Risk"`).
- **JSON in columns:** `orl_lndscp_dim.RISK_AREA` is a `JSON`-typed column holding a grouped array (`[{groupName, isGroup, riskAreas:[{riskArea, riskClusters}]}]`) — read/written only through `util/RiskAreaParser` (the single source of truth: `parseStrict` for write/validation paths, `parseQuietly` for read paths, plus `lookupByRiskArea`/`riskAreaClusterMap`/`distinctRiskClusters`/`normalizeCompact`). Callout `LOCATIONS`/`BIZ_UNITS` (arrays) are stored as JSON strings parsed with an `ObjectMapper` that has `STRICT_DUPLICATE_DETECTION` enabled. Parse failures are logged and degraded gracefully, not thrown, in read paths. GRC metrics are **not** stored as JSON — they are read from the per-module fact tables.
- **Lombok** is used throughout (`@Builder`, `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`) — annotation processing is configured in the compiler plugin.
