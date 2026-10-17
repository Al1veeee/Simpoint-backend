-- Цена тарифа необязательна: если не задана, продавец вводит её при продаже
ALTER TABLE tariff ALTER COLUMN monthly_price DROP NOT NULL;

-- Демо-тарифы скрываем, а не удаляем: на них могут ссылаться тестовые продажи
UPDATE tariff SET active = FALSE;

INSERT INTO tariff (operator, name, monthly_price, minutes, internet_gb, unlimited_internet) VALUES
    ('T2', 'Партнер М', NULL, NULL, NULL, FALSE),
    ('T2', 'Прозрачные границы L', NULL, NULL, NULL, FALSE),
    ('MTS', 'Легкий старт 1', NULL, NULL, NULL, FALSE),
    ('MTS', 'Легкий старт 2', NULL, NULL, NULL, FALSE),
    ('MTS', 'СУПЕР', NULL, NULL, NULL, FALSE),
    ('BEELINE', 'Эксклюзивный Хит', NULL, NULL, NULL, FALSE),
    ('BEELINE', 'Добро пожаловать. Москва', NULL, NULL, NULL, FALSE),
    ('MEGAFON', 'ТП Секретный', NULL, NULL, NULL, FALSE),
    ('YOTA', 'ТП Просто', NULL, NULL, NULL, FALSE)
ON CONFLICT (operator, name) DO UPDATE SET active = TRUE;
