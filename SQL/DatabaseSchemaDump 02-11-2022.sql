-- --------------------------------------------------------
-- Host:                         127.0.0.1
-- Server version:               10.6.8-MariaDB - mariadb.org binary distribution
-- Server OS:                    Win64
-- HeidiSQL Version:             12.0.0.6468
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT = @@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE = @@TIME_ZONE */;
/*!40103 SET TIME_ZONE = '+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS = 0 */;
/*!40101 SET @OLD_SQL_MODE = @@SQL_MODE, SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES = @@SQL_NOTES, SQL_NOTES = 0 */;


-- Dumping database structure for chat_stats
CREATE DATABASE IF NOT EXISTS `chat_stats` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;
USE `chat_stats`;

-- Dumping structure for table chat_stats.alternate_names
CREATE TABLE IF NOT EXISTS `alternate_names`
(
    `Main` varchar(50) NOT NULL,
    `Alt`  varchar(50) NOT NULL,
    PRIMARY KEY (`Main`, `Alt`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table chat_stats.blacklist
CREATE TABLE IF NOT EXISTS `blacklist`
(
    `word` varchar(512) NOT NULL,
    `type` varchar(64)  NOT NULL,
    PRIMARY KEY (`word`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table chat_stats.disabled_users
CREATE TABLE IF NOT EXISTS `disabled_users`
(
    `id`       int(11)     NOT NULL AUTO_INCREMENT,
    `time`     datetime    NOT NULL DEFAULT utc_timestamp(),
    `addedby`  varchar(50) NOT NULL DEFAULT '',
    `username` varchar(50) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 13018
  DEFAULT CHARSET = utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for view chat_stats.mentions
-- Creating temporary table to overcome VIEW dependency errors
CREATE TABLE `mentions`
(
    `id`            INT(11)      NOT NULL,
    `time`          DATETIME     NOT NULL,
    `username`      VARCHAR(45)  NOT NULL COLLATE 'utf8mb4_general_ci',
    `userid`        VARCHAR(45)  NULL COLLATE 'utf8mb4_general_ci',
    `message`       VARCHAR(550) NULL COLLATE 'utf8mb4_general_ci',
    `online_status` TINYINT(4)   NULL
) ENGINE = MyISAM;

-- Dumping structure for table chat_stats.messages
CREATE TABLE IF NOT EXISTS `messages`
(
    `id`            int(11)     NOT NULL AUTO_INCREMENT,
    `time`          datetime    NOT NULL DEFAULT utc_timestamp(),
    `username`      varchar(45) NOT NULL,
    `userid`        varchar(45)          DEFAULT NULL,
    `message`       varchar(550)         DEFAULT NULL,
    `online_status` tinyint(4)           DEFAULT NULL,
    `subscribed`    tinyint(4)           DEFAULT NULL,
    `full`          varchar(2048)        DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `id_UNIQUE` (`id`) USING BTREE,
    KEY `username` (`username`) USING BTREE,
    KEY `userid` (`userid`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 194111975
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC;

-- Data exporting was unselected.

-- Dumping structure for table chat_stats.mods
CREATE TABLE IF NOT EXISTS `mods`
(
    `name` varchar(100) NOT NULL,
    PRIMARY KEY (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table chat_stats.names_uids
CREATE TABLE IF NOT EXISTS `names_uids`
(
    `userid`   varchar(100) NOT NULL,
    `username` varchar(100) NOT NULL,
    PRIMARY KEY (`userid`, `username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for procedure chat_stats.sp_add_alt
DELIMITER //
CREATE PROCEDURE `sp_add_alt`(
    IN `a_main` VARCHAR(50),
    IN `a_alt` VARCHAR(50)
)
BEGIN
    INSERT INTO alternate_names VALUES (a_main, a_alt);
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_add_blacklist
DELIMITER //
CREATE PROCEDURE `sp_add_blacklist`(
    IN `a_word` VARCHAR(1024),
    IN `a_type` VARCHAR(64)
)
BEGIN
    INSERT INTO blacklist VALUES (a_word, a_type);
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_add_disabled
DELIMITER //
CREATE PROCEDURE `sp_add_disabled`(
    IN `a_addedby` VARCHAR(50),
    IN `a_username` VARCHAR(50)
)
BEGIN
    INSERT INTO disabled_users (addedby, username) VALUES (a_addedby, a_username);
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_add_timeout
DELIMITER //
CREATE PROCEDURE `sp_add_timeout`(
    IN `f_name` VARCHAR(50),
    IN `f_amount` INT
)
BEGIN
    INSERT INTO users
    VALUES (f_name, f_amount, DEFAULT)
    ON DUPLICATE KEY UPDATE timeout = timeout + f_amount;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_all_user_logs_mish
DELIMITER //
CREATE PROCEDURE `sp_get_all_user_logs_mish`(
    IN `a_username` VARCHAR(50)
)
BEGIN
    SELECT message FROM messages WHERE a_username = username ORDER BY id DESC LIMIT 50000;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_alts
DELIMITER //
CREATE PROCEDURE `sp_get_alts`()
BEGIN
    SELECT * FROM alternate_names;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_blacklist
DELIMITER //
CREATE PROCEDURE `sp_get_blacklist`()
BEGIN
    SELECT word, type FROM blacklist;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_context
DELIMITER //
CREATE PROCEDURE `sp_get_context`(
    IN `a_id` INT
)
BEGIN
    SELECT * FROM messages WHERE id >= a_id - 100 AND id <= a_id + 100 ORDER BY id DESC;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_disabled_users
DELIMITER //
CREATE PROCEDURE `sp_get_disabled_users`()
BEGIN
    SELECT username FROM disabled_users;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_first_message
DELIMITER //
CREATE PROCEDURE `sp_get_first_message`(
    IN `a_username` VARCHAR(50)
)
BEGIN
    SELECT * FROM messages WHERE id = (SELECT min(id) from messages where a_username = username);
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_last_id
DELIMITER //
CREATE PROCEDURE `sp_get_last_id`()
BEGIN
    SELECT MAX(id) as id FROM messages;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_last_message
DELIMITER //
CREATE PROCEDURE `sp_get_last_message`(
    IN `f_username` VARCHAR(45)
)
BEGIN
    SELECT * FROM messages WHERE id = (SELECT max(id) from messages where f_username = username);
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_message_count
DELIMITER //
CREATE PROCEDURE `sp_get_message_count`(
    IN `a_username` VARCHAR(50)
)
BEGIN
    SELECT COUNT(*) AS 'count' FROM messages WHERE username = a_username ORDER BY id DESC;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_mods
DELIMITER //
CREATE PROCEDURE `sp_get_mods`()
BEGIN
    SELECT * FROM mods;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_names
DELIMITER //
CREATE PROCEDURE `sp_get_names`(
    IN `a_username` VARCHAR(100)
)
BEGIN
    SELECT DISTINCT username
    FROM names_uids
    WHERE userid =
          (SELECT DISTINCT userid FROM names_uids WHERE username = a_username AND userid != 0);
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_top10to
DELIMITER //
CREATE PROCEDURE `sp_get_top10to`()
BEGIN
    SELECT * FROM chat_stats.users ORDER BY timeout DESC LIMIT 10;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_usernam
DELIMITER //
CREATE PROCEDURE `sp_get_usernam`(
    IN `f_username` VARCHAR(45)
)
BEGIN
    SELECT timeout FROM chat_stats.users WHERE f_username = username;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_user_logs
DELIMITER //
CREATE PROCEDURE `sp_get_user_logs`(
    IN `a_username` VARCHAR(50)
)
BEGIN
    SELECT time, username, message
    FROM messages
    WHERE a_username = username
    ORDER BY id DESC
    LIMIT 100000;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_get_user_permissions
DELIMITER //
CREATE PROCEDURE `sp_get_user_permissions`(
    IN `a_user` VARCHAR(50)
)
BEGIN
    SELECT permissions FROM users WHERE a_user = username;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_log_message_all
DELIMITER //
CREATE PROCEDURE `sp_log_message_all`(
    IN `a_id` INT,
    IN `a_time` DATETIME,
    IN `a_name` VARCHAR(50),
    IN `a_uid` VARCHAR(50),
    IN `a_message` VARCHAR(1000),
    IN `a_online` TINYINT,
    IN `a_subscribed` TINYINT,
    IN `a_full` VARCHAR(2048)
)
BEGIN
    INSERT INTO messages (id, TIME, username, userid, message, online_status, subscribed, FULL)
    VALUES (a_id, a_time, a_name, a_uid, a_message, a_online, a_subscribed, a_full);
END//
DELIMITER ;

-- Dumping structure for function chat_stats.sp_log_message_return_id
DELIMITER //
CREATE FUNCTION `sp_log_message_return_id`(`a_time` DATETIME,
                                           `a_name` VARCHAR(50),
                                           `a_uid` VARCHAR(50),
                                           `a_message` VARCHAR(1000),
                                           `a_online` TINYINT,
                                           `a_subscribed` TINYINT,
                                           `a_full` VARCHAR(2048)
) RETURNS int(11)
    DETERMINISTIC
BEGIN
    INSERT INTO messages (TIME, username, userid, message, online_status, subscribed, FULL)
    VALUES (a_time, a_name, a_uid, a_message, a_online, a_subscribed, a_full);
    RETURN LAST_INSERT_ID();
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_log_timeout
DELIMITER //
CREATE PROCEDURE `sp_log_timeout`(
    IN `a_name` VARCHAR(45),
    IN `a_uid` VARCHAR(45),
    IN `a_length` INT(11),
    IN `a_online` TINYINT(4)
)
BEGIN
    INSERT INTO timeouts (username, userid, length, online_status)
    VALUES (a_name, a_uid, a_length, a_online);
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_log_whisper
DELIMITER //
CREATE PROCEDURE `sp_log_whisper`(
    IN `a_time` DATETIME,
    IN `a_name` VARCHAR(50),
    IN `a_message` VARCHAR(1024)
)
BEGIN
    INSERT INTO whispers (TIME, username, message) VALUES (a_time, a_name, a_message);
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_remove_disabled
DELIMITER //
CREATE PROCEDURE `sp_remove_disabled`(
    IN `a_username` VARCHAR(50)
)
BEGIN
    DELETE FROM disabled_users WHERE username = a_username;
END//
DELIMITER ;

-- Dumping structure for procedure chat_stats.sp_set_user_permissions
DELIMITER //
CREATE PROCEDURE `sp_set_user_permissions`(
    IN `a_user` VARCHAR(50),
    IN `a_permissions` JSON
)
BEGIN
    UPDATE users SET permissions = a_permissions WHERE username = a_user;
END//
DELIMITER ;

-- Dumping structure for table chat_stats.timeouts
CREATE TABLE IF NOT EXISTS `timeouts`
(
    `id`            int(11)     NOT NULL AUTO_INCREMENT,
    `time`          datetime    NOT NULL DEFAULT current_timestamp(),
    `username`      varchar(45) NOT NULL,
    `userid`        varchar(45) NOT NULL,
    `length`        int(11)              DEFAULT NULL,
    `online_status` tinyint(4)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `id_UNIQUE` (`id`),
    KEY `username` (`username`),
    KEY `userid` (`userid`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 812930
  DEFAULT CHARSET = utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for table chat_stats.users
CREATE TABLE IF NOT EXISTS `users`
(
    `username`    varchar(50) NOT NULL,
    `timeout`     int(11)     NOT NULL DEFAULT 0,
    `permissions` longtext    NOT NULL DEFAULT '{}',
    PRIMARY KEY (`username`),
    UNIQUE KEY `username_UNIQUE` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for view chat_stats.users_with_permissions
-- Creating temporary table to overcome VIEW dependency errors
CREATE TABLE `users_with_permissions`
(
    `username`    VARCHAR(50) NOT NULL COLLATE 'utf8mb4_general_ci',
    `timeout`     INT(11)     NOT NULL,
    `permissions` LONGTEXT    NOT NULL COLLATE 'utf8mb4_general_ci'
) ENGINE = MyISAM;

-- Dumping structure for table chat_stats.whispers
CREATE TABLE IF NOT EXISTS `whispers`
(
    `id`       int(11)     NOT NULL AUTO_INCREMENT,
    `time`     datetime    NOT NULL DEFAULT utc_timestamp(),
    `username` varchar(50) NOT NULL,
    `message`  varchar(1024)        DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 476
  DEFAULT CHARSET = utf8mb4;

-- Data exporting was unselected.

-- Dumping structure for trigger chat_stats.messages_before_insert
SET @OLDTMP_SQL_MODE = @@SQL_MODE, SQL_MODE =
        'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION';
DELIMITER //
CREATE TRIGGER `messages_before_insert`
    AFTER INSERT
    ON `messages`
    FOR EACH ROW
BEGIN
    INSERT INTO names_uids
    VALUES (NEW.userid, NEW.username)
    ON DUPLICATE KEY UPDATE username=username;
    INSERT INTO users
    VALUES (NEW.username, DEFAULT, DEFAULT)
    ON DUPLICATE KEY UPDATE username=username;
END//
DELIMITER ;
SET SQL_MODE = @OLDTMP_SQL_MODE;

-- Dumping structure for view chat_stats.mentions
-- Removing temporary table and create final VIEW structure
DROP TABLE IF EXISTS `mentions`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `mentions` AS
select `messages`.`id`            AS `id`,
       `messages`.`time`          AS `time`,
       `messages`.`username`      AS `username`,
       `messages`.`userid`        AS `userid`,
       `messages`.`message`       AS `message`,
       `messages`.`online_status` AS `online_status`
from `messages`
where `messages`.`id` > (select max(`messages`.`id`) from `messages`) - 300000
  AND (`messages`.`message` like '%kroom%' OR username = 'kroom')
order by `messages`.`id` desc;

-- Dumping structure for view chat_stats.users_with_permissions
-- Removing temporary table and create final VIEW structure
DROP TABLE IF EXISTS `users_with_permissions`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `users_with_permissions` AS
SELECT *
FROM users
WHERE NOT permissions = '{}';

/*!40103 SET TIME_ZONE = IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE = IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS = IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT = @OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES = IFNULL(@OLD_SQL_NOTES, 1) */;
