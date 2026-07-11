-- ============================================================
-- V1 – Create all tables (final structure)
-- ============================================================

-- ── orl_biz_unit ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `orl_biz_unit` (
    `BU_NUM`        INT(11)       NOT NULL,
    `BU_NM`         VARCHAR(1200) NOT NULL,
    `LVL_OF_HIER`   INT(2)        NOT NULL,
    `ORL_BU_NM_L2`  VARCHAR(1200) NULL DEFAULT NULL,
    `ORL_BU_NM_L3`  VARCHAR(1200) NULL DEFAULT NULL,
    `ORL_BU_NM_L4`  VARCHAR(1200) NULL DEFAULT NULL,
    `BU_FULL_PATH`  VARCHAR(1200) NULL DEFAULT NULL,
    `CREATED_BY`    VARCHAR(1200) NOT NULL DEFAULT '',
    `CREATE_DT_TM`  DATETIME      NOT NULL DEFAULT current_timestamp(),
    `UPDATED_BY`    VARCHAR(1200) NULL DEFAULT NULL,
    `UPDATE_DT_TM`  DATETIME      NULL DEFAULT NULL ON UPDATE current_timestamp(),
    PRIMARY KEY (`BU_NUM`)
);

-- ── orl_entity_mstr ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `orl_entity_mstr` (
    `ENTITY_NUM`      INT           NOT NULL,
    `ENTITY_NM`       VARCHAR(50)   NOT NULL,
    `orl_location`    VARCHAR(20)   NOT NULL,
    `orl_location_ic` VARCHAR(100)  NULL DEFAULT NULL,
    `CREATED_BY`      VARCHAR(1200) NOT NULL,
    `CREATED_DT_TM`   DATETIME      NOT NULL DEFAULT current_timestamp(),
    `UPDATED_BY`      VARCHAR(1200) NULL DEFAULT NULL,
    `UPDATE_DT_TM`    DATETIME      NULL DEFAULT NULL ON UPDATE current_timestamp(),
    PRIMARY KEY (`ENTITY_NUM`),
    UNIQUE KEY `uq_entity_nm` (`ENTITY_NM`)
);

-- ── orl_risk_type_risk_area_map ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `orl_risk_type_risk_area_map` (
    `ID`               INT           NOT NULL AUTO_INCREMENT,
    `RISK_AREA`        VARCHAR(120)   NOT NULL,
    `RISK_TYPE_L4_NUM` INT           NOT NULL,
    `RISK_TYPE_L4_NM`  VARCHAR(120)  NOT NULL,
    `CREATED_BY`       VARCHAR(1200) NOT NULL,
    `CREATED_DT_TM`    DATETIME      NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`ID`),
    UNIQUE KEY `uq_risk_area_risk_type` (`RISK_AREA`, `RISK_TYPE_L4_NUM`)
);

