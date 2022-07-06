CREATE TABLE tis.totalpayinfo
(
    `id` String,
    `num` String,
    `entity_id` String,
    `create_time` Int64,
    `__cc_ck_sign` Int8 DEFAULT 1
)
ENGINE = CollapsingMergeTree(__cc_ck_sign)
ORDER BY id
SETTINGS index_granularity = 8192;