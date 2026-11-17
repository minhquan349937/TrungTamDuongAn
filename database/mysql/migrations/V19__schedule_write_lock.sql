-- Technical mutex for schedule/attendance transactions, including an empty calendar.
-- This does not insert any example class, student or lesson.
CREATE TABLE IF NOT EXISTS schedule_write_lock (
    id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
    CONSTRAINT ck_schedule_write_lock_singleton CHECK (id=1)
) ENGINE=InnoDB;
INSERT INTO schedule_write_lock(id) VALUES(1) ON DUPLICATE KEY UPDATE id=1;