-- ── orl_bu_loctn_headcount ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `orl_bu_loctn_headcount` (
    `id`           INT(11)              NOT NULL AUTO_INCREMENT,
    `ORL_BU_NM_L2` VARCHAR(120)         NOT NULL,
    `ORL_BU_NM_L3` VARCHAR(120)         NULL DEFAULT NULL,
    `ORL_BU_NM_L4` VARCHAR(120)         NULL DEFAULT NULL,
    `location`     VARCHAR(50)          NOT NULL,
    `headcount`    INT(11)              NOT NULL DEFAULT 0,
    `CREATE_DT_TM` DATETIME             NOT NULL DEFAULT current_timestamp(),
    `CREATED_BY`   VARCHAR(50)          NOT NULL DEFAULT 'SYSTEM',
    `UPDATE_DT_TM` DATETIME             NULL DEFAULT NULL ON UPDATE current_timestamp(),
    `UPDATED_BY`   VARCHAR(50)          NULL DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uq_bu_loctn` (`ORL_BU_NM_L2`, `ORL_BU_NM_L3`, `ORL_BU_NM_L4`, `location`) USING BTREE
);

-- ── feature_score_band ────────────────────────────────────────────────────────
-- config_version is now computed server-side per (feature_name, feature_bin, module) group,
-- no longer supplied by the CSV.
CREATE TABLE IF NOT EXISTS `feature_score_band` (
    `id`             BIGINT(20)                            NOT NULL AUTO_INCREMENT,
    `config_version` INT(11)                               NOT NULL,
    `feature_bin`    INT(11)                               NOT NULL,
    `feature_name`   VARCHAR(64)                           NOT NULL,
    `range_low`      DECIMAL(20,6)                         NOT NULL DEFAULT '0.000000',
    `range_high`     DECIMAL(20,6)                         NOT NULL DEFAULT '0.000000',
    `score`          INT(11)                               NOT NULL,
    `module`         ENUM('INC','INA','RCSA','KRI','TPRM') NOT NULL,
    `CREATED_BY`     VARCHAR(50)                           NOT NULL DEFAULT 'SYSTEM',
    `CREATE_DT_TM`   DATETIME                              NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_fsb_unique` (`module`, `config_version`, `feature_bin`, `feature_name`) USING BTREE
);

-- ── train_stats ───────────────────────────────────────────────────────────────
-- config_version is now computed server-side per (lvl, module) group.
CREATE TABLE IF NOT EXISTS `train_stats` (
    `id`             INT(11)                                                NOT NULL AUTO_INCREMENT,
    `config_version` INT(11)                                                NOT NULL,
    `lvl`            ENUM('L2','L3','L4','grp_l2','grp_l3','grp_l4','loc') NOT NULL,
    `train_mean`     DECIMAL(18,6)                                          NOT NULL,
    `train_var`      DECIMAL(18,6)                                          NOT NULL,
    `module`         ENUM('INC','INA','RCSA','KRI','TPRM')                  NOT NULL,
    `CREATED_BY`     VARCHAR(50)                                            NOT NULL,
    `CREATE_DT_TM`   DATETIME                                               NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_ts_unique` (`config_version`, `lvl`, `module`) USING BTREE
);

-- ── net_risk_band ─────────────────────────────────────────────────────────────
-- range_low/range_high changed from DOUBLE to DECIMAL(20,6) to match feature_score_band's
-- precision and safely hold the open-ended sentinel bounds without float rounding.
-- config_version is now computed server-side per (net_risk_rtng, module) group.
CREATE TABLE IF NOT EXISTS `net_risk_band` (
    `id`             INT(11)                                 NOT NULL AUTO_INCREMENT,
    `config_version` INT(11)                                 NOT NULL,
    `range_low`      DECIMAL(20,6)                           NOT NULL DEFAULT '0.000000',
    `range_high`     DECIMAL(20,6)                           NOT NULL DEFAULT '0.000000',
    `net_risk_rtng`  ENUM('Low','Med Low','Med High','High') NOT NULL,
    `module`         ENUM('INC','INA','RCSA','KRI','TPRM')   NOT NULL,
    `CREATED_BY`     VARCHAR(50)                             NOT NULL,
    `CREATE_DT_TM`   DATETIME                                NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_nrb_unique` (`module`, `config_version`, `net_risk_rtng`) USING BTREE
);

