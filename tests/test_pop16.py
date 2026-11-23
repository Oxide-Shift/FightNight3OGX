"""Check the regression in the pinned Xbox recompiler using synthetic POP operands."""
import argparse
from pathlib import Path
import sys
from types import SimpleNamespace


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("toolkit", type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.toolkit.resolve()))
    from tools.recomp.lifter import Lifter

    def lift(reg):
        operand = SimpleNamespace(type="reg", reg=reg, text=reg)
        insn = SimpleNamespace(mnemonic="pop", operands=[operand], address=0x1000,
                               size=2, end_address=0x1002, call_target=None)
        return " ".join(Lifter().lift_instruction(insn))

    for reg in ("ax", "bx", "cx", "dx", "si", "di", "sp", "bp"):
        code = lift(reg)
        operations = ["MEM16(esp)", "esp += 2", f"SET_LO16(e{reg}, _word)"]
        positions = [code.index(op) for op in operations]
        if positions != sorted(positions):
            raise AssertionError((reg, code))
    if lift("ecx") != "POP32(esp, ecx);":
        raise AssertionError("The 32-bit register path changed")
    print("All eight 16-bit register emissions and the 32-bit control passed.")


if __name__ == "__main__":
    main()
