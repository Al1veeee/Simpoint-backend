-- Демо-данные для локального запуска. Пароли: admin / admin123, seller / seller123

INSERT INTO user_account (username, password_hash, full_name, role) VALUES
    ('admin', '$2b$10$zHtVR43lmcAGYkuscG.eYu/aubIhmoj04qhGaXI632bCSqZ9RgVlG', 'Администратор', 'ADMIN'),
    ('seller', '$2b$10$/xc7C68TBd7v125Zykz9R./h5x1rLYdvLWl7KVvr3GOT3b42CiVSu', 'Продавец', 'SELLER');

INSERT INTO tariff (operator, name, monthly_price, minutes, internet_gb, unlimited_internet) VALUES
    ('T2', 'Безлимит + 1000 минут', 500.00, 1000, NULL, TRUE),
    ('MTS', 'Базовый', 450.00, 600, 30, FALSE),
    ('BEELINE', 'Старт', 400.00, 500, 25, FALSE),
    ('MEGAFON', 'Стандарт', 550.00, 800, 40, FALSE),
    ('YOTA', 'Безлимит', 450.00, 300, NULL, TRUE);

INSERT INTO sim_card (operator, iccid, status) VALUES
    ('T2', '8970100000000000001', 'IN_STOCK'),
    ('T2', '8970100000000000002', 'IN_STOCK'),
    ('T2', '8970100000000000003', 'IN_STOCK'),
    ('MTS', '8970100000000000011', 'IN_STOCK'),
    ('MTS', '8970100000000000012', 'IN_STOCK'),
    ('BEELINE', '8970100000000000021', 'IN_STOCK'),
    ('BEELINE', '8970100000000000022', 'IN_STOCK'),
    ('MEGAFON', '8970100000000000031', 'IN_STOCK'),
    ('MEGAFON', '8970100000000000032', 'IN_STOCK'),
    ('YOTA', '8970100000000000041', 'IN_STOCK');
