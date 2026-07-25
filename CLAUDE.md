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
- **Tests run against in-memory H2** (`src/test/resources/application-test.properties`), MySQL-compatibility mode, schema from `src/test/resources/schema-test.sql` (Flyway is disabled for tests). The production schema is Flyway migration `V1__create_all_tables.sql`. **These two schema files are maintained separately — when you change one, update the other**, and be aware H2 does not support every MariaDB feature (e.g. `ON DUPLICATE KEY UPDATE`, `ON UPDATE current_timestamp()`), which is why some behaviour is done in Java rather than SQL.
- Running the app locally requires MariaDB at `jdbc:mariadb://localhost:3306/grc_orl2_db` (root/root by default). Swagger UI is available at `/swagger-ui.html` when running.

## Architecture

### Two feature areas

1. **Generic CSV import/export** (`common/csv`, `common/controller/CsvController`, plus `csv/handler`, `csv/mapper`, `csv/validator`) for the static/reference tables.
2. **Landscape assessment domain** (`controller`, `service`, `entity`, `repository`, `dto`) — assessment generation, read/drill-down, and callouts.

### CSV pipeline — extend without touching the controller

One controller (`CsvController`) serves every table via `POST/GET /api/csv/{tableName}/upload|download`. `tableName` is resolved to a `CsvHandler` bean by `CsvHandlerRegistry`, which auto-discovers all `CsvHandler` `@Component`s at startup and keys them by `getTableName()`.

**To support a new table, add three beans and nothing else** (no controller/registry changes):
- `csv/handler/XxxCsvHandler implements CsvHandler` — declares table name, headers, and the persistence SQL (typically `INSERT … ON DUPLICATE KEY UPDATE` upsert via `NamedParameterJdbcTemplate`).
- `csv/mapper/XxxCsvRowMapper implements CsvRowMapper<T>` — one CSV row → DTO, collecting per-cell/type errors.
- `csv/validator/XxxCsvRowValidator implements CsvRowValidator<T>` — cross-row/dataset checks (uniqueness, references).

All handlers delegate parsing to the shared `CsvImportProcessor.process(...)`, which runs a fixed pipeline: file validation → header validation (exact set, no duplicates, BOM-stripped) → per-row mapping → per-row Hibernate Bean Validation on the DTO → dataset-level validation. Mapping/bean errors fail before cross-row checks; both collect **all** errors and throw `CsvValidationException` (→ HTTP 400 with per-row detail).

`processAndImport` and `CsvController.upload` are both `@Transactional` so the row import and the `orl_static_data_maintianance_csv_upload_audit` insert commit as one unit.

The three **scoring tables** (`feature_score_band`, `train_stats`, `net_risk_band`) are special: their `config_version` is **computed server-side** by `ConfigVersionResolver` as `MAX(existing version for the natural-key group) + 1` (in-memory grouping to stay dialect-portable). Open-ended range bounds use the sentinels in `RangeSentinels` (rounded to the `DECIMAL(20,6)` column precision).

### Assessment domain model (Spring Data **JDBC**, not JPA)

This uses **Spring Data JDBC**. Key consequences that differ from JPA:
- FKs are modelled as `AggregateReference<T, Long>` — one-way, no cascade, no lazy navigation. Extract the id with `.getId()`.
- `OrlLndscpAssmt` owns its detail rows as a `@MappedCollection` (`orl_lndscp_assmt_details`). **Fetching the `OrlLndscpAssmt` entity eagerly loads all detail rows.** When you only need the assessment's existence + landscape FK, use the `findRefById` projection (`LandscapeAssmtRef`) to avoid loading the collection. The listing service uses a summary projection for the same reason.
- **`save()` on an existing aggregate does a full delete+reinsert of its children.** Only use `save()` for fresh inserts (assessment generation does this). For updates/soft-deletes, issue targeted `JdbcTemplate` UPDATE statements (see `LandscapeAssmtCalloutService`) rather than reloading and re-saving.
- **No hand-written join SQL.** Services deliberately issue plain single-table queries (`findById`, `findByBizDt`, `findAll`, derived queries) and do all matching/merging/derivation in Java. This is an intentional, documented choice (see class-level Javadoc in the services) — follow it.

### The assessment/fact split (important)

Assessment detail rows (`orl_lndscp_assmt_details`) are **thin**: only dimensions, category, status, overlay fields, and audit columns. Row-level **computed** values (calculated NRR, rating change, control effectiveness, commentary, inherent risk) live in the separate `fact_orl` snapshot table (`CTRL_EFF_RTN` is a plain `VARCHAR` — surfaced as a raw string, not an enum), and the per-module **GRC metrics** live in the module snapshot tables (`rcsa_fact_orl`, `inc_fact_orl`, `ina_fact_orl`, `kri_fact_orl`, assembled by `GrcMetricsService` for the drill-down only). All are **matched at read time by dimension key** (`RISK_AREA`, `ORL_BU_NM_L2/L3/L4`, `LOCATION`) for a business date. The module tables are **standardized** on those same key column names plus `biz_dt`, `NET_RISK_RTNG`, and `RISK_RTNG_CHGE` (matching `fact_orl`), mapped in each entity. `GrcMetricsService` **always names all four modules** in its `grcMetrics` map, in `RCSA → INC → INA → KRI` order; a module with no matching snapshot row maps to `null` rather than being omitted, so the block's shape never varies. The business date used for each lookup is:
- current month → the assessment's own `biz_dt` (`orl_lndscp_assmt.biz_dt`)
- previous month → the previous assessment's `biz_dt` (followed via `PREV_ASSMT_NUM`)
- live → the latest `biz_dt` matching the key, resolved with a `MAX(biz_dt)` correlated subquery over the same dimension (never `ORDER BY biz_dt DESC LIMIT 1`), via named parameters only

