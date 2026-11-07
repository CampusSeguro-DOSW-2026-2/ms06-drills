-- Schema of MS06 - Events.
--
-- Flyway is not used: Spring runs this file on every startup (spring.sql.init.mode=always)
-- BEFORE Hibernate validates the entities (ddl-auto=validate). That is why every
-- statement is idempotent (IF NOT EXISTS / ON CONFLICT) and can be repeated safely.
-- To change an existing table, add an ALTER TABLE ... IF NOT EXISTS at the end of this
-- file; CREATE TABLE IF NOT EXISTS does not modify tables that already exist.

-- Event type catalog. The code SIMULACRO makes an event a drill.
CREATE TABLE IF NOT EXISTS event_types (
    id          uuid         PRIMARY KEY,
    code        varchar(40)  NOT NULL UNIQUE,
    name        varchar(80)  NOT NULL UNIQUE,
    active      boolean      NOT NULL,
    created_by  varchar(64)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL
);

-- Event or drill.
CREATE TABLE IF NOT EXISTS events (
    id                uuid          PRIMARY KEY,
    name              varchar(150)  NOT NULL,
    description       varchar(1000),
    status            varchar(20)   NOT NULL,
    simulacro         boolean       NOT NULL,
    scheduled_start   timestamptz,
    started_at        timestamptz,
    ended_at          timestamptz,
    start_blocked_at  timestamptz,
    created_by        varchar(64)   NOT NULL,
    version           bigint        NOT NULL,
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_status ON events (status);
CREATE INDEX IF NOT EXISTS idx_events_status_start ON events (status, scheduled_start);

-- Types of each event (many to many). The service checks the maximum of 4.
CREATE TABLE IF NOT EXISTS event_type_links (
    event_id  uuid NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    type_id   uuid NOT NULL REFERENCES event_types (id),
    PRIMARY KEY (event_id, type_id)
);

-- Scope: buildings and zones from CAM.
CREATE TABLE IF NOT EXISTS event_scope_items (
    id            uuid         PRIMARY KEY,
    event_id      uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    scope_type    varchar(20)  NOT NULL,
    scope_ref_id  varchar(64)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    UNIQUE (event_id, scope_type, scope_ref_id)
);

-- Invited roles (user types).
CREATE TABLE IF NOT EXISTS event_participants (
    id          uuid         PRIMARY KEY,
    event_id    uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    role        varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    UNIQUE (event_id, role)
);

-- Synced copy of the brigadiers from BEI (MS05).
CREATE TABLE IF NOT EXISTS event_brigadiers (
    id            uuid          PRIMARY KEY,
    event_id      uuid          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    brigadier_id  varchar(64)   NOT NULL,
    name          varchar(150),
    zone_id       varchar(64),
    status        varchar(20)   NOT NULL,
    synced_at     timestamptz   NOT NULL,
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    UNIQUE (event_id, brigadier_id)
);

-- ALE (MS03) alerts created by each event and their purpose.
CREATE TABLE IF NOT EXISTS event_alerts (
    id          uuid         PRIMARY KEY,
    event_id    uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    alert_id    varchar(64)  NOT NULL,
    purpose     varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_event_alerts_event ON event_alerts (event_id, purpose);

-- Immutable history of status changes.
CREATE TABLE IF NOT EXISTS event_state_history (
    id           uuid          PRIMARY KEY,
    event_id     uuid          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    from_status  varchar(20),
    to_status    varchar(20)   NOT NULL,
    changed_by   varchar(64)   NOT NULL,
    reason       varchar(300),
    changed_at   timestamptz   NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_event_history_event ON event_state_history (event_id, changed_at);

-- Automatic evaluation (CHI and BEI snapshot) created on finish.
CREATE TABLE IF NOT EXISTS event_evaluations (
    id                          uuid          PRIMARY KEY,
    event_id                    uuid          NOT NULL UNIQUE REFERENCES events (id) ON DELETE CASCADE,
    evaluated_at                timestamptz   NOT NULL,
    total_checkins              integer,
    checkin_participation_pct   numeric(5,2),
    total_incidents             integer,
    cases_attended              integer,
    cases_resolved              integer,
    avg_attention_seconds       bigint,
    details                     text,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL
);

-- Audit outbox for MS08 (the operation never fails because of this).
CREATE TABLE IF NOT EXISTS pending_audit_records (
    id           uuid          PRIMARY KEY,
    action       varchar(60)   NOT NULL,
    actor        varchar(64)   NOT NULL,
    event_id     uuid,
    payload      text,
    occurred_at  timestamptz   NOT NULL,
    attempts     integer       NOT NULL,
    last_error   varchar(500),
    sent_at      timestamptz,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_pending_audit_unsent ON pending_audit_records (occurred_at) WHERE sent_at IS NULL;

-- Predefined catalog types.
INSERT INTO event_types (id, code, name, active, created_by, created_at, updated_at) VALUES
    (gen_random_uuid(), 'SIMULACRO',     'Simulacro',                      true, 'system', now(), now()),
    (gen_random_uuid(), 'SISMO',         'Sismo',                          true, 'system', now(), now()),
    (gen_random_uuid(), 'INCENDIO',      'Incendio',                       true, 'system', now(), now()),
    (gen_random_uuid(), 'DISTURBIOS',    'Disturbios',                     true, 'system', now(), now()),
    (gen_random_uuid(), 'INUNDACION',    'Inundación',                     true, 'system', now(), now()),
    (gen_random_uuid(), 'FUGA_QUIMICA',  'Fuga de Sustancias Químicas',    true, 'system', now(), now()),
    (gen_random_uuid(), 'OTRO',          'Otro',                           true, 'system', now(), now())
ON CONFLICT (code) DO NOTHING;
