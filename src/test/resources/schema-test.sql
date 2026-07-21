-- ===================================================================
-- Test schema (H2 in MySQL-compat mode).
-- All ENUM columns use VARCHAR + inline CHECK to keep H2 compatibility.
-- ===================================================================

-- Drop in FK-child-first order.
DROP TABLE IF EXISTS fact_orl;
DROP TABLE IF EXISTS rcsa_fact_orl;
DROP TABLE IF EXISTS inc_fact_orl;
DROP TABLE IF EXISTS ina_fact_orl;
DROP TABLE IF EXISTS kri_fact_orl;
DROP TABLE IF EXISTS orl_static_data_maintianance_csv_upload_audit;
DROP TABLE IF EXISTS orl_lndscp_callout_comment_hist;
DROP TABLE IF EXISTS orl_lndscp_callout;
DROP TABLE IF EXISTS orl_lndscp_assmt_details;
DROP TABLE IF EXISTS orl_risk_type_risk_area_map;
DROP TABLE IF EXISTS orl_entity_mstr;
DROP TABLE IF EXISTS orl_biz_unit;
DROP TABLE IF EXISTS orl_bu_loctn_headcount;
DROP TABLE IF EXISTS feature_score_band;
DROP TABLE IF EXISTS train_stats;
DROP TABLE IF EXISTS net_risk_band;
DROP TABLE IF EXISTS orl_lndscp_assmt;
DROP TABLE IF EXISTS orl_lndscp_dim;

-- ── orl_biz_unit ─────────────────────────────────────────────────────────────
CREATE TABLE orl_biz_unit (
    BU_NUM        INT           NOT NULL,
    BU_NM         VARCHAR(1200) NOT NULL,
    LVL_OF_HIER   INT           NOT NULL,
    ORL_BU_NM_L2  VARCHAR(1200) NULL,
    ORL_BU_NM_L3  VARCHAR(1200) NULL,
    ORL_BU_NM_L4  VARCHAR(1200) NULL,
    BU_FULL_PATH  VARCHAR(1200) NULL,
    CREATED_BY    VARCHAR(1200) NOT NULL DEFAULT '',
    CREATE_DT_TM  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_BY    VARCHAR(1200) NULL,
    UPDATE_DT_TM  TIMESTAMP     NULL,
    PRIMARY KEY (BU_NUM)
);

-- ── orl_entity_mstr ──────────────────────────────────────────────────────────
CREATE TABLE orl_entity_mstr (
    ENTITY_NUM      INT           NOT NULL,
    ENTITY_NM       VARCHAR(50)   NOT NULL,
    orl_location    VARCHAR(20)   NOT NULL,
    orl_location_ic VARCHAR(100)  NULL,
    CREATED_BY      VARCHAR(1200) NOT NULL,
    CREATED_DT_TM   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_BY      VARCHAR(1200) NULL,
    UPDATE_DT_TM    TIMESTAMP     NULL,
    PRIMARY KEY (ENTITY_NUM),
    UNIQUE (ENTITY_NM)
);

-- ── orl_risk_type_risk_area_map ──────────────────────────────────────────────
CREATE TABLE orl_risk_type_risk_area_map (
    ID               INT           NOT NULL AUTO_INCREMENT,
    RISK_AREA        VARCHAR(120)   NOT NULL,
    RISK_TYPE_L4_NUM INT           NOT NULL,
    RISK_TYPE_L4_NM  VARCHAR(120)  NOT NULL,
    CREATED_BY       VARCHAR(1200) NOT NULL,
    CREATED_DT_TM    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ID),
    UNIQUE (RISK_AREA, RISK_TYPE_L4_NUM)
);

-- ── orl_bu_loctn_headcount ────────────────────────────────────────────────────
CREATE TABLE orl_bu_loctn_headcount (
    id           INT           NOT NULL AUTO_INCREMENT,
    ORL_BU_NM_L2 VARCHAR(120)  NOT NULL,
    ORL_BU_NM_L3 VARCHAR(120)  NULL,
    ORL_BU_NM_L4 VARCHAR(120)  NULL,
    location     VARCHAR(50)   NOT NULL,
    headcount    INT           NOT NULL DEFAULT 0,
    CREATE_DT_TM TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY   VARCHAR(50)   NOT NULL DEFAULT 'SYSTEM',
    UPDATE_DT_TM TIMESTAMP     NULL,
    UPDATED_BY   VARCHAR(50)   NULL,
    PRIMARY KEY (id),
    UNIQUE (ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, location)
);

