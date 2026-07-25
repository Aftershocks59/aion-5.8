-- Record when a scheduled task last ran.
--
-- The handlers have always written this back after firing, but the column was
-- never created, so every write failed and was swallowed by the DAO's catch.
ALTER TABLE `tasks` ADD COLUMN `last_activation` datetime DEFAULT NULL;
