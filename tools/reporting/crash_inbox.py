"""Private Fight Night crash inbox. Accept a fixed diagnostic schema over HTTPS and group private reports."""
import argparse
import hmac
import secrets
import json
import os
import re
import sqlite3
import ssl
import threading
from socketserver import ThreadingMixIn
import time
import uuid
from contextlib import closing
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

FIELDS = {"id", "version", "component", "exit", "pc", "event", "round", "height", "renderer", "android"}
EVENTS = {"STARTUP", "MENU", "BOUT", "DOWN", "GETUP", "COUNT", "KO", "TKO", "HAYMAKER", "STAGGER", "WON", "LOST", "FINAL", "LAUNCHER"}
DB = Path("/var/lib/fightnight-crashes/reports.sqlite3")
IMPORT_FIELDS = {"id", "version", "android", "reason", "stage", "format", "bytes", "title", "region", "revision", "sha256"}
IMPORT_FORMATS = {"unknown", "iso", "xiso", "img", "zip", "7z", "rar", "chd", "cci", "cso", "ziso", "zar", "split", "xbe", "xex"}
CONTEXT_FIELDS = {"manufacturer", "model", "abi", "milestone", "elapsed_ms", "last_frame", "call_target", "msaa", "anisotropy", "fps_limit"}
MILESTONES = {"launch", "linked", "kernel", "guest", "frame", "intro", "menu", "bout"}
# Anonymous player count (DeviceCheckin.java): a random install ID, never an IP address or device identifier.
CHECKIN_FIELDS = {"id", "version", "version_code", "android", "device", "displays", "playing"}
DEVICE_TYPES = {"thor", "duo", "rp6", "odin", "phone", "tablet"}
CHECKIN_KEEP_DAYS = 180
CHECKIN_MAX_DEVICES = 500000
NOW_SECONDS = 15*60  # the app checks in every 10 minutes while open

# Each optional field has a defined type and range. Partial imports have only the observations reached so far.
def log_tail_ok(value):
    """Scrubbed game log tail: printable ASCII lines, newline separated, bounded like DiagnosticTail.java."""
    return isinstance(value, str) and 0 < len(value) <= 6000 and all(c == "\n" or " " <= c <= "~" for c in value)