-- ── orl_lndscp_dim ───────────────────────────────────────────────────────────
-- RISK_AREA stores a JSON map of risk area names to lists of risk type codes,
-- e.g. {"Cyber Risk": ["OR"], "Conduct Risk": ["CR"]}
CREATE TABLE IF NOT EXISTS `orl_lndscp_dim` (
    `id`              INT(11)                      NOT NULL AUTO_INCREMENT,
    `CONFIG_ID`       VARCHAR(100)                 NOT NULL,
    `LNDSCP_NM`       VARCHAR(50)                  NOT NULL,
    `EFFECT_START_DT` DATE                         NOT NULL,
    `EFFECT_END_DT`   DATE                         NOT NULL DEFAULT '9999-12-31',
    `VERSION`         INT(11)                      NOT NULL,
    `STATUS`          ENUM('ACTIVE','DEACTIVATED') NOT NULL DEFAULT 'ACTIVE',
    `RISK_AREA`       VARCHAR(1000)                NOT NULL,
    `BIZ_UNITS`       VARCHAR(500)                 NULL DEFAULT NULL,
    `BIZ_UNIT_LVL`    INT(11)                      NULL DEFAULT NULL,
    `LOCATIONS`       VARCHAR(100)                 NOT NULL,
    `CREATED_BY`      VARCHAR(50)                  NOT NULL,
    `CREATE_DT_TM`    DATETIME                     NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_lndscp_unique` (`CONFIG_ID`, `VERSION`, `EFFECT_END_DT`) USING BTREE
);

-- ── orl_lndscp_assmt ─────────────────────────────────────────────────────────
-- PREV_ASSMT_NUM is a self-referencing FK to the previous month's assessment for the
-- same LNDSCP_NUM (null for a landscape config's first assessment).
CREATE TABLE IF NOT EXISTS `orl_lndscp_assmt` (
    `id`             INT(11)                                                   NOT NULL AUTO_INCREMENT,
    `LNDSCP_NUM`     INT(11)                                                   NOT NULL,
    `ASSEMT_PERIOD`  VARCHAR(100)                                              NOT NULL DEFAULT '',
    `status`         ENUM('Draft','Open','Locked','Partial locked','Closed')   NOT NULL DEFAULT 'Draft',
    `PREV_ASSMT_NUM` INT(11)                                                   NULL DEFAULT NULL,
    `CREATED_BY`     VARCHAR(50)                                               NOT NULL,
    `CREATE_DT_TM`   DATETIME                                                  NOT NULL DEFAULT current_timestamp(),
    `UPDATE_DT_TM`   DATETIME                                                  NULL DEFAULT NULL,
    `UPDATED_BY`     VARCHAR(50)                                               NULL DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `UQI` (`LNDSCP_NUM`, `ASSEMT_PERIOD`) USING BTREE,
    INDEX `FK_orl_lndscp_assmt_orl_lndscp_assmt` (`PREV_ASSMT_NUM`) USING BTREE,
    CONSTRAINT `FK_orl_lndscp_assmt_orl_lndscp_assmt` FOREIGN KEY (`PREV_ASSMT_NUM`) REFERENCES `orl_lndscp_assmt` (`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `FK_orl_lndscp_assmt_orl_lndscp_config` FOREIGN KEY (`LNDSCP_NUM`) REFERENCES `orl_lndscp_dim` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
);