-- ── feature_score_band ────────────────────────────────────────────────────────
CREATE TABLE feature_score_band (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    config_version INT           NOT NULL,
    feature_bin    INT           NOT NULL,
    feature_name   VARCHAR(64)   NOT NULL,
    range_low      DECIMAL(20,6) NOT NULL DEFAULT 0,
    range_high     DECIMAL(20,6) NOT NULL DEFAULT 0,
    score          INT           NOT NULL,
    module         VARCHAR(10)   NOT NULL,
    CREATED_BY     VARCHAR(50)   NOT NULL DEFAULT 'SYSTEM',
    CREATE_DT_TM   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (module, config_version, feature_bin, feature_name)
);

-- ── train_stats ───────────────────────────────────────────────────────────────
CREATE TABLE train_stats (
    id             INT           NOT NULL AUTO_INCREMENT,
    config_version INT           NOT NULL,
    lvl            VARCHAR(10)   NOT NULL,
    train_mean     DECIMAL(18,6) NOT NULL,
    train_var      DECIMAL(18,6) NOT NULL,
    module         VARCHAR(10)   NOT NULL,
    CREATED_BY     VARCHAR(50)   NOT NULL,
    CREATE_DT_TM   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (config_version, lvl, module)
);

-- ── orl_lndscp_dim ───────────────────────────────────────────────────────────
CREATE TABLE orl_lndscp_dim (
    id              INT           NOT NULL AUTO_INCREMENT,
    CONFIG_ID       VARCHAR(100)  NOT NULL,
    LNDSCP_NM       VARCHAR(50)   NOT NULL,
    EFFECT_START_DT DATE          NOT NULL,
    EFFECT_END_DT   DATE          NOT NULL DEFAULT DATE '9999-12-31',
    VERSION         INT           NOT NULL,
    STATUS          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    -- RISK_AREA holds the grouped risk-area JSON document; CLOB mirrors how the other
    -- JSON columns (LOCATIONS/BIZ_UNITS/GRC_METRICS) are represented in the H2 test schema.
    RISK_AREA       CLOB          NOT NULL,
    BIZ_UNITS       VARCHAR(500)  NULL,
    BIZ_UNIT_LVL    INT           NULL,
    LOCATIONS       VARCHAR(100)  NOT NULL,
    CREATED_BY      VARCHAR(50)   NOT NULL,
    CREATE_DT_TM    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (CONFIG_ID, VERSION, EFFECT_END_DT)
);

-- ── orl_lndscp_assmt ─────────────────────────────────────────────────────────
-- PREV_ASSMT_NUM is a self-referencing FK to the previous month's assessment.
CREATE TABLE orl_lndscp_assmt (
    id              INT           NOT NULL AUTO_INCREMENT,
    LNDSCP_NUM      INT           NOT NULL,
    ASSEMT_PERIOD   VARCHAR(100)  NOT NULL DEFAULT '',
    status          VARCHAR(20)   NOT NULL DEFAULT 'Draft',
    PREV_ASSMT_NUM  INT           NULL DEFAULT NULL,
    CREATED_BY      VARCHAR(50)   NOT NULL,
    CREATE_DT_TM    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATE_DT_TM    TIMESTAMP     NULL DEFAULT NULL,
    UPDATED_BY      VARCHAR(50)   NULL,
    PRIMARY KEY (id),
    UNIQUE (LNDSCP_NUM, ASSEMT_PERIOD),
    FOREIGN KEY (PREV_ASSMT_NUM) REFERENCES orl_lndscp_assmt(id),
    FOREIGN KEY (LNDSCP_NUM) REFERENCES orl_lndscp_dim(id)
);

-- ── net_risk_band ─────────────────────────────────────────────────────────────
-- range_low/range_high are DECIMAL(20,6) (matches feature_score_band precision, holds sentinels).
CREATE TABLE net_risk_band (
    id             INT           NOT NULL AUTO_INCREMENT,
    config_version INT           NOT NULL,
    range_low      DECIMAL(20,6) NOT NULL DEFAULT 0,
    range_high     DECIMAL(20,6) NOT NULL DEFAULT 0,
    net_risk_rtng  VARCHAR(10)   NOT NULL,
    module         VARCHAR(10)   NOT NULL,
    CREATED_BY     VARCHAR(50)   NOT NULL,
    CREATE_DT_TM   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (module, config_version, net_risk_rtng)
);

-- ── orl_lndscp_callout ───────────────────────────────────────────────────────
-- LOCATIONS/BIZ_UNITS hold JSON string arrays (CLOB; H2 skips the json_valid CHECK).
CREATE TABLE orl_lndscp_callout (
    id                INT          NOT NULL AUTO_INCREMENT,
    RISK_AREA         VARCHAR(120) NOT NULL DEFAULT '',
    LOCATIONS         CLOB         NOT NULL,
    BIZ_UNITS         CLOB         NOT NULL,
    lndscp_assmt_id   INT          NOT NULL,
    comment           VARCHAR(400) NOT NULL,
    DEL_FLG           BOOLEAN      NOT NULL DEFAULT FALSE,
    CREATE_DT_TM      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    SME               VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',
    UPDATE_DT_TM      TIMESTAMP    NULL DEFAULT NULL,
    LAST_MODIFIED_SME VARCHAR(50)  NULL DEFAULT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (lndscp_assmt_id) REFERENCES orl_lndscp_assmt(id)
);

