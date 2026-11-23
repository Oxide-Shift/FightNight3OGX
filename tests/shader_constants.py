"""Check register bounds used for Vulkan uploads against the Xbox shader's addressing rules."""
from pathlib import Path
from tempfile import TemporaryDirectory
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/"tools"))
from build_vertex_shaders import constant_count
from build_fragment_shaders import RECORD, build, select_programs

def instruction(a, b=1, c=2, end=True):
    return struct.pack("<8I", int(end) << 26, 0, a, b, c, 0, 0, 0)

def fragment_profiles():
    def record(draws, mode, *, flags=0, alpha=0, mask=15, specialized=1):
        key = [0] * 47
        key[4], key[7], key[8], key[9], key[46] = mask, flags, specialized, mode, alpha
        return RECORD.pack(draws, *key)

    with TemporaryDirectory() as tmp:
        directory = Path(tmp)
        profile = directory / "profile.bin"
        assert build(profile, directory, directory) == 0
        assert "FIGHT_FRAGMENT_COUNT 0" in (directory / "fight_fragments.h").read_text()
        profile.write_bytes(record(3, 1) + record(3, 1) + record(5, 2, flags=64) +
                            record(4, 3, alpha=0x201) + record(2, 4, alpha=0x207) +
                            record(99, 5, specialized=0) + record(99, 6, mask=0) +
                            record(99, 7, flags=1) + record(99, 8, flags=16))
        assert [(full, words[1]) for full, words in select_programs(profile)] == [
            (True, 1), (False, 2), (False, 3), (True, 4)]
        profile.write_bytes(b"x")
        try:
            select_programs(profile)
        except ValueError:
            pass
        else:
            raise AssertionError("Truncated fragment profile accepted")

def main():
    fragment_profiles()
    assert constant_count(instruction(1)) == 1
    for register in range(256):
        operand = 3 | (register << 20)
        for slot in range(3):
            words = [1, 2, 1]
            words[slot] = operand
            assert constant_count(instruction(*words)) == (register + 1 if register < 192 else 1)
            words[slot] |= 1 << 11
            assert constant_count(instruction(*words)) == 192
    assert constant_count(instruction(3 | (2 << 20), end=False) + instruction(3 | (99 << 20))) == 100
    assert constant_count(instruction(3) + instruction(3 | (1 << 11))) == 1
    print("Shader constant addressing passed")

if __name__ == "__main__":
    main()
