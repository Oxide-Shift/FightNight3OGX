"""Regression fixtures for interrupted Android screenrecord containers."""
from pathlib import Path
import struct
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from thor_recording import CaseRecording, frame_clock


def video(frames, extended=False):
    payload=b"#VV1NSC0PET1ME2#"+struct.pack("<IqI",2,0,len(frames))
    payload+=struct.pack(f"<{len(frames)}q",*frames)
    if extended:
        media=struct.pack(">I4sQ",1,b"mdat",len(payload)+16)
    else:
        media=struct.pack(">I4s",len(payload)+8,b"mdat")
    return media+payload+struct.pack(">I4s",8,b"moov")


class RecordingTest(unittest.TestCase):
    def read(self,data):
        with TemporaryDirectory() as folder:
            path=Path(folder)/"display.mp4"
            path.write_bytes(data)
            return frame_clock(path)

    def test_frame_bounds(self):
        for extended in (True,False):
            with self.subTest(extended=extended):
                clock=self.read(video((800,1600,2400),extended))
                self.assertEqual((clock["first_ns"],clock["last_ns"],clock["frames"]),(800,2400,3))

    def test_incomplete_recordings(self):
        valid=video((800,1600),True)
        for broken in (b"",valid[:-1],valid[:-8],valid[:8]+b"????????"+valid[16:],video((1600,800))):
            with self.subTest(size=len(broken)),self.assertRaises(ValueError):
                self.read(broken)

    def test_settings_hold_waits_for_encoder_startup_and_is_bounded(self):
        recorder=CaseRecording(Mock(),"settings")
        recorder.thread=Mock()
        recorder.rows=[object()]
        recorder.encoders_launched=40
        with patch('time.monotonic',side_effect=[40,40,40,41,41,42,42]), patch.object(recorder.done,'wait') as wait:
            recorder.hold_settings_view()
            self.assertEqual(wait.call_count,2)
        recorder.rows=[]
        with patch('time.monotonic',side_effect=[40,40,46]), patch.object(recorder.done,'wait') as wait:
            recorder.hold_settings_view()
            self.assertEqual(wait.call_count,1)


if __name__=="__main__":
    unittest.main()
