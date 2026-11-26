"""Run the real inbox against a temporary SQLite database on localhost."""
from contextlib import closing, contextmanager
import http.client
from io import BytesIO
import os
from types import SimpleNamespace
import importlib.util
import json
from http.server import HTTPServer
from pathlib import Path
import sqlite3
import tempfile
from threading import Thread
import unittest
import uuid

path = Path(__file__).resolve().parents[1]/"tools/reporting/crash_inbox.py"
spec = importlib.util.spec_from_file_location("inbox", path)
inbox = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inbox)


@contextmanager
def inbox_transport():
    if os.environ.get("FN3_TEST_HTTP_IN_MEMORY") == "1":
        server = SimpleNamespace()
        def request(method, path, body=None, auth=None):
            data = json.dumps(body).encode() if body is not None else b""
            head = [f"{method} {path} HTTP/1.0", "Host: localhost", "Content-Type: application/json",
                    f"Content-Length: {len(data)}"]
            if auth:
                head.append("Authorization: Bearer "+auth)
            incoming = BytesIO(("\r\n".join(head)+"\r\n\r\n").encode()+data)
            outgoing = BytesIO()
            class Wire:
                def makefile(self, mode, *args):
                    return incoming
                def sendall(self, packet):
                    outgoing.write(packet)
            inbox.Inbox(Wire(), ("127.0.0.1", 0), server)
            response = http.client.HTTPResponse(SimpleNamespace(makefile=lambda mode: BytesIO(outgoing.getvalue())))
            response.begin()
            return response.status, response.read(), response.getheader("X-FightNight-Inbox")
        yield server, request
        return
    with HTTPServer(("127.0.0.1", 0), inbox.Inbox) as server:
        def request(method, path, body=None, auth=None):
            with closing(http.client.HTTPConnection(*server.server_address, timeout=5)) as conn:
                headers = {"Content-Type": "application/json"}
                if auth:
                    headers["Authorization"] = "Bearer "+auth
                conn.request(method, path, json.dumps(body) if body is not None else None, headers)
                response = conn.getresponse()
                return response.status, response.read(), response.getheader("X-FightNight-Inbox")
        worker = Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            yield server, request
        finally:
            server.shutdown()
            worker.join()

