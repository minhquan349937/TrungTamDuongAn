-- Library activities may be created before they are linked to any catalog goal.
ALTER TABLE suggested_activities MODIFY COLUMN goal_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
