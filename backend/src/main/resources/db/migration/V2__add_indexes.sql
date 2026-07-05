-- Every dashboard load queries transactions by user and month, and the budget
-- summary aggregates over the same range. The FK auto-index on user_id alone
-- can't serve the date-range filter.

CREATE INDEX idx_transactions_user_date ON transactions (user_id, date);