def validate_details(details, importing, report, extended=False, v4=False):
    if not isinstance(details, dict) or set(details) != {"device", "failure", "rom"}:
        raise ValueError("detail sections")
    label = r"[ -~]{1,64}"
    hex8 = r"[0-9a-f]{8}"
    hash256 = r"[0-9a-f]{64}"
    uint = (0, 2**32-1)
    size = (0, 2**53-1)
    device = dict.fromkeys(("manufacturer","model","android_release","hardware","preferred_abi","soc_vendor","soc_model"), label)
    device.update(cpu_cores=(1,65536), total_ram_mib=uint, available_ram_mib=uint, free_storage_mib=size,
                  low_memory=bool, power_save=bool, thermal_status=(0,6), display_width=(1,65536),
                  display_height=(1,65536), display_millihz=(1,10000000), vulkan_api_version=uint)
    if extended:
        device.update(host_page_bytes=(1024,65536), presentation_displays=(0,255))
    if v4:
        device.update(app_version_code=(1, 2**31-1), firmware_build=label, security_patch=r"[0-9]{4}-[0-9]{2}-[0-9]{2}",
                      display_count=(0,64), lower_display=r"[0-9]{1,5}x[0-9]{1,5}@[0-9]{1,7}",
                      controllers=r"none|[0-9a-f]{4}:[0-9a-f]{4}(?:,[0-9a-f]{4}:[0-9a-f]{4}){0,5}")
    rom = dict.fromkeys(("selected_bytes","image_bytes","partition_offset","payload_bytes","source_xbe_bytes","installed_xbe_bytes"),size)
    rom.update(origin={"import","installed_only"}, container={"raw","zip","7z"}, layout={"xiso","partitioned"}, files=(0,25000),
               title=hex8, region=hex8, media_mask=hex8, disc_number=uint, revision=uint,
               source_xbe_sha256=hash256, installed_xbe_sha256=hash256, disc_files_sha256=hash256,
               image_sha1=r"[0-9a-f]{40}", selected_sha1=r"[0-9a-f]{40}",
               xbe_selection={"exact","aligned","certificate_restored","backup"})
    failure = dict(build_kit_sha256=hash256)
    if not importing:
        if report["component"] == "game":
            failure.update(address_kind={"unknown","guest_stack_word","guest_instruction","native_module_offset"},
                           signal_from_exit=(1,64), active_renderer={"vulkan","software"},
                           kernel_imports=r"[0-9]{1,6}/[0-9]{1,6}", round_seconds=uint,
                           audio_gaps=size, audio_drops=size, audio_xruns=size)
            failure.update({m+"_ms": size for m in MILESTONES})
            if extended:
                failure.update(fault_location={"outside_game_module"}, native_return_offset=hex8,
                               fault_module={"game","shader","runner"}, module_pc=re.compile(r"[0-9a-f]{16}"))
            if v4:
                failure.update(renderer_mode={"vulkan","software","general","shared"}, pipelined_frames=bool, widescreen=bool,
                               in_bout=bool, sleeping=bool, gpu_device_lost=bool, gpu_init_failed=bool,
                               pace_work_us=uint, pace_gpu_wait_us=uint,
                               soft_targets=r"format=[0-9]{1,9} front=[0-9]{1,9} conflict=[0-9]{1,9} depth=[0-9]{1,9}",
                               gpu_name=label, gpu_driver=r"[0-9.]{1,24}", gpu_vendor_id=r"[0-9a-f]{1,8}",
                               log_tail=log_tail_ok, log_lines=(1,40))
        else:
            failure.update(android_exit_reason=(0,255), importance=(0,10000), process_pss_kib=size, process_rss_kib=size)
    for group, spec in (("device",device),("rom",rom),("failure",failure)):
        data = details[group]
        if not isinstance(data, dict) or data.keys() - spec.keys():
            raise ValueError(group)
        for key, v in data.items():
            rule = spec[key]
            if rule is bool:
                valid = type(v) is bool
            elif isinstance(rule, tuple):
                valid = type(v) is int and rule[0] <= v <= rule[1]
            elif isinstance(rule, set):
                valid = isinstance(v, str) and v in rule
            elif callable(rule) and not isinstance(rule, (str, re.Pattern)):
                valid = bool(rule(v))
            else:
                valid = isinstance(v, str) and re.fullmatch(rule, v)
            if not valid:
                raise ValueError(key)


def validate_context(data, report):
    if not isinstance(data, dict) or set(data) != CONTEXT_FIELDS:
        raise ValueError("context")
    for field in ("manufacturer", "model", "abi"):
        s = data[field]
        if not isinstance(s, str) or not 1 <= len(s) <= 64 or not s.isprintable():
            raise ValueError(field)
    game = report["component"] == "game"
    if game and data["milestone"] not in MILESTONES:
        raise ValueError("milestone")
    if not game and any(data[k] is not None for k in CONTEXT_FIELDS - {"manufacturer","model","abi"}):
        raise ValueError("launcher context")
    if data["call_target"] is not None and (report["exit"] not in (40, 41) or not re.fullmatch("[0-9a-f]{8}", data["call_target"])):
        raise ValueError("call_target")
    for k in ("elapsed_ms","last_frame"):
        n = data[k]
        if n is not None and (type(n) is not int or not 0 <= n <= 2**53-1):
            raise ValueError(k)
    for key, options in (("msaa", (1,2)), ("anisotropy", (1,2,4)), ("fps_limit", (30,45,60,120))):
        if data[key] is not None and (type(data[key]) is not int or data[key] not in options):
            raise ValueError(key)


def validate(report, importing=False):
    if not isinstance(report, dict) or set(report) != (IMPORT_FIELDS if importing else FIELDS):
        raise ValueError("schema")
    if str(uuid.UUID(report["id"], version=4)) != report["id"]:
        raise ValueError("id")
    if not re.fullmatch(r"[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}(?:-(?:alpha|beta|rc)\.[0-9]{1,5}(?:\.[0-9]{1,3})?)?", report["version"]):
        raise ValueError("version")
    if importing:
        if report["stage"] not in {"open", "copy", "unpack", "verify", "extract", "prepare"} or report["format"] not in IMPORT_FORMATS:
            raise ValueError("stage or format")
        for key, low, high in (("android", 30, 100), ("bytes", -1, 2**63-1), ("reason", -120, -101)):
            if type(report[key]) is not int or not low <= report[key] <= high:
                raise ValueError(key)
        for key, size in (("title", 8), ("region", 8), ("sha256", 64)):
            if report[key] is not None and not re.fullmatch("[0-9a-f]{%d}" % size, report[key]):
                raise ValueError(key)
        rev = report["revision"]
        if rev is not None and (type(rev) is not int or not 0 <= rev <= 0xffffffff):
            raise ValueError("revision")
        return
    if report["component"] not in ("game", "launcher") or report["event"] not in EVENTS:
        raise ValueError("event")
    if not re.fullmatch(r"(?:unknown|[0-9a-f]{8})", report["pc"]):
        raise ValueError("pc")
    if report["renderer"] not in ("vulkan", "software", "none"):
        raise ValueError("renderer")
    for key, lo, hi in (("exit", -255, 255), ("round", 0, 99), ("height", 0, 4320), ("android", 30, 100)):
        if type(report[key]) is not int or not lo <= report[key] <= hi:
            raise ValueError(key)


