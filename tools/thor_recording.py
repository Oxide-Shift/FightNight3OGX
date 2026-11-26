"""Record the isolated game displays and retain their common Android frame clock."""
import json
import mmap
import re
import struct
import subprocess
import threading
import time
from uuid import uuid4


def frame_clock(path):
    magic=b"#VV1NSC0PET1ME2#"
    if path.stat().st_size<8:
        raise ValueError("Empty or truncated screen recording")
    with path.open("rb") as stream, mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as data:
        cursor=0
        movie=False
        media=[]
        while cursor<len(data):
            if len(data)-cursor<8:
                raise ValueError("Truncated MP4 box header")
            size,kind=struct.unpack_from(">I4s",data,cursor)
            header=8
            if size==1:
                if len(data)-cursor<16:
                    raise ValueError("Truncated MP4 extended size")
                size=struct.unpack_from(">Q",data,cursor+8)[0]
                header=16
            elif size==0:
                size=len(data)-cursor
            if size<header or size>len(data)-cursor:
                raise ValueError("Unfinalized MP4 recording")
            if kind==b"moov":
                movie=True
            elif kind==b"mdat":
                media.append((cursor+header,cursor+size))
            cursor+=size
        if not movie:
            raise ValueError("Recording lacks its MP4 movie index")
        for start,end in reversed(media):
            at=data.rfind(magic,start,end)
            if at>=0:
                break
        else:
            raise ValueError("Winscope v2 frame clock absent")
        first=at+len(magic)+16
        if first>end:
            raise ValueError("Truncated recording clock")
        version,offset,count=struct.unpack_from("<IqI",data,at+len(magic))
        if version!=2 or not 1<=count<=100000 or first+8*count>end:
            raise ValueError("Invalid recording clock")
        values=struct.unpack_from("<%dq"%count,data,first)
    if any(b<a for a,b in zip(values,values[1:])):raise ValueError("Recording clock went backwards")
    return {"clock":"Android elapsed realtime ns","frames":count,"first_ns":values[0],"last_ns":values[-1]}


class CaseRecording:
    def __init__(self,usb,name):
        self.usb=usb
        self.name=name
        self.rows=[]
        self.done=threading.Event()
        self.layer_lock=threading.Lock()
        self.segment=0
        self.encoders_launched=None
        self.streams_saved=0
        self.errors=[]
        self.suspended=False
        self.thread=threading.Thread(target=self.monitor,name="QA display capture",daemon=True)

    def begin(self):
        self.thread.start()

    def pause(self):
        with self.layer_lock:
            self.suspended=True
            self.stop_segment(app_stopping=True)

    def resume(self):
        with self.layer_lock:self.suspended=False

    def hold_settings_view(self):
        # A persisted setting can be read before screenrecord receives its first
        # buffer. Keep this view alive briefly before the QA driver cold starts it.
        deadline=time.monotonic()+5
        while time.monotonic()<deadline and self.thread.is_alive() and not self.done.is_set():
            with self.layer_lock:
                if self.suspended:return
                if self.rows and self.encoders_launched is not None and time.monotonic()-self.encoders_launched>=2:return
            self.done.wait(.15)

    def finish(self):
        self.done.set()
        self.thread.join()
        if not self.streams_saved:
            self.errors.append("No app-owned video frames were collected for this case")
        return self.errors

    def report(self,record):
        with (self.usb.out/"recording-timeline.jsonl").open("a",encoding="utf-8") as file:
            file.write(json.dumps(dict(case=self.name,segment=self.segment,**record))+"\n")

    def start_segment(self,count):
        self.segment+=1
        self.started=time.monotonic()
        self.encoders_launched=None
        for index,display in enumerate(self.usb.displays[:count]):
            if not re.fullmatch(r"[0-9]+",str(display)):raise ValueError("Invalid physical display")
            size=self.usb.capture_sizes.get(display)
            if not size:
                png=self.usb.call("exec-out","screencap","-p","-d",display)
                if png[:8]!=bytes.fromhex("89504e470d0a1a0a"):raise ValueError("Display dimensions unavailable")
                size=struct.unpack_from(">II",png,16)
                self.usb.capture_sizes[display]=size
            scale=min(1,960/max(size))
            width,height=(max(2,round(n*scale/2)*2) for n in size)
            path="/data/local/tmp/fn3-qa-"+uuid4().hex+".mp4"
            command=(f"echo $$ > {path}.pid && exec screenrecord --display-id {display} "
                     f"--size {width}x{height} --bit-rate 4000000 --time-limit 120 {path}")
            process=subprocess.Popen([*self.usb.adb,"shell",command],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            self.rows.append((index,path,process))
        self.encoders_launched=time.monotonic()

    def stop_segment(self,app_stopping=False):
        rows,self.rows=self.rows,[]
        if not rows:return
        clocks={}
        early_stop=app_stopping and time.monotonic()-self.started<2
        for _,path,proc in rows:
            if proc.poll() is None:
                try:
                    self.usb.call("shell",f"p=$(cat {path}.pid 2>/dev/null) && test -r /proc/$p/cmdline && "
                              f"tr '\\0' '\\n' < /proc/$p/cmdline | grep -Fxq {path} && kill -2 $p",check=False)
                except (OSError, RuntimeError, subprocess.SubprocessError) as exc:
                    self.report({"display":path,"stop_error":str(exc)})
        for index,path,proc in rows:
            dest=self.usb.out/(f"{self.name}-{self.segment:03d}-display-{index}.mp4")
            try:
                try:proc.wait(timeout=10)
                except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=5)
                self.usb.call("pull",path,dest,timeout=60)
                clocks[index]=dict(file=dest.name,**frame_clock(dest))
                self.streams_saved+=1
                self.usb.shell("rm","-f",path,path+".pid")
            except Exception as exc:
                absent="failed to stat remote object" in str(exc) and "No such file" in str(exc)
                empty=dest.is_file() and dest.stat().st_size==0
                if early_stop and (absent or empty):
                    self.report({"unrecorded_transition":"app stopped before first video frame","display":index})
                else:
                    self.errors.append(str(exc))
                    self.report({"error":str(exc),"device_clip_path":path,"display":index})
        record={"streams":clocks}
        if len(clocks)==2:
            start=max(c["first_ns"] for c in clocks.values());end=min(c["last_ns"] for c in clocks.values())
            record.update(overlap_ms=round((end-start)/1e6,3),
                          trim_start_ms={n:round((start-c["first_ns"])/1e6,3) for n,c in clocks.items()})
            if end<=start:self.errors.append("No synchronized overlap")
        self.report(record)

    def monitor(self):
        try:
            while not self.done.is_set():
                with self.layer_lock:
                    if not self.suspended:
                        try:
                            state=self.usb.state()
                            visible=bool(state and (state.get("focused") or self.usb.foreground()))
                        except (OSError, RuntimeError, subprocess.SubprocessError) as exc:
                            state={}
                            visible=False
                            self.report({"observation_gap":str(exc)})
                        count=(2 if state.get("companionDisplay",-1)>0 else 1) if visible else 0
                        if self.rows and (len(self.rows)!=count or time.monotonic()-self.started>=119 or
                                          any(p.poll() is not None for _,_,p in self.rows)):self.stop_segment()
                        if count and not self.rows:self.start_segment(count)
                self.done.wait(1)
        except Exception as exc:
            self.errors.append(str(exc));self.report({"error":str(exc)})
        finally:
            with self.layer_lock:self.stop_segment()
