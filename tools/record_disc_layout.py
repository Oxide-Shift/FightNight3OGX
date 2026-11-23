"""Record address and calling-convention metadata from the pinned desktop analysis.

Run after a verified full bringup. The device decodes all instructions from its
own XBE. The map contains no instructions, operands, generated C or objects.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

from bringup import XBE_SHA256


def toolkit_digest(toolkit):
    digest = hashlib.sha256()
    for section in ("xbe_parser", "disasm", "abi_analysis", "func_id", "recomp"):
        for script in sorted((toolkit/"tools"/section).glob("*.py")):
            if not script.name.startswith("test_"):
                digest.update(script.relative_to(toolkit).as_posix().encode() + b"\0")
                digest.update(hashlib.sha256(script.read_bytes().replace(b"\r\n",b"\n")).digest())
    return digest.hexdigest()


def release_layout(path, toolkit):
    payload = path.read_bytes()
    plan = json.loads(payload)
    if plan["toolkit"] != toolkit_digest(toolkit) or plan["xbe"] != XBE_SHA256:
        raise ValueError("Regenerate the private disc layout for this toolkit and executable")
    return payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("analysis", type=Path)
    parser.add_argument("toolkit", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    analysis = args.analysis.resolve()
    xbe = analysis / "default.xbe"
    if hashlib.sha256(xbe.read_bytes()).hexdigest() != XBE_SHA256:
        parser.error("Unsupported Xbox executable")
    sys.path.insert(0, str(args.toolkit.resolve()))
    from tools.recomp import __main__ as cli
    from tools.recomp.translator import BatchTranslator

    hooks = Path(__file__).resolve().parents[1] / "native/title_hooks.c"
    def collect(batch, functions, **options):
        engine = batch.translator
        lift = engine.lifter
        rows = []
        for addr, info in sorted(batch.func_db.items()):
            abi = batch.abi_db.get(addr, {})
            cls = batch.classification_db.get(addr, {})
            if cls.get("module") or cls.get("source_file"):
                raise ValueError("Unexpected symbol metadata")
            rows.append([addr, info["end"], info.get("name") if info.get("name") != f"sub_{addr:08X}" else None,
                         cls.get("category", "unknown"), abi.get("calling_convention","cdecl"),
                         abi.get("estimated_params",0), abi.get("return_hint","int_or_void"),
                         abi.get("frame_type","fpo_leaf"), abi.get("stack_frame_size",0)])
            if info.get("wrapper_name"):
                raise ValueError("Wrapped entries require an updated map schema")
        recovered = []
        for addr, cfg in sorted(engine._recovered_cfg.items()):
            spans = []
            for insn in cfg["instructions"]:
                if spans and spans[-1][1] == insn.address:
                    spans[-1][1] = insn.end_address
                else:
                    spans.append([insn.address, insn.end_address])
            recovered.append([addr, cfg["end"], spans, list(cfg["jump_tables"].items())])
        plan = {"version":1, "xbe":XBE_SHA256, "toolkit":toolkit_digest(args.toolkit.resolve()),
                "functions":rows, "labels":[
            [addr, name] for addr,name in sorted(batch.label_db.items()) if name != f"sub_{addr:08X}"],
            "helpers":[sorted(lift.SEH_PROLOGS),lift.SEH_EPILOG,lift.SETJMP_FN,lift.LONGJMP_FN],
            "owned":sorted(engine.owned_function_starts), "coalesced":sorted(engine.coalesced_function_starts),
            "manual":sorted(options.get("manual") or ()), "recovered":recovered,
            "hooks":hashlib.sha256(hooks.read_bytes().replace(b"\r\n",b"\n")).hexdigest(),
            "seeds":hashlib.sha256((analysis/"observed_functions.json").read_bytes().replace(b"\r\n",b"\n")).hexdigest()}
        payload = json.dumps(plan, separators=(",",":"),ensure_ascii=True).encode("ascii")
        args.output.write_bytes(payload)
        print(f"Recorded {len(rows)} function boundaries and {len(recovered)} recovered control-flow graphs")
        raise SystemExit(0)

    BatchTranslator.translate_batch_split = collect
    sys.argv = ["recomp",str(xbe),"--all","--split","160","--game-name","Fight Night Round 3",
                "--disasm-dir",str(analysis/"disasm"),"--func-id-dir",str(analysis/"symbols"),
                "--abi-dir",str(analysis/"abi"),"--exclude-manual",str(hooks)]
    cli.main()


if __name__ == "__main__":
    main()