def validate_checkin(report):
    if not isinstance(report, dict) or set(report) != CHECKIN_FIELDS:
        raise ValueError("schema")
    if not isinstance(report["id"], str) or str(uuid.UUID(report["id"], version=4)) != report["id"]:
        raise ValueError("id")
    if not isinstance(report["version"], str) or not re.fullmatch(
            r"[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}(?:-(?:alpha|beta|rc)\.[0-9]{1,5}(?:\.[0-9]{1,3})?)?", report["version"]):
        raise ValueError("version")
    for key, low, high in (("version_code", 1, 2**31-1), ("android", 30, 100), ("displays", 1, 16)):
        if type(report[key]) is not int or not low <= report[key] <= high:
            raise ValueError(key)
    if report["device"] not in DEVICE_TYPES:
        raise ValueError("device")
    if type(report["playing"]) is not bool:
        raise ValueError("playing")


def checkin(path, report, now=None):
    validate_checkin(report)
    now = int(time.time()) if now is None else now
    playing = now if report["playing"] else None
    with closing(sqlite3.connect(path, timeout=3)) as db, db:
        db.execute("BEGIN IMMEDIATE")
        db.execute("DELETE FROM devices WHERE last_seen < ?", (now - CHECKIN_KEEP_DAYS*86400,))
        known = db.execute("SELECT 1 FROM devices WHERE id=?", (report["id"],)).fetchone()
        if not known and db.execute("SELECT COUNT(*) FROM devices").fetchone()[0] >= CHECKIN_MAX_DEVICES:
            raise OverflowError("device table full")
        db.execute("""INSERT INTO devices (id,first_seen,last_seen,last_playing,version,version_code,android,device,displays)
            VALUES (:id,:now,:now,:playing,:version,:version_code,:android,:device,:displays)
            ON CONFLICT(id) DO UPDATE SET last_seen=:now, last_playing=coalesce(:playing,last_playing),
              version=:version, version_code=:version_code, android=:android, device=:device, displays=:displays""",
                   dict(report, now=now, playing=playing))


def device_rows(path, now=None):
    """Unique installs: ever (kept 180 days), active in the last 30/7/1 days, open now, and playing now."""
    now = int(time.time()) if now is None else now
    windows = dict(d30=now-30*86400, d7=now-7*86400, d1=now-86400, now=now-NOW_SECONDS)
    counts = """count(*) AS ever, sum(last_seen>=:d30) AS active_30d, sum(last_seen>=:d7) AS active_7d,
        sum(last_seen>=:d1) AS active_24h, sum(last_seen>=:now) AS open_now,
        sum(coalesce(last_playing,0)>=:now) AS playing_now, sum(first_seen>=:d7) AS new_7d"""
    with closing(sqlite3.connect(path.as_uri()+"?mode=ro", uri=True)) as db:
        db.row_factory = sqlite3.Row
        rows = [dict(row) for row in db.execute(f"SELECT 'all' AS grouping, 'all' AS name, {counts} FROM devices", windows)]
        for column in ("version", "device"):
            rows += [dict(row) for row in db.execute(
                f"SELECT '{column}' AS grouping, {column} AS name, {counts} FROM devices WHERE last_seen>=:d30 "
                f"GROUP BY {column} ORDER BY active_30d DESC, name", windows)]
        return rows


