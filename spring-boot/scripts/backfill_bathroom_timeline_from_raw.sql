-- Rebuild bathroom_timeline_point from bathroom_telemetry_event.
--
-- Behavior:
-- 1) Clears existing timeline rows.
-- 2) Inserts one timeline row per raw event timestamp.
-- 3) For each inserted row, carries forward the latest known value up to that timestamp for:
--    - occupancy
--    - illuminance
--    - light.bathroom_1
--    - light.bathroom_2
--    - light.gledopto_light
--
-- This script is written for SQLite.

BEGIN TRANSACTION;

DELETE FROM bathroom_timeline_point;

INSERT INTO bathroom_timeline_point (
    ts_utc,
    occupancy,
    illuminance_lux,
    light_bathroom_1_on,
    light_bathroom_2_on,
    light_gledopto_on
)
SELECT
    e.ts_utc,

    (
        SELECT e_occ.value_bool
        FROM bathroom_telemetry_event e_occ
        WHERE e_occ.signal_type = 'OCCUPANCY_STATE'
          AND e_occ.entity_id = 'binary_sensor.bathroom_motion_sensor_occupancy'
          AND e_occ.ts_utc <= e.ts_utc
        ORDER BY e_occ.ts_utc DESC, e_occ.id DESC
        LIMIT 1
    ) AS occupancy,

    (
        SELECT e_ill.value_num
        FROM bathroom_telemetry_event e_ill
        WHERE e_ill.signal_type = 'ILLUMINANCE'
          AND e_ill.entity_id = 'sensor.bathroom_motion_sensor_illuminance'
          AND e_ill.ts_utc <= e.ts_utc
        ORDER BY e_ill.ts_utc DESC, e_ill.id DESC
        LIMIT 1
    ) AS illuminance_lux,

    (
        SELECT e_l1.value_bool
        FROM bathroom_telemetry_event e_l1
        WHERE e_l1.signal_type = 'LIGHT_STATE'
          AND e_l1.entity_id = 'light.bathroom_1'
          AND e_l1.ts_utc <= e.ts_utc
        ORDER BY e_l1.ts_utc DESC, e_l1.id DESC
        LIMIT 1
    ) AS light_bathroom_1_on,

    (
        SELECT e_l2.value_bool
        FROM bathroom_telemetry_event e_l2
        WHERE e_l2.signal_type = 'LIGHT_STATE'
          AND e_l2.entity_id = 'light.bathroom_2'
          AND e_l2.ts_utc <= e.ts_utc
        ORDER BY e_l2.ts_utc DESC, e_l2.id DESC
        LIMIT 1
    ) AS light_bathroom_2_on,

    (
        SELECT e_lg.value_bool
        FROM bathroom_telemetry_event e_lg
        WHERE e_lg.signal_type = 'LIGHT_STATE'
          AND e_lg.entity_id = 'light.gledopto_light'
          AND e_lg.ts_utc <= e.ts_utc
        ORDER BY e_lg.ts_utc DESC, e_lg.id DESC
        LIMIT 1
    ) AS light_gledopto_on

FROM bathroom_telemetry_event e
ORDER BY e.ts_utc ASC, e.id ASC;

COMMIT;
