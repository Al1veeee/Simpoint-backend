-- Привязка сотрудника к аккаунту Telegram для служебного бота
ALTER TABLE user_account ADD COLUMN telegram_id BIGINT UNIQUE;