-- ── orl_lndscp_callout_comment_hist ──────────────────────────────────────────
CREATE TABLE orl_lndscp_callout_comment_hist (
    id           INT          NOT NULL AUTO_INCREMENT,
    callout_id   INT          NOT NULL,
    comment      VARCHAR(400) NOT NULL,
    SME          VARCHAR(50)  NOT NULL,
    CREATE_DT_TM TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (callout_id) REFERENCES orl_lndscp_callout(id)
);

-- ── orl_lndscp_assmt_details ─────────────────────────────────────────────────
-- Thin table: dimensions + overlay + status only. The computed values
-- (CAL_NET_RISK_RTNG, RISK_RTNG_CHGE, CTRL_EFF_RTN, COMMENTARY, GRC_METRICS) now
-- live in fact_orl and are matched at read time by dimension + business date.
CREATE TABLE orl_lndscp_assmt_details (
    id                       INT           NOT NULL AUTO_INCREMENT,
    lndscp_assmt_id          INT           NOT NULL,
    RISK_AREA                VARCHAR(50)   NOT NULL,
    ORL_BU_NM_L2             VARCHAR(120)  NOT NULL DEFAULT '',
    ORL_BU_NM_L3             VARCHAR(120)  NOT NULL DEFAULT '',
    ORL_BU_NM_L4             VARCHAR(120)  NOT NULL DEFAULT '',
    LOCATION                 VARCHAR(50)   NOT NULL DEFAULT '',
    category                 VARCHAR(20)   NOT NULL,
    REVISED_COMMENTARY       CLOB          NULL,
    OVRLY_NET_RISK_RTNG      VARCHAR(20)   NULL,
    OVRLY_JSTFKN             VARCHAR(4000) NULL,
    STATUS                   VARCHAR(30)   NOT NULL DEFAULT 'Open',
    CREATED_BY               VARCHAR(50)   NOT NULL,
    CREATE_DT_TM             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATE_DT_TM             TIMESTAMP     NULL DEFAULT NULL,
    UPDATED_BY               VARCHAR(50)   NULL,
    PRIMARY KEY (id),
    UNIQUE (lndscp_assmt_id, RISK_AREA, ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, LOCATION),
    FOREIGN KEY (lndscp_assmt_id) REFERENCES orl_lndscp_assmt(id)
);

-- ── orl_static_data_maintianance_csv_upload_audit ───────────────────────────────
CREATE TABLE orl_static_data_maintianance_csv_upload_audit (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    file_name      VARCHAR(255) NOT NULL,
    table_name     VARCHAR(100) NOT NULL,
    uploaded_by    VARCHAR(100) NOT NULL,
    uploaded_dt_tm TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_count      INT          NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    PRIMARY KEY (id)
);

-- ── fact_orl ─────────────────────────────────────────────────────────────────
-- Snapshot table with the row-level computed values, matched by dimension + biz_dt.
-- GRC metrics now live in the per-module *_fact_orl tables below.
CREATE TABLE fact_orl (
    ID                INT          NOT NULL AUTO_INCREMENT,
    biz_dt            DATE         NOT NULL,
    RISK_AREA         VARCHAR(200) NOT NULL,
    ORL_BU_NM_L2      VARCHAR(120) NOT NULL DEFAULT '',
    ORL_BU_NM_L3      VARCHAR(120) NOT NULL DEFAULT '',
    ORL_BU_NM_L4      VARCHAR(120) NOT NULL DEFAULT '',
    LOCATION          VARCHAR(50)  NOT NULL DEFAULT '',
    category          VARCHAR(20)  NOT NULL,
    INHERENT_RISK     VARCHAR(20)  NULL,
    RISK_RTNG_CHGE    VARCHAR(20)  NULL,
    CAL_NET_RISK_RTNG VARCHAR(20)  NOT NULL,
    CTRL_EFF_RTN      VARCHAR(200) NULL,
    COMMENTARY        CLOB         NULL,
    PRIMARY KEY (ID),
    UNIQUE (biz_dt, RISK_AREA, ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, LOCATION)
);

