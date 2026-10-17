CREATE TABLE user_account (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tariff (
    id                 BIGSERIAL PRIMARY KEY,
    operator           VARCHAR(16)    NOT NULL,
    name               VARCHAR(120)   NOT NULL,
    monthly_price      NUMERIC(10, 2) NOT NULL,
    minutes            INTEGER,
    internet_gb        INTEGER,
    unlimited_internet BOOLEAN        NOT NULL DEFAULT FALSE,
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_tariff_operator_name UNIQUE (operator, name)
);

CREATE TABLE sim_card (
    id          BIGSERIAL PRIMARY KEY,
    operator    VARCHAR(16) NOT NULL,
    iccid       VARCHAR(22) NOT NULL UNIQUE,
    msisdn      VARCHAR(15),
    status      VARCHAR(16) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sold_at     TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_sim_card_operator_status ON sim_card (operator, status);

CREATE TABLE customer (
    id                 BIGSERIAL PRIMARY KEY,
    full_name          VARCHAR(120) NOT NULL,
    phone              VARCHAR(20),
    preferred_language VARCHAR(8)   NOT NULL DEFAULT 'RU',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE operator_capability (
    id           BIGSERIAL PRIMARY KEY,
    operator     VARCHAR(16) NOT NULL,
    service_type VARCHAR(32) NOT NULL,
    supported    BOOLEAN     NOT NULL,
    note         VARCHAR(255),
    CONSTRAINT uq_capability UNIQUE (operator, service_type)
);

CREATE TABLE sale (
    id             BIGSERIAL PRIMARY KEY,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    employee_id    BIGINT         NOT NULL REFERENCES user_account (id),
    customer_id    BIGINT         REFERENCES customer (id),
    status         VARCHAR(16)    NOT NULL,
    payment_method VARCHAR(16)    NOT NULL,
    total          NUMERIC(12, 2) NOT NULL,
    cancelled_at   TIMESTAMPTZ,
    cancel_reason  VARCHAR(255)
);

CREATE INDEX idx_sale_created_at ON sale (created_at);

CREATE TABLE sale_item (
    id           BIGSERIAL PRIMARY KEY,
    sale_id      BIGINT         NOT NULL REFERENCES sale (id) ON DELETE CASCADE,
    service_type VARCHAR(32)    NOT NULL,
    operator     VARCHAR(16),
    sim_card_id  BIGINT         REFERENCES sim_card (id),
    tariff_id    BIGINT         REFERENCES tariff (id),
    description  VARCHAR(255),
    price        NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_sale_item_sale ON sale_item (sale_id);
