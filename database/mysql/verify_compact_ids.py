"""Verify V15/V16 against a disposable copy of the configured MySQL database.

Requires PyMySQL and MySQL client tools in PATH. Never writes to the source DB.
Use --keep only to run application integration tests against the printed schema.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

import pymysql


ROOT = Path(__file__).resolve().parents[2]


def settings():
    return {
        k.strip(): v.strip().strip("\"'")
        for line in (ROOT / ".env").read_text(encoding="utf8").splitlines()
        if "=" in line and not line.lstrip().startswith("#")
        for k, v in [line.split("=", 1)]
    }


def execute_file(cursor, path):
    delimiter = ";"
    statement = []
    for line in path.read_text(encoding="utf8").splitlines():
        if line.strip().startswith("--") or not line.strip():
            continue
        if line.strip().startswith("DELIMITER "):
            assert not statement
            delimiter = line.strip().split()[1]
            continue
        statement.append(line)
        if line.rstrip().endswith(delimiter):
            sql = "\n".join(statement).rstrip()[:-len(delimiter)]
            try:
                cursor.execute(sql)
            except Exception:
                print("Failed SQL:", sql[:350])
                raise
            statement = []
    assert not statement


def client(name):
    found = shutil.which(name)
    if found:
        return found
    return str(Path("C:/Program Files/MySQL/MySQL Server 9.1/bin") / (name + ".exe"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()
    config = settings()
    source = config.get("MYSQL_DATABASE", "duong_an_v11")
    schema = "test_compact_ids_" + uuid.uuid4().hex[:10]
    env = os.environ.copy()
    env["MYSQL_PWD"] = config["MYSQL_ROOT_PASSWORD"]
    flags = ["--host=127.0.0.1", "--port=" + config.get("MYSQL_PORT", "3308"), "--user=root", "--default-character-set=utf8mb4"]
    connection = pymysql.connect(host="127.0.0.1", port=int(config.get("MYSQL_PORT", "3308")),
                                 user="root", password=config["MYSQL_ROOT_PASSWORD"],
                                 charset="utf8mb4", autocommit=True)
    successful = False
    try:
        dump = subprocess.run([client("mysqldump"), *flags, "--single-transaction", "--routines", "--triggers",
                               "--hex-blob", "--set-gtid-purged=OFF", "--no-tablespaces", source],
                              env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout
        with connection.cursor() as q:
            q.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4")
        subprocess.run([client("mysql"), *flags, schema], input=dump, env=env,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        with connection.cursor() as q:
            q.execute("USE `" + schema + "`")
            q.execute("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('strategies','plan_goal_strategies')")
            strategy_tables = q.fetchone()[0]
            if strategy_tables == 0:
                execute_file(q, ROOT / "database/mysql/migrations/V15__plan_goal_strategies.sql")
            else:
                assert strategy_tables == 2, "V15 is only partially installed"
            execute_file(q, ROOT / "database/mysql/migrations/V17__starter_strategies.sql")
            # Nonempty connected fixtures exercise UUIDs, short IDs, snapshots,
            # numeric publications, and an immutable published evaluation.
            q.execute("SELECT id FROM app_user WHERE role='ADMIN' LIMIT 1")
            actor = q.fetchone()[0]
            q.execute("SELECT id FROM student LIMIT 1")
            student = q.fetchone()[0]
            q.execute("SELECT id FROM goals WHERE is_active=1 ORDER BY id LIMIT 1")
            goal = q.fetchone()[0]
            plan, target, evaluation = (str(uuid.uuid4()) for _ in range(3))
            q.execute("INSERT INTO learning_plans(id,student_id,name,start_date,created_by) VALUES(%s,%s,'V16 fixture','2026-11-07',%s)", (plan, student, actor))
            q.execute("INSERT INTO plan_goals(id,plan_id,goal_id,assigned_by) VALUES(%s,%s,%s,%s)", (target, plan, goal, actor))
            q.execute("INSERT INTO plan_evaluations(id,learning_plan_id,evaluation_notes,evaluated_by,evaluated_at,sent_to_parent,sent_at) VALUES(%s,%s,'Published fixture',%s,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6))", (evaluation, plan, actor))
            q.execute("SELECT id FROM strategies")
            existing = {r[0] for r in q.fetchall()}
            fixture_strategy = next("CL" + str(n) for n in range(990000, 999999) if "CL" + str(n) not in existing)
            q.execute("INSERT INTO strategies(id,name) VALUES(%s,'Model')", (fixture_strategy,))
            q.execute("SELECT id FROM plan_goal_strategies")
            existing = {r[0] for r in q.fetchall()}
            fixture_link = next("MTCL" + str(n) for n in range(990000, 999999) if "MTCL" + str(n) not in existing)
            q.execute("INSERT INTO plan_goal_strategies(id,plan_goal_id,strategy_id,description) VALUES(%s,%s,%s,' \t\n ')", (fixture_link, target, fixture_strategy))
            q.execute("SELECT description FROM plan_goal_strategies WHERE id=%s", (fixture_link,))
            assert q.fetchone() == (None,)
            media = str(uuid.uuid4())
            q.execute("INSERT INTO website_media(id,kind,storage_key,original_name,content_type,size_bytes,public_consent,created_by) VALUES(%s,'IMAGE','v16-fixture.png','fixture.png','image/png',2,1,%s)", (media, actor))
            q.execute("INSERT INTO website_publications(content,published_by) VALUES(%s,%s)", (json.dumps({"imageUrl": "/api/public/website/media/" + media, "caption": media, "requestId": plan}), actor))
            publication = q.lastrowid
            q.execute("INSERT INTO website_publication_media(publication_id,media_id) VALUES(%s,%s)", (publication, media))
            q.execute("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")
            tables = [r[0] for r in q.fetchall()]
            counts = {}
            for table in tables:
                q.execute("SELECT COUNT(*) FROM `" + table + "`")
                counts[table] = q.fetchone()[0]
            q.execute("SELECT TRIGGER_NAME,ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()")
            triggers = dict(q.fetchall())
            execute_file(q, ROOT / "database/mysql/migrations/V16__compact_entity_ids.sql")
            for table, count in counts.items():
                if table in {"student_code_sequence", "profile_code_sequences"}:
                    continue  # Missing profile counters are repaired by V16.
                q.execute("SELECT COUNT(*) FROM `" + table + "`")
                assert q.fetchone()[0] == count, table
            q.execute("SELECT TRIGGER_NAME,ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()")
            assert dict(q.fetchall()) == triggers
            prefixes = json.loads((ROOT / "database/mysql/short_id_prefixes.json").read_text())
            for table, prefix in prefixes.items():
                q.execute("SELECT COUNT(*) FROM `" + table + "` WHERE NOT REGEXP_LIKE(id,%s,'c')", ("^" + prefix + "[0-9]{3,6}$",))
                assert q.fetchone()[0] == 0, table
            q.execute("SELECT p.id,p.request_key,g.id,s.strategy_id,s.description FROM learning_plans p JOIN plan_goals g ON g.plan_id=p.id JOIN plan_goal_strategies s ON s.plan_goal_id=g.id WHERE p.request_key=%s", (plan,))
            row = q.fetchone()
            assert row and row[0].startswith("KH") and row[1] == plan and row[2].startswith("MTKH") and row[3].startswith("CL") and row[4] is None
            q.execute("SELECT JSON_UNQUOTE(JSON_EXTRACT(goal_snapshot,'$.goal_id')),goal_id FROM plan_goals WHERE id=%s", (row[2],))
            first, second = q.fetchone()
            assert first == second
            q.execute("SELECT pm.media_id,p.content,m.storage_key FROM website_publication_media pm JOIN website_publications p ON p.id=pm.publication_id JOIN website_media m ON m.id=pm.media_id WHERE m.storage_key='v16-fixture.png'")
            media_id, content, storage_key = q.fetchone()
            assert json.loads(content)["imageUrl"] == "/api/public/website/media/" + media_id
            assert json.loads(content)["caption"] == media
            assert json.loads(content)["requestId"] == plan
            assert media_id.startswith("MD") and storage_key == "v16-fixture.png"
            q.execute("SELECT id FROM plan_evaluations WHERE evaluation_notes='Published fixture'")
            immutable_id = q.fetchone()[0]
            try:
                q.execute("UPDATE plan_evaluations SET evaluation_notes='changed' WHERE id=%s", (immutable_id,))
            except pymysql.MySQLError as error:
                assert error.args[0] == 1644
            else:
                raise AssertionError("Published report lost immutability")
        successful = True
        print("PASS V15/V16: row counts, 39 ID prefixes, 69 FK checks, snapshots, descriptions, idempotency, and business triggers")
        if args.keep:
            print("TEST_SCHEMA=" + schema)
    finally:
        if not args.keep or not successful:
            with connection.cursor() as q:
                q.execute("DROP DATABASE IF EXISTS `" + schema + "`")
            print("Disposable test schema removed; source database unchanged")
        connection.close()


if __name__ == "__main__":
    main()
