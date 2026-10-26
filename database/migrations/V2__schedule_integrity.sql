SET search_path TO duong_an, public;

-- Serialize roster writes with rescheduling by locking the parent session row.
CREATE FUNCTION sync_roster_time() RETURNS trigger
LANGUAGE plpgsql SET search_path = duong_an, public AS $$
DECLARE s class_session%ROWTYPE;
BEGIN
    SELECT * INTO STRICT s FROM class_session WHERE id=NEW.session_id FOR UPDATE;
    NEW.starts_at := s.starts_at;
    NEW.ends_at := s.ends_at;
    NEW.cancelled := s.status='CANCELLED';
    RETURN NEW;
END;
$$;
CREATE TRIGGER session_teacher_time BEFORE INSERT OR UPDATE ON session_teacher
    FOR EACH ROW EXECUTE FUNCTION sync_roster_time();
CREATE TRIGGER session_student_time BEFORE INSERT OR UPDATE ON session_student
    FOR EACH ROW EXECUTE FUNCTION sync_roster_time();

CREATE FUNCTION propagate_session_time() RETURNS trigger
LANGUAGE plpgsql SET search_path = duong_an, public AS $$
BEGIN
    IF (NEW.starts_at, NEW.ends_at, NEW.status) IS DISTINCT FROM
       (OLD.starts_at, OLD.ends_at, OLD.status) THEN
        UPDATE session_teacher SET starts_at=NEW.starts_at, ends_at=NEW.ends_at,
            cancelled=(NEW.status='CANCELLED') WHERE session_id=NEW.id;
        UPDATE session_student SET starts_at=NEW.starts_at, ends_at=NEW.ends_at,
            cancelled=(NEW.status='CANCELLED') WHERE session_id=NEW.id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER session_roster_time AFTER UPDATE ON class_session
    FOR EACH ROW EXECUTE FUNCTION propagate_session_time();

CREATE FUNCTION touch_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := now(); RETURN NEW; END; $$;
DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['branch','app_user','staff','guardian','student',
      'learning_class','enrollment','class_session','student_attendance','staff_checkin',
      'leave_request','invoice','notification','daycare_daily_log']
    LOOP
        EXECUTE format('CREATE TRIGGER touch_updated_at BEFORE UPDATE ON duong_an.%I FOR EACH ROW EXECUTE FUNCTION duong_an.touch_updated_at()', table_name);
    END LOOP;
END;
$$;
