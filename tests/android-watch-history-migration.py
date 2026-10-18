"""Run after assembleDebug: validate migration SQL against Room's generated schema.

Uses a temporary SQLite database, without accessing app or account data.
"""
import re
import sqlite3
import tempfile
import time
from contextlib import closing
from pathlib import Path
from datetime import datetime, timedelta

root = Path(__file__).resolve().parents[1]
local = "com/bilibili/watchlater/data/local"
source = (root / "android-app/app/src/main/java" / local / "AppDatabase.kt").read_text(encoding="utf-8")
generated = (root / "android-app/app/build/generated/ksp/debug/java" / local / "AppDatabase_Impl.java").read_text(encoding="utf-8")
sql = re.search(r'MIGRATION_3_4.*?db\.execSQL\("""(.*?)"""\)', source, re.S).group(1)
migration_4_5 = source.split("private val MIGRATION_4_5", 1)[1].split("private val MIGRATION_5_6", 1)[0]
timestamp_sql = re.findall(r'db\.execSQL\("(.*?)"\)', migration_4_5)
ddl = re.findall(r'db\.execSQL\("(CREATE (?:TABLE|INDEX).*?)"\)', generated)
watched_ddl = next(statement for statement in ddl if "`watched_videos`" in statement)
session_ddl = next(statement for statement in ddl if "`check_session`" in statement)
session_migration = re.search(r'MIGRATION_5_6.*?db\.execSQL\("(.*?)"\)', source, re.S).group(1)

build_dir = (root / "android-app/app/build").resolve()
with tempfile.TemporaryDirectory(dir=build_dir, prefix="watch-history-migration-") as folder:
    assert Path(folder).resolve().is_relative_to(build_dir)
    filename = Path(folder) / "migration.db"
    with closing(sqlite3.connect(filename)) as db:
        db.execute("PRAGMA foreign_keys = ON")
        for statement in ddl:
            if "`watched_videos`" not in statement and "`check_session`" not in statement:
                db.execute(statement)
        db.execute("INSERT INTO creators(mid, name, face, added_at) VALUES (1, 'existing', '', 100)")
        db.execute("INSERT INTO tracked_videos VALUES ('BVtracked', 'existing', 1, 'existing', 100, 100)")
        db.execute("INSERT INTO publications VALUES (1, 'BVpublished', 'BVpublished', 100)")
        db.execute(sql)
        db.execute("INSERT INTO watched_videos VALUES ('999', 'BVlegacy')")
        for statement in timestamp_sql:
            db.execute(statement)
        db.execute(session_migration)
        expected = sqlite3.connect(":memory:")
        expected.execute(watched_ddl)
        expected.execute(session_ddl)
        assert db.execute("PRAGMA table_info(check_session)").fetchall() == expected.execute("PRAGMA table_info(check_session)").fetchall()
        assert db.execute("PRAGMA table_info(watched_videos)").fetchall() == expected.execute("PRAGMA table_info(watched_videos)").fetchall()
        expected.close()
        assert db.execute("SELECT name FROM creators").fetchall() == [("existing",)]
        assert db.execute("SELECT bvid FROM tracked_videos").fetchall() == [("BVtracked",)]
        assert db.execute("SELECT bvid FROM publications").fetchall() == [("BVpublished",)]
        now = int(time.time())
        assert db.execute("SELECT view_at FROM watched_videos WHERE uid = '999'").fetchone()[0] >= now - 60
        db.executemany("INSERT OR IGNORE INTO watched_videos VALUES (?, ?, ?)",
                       [("123", f"BVold{i}", now) for i in range(600)] + [("123", "BVold0", now), ("456", "BVother", now)])
        cutoff = int((datetime.now().replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=2)).timestamp())
        db.execute("INSERT INTO watched_videos VALUES ('123', 'BVexpired', ?)", (cutoff - 1,))
        db.execute("INSERT INTO watched_videos VALUES ('123', 'BVearliestDay', ?)", (cutoff,))
        db.execute("DELETE FROM watched_videos WHERE view_at < ?", (cutoff,))
        assert db.execute("SELECT COUNT(*) FROM watched_videos WHERE bvid = 'BVexpired'").fetchone()[0] == 0
        db.commit()
    with closing(sqlite3.connect(filename)) as reopened:
        assert reopened.execute("SELECT COUNT(*) FROM watched_videos WHERE uid = '123'").fetchone()[0] == 601
        assert reopened.execute("SELECT view_at FROM watched_videos WHERE bvid = 'BVearliestDay'").fetchone()[0] == cutoff
        assert reopened.execute("SELECT bvid FROM watched_videos WHERE uid = '456'").fetchall() == [("BVother",)]

print("PASS: migrations match Room schema, preserve existing data, persist 600 watches with account isolation, and prune expired watches")