Because MariaDB treats each NULL as distinct in a unique index, all dimension columns in `orl_lndscp_assmt_details` and `fact_orl` are `NOT NULL DEFAULT ''` — generation writes `''` (never null) for empty dimensions so the unique index actually enforces one row per dimension. Preserve this invariant.

### Generation flow

`BulkAssmtGenerationService` (`POST /landscape/assessments/generate`) is the orchestrator and is **intentionally not `@Transactional`**: it loads active+effective configs (`OrlLndscpDim.isActiveAndEffectiveOn`), groups by landscape name, and calls `LandscapeAssmtGenerationService.generateForDim(...)` per landscape — each in its own transaction — so one landscape's skip/failure never rolls back the others. Per-landscape outcomes are reported via `AssmtGenerationStatus` (`GENERATED`, `SKIPPED_AMBIGUOUS_CONFIG`, `SKIPPED_ALREADY_EXISTS`). `generateForDim` expands the `RISK_AREA` risk-area names (`riskAreas[*].riskArea`) × business units × locations into `L{lvl}`, `grp_l{lvl}`, and `loc` category rows. An assessment generated in month **M** represents the **previous** month **M-1**: `ASSEMT_PERIOD` is M-1, `biz_dt` is that month's end date — or, if no `fact_orl` row exists on the month-end, the latest `fact_orl.biz_dt` within M-1 (`FactOrlRepository.findMaxBizDtBetween`) — and `PREV_ASSMT_NUM` links to the M-2 assessment. The duplicate-period guard keys on the M-1 period.

### Cross-cutting conventions

- **Auth:** every endpoint requires the `X-EGRC-UserId` header (used as `CREATED_BY`/operator identity); missing/blank → **HTTP 401**. Most controllers check `required = false` + manual blank check and return 401 explicitly; a genuinely absent required header is turned into 401 by `GlobalExceptionHandler` (`MissingRequestHeaderException`).
- **Responses:** all endpoints return the `ApiResponse<T>` envelope. All error→HTTP mapping is centralized in `GlobalExceptionHandler` (`@RestControllerAdvice`) — throw the domain exceptions (`BadRequestException`→400, `NotFoundException`→404, `ConflictException`→409, `CsvValidationException`→400 w/ details) rather than building error responses in controllers/services.
- **Nulls are always serialized.** No response DTO carries `@JsonInclude(NON_NULL)` and there is no global `default-property-inclusion` — every field of every response, including the envelope's `data`/`errors`, is always present so clients see one fixed shape. Do not add `NON_NULL` to a response DTO.
- **No SQL built by concatenation.** Values are bound as parameters (repository `@Query` uses named parameters; `JdbcTemplate` uses `?` with args). Identifiers (table/column names) *cannot* be bound, so any statement that varies by table keeps its full SQL as a compile-time constant — see `common/util/ConfigTable`, whose enum constants own the scoring tables' queries and make an unsupported table unrepresentable. A variable-length `IN` list is bound as one named collection parameter (`IN (:configIds)`), never as generated placeholders.
- **Logging:** `RequestLoggingFilter` puts `X-EGRC-UserId` into SLF4J MDC (`username`) so it appears in every log line; `ServiceLoggingAspect` adds DEBUG entry/exit + timing around all `..service..` methods and `csv.handler.*.processAndImport`. Both **summarise `MultipartFile` args (name/size only)** — never log raw file content.
- **Enum columns:** DB `ENUM` columns map to Java enums implementing `common/enums/PersistableEnum` (constant + `dbValue`, since values like `"Med Low"`/`"Poor/Fail"` aren't identifiers). Spring Data JDBC read/write is handled by the converters registered in `common/config/JdbcConfig`; CSV boundary DTOs stay `String` and convert via `X.fromDbValue(...)`. Response DTOs stay `String` (populated via `PersistableEnum.dbValue(...)`); the exception is `NetRiskRating`, whose API fields use `getDisplayValue()` (e.g. `"Medium-Low Risk"`).
- **JSON in columns:** `orl_lndscp_dim.RISK_AREA` is a `JSON`-typed column holding a grouped array (`[{groupName, isGroup, riskAreas:[{riskArea, riskClusters}]}]`) — read/written only through `common/util/RiskAreaParser` (the single source of truth: `parseStrict` for write/validation paths, `parseQuietly` for read paths, plus `riskAreaNames`/`lookupByRiskArea`/`normalizeCompact`). Callout `LOCATIONS`/`BIZ_UNITS` (arrays) are stored as JSON strings parsed with an `ObjectMapper` that has `STRICT_DUPLICATE_DETECTION` enabled. Parse failures are logged and degraded gracefully, not thrown, in read paths. GRC metrics are **not** stored as JSON — they are read from the per-module fact tables.
- **Lombok** is used throughout (`@Builder`, `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`) — annotation processing is configured in the compiler plugin.
