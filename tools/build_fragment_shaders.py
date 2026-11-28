"""Bake observed NV2A combiner states into the port's existing fragment shaders."""
from collections import Counter
import struct
import subprocess


# Draw count, eight pipeline words and 39 Vulkan specialization constants.
RECORD = struct.Struct("<48I")


def select_programs(profile):
    if profile is None or not profile.is_file():
        return []
    counts = Counter()
    with profile.open("rb") as stream:
        while record := stream.read(RECORD.size):
            if len(record) != RECORD.size:
                raise ValueError(f"Incomplete fragment shader profile record in {profile}")
            count, *key = RECORD.unpack(record)
            flags = key[7]
            if not count or not key[4] or not key[8] or flags & (1 | 16):
                continue
            # Choose the same source module as the ordinary pipeline builder.
            full_coverage = not (flags & 64) and key[46] in (0, 0x207)
            counts[full_coverage, tuple(key[8:])] += count
    return sorted(counts, key=lambda item: (-counts[item], item))[:32]


def build(profile, generated, shader_tools):
    programs = select_programs(profile)
    header = [f"#define FIGHT_FRAGMENT_COUNT {len(programs)}"]
    entries = []
    for i, (full_coverage, words) in enumerate(programs):
        symbol = f"fight_fragment_{i}"
        module = generated / ("fight_full_coverage.spv" if full_coverage else "xbox_frag.spv")
        output = generated / (symbol + ".spv")
        values = " ".join(f"{index}:{value}" for index, value in enumerate(words))
        subprocess.run([shader_tools / "spirv-opt.exe", "--set-spec-const-default-value", values,
                        "--freeze-spec-const", "--fold-spec-const-op-composite", "-O",
                        "--eliminate-dead-code-aggressive", "--compact-ids", module, "-o", output],
                       check=True)
        subprocess.run([shader_tools / "spirv-val.exe", "--target-env", "vulkan1.1", output],
                       check=True)
        code = [f"0x{word:08x}u" for (word,) in struct.iter_unpack("<I", output.read_bytes())]
        header.append(f"static const uint32_t {symbol}[] = {{")
        header.extend("    " + ", ".join(code[j:j+8]) + "," for j in range(0, len(code), 8))
        header.append("};")
        constants = ", ".join(f"0x{word:08x}u" for word in words)
        entries.append(f"    {{{int(full_coverage)}, {{{constants}}}, {symbol}, sizeof({symbol})}},")
    header.extend(["static const struct {", "    int full_coverage;", "    uint32_t words[39];",
                   "    const uint32_t *code;", "    unsigned bytes;",
                   "} fight_fragment_programs[] = {", *(entries or ["    {0},"]), "};"])
    (generated / "fight_fragments.h").write_text("\n".join(header) + "\n", encoding="ascii")
    return len(programs)