def initialize(db):
    db.execute("""CREATE TABLE IF NOT EXISTS reports (
        id TEXT PRIMARY KEY, received INTEGER NOT NULL, version TEXT, component TEXT,
        exit INTEGER, pc TEXT, event TEXT, round INTEGER, height INTEGER, renderer TEXT, android INTEGER)""")
    db.execute("CREATE INDEX IF NOT EXISTS reports_received ON reports(received)")
    db.execute("""CREATE TABLE IF NOT EXISTS disc_imports (
        id TEXT PRIMARY KEY, received INTEGER NOT NULL, version TEXT, android INTEGER, reason INTEGER,
        stage TEXT, format TEXT, bytes INTEGER, title TEXT, region TEXT, revision INTEGER, sha256 TEXT)""")
    db.execute("CREATE INDEX IF NOT EXISTS disc_imports_received ON disc_imports(received)")
    db.execute("""CREATE TABLE IF NOT EXISTS crash_context (
        id TEXT PRIMARY KEY REFERENCES reports(id) ON DELETE CASCADE, context TEXT NOT NULL)""")
    for child, parent in (("crash_details","reports"),("import_details","disc_imports")):
        db.execute(f"""CREATE TABLE IF NOT EXISTS {child} (
            id TEXT PRIMARY KEY REFERENCES {parent}(id) ON DELETE CASCADE, details TEXT NOT NULL)""")
    db.execute("""CREATE TABLE IF NOT EXISTS devices (
        id TEXT PRIMARY KEY, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, last_playing INTEGER,
        version TEXT, version_code INTEGER, android INTEGER, device TEXT, displays INTEGER)""")
    db.execute("CREATE INDEX IF NOT EXISTS devices_last_seen ON devices(last_seen)")


def accept(path, report, importing=False, detailed=False, rich=False, extended=False, v4=False):
    context = None
    if rich:
        if not isinstance(report, dict) or "details" not in report:
            raise ValueError("details")
        report = report.copy()
        details = report.pop("details")
    if detailed:
        if not isinstance(report, dict) or "context" not in report:
            raise ValueError("context")
        report = report.copy()
        context = report.pop("context")
    validate(report, importing)
    if detailed:
        validate_context(context, report)
    if rich:
        validate_details(details, importing, report, extended, v4)
    table = "disc_imports" if importing else "reports"
    now = int(time.time())
    with closing(sqlite3.connect(path, timeout=3)) as db, db:
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("BEGIN IMMEDIATE")
        db.execute("DELETE FROM reports WHERE received < ?", (now - 90*86400,))
        db.execute("DELETE FROM disc_imports WHERE received < ?", (now - 90*86400,))
        exists = db.execute(f"SELECT 1 FROM {table} WHERE id=?", (report["id"],)).fetchone()
        if exists and not (detailed or rich):
            return
        if not exists and db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] >= 50000:
            raise OverflowError("inbox full")
        if not exists:
            fields=sorted(report)
            db.execute(f"INSERT INTO {table} (received,{','.join(fields)}) VALUES (:received,{','.join(':'+k for k in fields)})",
                       dict(report, received=now))
        else:
            match=" AND ".join(f"{key} IS :{key}" for key in sorted(report))
            if not db.execute(f"SELECT 1 FROM {table} WHERE {match}",report).fetchone():
                raise ValueError("different report for existing id")
        if detailed:
            store_section(db,"crash_context","context",report["id"],context)
        if rich:
            store_section(db,"import_details" if importing else "crash_details","details",report["id"],details)


def store_section(db,table,column,receipt,data):
    row=db.execute(f"SELECT {column} FROM {table} WHERE id=?", (receipt,)).fetchone()
    if row is None:
        db.execute(f"INSERT INTO {table} VALUES (?,?)",(receipt,json.dumps(data)))
    else:
        prior = json.loads(row[0])
        if prior == data:
            return
        if column != "details":
            raise ValueError("different context for existing receipt")
        # accept() validated the allowlist and holds BEGIN IMMEDIATE.
        # Legacy retries may omit extended fields. Preserve the richest receipt.
        merged = {section: dict(fields) for section, fields in prior.items()}
        for section, fields in data.items():
            for key, value in fields.items():
                if key in merged[section] and merged[section][key] != value:
                    raise ValueError("different details for existing receipt")
                merged[section][key] = value
        if merged != prior:
            db.execute(f"UPDATE {table} SET {column}=? WHERE id=?", (json.dumps(merged), receipt))


