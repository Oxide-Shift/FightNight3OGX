"""Specialize captured Xbox vertex programs using the port's existing Vulkan shader."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import struct
import subprocess


def load_programs(path, counts):
    if path is None or not path.is_file():
        return []
    data = path.read_bytes()
    programs = {}
    offset = 0
    while offset < len(data):
        if len(data) - offset < 8:
            raise ValueError("Incomplete vertex profile header")
        key, length = struct.unpack_from("<II", data, offset)
        offset += 8
        if not 1 <= length <= 136 or length * 32 > len(data) - offset:
            raise ValueError("Incomplete vertex program")
        code = data[offset:offset + length * 32]
        offset += len(code)
        if key in programs and programs[key] != code:
            raise ValueError("Ambiguous vertex program fingerprint")
        programs[key] = code
    weight = {}
    if counts.is_file():
        for key, count in re.findall(r"\[VSH profile\] ([0-9A-Fa-f]{8}) (\d+)", counts.read_text(errors="replace")):
            h = int(key, 16)
            weight[h] = weight.get(h, 0) + int(count)
    return sorted(programs.items(), key=lambda pair: (-weight.get(pair[0], 0), pair[0]))[:32]


def input_count(code):
    slots = [((word >> 6) & 15) for ins in struct.iter_unpack("<8I", code)
             for word in ins[2:5] if (word & 3) == 2]
    return max(slots, default=-1) + 1


def constant_count(code):
    """Number of leading vec4 registers that this specialized shader can read."""
    count = 1
    for ins in struct.iter_unpack("<8I", code):
        for word in ins[2:5]:
            if word & 3 in (1, 2):
                continue
            if word & (1 << 11):  # Relative addressing can reach any register.
                return 192
            index = (word >> 20) & 255
            count = max(count, 1 + (index if index < 192 else 0))
        if ins[0] & (1 << 26):
            break
    return count


def specialize(template, code):
    # Vertex fetch uses the packed order assigned by nv2a_vsh_export.
    count = input_count(code)
    attributes = "".join(f"layout(location={i}) in vec4 attr{i};\n" for i in range(count))
    cases = "".join(f"case {i}u: return attr{i};\n" for i in range(count))
    fetch = attributes + "vec4 input_at(uint i){switch(i){\n" + cases + "default: return vec4(0.0);}}\n"
    template = template.replace("vec4 src(uint w, uint vbase)", fetch + "vec4 src(uint w, uint vbase)")
    template = template.replace("f[vbase + ((w >> 6) & 15u)]", "input_at((w >> 6) & 15u)")

    begin = template.index("    for (uint s = 0u; s < 136u; s++) {")
    end = template.index("    vec4 pos = R[12];", begin)
    body = template[template.index("        uint ctl = i0.x;", begin):
                    template.index("        if (((ctl >> 26)", begin)]
    blocks = []
    for instruction in struct.iter_unpack("<8I", code):
        def vector(words):
            return "uvec4(" + ",".join(f"0x{x:x}u" for x in words) + ")"
        blocks.append("    {\n        const uvec4 i0=" + vector(instruction[:4]) +
                      ", i1=" + vector(instruction[4:]) + ";\n" + body + "    }\n")
        if instruction[0] & (1 << 26):
            break
    return template[:begin] + "".join(blocks) + template[end:]


def build(template, output, glslc, profile, counts):
    programs = load_programs(profile, counts)
    shader = template.read_text()
    def compile_one(item):
        key, code = item
        base = output / f"fight_{key:08x}"
        source, binary = base.with_suffix(".vert"), base.with_suffix(".spv")
        source.write_text(specialize(shader, code))
        subprocess.run([glslc, "--target-env=vulkan1.3", "-O", source, "-o", binary], check=True)
        return key, code, binary.read_bytes()
    header = ["/* Generated from this private build's optional vertex profile. */"]
    table = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        for key, code, spv in pool.map(compile_one, programs):
            name = f"fight_{key:08x}"
            for label, data in (("words", code), ("spv", spv)):
                words = struct.unpack("<%dI" % (len(data)//4), data)
                encoded = [",".join(hex(v) + "u" for v in words[i:i+12]) for i in range(0,len(words),12)]
                header.append(f"static const uint32_t {name}_{label}[]={{\n" + ",\n".join(encoded) + "\n};")
            table.append(f"{{0x{key:x}u,{len(code)//32},{input_count(code)},{constant_count(code)}, {name}_words,{name}_spv,sizeof {name}_spv}}")
    header.append("static const struct { uint32_t hash,n,inputs,constants; const uint32_t *words,*spv; size_t bytes; } "
                  "fight_programs[]={" + ",\n".join([*table,"{0,0,0,0,NULL,NULL,0}"]) + "};")
    (output / "fight_programs.h").write_text("\n".join(header)+"\n")
    return len(programs)
