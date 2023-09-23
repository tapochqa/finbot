CREATE TABLE `telegram`.`finbot` (
  `timestamp` BIGINT NOT NULL,
  `chat_id` BIGINT NULL,
  `message_id` BIGINT NULL,
  `recipient` TEXT NULL,
  `source` TEXT NULL,
  `amount` DOUBLE NOT NULL,
  `active` TINYINT NOT NULL,
  PRIMARY KEY (`timestamp`),
  UNIQUE INDEX `timestamp_UNIQUE` (`timestamp` ASC) VISIBLE);