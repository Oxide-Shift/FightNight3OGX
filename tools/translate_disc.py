"""Translate the verified disc using its address map and the upstream x86 lifter."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import marshal
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import threading

from bringup import XBE_SHA256

GROUP = 320


def accelerate_decode(disasm):
    """Read each pinned Capstone detail record while its batch is alive."""
    import ctypes
    from capstone import CsInsn, CsError, CS_ERR_OK, _cs, _cs_insn
    from tools.recomp.disasm import Instruction

    decoder = disasm._cs
    original = disasm.disassemble_function
    cache = {}

    def decode(data, start, end, resync=None):
        if resync:
            return original(data, start, end, resync=resync)
        size = end-start
        if size <= 0 or size > len(data):
            return []
        records = ctypes.POINTER(_cs_insn)()
        count = _cs.cs_disasm(decoder.csh, data, size, start, 0, ctypes.byref(records))
        if not count:
            error = _cs.cs_errno(decoder.csh)
            if error != CS_ERR_OK:
                raise CsError(error)
            return []
        result = []
        try:
            for i in range(count):
                raw = records[i]
                offset = raw.address-start
                # op_str includes the resolved destination of relative branches.
                key = (data[offset:offset+raw.size], raw.op_str)
                fields = cache.get(key)
                if fields is None:
                    view = CsInsn.__new__(CsInsn)
                    view._cs, view._raw = decoder, raw
                    insn = disasm._decode_instruction(view)
                    # Only plain Python values escape, never Capstone pointers.
                    fields = (insn.size, insn.mnemonic, insn.op_str, insn.bytes_hex,
                              insn.operands, insn.call_target, insn.jump_target,
                              insn.memory_refs, insn.imm_values, insn.regs_written)
                    if len(cache) >= 8192:
                        cache.clear()
                    cache[key] = fields
                else:
                    insn = Instruction(raw.address, *fields)
                result.append(insn)
            return result
        finally:
            _cs.cs_free(records, count)

    disasm.disassemble_function = decode


def load_layout(layout, xbe, toolkit):
    sys.path.insert(0, str(toolkit))
    from tools.recomp import config, translator
    from tools.recomp.manual_scan import entry_hooks

    image = xbe.read_bytes()
    if hashlib.sha256(image).hexdigest() != XBE_SHA256:
        raise ValueError("Unsupported Xbox executable")
    with layout.open("r", encoding="ascii") as stream:
        plan = json.load(stream)
    native = Path(__file__).resolve().parents[1] / "native"
    for field, name in (("hooks", "title_hooks.c"), ("seeds", "observed_functions.json")):
        if hashlib.sha256((native/name).read_bytes().replace(b"\r\n",b"\n")).hexdigest() != plan[field]:
            raise ValueError("Rebuild the disc layout for the current " + name)
    if plan["version"] != 1 or plan["xbe"] != XBE_SHA256:
        raise ValueError("Disc layout does not match this release")
    config.set_game_name("Fight Night Round 3")
    config.configure_from_xbe(str(xbe))
    translator.ENTRY_HOOKS = entry_hooks(str(native/"title_hooks.c"))
    funcs, classes, abi = {}, {}, {}
    for addr, end, name, category, cc, params, hint, frame, size in plan["functions"]:
        funcs[addr] = {"_addr":addr, "end":end, "size":end-addr, "name":name or f"sub_{addr:08X}"}
        classes[addr] = {"category":category}
        abi[addr] = {"calling_convention":cc, "estimated_params":params, "return_hint":hint,
                     "frame_type":frame, "stack_frame_size":size}
    labels = dict(plan["labels"])
    prologs, epilog, setjmp, longjmp = plan["helpers"]
    engine = translator.FunctionTranslator(image, funcs, labels, classes, abi,
                                          seh_prolog=prologs, seh_epilog=epilog,
                                          setjmp_fn=setjmp, longjmp_fn=longjmp)
    engine.owned_function_starts = set(plan["owned"])
    engine.coalesced_function_starts = set(plan["coalesced"])
    engine.lifter.manual_functions = set(plan["manual"])
    accelerate_decode(engine.disasm)
    for addr, end, spans, tables in plan["recovered"]:
        instructions = []
        for start, stop in spans:
            raw = engine._read_func_bytes(start, stop)
            decoded = engine.disasm.disassemble_function(raw, start, stop)
            if not decoded or decoded[-1].end_address != stop:
                raise ValueError(f"Disc layout failed at 0x{start:X}")
            instructions.extend(decoded)
        engine._recovered_cfg[addr] = {"end":end,"instructions":instructions,"jump_tables":dict(tables)}

    # Use the upstream batch writer for headers, stubs, ordering and dispatch.
    batch = translator.BatchTranslator.__new__(translator.BatchTranslator)
    batch.title = translator.xbe_title(image, str(xbe))
    batch.func_db, batch.classification_db, batch.abi_db = funcs, classes, abi
    batch.translator = engine
    return batch


def worker(args, batch, addresses):
    engine = batch.translator
    # Distribute the largest blocks first. Tiny helpers and entire boxing routines
    # can occupy equal numbers of addresses but take very different times to lift.
    groups = range(0, len(addresses), GROUP)
    weight = lambda start: sum(batch.func_db[a]["size"] for a in addresses[start:start+GROUP])
    queues = [[] for _ in range(args.jobs)]
    loads = [0] * args.jobs
    for first in sorted(groups,key=weight,reverse=True):
        lane = min(range(args.jobs),key=loads.__getitem__)
        queues[lane].append(first)
        loads[lane] += weight(first)
    for first in queues[args.worker]:
        group = addresses[first:first+GROUP]
        bodies = []
        for addr in group:
            bodies.append(engine.translate_function(addr, batch.func_db[addr]))
        result = {"bodies":bodies, "calls":list(engine.lifter.referenced_calls.items()),
                  "unimplemented":engine.lifter.unimplemented}
        with (args.spool/str(first)).open("wb") as stream:
            marshal.dump(result, stream, 4)
        engine.lifter.referenced_calls.clear()
        engine.lifter.unimplemented.clear()
        print(f"done {len(group)}", flush=True)


def translate(args, batch, funcs, addresses):
    completed = 0
    total = len(addresses)
    lock = threading.Lock()
    print(f"Translating Xbox functions: 0 / {total}", flush=True)
    with tempfile.TemporaryDirectory(dir=args.work) as tmp:
        spool = Path(tmp)
        def run_worker(number):
            nonlocal completed
            cmd = [sys.executable,"-u",str(Path(__file__).resolve()),str(args.xbe),str(args.layout),
                   str(args.toolkit),str(args.work),"--jobs",str(args.jobs),
                   "--worker",str(number),"--spool",str(spool)]
            with subprocess.Popen(cmd,stdout=subprocess.PIPE,text=True) as process:
                for line in process.stdout:
                    if re.fullmatch(r"done [0-9]+",line.strip()):
                        with lock:
                            completed += int(line.split()[1])
                            print(f"Translating Xbox functions: {completed} / {total}",flush=True)
                if process.wait():
                    raise RuntimeError(f"Translation worker exited with code {process.returncode}")

        with ThreadPoolExecutor(max_workers=args.jobs) as pool:
            list(pool.map(run_worker,range(args.jobs)))
        if completed != total:
            raise ValueError("Incomplete translation")
        print("Writing translated Xbox functions",flush=True)
        engine = batch.translator
        original = engine.translate_function
        position, bodies = 0, []
        def consume(addr, info):
            nonlocal position, bodies
            # The upstream stub writer may translate a function again with a
            # forced entry label. It must use the real lifter for that entry.
            if position == total:
                return original(addr,info)
            if addr != addresses[position]:
                raise ValueError("Unexpected function order")
            if position % GROUP == 0:
                part = spool/str(position)
                with part.open("rb") as stream:
                    data = marshal.load(stream)
                part.unlink()
                bodies = data["bodies"]
                engine.lifter.referenced_calls.update(data["calls"])
                for mnemonic, hits in data["unimplemented"].items():
                    engine.lifter.unimplemented.setdefault(mnemonic,[]).extend(hits)
            code = bodies[position % GROUP]
            position += 1
            return code
        engine.translate_function = consume
        stats = batch.translate_batch_split(funcs,str(args.work/"gen"),chunk_size=160,
                                            manual=engine.lifter.manual_functions)
        if position != total or stats["failed"]:
            raise ValueError("Could not translate all Xbox functions")
        from tools.recomp.output import write_summary
        write_summary(stats,str(args.work/"translation"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ("xbe","layout","toolkit","work"):
        parser.add_argument(field,type=Path)
    parser.add_argument("--jobs",type=int,default=1,choices=range(1,6))
    parser.add_argument("--worker",type=int)
    parser.add_argument("--spool",type=Path)
    args = parser.parse_args()
    batch = load_layout(args.layout,args.xbe,args.toolkit)
    funcs = batch.get_functions_by_category()
    addresses = [addr for addr,_ in funcs if addr not in batch.translator.lifter.manual_functions]
    if args.worker is None:
        translate(args,batch,funcs,addresses)
    else:
        worker(args,batch,addresses)


if __name__ == "__main__":
    main()