-- ── orl_lndscp_assmt_details ─────────────────────────────────────────────────
-- RISK_AREA holds the risk area code for each detail row (e.g. "OR", "CR").
-- CAL_NET_RISK_RTNG is a plain enum value on the row itself (no longer an FK to
-- net_risk_band) — the calculated rating is now stored directly per detail row.
-- LV_* columns hold the live (latest-refresh) NRR snapshot for the row.
-- The previous month's ratings are no longer stored per row; they are derived at
-- read time from the previous assessment via orl_lndscp_assmt.PREV_ASSMT_NUM.
CREATE TABLE IF NOT EXISTS `orl_lndscp_assmt_details` (
    `id`                        INT(11)                                              NOT NULL AUTO_INCREMENT,
    `lndscp_assmt_id`           INT(11)                                              NOT NULL,
    `RISK_AREA`                 VARCHAR(50)                                          NOT NULL,
    `ORL_BU_NM_L2`              VARCHAR(120)                                         NULL DEFAULT NULL,
    `ORL_BU_NM_L3`              VARCHAR(120)                                         NULL DEFAULT NULL,
    `ORL_BU_NM_L4`              VARCHAR(120)                                         NULL DEFAULT NULL,
    `LOCATION`                  VARCHAR(50)                                          NULL DEFAULT NULL,
    `category`                  ENUM('L2','L3','L4','grp_l2','grp_l3','grp_l4','loc') NOT NULL,
    `RISK_RTNG_CHGE`            ENUM('Improved','Deteriorated','Stable','N.A')       NULL DEFAULT NULL,
    `CAL_NET_RISK_RTNG`         ENUM('Low','Med Low','Med High','High')              NULL DEFAULT NULL,
    `LV_NET_RISK_RTNG`          ENUM('Low','Med Low','Med High','High')              NULL DEFAULT NULL,
    `LV_LST_RFRSH_DT_TM`        DATETIME                                             NULL DEFAULT NULL,
    `LV_CTRL_EFF_RTN`           VARCHAR(120)                                         NULL DEFAULT NULL,
    `COMMENTARY`                LONGTEXT                                             NULL DEFAULT NULL,
    `REVISED_COMMENTARY`        LONGTEXT                                             NULL DEFAULT NULL,
    `GRC_METRICS`               LONGTEXT                                             NULL DEFAULT NULL COLLATE 'utf8mb4_bin',
    `RISK_SIGNAL`               TEXT                                                 NULL DEFAULT NULL,
    `CTRL_EFF_RTN`              VARCHAR(120)                                         NULL DEFAULT NULL,
    `OVRLY_NET_RISK_RTNG`       ENUM('Low','Med Low','Med High','High')              NULL DEFAULT NULL,
    `OVRLY_JSTFKN`              VARCHAR(4000)                                        NULL DEFAULT NULL,
    `STATUS`                    ENUM('Open','Locked','Pending unlock','Completed')   NOT NULL DEFAULT 'Open',
    `CREATED_BY`                VARCHAR(50)                                          NOT NULL,
    `CREATE_DT_TM`              DATETIME                                             NOT NULL DEFAULT current_timestamp(),
    `UPDATE_DT_TM`              DATETIME                                             NULL DEFAULT NULL,
    `UPDATED_BY`                VARCHAR(50)                                          NULL DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `Index 3` (`lndscp_assmt_id`, `RISK_AREA`, `ORL_BU_NM_L2`, `ORL_BU_NM_L3`, `ORL_BU_NM_L4`, `LOCATION`, `category`) USING BTREE,
    INDEX `FK_lndscp_assmt_details_lndscp_assmt` (`lndscp_assmt_id`) USING BTREE,
    CONSTRAINT `FK_lndscp_assmt_details_lndscp_assmt` FOREIGN KEY (`lndscp_assmt_id`) REFERENCES `orl_lndscp_assmt` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `GRC_METRICS` CHECK (json_valid(`GRC_METRICS`))
);

