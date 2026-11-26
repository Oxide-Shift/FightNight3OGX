"""Preserve the phone's last timing messages across a USB disconnect or reboot."""
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import time
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from record_thor_health import SurfaceHistory, battery_state, thread_ticks, timing_stream


def check(disconnect):
    # A separate process writes to the same OS file descriptor as real adb.
    launch = subprocess.Popen
    def fake_adb(command, **kwargs):
        assert command == ["adb", "-d", "logcat", "-T", "1", "-v", "threadtime", "FightNightTiming:I", "*:S"]
        end = "sys.exit(7)" if disconnect else "time.sleep(60)"
        return launch([sys.executable, "-u", "-c",
                       f"import sys,time; print('slow flip interval_ms=502', flush=True); {end}"], **kwargs)

    with TemporaryDirectory() as tmp, patch.object(subprocess, "Popen", fake_adb):
        path = Path(tmp) / "timing.txt"
        with timing_stream("adb", "", path) as reader:
            deadline = time.monotonic() + 5
            while b"interval_ms=502" not in path.read_bytes() and time.monotonic() < deadline:
                time.sleep(.02)
            assert b"interval_ms=502" in path.read_bytes()  # Already on the PC during play.
            if disconnect:
                assert reader.wait(timeout=5) == 7
        assert reader.poll() is not None and b"interval_ms=502" in path.read_bytes()


if __name__ == "__main__":
    from record_thor_health import sustained_windows,process_usage,thermal_sensors
    dump=("Cached temperatures:\nTemperature{mValue=2.0, mType=0, mName=cpu, mStatus=0}\n"
          "Current temperatures from HAL:\n\tTemperature{mValue=39.5, mType=1, mName=gpu0, mStatus=0}\n"
          "\tTemperature{mValue=4000, mType=6, mName=voltage, mStatus=0}\nCurrent cooling devices:\n")
    assert thermal_sensors(dump)==[dict(sensor="gpu0",role="gpu",celsius=39.5,status=0)]
    assert thermal_sensors("Thermal Status: 0")==[]
    surface=SurfaceHistory()
    assert not surface.update([100,110])
    assert surface.update([100,110,120,140])==[110,120,140]
    assert not surface.update([110,120,140])
    assert surface.update([190,210,240])==[190,210,240]
    assert surface.deltas==[10,20,20,30] and surface.gaps==1
    windows=sustained_windows([20_000_000]*1500+[25_000_000]*1200)
    assert windows["first_30s"]["fps"]==50 and windows["last_30s"]["fps"]==40
    assert sustained_windows([20_000_000]*100) is None
    first=dict(process_ticks=50,sample_monotonic_ns=1_000_000_000,process_start_ticks=4)
    last=dict(process_ticks=200,sample_monotonic_ns=2_000_000_000,process_start_ticks=4)
    assert process_usage(first,last,100)==150 and process_usage({},last,100) is None
    assert process_usage(first,dict(last,process_start_ticks=5),100) is None
    surface = SurfaceHistory()
    for stamps in ([10,20,30], [20,30,40], [20,30,40], [65,75,85]):
        surface.update(stamps)
    assert surface.deltas == [10,10,10] and surface.gaps == 1
    surface.previous = None
    surface.update([140,150,160])
    assert surface.deltas == [10,10,10]
    fields = list(map(str, range(4, 43)))
    assert thread_ticks("42 (a) b) S "+" ".join(fields)) == [
        dict(tid=42, name="a) b", user_ticks=14, system_ticks=15, start_ticks=22)]
    assert thread_ticks("Permission denied") == []
    assert battery_state("  USB powered: true\n  temperature: 342\n  voltage: 4019\n  Charge counter: 1900432\n") == {
        "usb_powered":True, "temperature_tenths_c":342, "millivolts":4019, "charge_counter_uah":1900432}
    check(False)
    check(True)
    print("Live timing survives capture stop and a device disconnect.")