class Reports(unittest.TestCase):
    def test_consent_delivery_and_private_digest(self):
        with tempfile.TemporaryDirectory() as tmp, inbox_transport() as (server, request):
            server.database = Path(tmp)/"inbox.sqlite3"
            server.reader = "fixture-reader"
            with closing(sqlite3.connect(server.database)) as db, db:
                inbox.initialize(db)
            report = dict(id=str(uuid.uuid4()), version="0.4.0-alpha.28", component="game",
                          exit=139, pc="020493cc", event="DOWN", round=2, height=900, renderer="vulkan", android=33)
            self.assertEqual(request("GET", "/v1/health"), (200, b"", "1"))
            for _ in range(2):
                self.assertEqual(request("POST", "/v1/reports", report)[0], 201)
            self.assertEqual(request("POST", "/v1/reports", dict(report, id=str(uuid.uuid4()), event="COUNT"))[0], 201)
            self.assertEqual(request("POST", "/v1/reports", dict(report, player_name="extra field"))[0], 400)
            self.assertEqual(request("POST", "/v1/reports", dict(report, event="unexpected text"))[0], 400)
            self.assertEqual(request("GET", "/v1/digest")[0], 401)
            self.assertEqual(request("GET", "/v1/digest", auth="wrong")[0], 401)
            rows = json.loads(request("GET", "/v1/digest", auth=server.reader)[1])
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0]["reports"], 2)
            self.assertEqual(set(rows[0]["events"].split(",")), {"DOWN", "COUNT"})
            self.assertNotIn("id", rows[0])
            crashes = "/v1/crashes?version=0.4.0-alpha.28&exit=139"
            self.assertEqual(request("GET", crashes)[0], 401)
            def individual():
                return json.loads(request("GET", crashes, auth=server.reader)[1])
            self.assertEqual(len(individual()), 2)
            self.assertTrue(all(r["context"] is None and r["android"]==33 for r in individual()))
            context = dict(manufacturer="AYN", model="Thor", abi="arm64-v8a", milestone="frame", elapsed_ms=1250,
                           last_frame=60, call_target=None, msaa=2, anisotropy=4, fps_limit=60)
            extended = dict(report, context=context)
            for _ in range(2):
                self.assertEqual(request("POST", "/v1/crashes", extended)[0], 201)
            details = individual()
            self.assertEqual(len(details), 2)
            self.assertEqual([r["context"] for r in details if r["context"]], [context])
            self.assertEqual(request("POST","/v1/crashes",dict(extended, height=720))[0],400)
            for change in ({"filename":"private.iso"},{"last_frame":-1},{"milestone":"free text"},
                           {"model":"bad\nmodel"},{"msaa":True},{"call_target":"00000000"}):
                self.assertEqual(request("POST","/v1/crashes",dict(extended,context=dict(context,**change)))[0],400)
            call = dict(extended,id=str(uuid.uuid4()), exit=41, pc="002dabcd",
                        context=dict(context,call_target="00000000"))
            self.assertEqual(request("POST","/v1/crashes",call)[0],201)
            self.assertEqual(request("POST","/v1/reports",{k:v for k,v in call.items() if k!="context"})[0],201)
            self.assertEqual(request("GET","/v1/crashes?exit=99999999999999999999",auth=server.reader)[0],400)
            issue = dict(id=str(uuid.uuid4()), version="0.4.0-alpha.65", android=33, reason=-106,
                         stage="verify", format="xiso", bytes=2405171200, title="45410086",
                         region="00000001", revision=2, sha256="a"*64)
            basic = dict(report, id=issue["id"], component="launcher", event="LAUNCHER",
                         exit=-106, pc="unknown", renderer="none", round=0, height=0, version=issue["version"])
            self.assertEqual(request("POST", "/v1/reports", basic)[0], 201)
            self.assertEqual(request("GET", "/v1/import-digest")[0], 401)
            def imports():
                return json.loads(request("GET", "/v1/import-digest", auth=server.reader)[1])
            self.assertEqual(imports()[0]["reports"], 1)
            self.assertIsNone(imports()[0]["sha256"])
            for _ in range(2):
                self.assertEqual(request("POST", "/v1/imports", issue)[0], 201)
            self.assertEqual(len(imports()), 1)
            self.assertEqual(imports()[0]["sha256"], "a"*64)
            self.assertEqual(imports()[0]["reports"], 1)
            for change in ({"filename":"private.iso"}, {"sha256":"invalid"}, {"format":"arbitrary"},
                           {"reason":139}, {"bytes":-2}, {"revision":True}, {"region":"not-hex!"}):
                self.assertEqual(request("POST", "/v1/imports", dict(issue, **change))[0], 400)
            missing = dict(issue, id=str(uuid.uuid4()), stage="open", format="unknown", bytes=-1,
                           reason=-114, title=None, region=None, revision=None, sha256=None)
            self.assertEqual(request("POST", "/v1/imports", missing)[0], 201)
            details = dict(device=dict(model="Thor", cpu_cores=8, low_memory=False, thermal_status=0),
                           rom=dict(origin="import",layout="xiso", source_xbe_sha256="a"*64,
                                    disc_files_sha256="c"*64, files=901),
                           failure=dict(address_kind="native_module_offset",signal_from_exit=11,guest_ms=81))
            rich=dict(extended,details=details)
            for _ in range(2):
                self.assertEqual(request("POST","/v2/crashes",rich)[0],201)
            self.assertEqual([r["details"] for r in individual() if r["details"]], [details])
            self.assertEqual(request("POST","/v2/crashes",dict(rich,details=dict(details,rom={})))[0],201)
            self.assertEqual([r["details"] for r in individual() if r["details"]],[details])
            conflicting=dict(details,rom=dict(details["rom"],files=900))
            self.assertEqual(request("POST","/v2/crashes",dict(rich,details=conflicting))[0],400)
            for group,extra in (("rom",{"path":"private"}),("device",{"serial":"private"}),
                                ("failure",{"log":"private"}),("device",{"cpu_cores":True})):
                bad=dict(details);bad[group]=dict(details[group],**extra)
                self.assertEqual(request("POST","/v2/crashes",dict(rich,details=bad))[0],400)
            extended_details=dict(details,device=dict(details["device"],host_page_bytes=16384,presentation_displays=0),
                                  failure=dict(details["failure"],fault_location="outside_game_module",native_return_offset="01234567",fault_module="runner",module_pc="0000000000000120"))
            candidate=dict(rich,id=str(uuid.uuid4()),version="0.4.0-alpha.78.3",details=extended_details)
            self.assertEqual(request("POST","/v3/crashes",candidate)[0],201)
            self.assertEqual(request("POST","/v2/crashes",candidate)[0],400)
            bad=dict(extended_details,failure=dict(extended_details["failure"],native_return_offset="/private/path"))
            self.assertEqual(request("POST","/v3/crashes",dict(candidate,details=bad))[0],400)
            # Schema v4: identity, settings, timing and a scrubbed log tail. Old routes keep rejecting the new keys.
            v4_device=dict(extended_details["device"],app_version_code=94,firmware_build="AYN_Thor_20261101",
                           security_patch="2026-10-01",display_count=2,lower_display="1240x1080@60000",controllers="2020:0111")
            v4_failure=dict(extended_details["failure"],renderer_mode="vulkan",pipelined_frames=False,widescreen=True,in_bout=True,
                            sleeping=False,gpu_device_lost=False,gpu_init_failed=False,pace_work_us=18376,pace_gpu_wait_us=5296,
                            soft_targets="format=12 front=0 conflict=3 depth=0",gpu_name="Adreno (TM) 740",gpu_driver="512.744.0",
                            gpu_vendor_id="5143",log_tail="[Fight Night] renderer Vulkan\nFNR3/1 MILESTONE bout\n",log_lines=2)
            v4_details=dict(extended_details,device=v4_device,failure=v4_failure)
            v4_report=dict(candidate,id=str(uuid.uuid4()),version="0.4.0-alpha.80",details=v4_details)
            self.assertEqual(request("POST","/v3/crashes",v4_report)[0],400)
            self.assertEqual(request("POST","/v3/crashes",dict(v4_report,details=extended_details))[0],201)
            self.assertEqual(request("POST","/v4/crashes",v4_report)[0],201)
            v4_rows=json.loads(request("GET","/v1/crashes?version=0.4.0-alpha.80&exit=%d" % v4_report["exit"],auth=server.reader)[1])
            stored=[r["details"] for r in v4_rows if r["details"] and r["details"]["device"].get("app_version_code")==94]
            self.assertEqual(stored,[v4_details])
            for change in ({"log_tail":"café\n"},{"log_tail":"x"*6001},{"log_lines":41},{"gpu_driver":"/data/x"}):
                self.assertEqual(request("POST","/v4/crashes",dict(v4_report,id=str(uuid.uuid4()),
                                         details=dict(v4_details,failure=dict(v4_failure,**change))))[0],400)
            self.assertEqual(request("POST","/v4/crashes",dict(v4_report,id=str(uuid.uuid4()),
                                     details=dict(v4_details,device=dict(v4_device,controllers="Matt's pad"))))[0],400)
            import_details=dict(details,failure={})
            for _ in range(2):
                self.assertEqual(request("POST","/v2/imports",dict(issue,details=import_details))[0],201)
            self.assertEqual(request("GET","/v2/imports")[0],401)
            rows=json.loads(request("GET","/v2/imports?exit=-106",auth=server.reader)[1])
            self.assertEqual(len(rows),1)
            self.assertEqual(rows[0]["details"],import_details)
            self.assertEqual(request("POST","/v2/imports",dict(issue,details=import_details,sha256=None))[0],400)
            v4_import=dict(details,device=v4_device,failure={})
            self.assertEqual(request("POST","/v4/imports",dict(issue,id=str(uuid.uuid4()),details=v4_import))[0],201)
            self.assertEqual(request("POST","/v3/imports",dict(issue,id=str(uuid.uuid4()),details=v4_import))[0],400)

if __name__ == "__main__":
    unittest.main()