-- ── orl_lndscp_callout ───────────────────────────────────────────────────────
-- Folded in from the former V2 migration — no production data has been released yet,
-- so this project uses a single consolidated baseline migration.
CREATE TABLE IF NOT EXISTS `orl_lndscp_callout` (
    `id`              INT(11)      NOT NULL AUTO_INCREMENT,
    `RISK_AREA`       VARCHAR(50)  NOT NULL DEFAULT '',
    `LOCATIONS`       VARCHAR(50)  NULL DEFAULT NULL,
    `BIZ_UNITS`       VARCHAR(50)  NULL DEFAULT NULL,
    `lndscp_assmt_id` INT(11)      NOT NULL,
    `comment`         VARCHAR(400) NULL DEFAULT NULL,
    `DEL_FLG`         BIT(1)       NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `FK_orl_lndscp_callout_orl_lndscp_assmt` (`lndscp_assmt_id`) USING BTREE,
    CONSTRAINT `FK_orl_lndscp_callout_orl_lndscp_assmt`
        FOREIGN KEY (`lndscp_assmt_id`) REFERENCES `orl_lndscp_assmt` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- ── orl_static_data_maintianance_csv_upload_audit ───────────────────────────────
-- Renamed from csv_upload_audit — retains the upload history (still recorded on every
-- successful upload); only the GET /api/csv/uploads listing API was removed.
CREATE TABLE IF NOT EXISTS `orl_static_data_maintianance_csv_upload_audit` (
    `id`             BIGINT(20)   NOT NULL AUTO_INCREMENT,
    `file_name`      VARCHAR(255) NOT NULL,
    `table_name`     VARCHAR(100) NOT NULL,
    `uploaded_by`    VARCHAR(100) NOT NULL,
    `uploaded_dt_tm` DATETIME     NOT NULL DEFAULT current_timestamp(),
    `row_count`      INT(11)      NOT NULL,
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_csv_upload_audit_uploaded_dt_tm` (`uploaded_dt_tm`) USING BTREE
);

-- ============================================================
-- Module fact tables — source data for assessment generation.
-- Each row is keyed (for matching) on the shared dimension columns
-- (biz_dt, RISK_AREA, ORL_BU_NM_L2/L3/L4, LOCATION, category); the
-- module-specific integer columns are the raw metric counts.
-- ============================================================

-- ── inc_fact_orl ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `inc_fact_orl` (
    `id`                                 INT(11)                                  NOT NULL AUTO_INCREMENT,
    `inc_is_sinp_count_l3m_mtd`          INT(11)                                  NULL DEFAULT NULL,
    `inc_is_mi_count_l3m_mtd`            INT(11)                                  NULL DEFAULT NULL,
    `inc_is_gorc_count_l3m_mtd`          INT(11)                                  NULL DEFAULT NULL,
    `inc_is_min_reportable_count_l3m_mtd` INT(11)                                 NULL DEFAULT NULL,
    `inc_time_to_detect_sum_l11m_mtd`    INT(11)                                  NULL DEFAULT NULL,
    `inc_count_l11m_mtd`                 INT(11)                                  NULL DEFAULT NULL,
    `biz_dt`                             DATE                                     NOT NULL,
    `RISK_AREA`                          VARCHAR(200)                             NOT NULL,
    `ORL_BU_NM_L2`                       VARCHAR(120)                             NULL DEFAULT NULL,
    `ORL_BU_NM_L3`                       VARCHAR(120)                             NULL DEFAULT NULL,
    `ORL_BU_NM_L4`                       VARCHAR(120)                             NULL DEFAULT NULL,
    `LOCATION`                           VARCHAR(50)                              NULL DEFAULT NULL,
    `category`                           ENUM('L2','L3','L4','grp_l2','grp_l3','grp_l4','loc') NOT NULL,
    `NET_RISK_RTNG`                      ENUM('Low','Med Low','Med High','High')  NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_inc_fact_orl_biz_dt` (`biz_dt`) USING BTREE
);

-- ── ina_fact_orl ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ina_fact_orl` (
    `id`                        INT(11)                                  NOT NULL AUTO_INCREMENT,
    `issue_rating_high_count`   INT(11)                                  NULL DEFAULT NULL,
    `issue_rating_medium_count` INT(11)                                  NULL DEFAULT NULL,
    `issue_type_regulatory_count` INT(11)                                NULL DEFAULT NULL,
    `issue_type_audit_count`    INT(11)                                  NULL DEFAULT NULL,
    `issue_type_others_count`   INT(11)                                  NULL DEFAULT NULL,
    `issue_open_count`          INT(11)                                  NULL DEFAULT NULL,
    `issue_closed_count_l3m_mtd` INT(11)                                 NULL DEFAULT NULL,
    `issue_repeated_count`      INT(11)                                  NULL DEFAULT NULL,
    `biz_dt`                    DATE                                     NOT NULL,
    `RISK_AREA`                 VARCHAR(200)                             NOT NULL,
    `ORL_BU_NM_L2`              VARCHAR(120)                             NULL DEFAULT NULL,
    `ORL_BU_NM_L3`              VARCHAR(120)                             NULL DEFAULT NULL,
    `ORL_BU_NM_L4`              VARCHAR(120)                             NULL DEFAULT NULL,
    `LOCATION`                  VARCHAR(50)                              NULL DEFAULT NULL,
    `category`                  ENUM('L2','L3','L4','grp_l2','grp_l3','grp_l4','loc') NOT NULL,
    `NET_RISK_RTNG`             ENUM('Low','Med Low','Med High','High')  NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_ina_fact_orl_biz_dt` (`biz_dt`) USING BTREE
);

