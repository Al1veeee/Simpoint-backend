-- Пароли демо-пользователей из V3 опубликованы в репозитории, поэтому вход с ними отключается.
-- Сами аккаунты и их привязка к Telegram остаются: бот продолжает работать у тех, кто уже вошёл.
-- Новый пароль задаётся командой /password в боте или переменной ADMIN_PASSWORD при запуске.
UPDATE user_account
SET password_hash = '!disabled'
WHERE (username = 'admin' AND password_hash = '$2b$10$zHtVR43lmcAGYkuscG.eYu/aubIhmoj04qhGaXI632bCSqZ9RgVlG')
   OR (username = 'seller' AND password_hash = '$2b$10$/xc7C68TBd7v125Zykz9R./h5x1rLYdvLWl7KVvr3GOT3b42CiVSu');