-- ── Per-module GRC-metric snapshot tables ────────────────────────────────────
-- Each shares fact_orl's dimension key (biz_dt + RISK_AREA + ORL_BU_NM_L2/L3/L4 + LOCATION).
-- These mirror the (superset) production tables; only the columns the app reads are declared.
CREATE TABLE rcsa_fact_orl (
    ID                          INT          NOT NULL AUTO_INCREMENT,
    biz_date                    DATE         NOT NULL,
    orl_risk_area               VARCHAR(200) NOT NULL,
    orl_unit_l2                 VARCHAR(120) NOT NULL DEFAULT '',
    orl_unit_l3                 VARCHAR(120) NOT NULL DEFAULT '',
    orl_unit_l4                 VARCHAR(120) NOT NULL DEFAULT '',
    orl_location                VARCHAR(50)  NOT NULL DEFAULT '',
    NRR                         VARCHAR(20)  NULL,
    RISK_RTNG_CHGE              VARCHAR(20)  NULL,
    combined_count_high_risk    INT          NULL,
    combined_count_med_high_risk INT         NULL,
    combined_count_med_low_risk INT          NULL,
    combined_count_low_risk     INT          NULL,
    rcsa_high_risk_proportion   DECIMAL(18,6) NULL,
    rcsa_med_high_proportion    DECIMAL(18,6) NULL,
    rcsa_med_low_proportion     DECIMAL(18,6) NULL,
    rcsa_low_risk_proportion    DECIMAL(18,6) NULL,
    PRIMARY KEY (ID)
);

CREATE TABLE inc_fact_orl (
    ID                                INT          NOT NULL AUTO_INCREMENT,
    biz_dt                            DATE         NOT NULL,
    RISK_AREA                         VARCHAR(200) NOT NULL,
    ORL_BU_NM_L2                      VARCHAR(120) NOT NULL DEFAULT '',
    ORL_BU_NM_L3                      VARCHAR(120) NOT NULL DEFAULT '',
    ORL_BU_NM_L4                      VARCHAR(120) NOT NULL DEFAULT '',
    LOCATION                          VARCHAR(50)  NOT NULL DEFAULT '',
    NET_RISK_RTNG                     VARCHAR(20)  NULL,
    RISK_RTNG_CHGE                    VARCHAR(20)  NULL,
    inc_is_gorc_count_l3m_mtd         INT          NULL,
    inc_is_min_reportable_count_l3m_mtd INT        NULL,
    inc_is_sinp_count_l3m_mtd         INT          NULL,
    inc_is_mi_count_l3m_mtd           INT          NULL,
    PRIMARY KEY (ID)
);

CREATE TABLE ina_fact_orl (
    ID                           INT          NOT NULL AUTO_INCREMENT,
    biz_dt                       DATE         NOT NULL,
    RISK_AREA                    VARCHAR(200) NOT NULL,
    ORL_BU_NM_L2                 VARCHAR(120) NOT NULL DEFAULT '',
    ORL_BU_NM_L3                 VARCHAR(120) NOT NULL DEFAULT '',
    ORL_BU_NM_L4                 VARCHAR(120) NOT NULL DEFAULT '',
    LOCATION                     VARCHAR(50)  NOT NULL DEFAULT '',
    NET_RISK_RTNG                VARCHAR(20)  NULL,
    RISK_RTNG_CHGE               VARCHAR(20)  NULL,
    issue_repeated_count         INT          NULL,
    issue_rating_high_count      INT          NULL,
    issue_rating_medium_count    INT          NULL,
    issue_type_regulatory_count  INT          NULL,
    issue_type_audit_count       INT          NULL,
    issue_type_others_count      INT          NULL,
    residual_risk_approved_count INT          NULL,
    issue_open_count             INT          NULL,
    issue_closed_count_l3m_mtd   INT          NULL,
    PRIMARY KEY (ID)
);

CREATE TABLE kri_fact_orl (
    ID                                          INT          NOT NULL AUTO_INCREMENT,
    biz_dt                                      DATE         NOT NULL,
    ORL_RISK_AREA                               VARCHAR(200) NOT NULL,
    ORL_BU_NM_L2                                VARCHAR(120) NOT NULL DEFAULT '',
    ORL_BU_NM_L3                                VARCHAR(120) NOT NULL DEFAULT '',
    ORL_BU_NM_L4                                VARCHAR(120) NOT NULL DEFAULT '',
    LOCATION                                    VARCHAR(50)  NOT NULL DEFAULT '',
    NET_RISK_RATING                             VARCHAR(20)  NULL,
    RISK_RTNG_CHGE                              VARCHAR(20)  NULL,
    KRI_ACTIVE_CNT                              INT          NULL,
    KRI_SUSTND_RED_3M_OR_QTRLY_RED_CNT          INT          NULL,
    KRI_SUSTND_RED_2M_CNT                       INT          NULL,
    KRI_SUSTND_RED_AMBER_4M_OR_QTRLY_AMBER_CNT  INT          NULL,
    KRI_AMBER_SUSTND_RED_AMBER_3M_CNT           INT          NULL,
    KRI_RED_CNT                                 INT          NULL,
    KRI_AMBER_CNT                               INT          NULL,
    KRI_GREEN_CNT                               INT          NULL,
    PRIMARY KEY (ID)
);