-- ── kri_fact_orl ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `kri_fact_orl` (
    `id`                                                  INT(11)                 NOT NULL AUTO_INCREMENT,
    `kri_sustained_red_3m_or_quarterly_red_count`         INT(11)                 NULL DEFAULT NULL,
    `kri_sustained_red_2m_count`                          INT(11)                 NULL DEFAULT NULL,
    `kri_sustained_red_amber_4m_or_quarterly_amber_count` INT(11)                 NULL DEFAULT NULL,
    `kri_amber_sustained_red_amber_3m_count`              INT(11)                 NULL DEFAULT NULL,
    `kri_red_count`                                       INT(11)                 NULL DEFAULT NULL,
    `kri_amber_count`                                     INT(11)                 NULL DEFAULT NULL,
    `kri_green_count`                                     INT(11)                 NULL DEFAULT NULL,
    `biz_dt`                                              DATE                    NOT NULL,
    `RISK_AREA`                                           VARCHAR(200)            NOT NULL,
    `ORL_BU_NM_L2`                                        VARCHAR(120)            NULL DEFAULT NULL,
    `ORL_BU_NM_L3`                                        VARCHAR(120)            NULL DEFAULT NULL,
    `ORL_BU_NM_L4`                                        VARCHAR(120)            NULL DEFAULT NULL,
    `LOCATION`                                            VARCHAR(50)             NULL DEFAULT NULL,
    `category`                                            ENUM('L2','L3','L4','grp_l2','grp_l3','grp_l4','loc') NOT NULL,
    `NET_RISK_RTNG`                                       ENUM('Low','Med Low','Med High','High') NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_kri_fact_orl_biz_dt` (`biz_dt`) USING BTREE
);

-- ── rcsa_fact_orl ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `rcsa_fact_orl` (
    `id`                          INT(11)                                  NOT NULL AUTO_INCREMENT,
    `rcsa_high_risk_proportion`   INT(11)                                  NULL DEFAULT NULL,
    `rcsa_medhigh_risk_proportion` INT(11)                                 NULL DEFAULT NULL,
    `rcsa_medlow_risk_proportion` INT(11)                                  NULL DEFAULT NULL,
    `rcsa_low_risk_proportion`    INT(11)                                  NULL DEFAULT NULL,
    `biz_dt`                      DATE                                     NOT NULL,
    `RISK_AREA`                   VARCHAR(200)                             NOT NULL,
    `ORL_BU_NM_L2`                VARCHAR(120)                             NULL DEFAULT NULL,
    `ORL_BU_NM_L3`                VARCHAR(120)                             NULL DEFAULT NULL,
    `ORL_BU_NM_L4`                VARCHAR(120)                             NULL DEFAULT NULL,
    `LOCATION`                    VARCHAR(50)                              NULL DEFAULT NULL,
    `category`                    ENUM('L2','L3','L4','grp_l2','grp_l3','grp_l4','loc') NOT NULL,
    `NET_RISK_RTNG`               ENUM('Low','Med Low','Med High','High')  NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_rcsa_fact_orl_biz_dt` (`biz_dt`) USING BTREE
);
