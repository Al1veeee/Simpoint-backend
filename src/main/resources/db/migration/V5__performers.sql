-- Кто фактически оформил операцию. Выбирается в боте в конце оформления продажи.
CREATE TABLE performer (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0
);

INSERT INTO performer (name, sort_order) VALUES
    ('Дени (младший)', 1),
    ('Дени (старший)', 2),
    ('Тамерлан', 3);

-- Имя хранится строкой, чтобы история не менялась при переименовании или удалении из списка
ALTER TABLE sale ADD COLUMN performer_name VARCHAR(80);
CREATE INDEX idx_sale_performer_name ON sale (performer_name);