class Inbox(BaseHTTPRequestHandler):
    def log_message(self, *unused):
        pass

    def respond(self, status):
        self.send_response(status)
        self.send_header("X-FightNight-Inbox", "1")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        if self.path in ("/v1/health","/v2/health"):
            return self.respond(200)
        route = urlsplit(self.path)
        if route.path not in ("/v1/digest", "/v1/import-digest", "/v1/crashes", "/v2/imports", "/v1/devices"):
            return self.respond(404)
        expected = "Bearer " + self.server.reader
        supplied = self.headers.get("Authorization", "")
        if not hmac.compare_digest(supplied.encode("utf8"), expected.encode("ascii")):
            return self.respond(401)
        if route.path == "/v1/devices":
            rows = device_rows(self.server.database.resolve())
        elif route.path in ("/v1/crashes","/v2/imports"):
            try:
                filters = parse_qs(route.query, strict_parsing=True, max_num_fields=2)
                if set(filters)-{"version","exit"} or any(len(v)!=1 for v in filters.values()):
                    raise ValueError("filter")
                version = filters.get("version", [None])[0]
                code = int(filters["exit"][0]) if "exit" in filters else None
                if code is not None and not -255 <= code <= 255:
                    raise ValueError("exit")
                rows = (import_rows if route.path=="/v2/imports" else crash_rows)(self.server.database.resolve(), version, code)
            except ValueError:
                return self.respond(400)
        else:
            rows = digest(self.server.database.resolve(), 14, route.path == "/v1/import-digest")
        body = json.dumps(rows).encode("utf8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path == "/v1/checkin":
            return self.checkin()
        if self.path not in ("/v1/reports", "/v1/imports", "/v1/crashes", "/v2/imports", "/v2/crashes", "/v3/imports", "/v3/crashes",
                             "/v4/imports", "/v4/crashes"):
            return self.respond(404)
        if self.headers.get_content_type() != "application/json":
            return self.respond(415)
        if hasattr(self.server, "allow_report") and not self.server.allow_report(self.client_address[0]):
            return self.respond(429)
        try:
            size = int(self.headers.get("Content-Length", "0"))
            v4=self.path.startswith("/v4/")
            extended=v4 or self.path.startswith("/v3/")
            rich=extended or self.path.startswith("/v2/")
            # v4 carries a bounded log tail (6,000 characters), so it gets a larger, still fixed, body limit.
            if not 0 < size <= (16384 if v4 else 8192 if rich else 4096 if self.path == "/v1/crashes" else 1024):
                return self.respond(413)
            accept(self.server.database, json.loads(self.rfile.read(size)), self.path.endswith("/imports"), self.path.endswith("/crashes"), rich, extended, v4)
        except (ValueError, KeyError, TypeError, AttributeError, RecursionError):
            return self.respond(400)
        except (sqlite3.Error, OverflowError):
            return self.respond(503)
        self.respond(201)

    def checkin(self):
        if self.headers.get_content_type() != "application/json":
            return self.respond(415)
        # Check-ins have their own, higher budget so many players behind one carrier address are still counted.
        if hasattr(self.server, "allow_report") and not self.server.allow_report("checkin:" + self.client_address[0], 120):
            return self.respond(429)
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 512:
                return self.respond(413)
            checkin(self.server.database, json.loads(self.rfile.read(size)))
        except (ValueError, KeyError, TypeError, AttributeError, RecursionError):
            return self.respond(400)
        except (sqlite3.Error, OverflowError):
            return self.respond(503)
        self.respond(204)


class SecureInbox(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, address, tls):
        self.tls = tls
        self.slots = threading.BoundedSemaphore(32)
        self.limits = {}
        self.rate_lock = threading.Lock()
        super().__init__(address, Inbox)

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            request.close()
            return
        try:
            super().process_request(request, address)
        except BaseException:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            request.settimeout(6)
            with self.tls.wrap_socket(request, server_side=True) as conn:
                self.finish_request(conn, address)
        except (ConnectionError, OSError):
            pass
        finally:
            request.close()
            self.slots.release()

    def allow_report(self, address, limit=12):
        # Short-lived burst limit. No IP address is logged or written to disk.
        now = int(time.monotonic()) // 60
        with self.rate_lock:
            if len(self.limits) >= 2048:
                self.limits = {ip: v for ip, v in self.limits.items() if v[0] == now}
                if len(self.limits) >= 2048 and address not in self.limits:
                    return False
            minute, count = self.limits.get(address, (now, 0))
            count = count + 1 if minute == now else 1
            self.limits[address] = now, count
            return count <= limit


def crash_rows(path, version=None, code=None):
    with closing(sqlite3.connect(path.as_uri()+"?mode=ro", uri=True)) as db:
        db.row_factory = sqlite3.Row
        rows = [dict(row) for row in db.execute("""
            SELECT datetime(received,'unixepoch') AS received_utc,version,component,exit,event,round,pc,
                   android,height,renderer,context,details
            FROM reports LEFT JOIN crash_context USING(id) LEFT JOIN crash_details USING(id)
            WHERE received>=? AND (? IS NULL OR version=?) AND (? IS NULL OR exit=?)
              AND NOT (component='launcher' AND exit BETWEEN -120 AND -101)
            ORDER BY received DESC,reports.rowid DESC LIMIT 200""",
            (int(time.time())-14*86400,version,version,code,code))]
        for row in rows:
            if row["context"] is not None:
                row["context"] = json.loads(row["context"])
            if row["details"] is not None:
                row["details"] = json.loads(row["details"])
        return rows


def import_rows(path, version=None, code=None):
    with closing(sqlite3.connect(path.as_uri()+"?mode=ro", uri=True)) as db:
        db.row_factory=sqlite3.Row
        rows=[dict(row) for row in db.execute("""
            SELECT datetime(received,'unixepoch') AS received_utc,version,android,reason,stage,format,bytes,
                   title,region,revision,sha256,details FROM disc_imports LEFT JOIN import_details USING(id)
            WHERE received>=? AND (? IS NULL OR version=?) AND (? IS NULL OR reason=?)
            ORDER BY received DESC,disc_imports.rowid DESC LIMIT 200""",
            (int(time.time())-14*86400,version,version,code,code))]
        for row in rows:
            if row["details"] is not None:
                row["details"]=json.loads(row["details"])
        return rows


def digest(path, days, importing=False):
    start = int(time.time()) - days*86400
    with closing(sqlite3.connect(path.as_uri()+"?mode=ro", uri=True)) as db:
        db.row_factory = sqlite3.Row
        if importing:
            return [dict(row) for row in db.execute("""
                WITH failures AS (
                    SELECT version,android,reason,stage,format,bytes,title,region,revision,sha256,received FROM disc_imports
                    UNION ALL SELECT version,android,exit,NULL,NULL,NULL,NULL,NULL,NULL,NULL,received FROM reports r
                    WHERE component='launcher' AND exit BETWEEN -120 AND -101
                    AND NOT EXISTS (SELECT 1 FROM disc_imports d WHERE d.id=r.id))
                SELECT version,android,reason,stage,format,bytes,title,region,revision,sha256,count(*) AS reports,
                       datetime(max(received),'unixepoch') AS last_seen_utc FROM failures WHERE received>=?
                GROUP BY version,android,reason,stage,format,bytes,title,region,revision,sha256
                ORDER BY reports DESC,last_seen_utc DESC""", (start,))]
        return [dict(row) for row in db.execute("""
          SELECT version,component,exit,pc,group_concat(DISTINCT event) AS events,count(*) AS reports,max(round) AS highest_round,
                 datetime(max(received),'unixepoch') AS last_seen_utc
          FROM reports WHERE received>=?
          GROUP BY version,component,exit,pc,CASE WHEN pc='unknown' THEN event ELSE '' END ORDER BY reports DESC,last_seen_utc DESC""", (start,))]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["serve", "digest", "snapshot"])
    parser.add_argument("--database", type=Path, default=DB)
    parser.add_argument("--days", type=int, default=14)
    args = parser.parse_args()
    if args.command == "snapshot":
        path = args.database.resolve()
        print(json.dumps({"crashes": crash_rows(path), "imports": import_rows(path)}, ensure_ascii=True))
        return
    os.umask(0o077)
    if args.command == "digest":
        print(json.dumps(digest(args.database.resolve(), args.days), indent=2))
        return
    with closing(sqlite3.connect(args.database)) as db, db:
        initialize(db)
    key = args.database.parent/"reader.key"
    if not key.exists():
        with key.open("x", encoding="ascii") as output:
            output.write(secrets.token_urlsafe(32))
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(args.database.parent/"server.pem", args.database.parent/"server.key")
    with SecureInbox(("0.0.0.0", 18443), tls) as server:
        server.reader = key.read_text(encoding="ascii").strip()
        if len(server.reader) < 40:
            raise ValueError("Invalid private digest key")
        server.database = args.database
        server.serve_forever()


if __name__ == "__main__":
    main()
