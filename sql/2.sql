ALTER TABLE `telegram`.`finbot` 
DROP COLUMN `source`,
CHANGE COLUMN `recipient` `agent` TEXT NOT NULL ;