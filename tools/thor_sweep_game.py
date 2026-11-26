"""Fixed player journeys for the separately installed USB QA app."""
from contextlib import contextmanager
from io import BytesIO
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from uuid import uuid4

from PIL import Image, ImageChops, ImageFilter, ImageStat

from thor_music_qa import selected_track

ROOT = ("Play Now", "ESPN Classic", "Career Mode", "Game Modes", "Create Champ", "Achievements", "Music", "Options")
VENUES = ("warehouse", "windy", "garden", "aragon", "palace", "staples")
KEYS = {"A":304,"B":305,"X":307,"Y":308,"Start":315}
ANDROID_KEYS = {"A":96,"B":97,"X":99,"Y":100,"Start":108,"Up":19,"Down":20,"Left":21,"Right":22}
HATS = {"Up":(17,-1),"Down":(17,1),"Left":(16,-1),"Right":(16,1)}

def root_ink(picture):
    # Physical 16:9 top display, sampled at a fixed size. Only the centered,
    # illuminated caption counts. A partly completed slide must not pass.
    area=picture.convert("RGB").resize((960,540)).crop((552,330,752,353))
    mask=Image.new("L",area.size)
    mask.putdata([255 if r>190 and 55<g<169 and b<54 and r-g>62 else 0 for r,g,b in area.getdata()])
    return mask

def settled_caption(mask,reference):
    scores=[]
    for title,template in reference.items():
        intersection=ImageChops.darker(mask,template).histogram()[255]
        union=ImageChops.lighter(mask,template).histogram()[255]
        scores.append((intersection/max(union,1),title))
    scores.sort(reverse=True)
    if len(scores)>=2 and scores[0][0]>=.52 and scores[0][0]-scores[1][0]>=.12:
        return scores[0][1]
    return None

def same_glyphs(left,right,correlation):
    # Centered correlation tolerates changes in brightness behind retail lettering.
    a=list(left.getdata());b=list(right.getdata())
    mean_a=sum(a)/len(a);mean_b=sum(b)/len(b)
    cov=sum((x-mean_a)*(y-mean_b) for x,y in zip(a,b))
    var_a=sum((x-mean_a)**2 for x in a);var_b=sum((y-mean_b)**2 for y in b)
    return cov>0 and cov*cov>correlation*correlation*var_a*var_b


def retail_select_icon(image):
    # The illuminated A glyph is yellow-green on the Thor. Retail backdrops
    # and presentation modes change its absolute brightness and saturation.
    return sum(g-r>12 and g-b>22 and g>66 for r,g,b in image.getdata())>=12


def wait_for_view(views,matches,timeout,*,sleep=time.sleep,clock=time.monotonic,interval=.35):
    """Poll until any captured view satisfies matches(view). Returns True or False and never raises.

    Used for retail screens whose backdrop animates: callers pass a glyph-based predicate instead of a
    mean pixel-difference threshold against a still reference."""
    deadline=clock()+timeout
    while clock()<deadline:
        if any(matches(view) for view in views()):return True
        sleep(interval)
    return False


class Controls:
    def __init__(self, usb):
        self.usb = usb
        self.pipe = None
        self.output = None
        self.analog = "activity dispatch using handheld axis ranges"

    def open(self):
        self.output = (self.usb.out/"virtual-pad.txt").open("ab")
        self.pipe = subprocess.Popen([*self.usb.adb,"shell","-T","uinput","-"],stdin=subprocess.PIPE,
                                     stdout=self.output,stderr=self.output)
        # Vendor_045e_Product_028e.kl maps ABS_RX/RY (3,4) to Android Z/RZ.
        # ABS_Z/RZ (2,5) are unsigned triggers on this registered controller.
        axes = (0,1,2,3,4,5,16,17)
        registration = dict(id=1,command="register",name="Fight Night QA Pad",vid=1118,pid=654,bus="usb",
            configuration=[{"type":100,"data":[1,3]},{"type":101,"data":list(KEYS.values())+[310,311]},
                           {"type":103,"data":list(axes)}],
            abs_info=[{"code":a,"info":{"minimum":-1 if a>=16 else 0 if a in (2,5) else -32767,
                       "maximum":1 if a>=16 else 255 if a in (2,5) else 32767,
                       "value":0,"flat":0,"fuzz":0,"resolution":0}} for a in axes])
        try:
            self.write(registration)
            for _ in range(8):
                time.sleep(.55)
                if self.pipe.poll() is not None:break
                inventory=self.usb.shell("dumpsys","input")
                if "Fight Night QA Pad" in inventory:
                    blocks=re.split(r"(?m)(?=^  Device -?\d+:)",inventory)
                    pad_block=next((part for part in blocks if part.startswith("  Device ") and
                                    "Fight Night QA Pad" in part.splitlines()[0]),"")
                    lines=[line.strip() for line in pad_block.splitlines()
                           if any(label in line for label in ("KeyLayoutFile:","ConfigurationFile:","Identifier:"))]
                    if lines:(self.usb.out/"qa-pad-layout.txt").write_text("\n".join(lines)+"\n",encoding="utf-8")
                    self.analog="Android uinput gamepad";break
            else:pass
        except (OSError, RuntimeError, subprocess.SubprocessError):pass
        if self.analog!="Android uinput gamepad":self.close()
        self.usb.note("controller route", path=self.analog)

    def write(self, msg):
        self.pipe.stdin.write((json.dumps(msg)+"\n").encode())
        self.pipe.stdin.flush()

    def events(self,*triples):
        events = [n for triple in triples for n in triple]
        self.write(dict(id=1,command="inject",events=events+[0,0,0]))

    def press(self,key,hold=.14):
        if not self.usb.foreground():raise RuntimeError("Isolated game lost focus before controller input")
        self.reconnect()
        if self.pipe:
            if key in KEYS:typ,code,value=1,KEYS[key],1
            else:typ,(code,value)=3,HATS[key]
            self.events((typ,code,value));time.sleep(hold);self.events((typ,code,0))
        else:
            self.usb.shell("input","gamepad","keyevent","--longpress",str(ANDROID_KEYS[key]))
        time.sleep(.24)

    def combo(self,*,endurance=False):
        if not self.usb.foreground():raise RuntimeError("Isolated game lost focus before the punch combination")
        self.reconnect()
        if self.pipe:
            self.events((3,0,27000),(3,3,-17000),(3,4,-27000));time.sleep(.19)
            self.events((3,3,0),(3,4,0));time.sleep(.12)
            self.events((3,3,30000));time.sleep(.13)
            self.events((3,3,19000),(3,4,-26000));time.sleep(.16)
            self.events((3,0,0),(3,3,0),(3,4,0))
            if endurance:
                # Alternate right-stick punches, lateral movement and rest. Face buttons
                # may map to other retail actions under the selected controller configuration.
                self.events((3,0,-23000));time.sleep(.55);self.events((3,0,0));time.sleep(.45)
            else:
                time.sleep(.15)
        else:
            raw = self.usb.shell("am broadcast -a isolated.STICK_COMBO -n 'org.oxideshift.corner.usbqa/org.oxideshift.corner.UsbSweepCheck$Inspect'")
            if 'data="GESTURE"' not in raw:raise RuntimeError("No recognized joystick axis map for analog punches")
            time.sleep(.55)

    def reconnect(self):
        if self.pipe is not None and self.pipe.poll() is not None:
            self.close()
            self.analog="activity dispatch using handheld axis ranges"
            self.open()

    def close(self):
        if self.pipe:
            try:
                self.pipe.stdin.close()
                self.pipe.wait(timeout=4)
            except (OSError,subprocess.TimeoutExpired):
                self.pipe.kill();self.pipe.wait()
            self.pipe=None
        if self.output:
            self.output.close();self.output=None


class Journey:
    def __init__(self, usb, pad):
        self.usb,self.pad=usb,pad
        self.healing_venues=set()
        self.missed_treatments=[]
        self.refs={}
        self.root_refs={}
        for row in ROOT:
            path=usb.kit/"references"/("root-"+row.replace(" ","-")+".png")
            if path.is_file():
                with Image.open(path) as img:self.root_refs[row]=img.convert("L")
        for path in (usb.kit/"references").glob("*.jpg"):
            with Image.open(path) as img:self.refs[path.stem]=img.convert("RGB").resize((640,480))

    def png(self):
        return self.usb.call("exec-out","screencap","-p","-d",self.usb.displays[0])

    def views(self):
        with Image.open(BytesIO(self.png())) as img:
            rgb=img.convert("RGB")
        w,h=rgb.size
        widths={w,min(w,round(h*4/3))}
        return [rgb.crop(((w-n)//2,0,(w+n)//2,h)).resize((640,480)) for n in widths]

    def match(self,names,box,threshold):
        views=self.views()
        scores={name:min(max(ImageStat.Stat(ImageChops.difference(image.crop(box),self.refs[name].crop(box))).mean)
                         for image in views) for name in names}
        name=min(scores,key=scores.get)
        return name if scores[name]<threshold else None

    def start(self):
        self.usb.stop_test_app()
        s=self.usb.launch("game")
        if s["outcome"]=="PREPARING":
            self.usb.wait(lambda x:not x.get("busy",True),2400,"prepare")
            s=self.usb.launch("game")
        if s["outcome"] not in ("GAME","EXISTING_GAME"):
            raise RuntimeError("Launcher returned "+s["outcome"]+": "+s.get("setup",""))
        deadline=time.monotonic()+150
        selected=False
        next_start=0
        while time.monotonic()<deadline:
            s=self.usb.state()
            if s.get("root"):return
            if not selected and self.match(["profile"],(155,142,460,331),29):
                self.usb.capture("profile-picker");self.pad.press("Down");time.sleep(.45);self.pad.press("A");selected=True
            elif time.monotonic()>next_start and self.match(["title"],(150,90,480,351),33):
                self.pad.press("Start");next_start=time.monotonic()+3
            time.sleep(.6)
        raise RuntimeError("Did not observe root carousel after normal title/profile navigation")

    def root(self,target, *, restart=False):
        if restart or not self.usb.state().get("root"):self.start()
        last=None;last_press=0;deadline=time.monotonic()+30
        while time.monotonic()<deadline:
            state=self.usb.state()
            current=state.get("menu") if state.get("root") else None
            if len(self.root_refs)==len(ROOT) and state.get("root"):
                with Image.open(BytesIO(self.png())) as picture:
                    current=settled_caption(root_ink(picture),self.root_refs)
            observed={k:state.get(k) for k in ("root","front","menu","scene","serial","active","focused")}
            observed.update(target=target,visible=current,elapsedMs=round((time.monotonic()-(deadline-30))*1000))
            with (self.usb.out/"carousel-observations.jsonl").open("a",encoding="utf-8") as trace:
                trace.write(json.dumps(observed)+"\n")
            if current in ROOT:
                if current==target:return
                if current!=last or time.monotonic()-last_press>2:
                    self.pad.press("Up" if ROOT.index(current)>ROOT.index(target) else "Down")
                    last=current;last_press=time.monotonic()
            time.sleep(.22)
        raise RuntimeError("Did not find root row "+target)

    def gallery(self):
        self.start()
        seen=set()
        for row in ROOT:
            self.root(row);self.usb.capture("carousel-"+row.replace(" ","-"));seen.add(row)
        for cycle in range(1,4):
            self.root("Achievements");self.pad.press("A")
            self.usb.wait(lambda s:s.get("cabinet"),18,"Achievements")
            self.usb.capture("achievements-"+str(cycle));self.pad.press("Down");self.pad.press("Up");self.pad.press("B")
            self.usb.wait(lambda s:s.get("root") and not s.get("cabinet"),15,"return from Achievements")
            self.root("Music");self.pad.press("A")
            self.usb.wait(lambda s:s.get("cabinet"),18,"Music parent");self.usb.capture("music-"+str(cycle))
            self.pad.press("Down");self.pad.press("Down");self.pad.press("A")
            self.usb.wait(lambda s:s.get("front")==-1 or s.get("trax"),20,"retail EA Trax")
            self.track_row(1);self.pad.press("Down");self.track_row(2);self.pad.press("Up");self.track_row(1)
            self.usb.capture("ea-trax-"+str(cycle));self.pad.press("B")
            self.usb.wait(lambda s:s.get("cabinet") and s.get("front")!=-1,18,"Music parent after EA Trax")
            self.pad.press("B")
            self.usb.wait(lambda s:s.get("root") and not s.get("cabinet"),18,"carousel after Music")
        return {"root_rows":sorted(seen),"repeated_round_trips":3}

    def track_row(self,row):
        limit=time.monotonic()+20
        while time.monotonic()<limit:
            if selected_track(self.png())==row:return
            time.sleep(.4)
        raise RuntimeError("Missing visible selection arrows on EA Trax row "+str(row))

    def submenu(self, row):
        self.root(row,restart=True);self.pad.press("A")
        self.usb.wait(lambda st:st.get("active") and not st.get("root"),35,row+" entry")
        reference="entry-"+row.replace(" ","-")
        if reference in self.refs:
            # Game Modes retains the logo and film, so inspect its destination
            # rows. Other pages have distinctive title or menu artwork.
            box=(367,251,537,333) if row=="Game Modes" else (110,43,444,153)
            self.wait_picture(reference,box,23)
            time.sleep(.35)
            self.wait_picture(reference,box,23)
        else:
            self.usb.wait(lambda st:st.get("scene")=="MENU" and not st.get("root") and st.get("menu"),
                          35,row+" settled menu")
        s=self.usb.state()
        self.usb.capture("opened-"+row.replace(" ","-"))
        if row=="Career Mode":
            for i in range(5):
                st=self.usb.state();self.usb.capture("career-%02d"%i)
                if st.get("menu")=="Career Central" or st.get("career") not in (None,"null","None","NONE"):break
                self.pad.press("A");time.sleep(1.5)
        elif row=="Options":
            self.pad.press("A");time.sleep(1.5);self.usb.capture("options-gameplay")
        return {"menu":s.get("menu"),"career":s.get("career"),"scene":s.get("scene")}

    def wait_picture(self,name,box,threshold=20,timeout=30):
        if name not in self.refs:raise RuntimeError("Missing reference image "+name)
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            if self.match([name],box,threshold):return
            time.sleep(.35)
        raise RuntimeError("Could not verify "+name)

    def unlock_qa_venues(self):
        # NEWVIEW is recognized by the original Create Champ name handler. Its unlock
        # lasts only for the current process. All input stays in the disposable profile.
        self.root("Create Champ",restart=True)
        self.pad.press("A")
        self.wait_picture("champ-list",(118,39,270,80))
        # A reused QA profile can have older boxers above the new-boxer row.
        box=(146,136,247,155)
        template=self.refs["champ-new"].convert("L").filter(ImageFilter.FIND_EDGES).crop(box)
        def selected_new_boxer():
            for img in self.views():
                edges=img.convert("L").filter(ImageFilter.FIND_EDGES)
                for offset in [0]+[row*29+dy for row in range(1,9) for dy in range(-row,row+1)]:
                    l,top,r,bottom=box
                    button=img.crop((120,top+offset-3,149,bottom+offset+2))
                    if not retail_select_icon(button):continue
                    if same_glyphs(edges.crop((l,top+offset,r,bottom+offset)),template,.8):return True
            return False
        for _ in range(24):
            if selected_new_boxer():break
            self.pad.press("Down");time.sleep(.35)
        else:raise RuntimeError("The new-boxer row was not selected")
        self.pad.press("A")
        self.wait_picture("champ-info",(117,45,376,118))
        time.sleep(1.2)
        self.pad.press("A")
        self.wait_picture("champ-keyboard",(125,43,516,85))
        time.sleep(.6)
        # On the retail keyboard focus starts on A. X erases the default FIRST.
        for key,count in (("X",5),("Down",1),("Right",5),("A",1),("Up",2),("Left",3),
                          ("A",1),("Left",1),("A",1),("Down",2),("Right",2),("A",1),
                          ("Up",2),("Right",4),("A",1),("Left",5),("A",1),("Left",1),("A",1)):
            for _ in range(count):self.pad.press(key,.12)
        self.wait_picture("champ-newview",(131,99,194,124),13)
        self.pad.press("Start")
        self.wait_picture("champ-info",(117,45,376,118))
        time.sleep(1.2)
        self.pad.press("Start")
        self.wait_picture("champ-confirm",(186,109,459,174))
        self.pad.press("Down");self.pad.press("A")
        self.wait_picture("champ-list",(118,39,270,80))
        self.pad.press("B")
        self.usb.wait(lambda st:st.get("root") and st.get("menu") in ROOT,35,"root after Create Champ")

    def select_venue(self,venue,*,keep_session=False):
        locked=venue in ("garden","palace","staples") and "champ-newview" in self.refs
        if locked:self.unlock_qa_venues()
        self.root("Play Now",restart=not (locked or keep_session))
        self.pad.press("A")
        if "select-corner" in self.refs:
            self.wait_picture("select-corner",(130,42,320,78),25)
            time.sleep(1)
        else:
            time.sleep(1.5)
        self.pad.press("Left");time.sleep(.8)
        names=["venue-"+v for v in VENUES];box=(150,152,454,384)
        current=None
        for _ in range(8):
            current=self.match(names,box,25)
            if current:break
            self.pad.press("A");time.sleep(1.25)
        if current is None:raise RuntimeError("Venue artwork did not match the verified selector captures")
        for _ in range(7):
            if current=="venue-"+venue:break
            self.pad.press("Right");time.sleep(.8);current=self.match(names,box,25)
        if current!="venue-"+venue:raise RuntimeError("Could not verify selected venue "+venue)
        self.usb.capture("selected-"+venue)

    @contextmanager
    def clip(self,name,seconds,*,stop_on_exit=False):
        if getattr(self.usb,"recorder",None):
            yield
            return
        usb=self.usb;records=[]
        try:
            if usb.foreground():
                # Presentation windows need not carry the activity name in dumpsys window.
                # The isolated activity reports the logical ID of its own visible content.
                lower=usb.state().get("companionDisplay",-1)>0 and len(usb.displays)==2
                for i,display in enumerate(usb.displays[:2 if lower else 1]):
                    path="/data/local/tmp/fn3-qa-"+uuid4().hex+".mp4"
                    if not str(display).isdecimal():raise RuntimeError("Unexpected display identifier")
                    # exec preserves this shell's PID. The unique output filename identifies
                    # this recording even if Android has recycled that PID when the bell rings.
                    size=usb.capture_sizes.get(display)
                    dimensions=""
                    if size and min(size)>0:
                        scale=min(1,640/max(size))
                        width,height=(max(2,2*round(n*scale/2)) for n in size)
                        dimensions=f"--size {width}x{height} "
                    command=(f"echo $$ > {path}.pid && exec screenrecord --display-id {display} "
                             f"{dimensions}--bit-rate 2500000 --time-limit {int(seconds)} {path}")
                    proc=subprocess.Popen([*usb.adb,"shell",command],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
                    records.append((i,path,proc))
            yield
        finally:
            if stop_on_exit:
                for _,path,proc in records:
                    if proc.poll() is None:
                        usb.call("shell",f"p=$(cat {path}.pid 2>/dev/null) && test -r /proc/$p/cmdline && "
                                  f"tr '\\\\0' '\\\\n' < /proc/$p/cmdline | grep -Fxq {path} && kill -2 $p",check=False)
            for i,path,proc in records:
                try:proc.wait(timeout=8 if stop_on_exit else seconds+8)
                except subprocess.TimeoutExpired:proc.terminate();proc.wait()
                dest=usb.out/(name+"-display-"+str(i)+".mp4")
                usb.call("pull",path,dest,timeout=45,check=False)
                usb.shell("rm","-f",path,path+".pid")
                if dest.exists() and dest.stat().st_size<512:dest.unlink()

    def treat(self,venue,state,*,clock=time.monotonic,sleep=time.sleep):
        """Drive the lower-screen cutman stick for one treatment window.

        Returns "no-pad" if the pad is not available yet (try again on the next sample), "healed" once the
        game has counted a move and a release, or "missed" if the retail window closed before the gesture
        registered. A missed window is recorded in missed_treatments and does not end the bout. A gesture
        that does not register while the window is demonstrably still open raises, because that is a
        lower-touch defect and not a timing miss."""
        target=state.get("treatmentPad",[])
        if len(target)!=5 or not all(type(value) is int and value>=0 for value in target) or target[0]==0:
            return "no-pad"
        if not self.usb.foreground():
            raise RuntimeError("Isolated game lost focus before the cutman gesture")
        display,*points=target
        moves,releases=state.get("treatmentMoves",0),state.get("treatmentReleases",0)
        started=clock()
        last={}
        for attempt in (1,2):
            self.usb.shell("input","touchscreen","-d",display,"swipe",*points,320)
            end=clock()+2.5
            while True:
                last=self.usb.state()
                if last.get("treatmentMoves",0)>moves and last.get("treatmentReleases",0)>releases:
                    self.healing_venues.add(venue)
                    self.usb.capture("manual-heal-"+venue)
                    return "healed"
                if last.get("scene")!="TREATMENT" or clock()>=end:break
                sleep(.15)
            if last.get("scene")!="TREATMENT":break
        if last.get("scene")!="TREATMENT":
            self.missed_treatments.append(dict(venue=venue,round=state.get("round"),gestures=attempt,
                                               seconds=round(clock()-started,2),
                                               reason="treatment window closed before the gesture registered"))
            self.usb.note("treatment window missed",venue=venue,round=state.get("round"),gestures=attempt)
            return "missed"
        raise RuntimeError("Lower touch did not move and release the treatment stick while the treatment window was open ("
                           +str(attempt)+" gestures)")

    def endurance_settings(self):
        # These two reference screens are recorded from the real Options and Gameplay menus.
        if not {"options-gameplay-row","twelve-rounds-two-minutes"}.issubset(self.refs):
            raise RuntimeError("Endurance settings references are missing from this QA kit")
        self.root("Options",restart=True)
        self.pad.press("A")
        # Retail animates an orange backdrop under Options. Compare the
        # lettering and select glyph for the complete row independently.
        box=(146,132,226,154)
        row_reference=self.refs["options-gameplay-row"].convert("L").filter(ImageFilter.FIND_EDGES)
        badge=(121,131,146,159)
        target=row_reference.crop(box)
        def gameplay_selected(im):
            edges=im.convert("L").filter(ImageFilter.FIND_EDGES)
            return (retail_select_icon(im.crop(badge)) or same_glyphs(edges.crop(badge),row_reference.crop(badge),.8)) and same_glyphs(edges.crop(box),target,.8)
        if not wait_for_view(self.views,gameplay_selected,30):
            raise RuntimeError("Could not verify selected Gameplay row in Options")
        time.sleep(1.3)
        self.pad.press("A")
        self.wait_picture("twelve-rounds-two-minutes",(115,42,228,74),24)
        time.sleep(.75)
        reference=self.refs["twelve-rounds-two-minutes"].convert("L")

        def glyph_matches(image,box):
            a=image.convert("L").crop(box).filter(ImageFilter.FIND_EDGES)
            b=reference.crop(box).filter(ImageFilter.FIND_EDGES)
            # Discard high-contrast border pixels from the edge filter.
            region=(1,1,a.width-1,a.height-1)
            return same_glyphs(a.crop(region),b.crop(region),.86)

        def selected(image,row):
            y=110+row*23
            for x in (336,460):
                patch=image.crop((x-7,y-6,x+8,y+8)).convert("RGB")
                if sum(min(rgb)>188 and max(rgb)-min(rgb)<50 for rgb in patch.getdata())>=8:return True
            return False

        for row,number in [(3,(359,171,382,192)),(4,(358,193,378,214))]:
            for _ in range(16):
                views=self.views()
                active=next((im for im in views if selected(im,row)),None)
                if active is not None:break
                position=next((i for i in range(12) if any(selected(im,i) for im in views)),None)
                if position is None:raise RuntimeError("The selected Gameplay setting could not be read")
                self.pad.press("Down" if position<row else "Up");time.sleep(.45)
            else:raise RuntimeError("Number of rounds or round length could not receive focus")
            for _ in range(14):
                if any(glyph_matches(im,number) for im in self.views()):break
                self.pad.press("Right");time.sleep(.45)
            else:raise RuntimeError("Could not verify the requested endurance setting")
        self.usb.capture("endurance-12-rounds-2-minutes")
        self.pad.press("A")
        # Same animation-tolerant glyph check as on entry. A mean pixel-difference threshold against a
        # still reference failed here even though the game was correctly back on the Gameplay row.
        if not wait_for_view(self.views,gameplay_selected,30):
            raise RuntimeError("Could not verify the Options Gameplay row after saving the endurance setting")
        time.sleep(1)
        self.pad.press("B")
        deadline=time.monotonic()+35
        confirmed=False
        while time.monotonic()<deadline:
            if self.usb.state().get("root"):break
            if not confirmed and self.match(["qa-settings-save"],(184,175,449,240),22):
                # The normal retail prompt saves the gameplay settings in our disposable QA profile.
                self.pad.press("Down");self.pad.press("A");confirmed=True
            time.sleep(.4)
        else:raise RuntimeError("The game did not return to the carousel after saving test settings")
        return "Verified the retail values for twelve rounds and two minutes"

    def fight(self, venue, endurance=False, repeat=False):
        usb=self.usb;self.select_venue(venue,keep_session=endurance)
        if endurance:venue="endurance-"+venue
        if repeat:venue=venue+"-repeat"
        with self.clip("entrance-"+venue,145,stop_on_exit=True):
            self.pad.press("A")
            first=usb.wait(lambda s:s.get("scene")=="ROUND" and s.get("round")==1 and s.get("clock",0)>0,150,"opening bell")
            first_time=time.monotonic()
        if first.get("renderHeight")!=1080 or first.get("targetFps")!=60 or first.get("quality")!=2:
            raise RuntimeError("The fight did not start with High, 1080p and a 60 FPS target")
        usb.capture("bell-"+venue)
        # Log measured surface times and device counters during the extended fight.
        if endurance and first.get("clock",0)<110:
            raise RuntimeError("Endurance round clock did not confirm two-minute rounds")
        duration=2850 if endurance else 1500
        stats=usb.out/("performance-"+venue)
        reader=subprocess.Popen([sys.executable,str(Path(__file__).with_name("record_thor_health.py")),
                        "--adb",usb.adb[0],"--serial",usb.adb[-1],"--package","org.oxideshift.corner.usbqa",
                        "--seconds",str(duration),"--stop-file",str(usb.out/("performance-"+venue+".stop")),"--out",str(stats)],stdout=subprocess.DEVNULL)
        end=first_time+duration;seen=set();rounds=set();samples=[];tested_pause=False;last_photo=0;last_advance=0;got_result=False;bout_seconds=0;treated=set()
        timeline=(usb.out/("scenes-"+venue+".jsonl")).open("w",encoding="utf-8")
        try:
            while time.monotonic()<end:
                t=time.monotonic()
                before=time.monotonic_ns()
                s=usb.state();scene=s.get("scene","")
                timeline.write(json.dumps(dict(host_query_ns=[before,time.monotonic_ns()],scene=scene,
                                               device_elapsed_ns=s.get("deviceElapsedNs"),device_monotonic_ns=s.get("deviceMonotonicNs"),
                                               notice=s.get("fightNotice"),notice_uptime_ms=s.get("noticeUptimeMs"),
                                               round=s.get("round"),clock=s.get("clock"),frame=s.get("serial")))+"\n")
                timeline.flush()
                seen.add(scene)
                if not s.get("active"):raise RuntimeError("Game session exited in "+venue)
                if s.get("result") or s.get("finish") or scene=="FINAL":
                    bout_seconds=t-first_time
                    with self.clip("decision-"+venue,44):
                        usb.capture("verdict-"+venue);time.sleep(20)
                        usb.capture("judges-"+venue);time.sleep(24)
                    got_result=True;self.pad.press("A");time.sleep(2);usb.capture("postfight-"+venue);break
                if scene=="ROUND":
                    rounds.add(s.get("round"));samples.append([round(t-first_time,2),s.get("serial"),s.get("round"),s.get("clock"),s.get("punches"),s.get("audio")])
                    if not tested_pause:
                        self.pad.press("Start");p=usb.wait(lambda st:st.get("scene")=="PAUSE",12,"pause")
                        usb.capture("pause-"+venue);self.pad.press("Start");tested_pause=True
                    self.pad.combo(endurance=endurance)
                    if t-last_photo>45:usb.capture("round-"+venue+"-"+str(s.get("round")));last_photo=t
                elif scene=="CORNER":
                    if t-last_advance>10:
                        usb.capture("corner-"+venue+"-"+str(s.get("round")));self.pad.press("A");last_advance=t
                elif scene=="TREATMENT":
                    # Act on the first usable sample of each window. The retail window lasts only about
                    # 4-6 s, and waiting on the generic 4 s advance gate could leave too little of it.
                    if s.get("round") not in treated and self.treat(venue,s)!="no-pad":
                        treated.add(s.get("round"));last_advance=t
                elif t-last_advance>25 and scene not in ("LOADING","INTRO"):
                    usb.capture("between-"+venue+"-"+str(s.get("round")));self.pad.press("A");last_advance=t
                time.sleep(.25)
        finally:
            timeline.close()
            if reader.poll() is None:
                # Finish this sampler via a flag so its timing stream is flushed and its children close.
                (usb.out/("performance-"+venue+".stop")).touch()
                try:reader.wait(timeout=18)
                except subprocess.TimeoutExpired:reader.terminate();reader.wait()
            (usb.out/("bout-"+venue+".json")).write_text(json.dumps(samples),encoding="utf-8")
        if not got_result:raise RuntimeError("Bout did not reach a verified ending; captured "+str(len(samples))+" in-ring samples")
        if reader.returncode!=0 or not (stats/"summary.json").is_file():raise RuntimeError("Performance recorder did not finish for "+venue)
        surfaces=json.loads((stats/"summary.json").read_text(encoding="utf-8"))["presented_surfaces"]
        if not any(row.get("timing") for row in surfaces.values()):raise RuntimeError("No measured surface intervals for "+venue)
        final_punches=next((v[4] for v in reversed(samples) if v[4]),None)
        return {"rounds":sorted(x for x in rounds if x is not None),"scenes":sorted(seen),"seconds":round(time.monotonic()-first_time),"result":True,"bell_to_verdict_seconds":round(bout_seconds,2),"samples":len(samples),"surfaces":surfaces,
                "last_live_punch_totals":final_punches,"renderer":first.get("renderer"),"renderer_selection":first.get("rendererSelection"),
                "render_height":first["renderHeight"],"target_fps":first["targetFps"],"preset":"High"}

    def sleep_wake(self):
        self.root("Play Now",restart=True)
        self.usb.shell("input","keyevent","KEYCODE_SLEEP");time.sleep(4)
        self.usb.shell("input","keyevent","KEYCODE_WAKEUP");time.sleep(3)
        self.usb.wait(lambda s:s.get("root") and s.get("active"),25,"resumed session")
        self.root("ESPN Classic")
        self.usb.capture("wake-controller")
        return "Same game resumed and received a directional input"

    def route(self,phase,field,expected):
        self.usb.stop_test_app()
        self.usb.launch("setup")
        self.usb.wait(lambda s:s.get("setupVisible") and s.get("focused") and not s.get("busy"),20,"visible launcher before "+phase)
        receipt=self.usb.launch(phase)
        if receipt.get("outcome") not in ("ROUTE_SELECTED","ROUTE_SELECTING"):
            raise RuntimeError("Launcher returned "+str(receipt.get("outcome"))+" for "+phase)
        saved="saved"+field[0].upper()+field[1:]
        self.usb.wait(lambda s:s.get("outcome")=="ROUTE_SELECTED" and s.get(field)==expected and s.get(saved)==expected,
                      12,phase+" saved setting")
        if getattr(self.usb,"recorder",None):self.usb.recorder.hold_settings_view()
        self.usb.stop_test_app()
        state=self.usb.launch("setup")
        if (state.get(field),state.get(saved))!=(expected,expected):
            raise RuntimeError(phase+" setting did not survive relaunch")
        # Alpha 79.5.1 recorded restore-ordinary-renderer as passed with null evidence. Return what was checked.
        return {"phase":phase,"field":field,"selected":expected,"saved":expected,"after_relaunch":state.get(field)}

    def single_screen(self):
        try:
            self.route("primary","displaySelection",1)
            self.root("Achievements",restart=True);self.pad.press("A")
            self.usb.wait(lambda s:s.get("cabinet") and "companionDisplay" not in s,20,"single-display gallery")
            self.usb.capture("single-screen-achievements");self.pad.press("B")
            self.usb.wait(lambda s:s.get("root") and not s.get("cabinet"),20,"single-display return")
        except (RuntimeError,subprocess.SubprocessError):
            try:
                self.usb.capture("single-display-before-restoration")
                self.route("automatic","displaySelection",0)
            except (RuntimeError,subprocess.SubprocessError) as restoration:
                self.usb.note("display restoration failed",reason=str(restoration))
            raise
        self.route("automatic","displaySelection",0)
        self.root("Play Now",restart=True)
        state=self.usb.wait(lambda s:s.get("companionDisplay",-1)>0,15,"automatic second display")
        return {"primary_gallery":True,"restored_companion_display":state["companionDisplay"]}

    def renderer_window(self,phase,selection):
        self.route(phase,"rendererSelection",selection)
        self.select_venue("windy")
        self.pad.press("A")
        first=self.usb.wait(lambda s:s.get("scene")=="ROUND" and s.get("round")==1 and s.get("clock",0)>0,150,"opening bell")
        if (first.get("quality"),first.get("renderHeight"),first.get("targetFps"),first.get("rendererSelection"))!=(2,1080,60,selection):
            actual={k:first.get(k) for k in ("quality","renderHeight","targetFps","rendererSelection","renderer")}
            raise RuntimeError("Renderer comparison has unequal settings: "+str(actual))
        out=self.usb.out/("renderer-"+phase)
        reader=subprocess.Popen([sys.executable,str(Path(__file__).with_name("record_thor_health.py")),
                      "--adb",self.usb.adb[0],"--serial",self.usb.adb[-1],"--package","org.oxideshift.corner.usbqa",
                      "--seconds","45","--out",str(out)],stdout=subprocess.DEVNULL)
        samples=[]
        try:
            until=time.monotonic()+45
            while time.monotonic()<until:
                s=self.usb.state()
                if s.get("scene")=="ROUND" and s.get("round")==1:
                    samples.append({k:s.get(k) for k in ("serial","clock","punches","renderer")})
                    self.pad.combo(endurance=True)
                else:time.sleep(.5)
            if reader.wait(timeout=25)!=0 or not (out/"summary.json").is_file():
                raise RuntimeError("Presentation recorder failed")
        finally:
            if reader.poll() is None:reader.terminate();reader.wait()
            (self.usb.out/(phase+"-round-samples.json")).write_text(json.dumps(samples),encoding="utf-8")
        if len(samples)<5 or len({s["clock"] for s in samples})<4:
            raise RuntimeError("Insufficient live boxing samples")
        return {"venue":"Windy City","quality":"High","height":1080,"target":60,"window_seconds":45,
                "round_samples":len(samples),"surface_trace":out.name}


def rocked_verdict(events):
    """79.5.2 confines the ROCKED spray away from the stamina rows. Pass only on real, measured events."""
    if not events:
        return {"status":"unverified","reason":"No ROCKED notice was shown during the fights in this run","events":[]}
    if any(e.get("clear") is False for e in events):
        return {"status":"failed","reason":"ROCKED lane overlapped the protected clock/meter band","events":events}
    if any(e.get("clear") is None for e in events):
        return {"status":"unverified","reason":"Lower layout bounds were missing for some ROCKED notices","events":events}
    return {"status":"passed","evidence":{"events":events}}


def sweep(usb):
    pad=Controls(usb)
    try:
        pad.open()
        run=Journey(usb,pad)
        continuing=getattr(usb,"continue_gameplay",False)
        if getattr(usb,"paired",False) and not continuing:
            usb.case("warehouse-crash-retest",lambda:run.fight("warehouse",repeat=True))
        if not continuing:
            usb.case("root-music-and-achievements",run.gallery)
            for row in ("ESPN Classic","Game Modes","Create Champ","Options","Career Mode"):
                usb.case("navigate-"+row.replace(" ","-"),lambda r=row:run.submenu(r))
        def high_settings():
            usb.stop_test_app()
            selected=usb.launch("high1080")
            if selected.get("outcome")!="GRAPHICS_SELECTED":raise RuntimeError("Launcher did not accept the graphics preset")
            if getattr(usb,"recorder",None):usb.recorder.hold_settings_view()
            usb.stop_test_app()
            saved=usb.launch("setup")
            if (saved.get("quality"),saved.get("requestedHeight"),saved.get("targetFps"))!=(2,1080,60):
                raise RuntimeError("Graphics settings did not survive relaunch")
            if getattr(usb,"recorder",None):usb.recorder.hold_settings_view()
            return "High, 1080p and 60 FPS persisted through relaunch"
        usb.case("graphics-settings-persistence",high_settings)
        if run.root_refs:
            usb.case("single-screen-and-restoration",run.single_screen)
            for mode,selection in (("ordinary",0),("shared",3)):
                usb.case("windy-renderer-"+mode,lambda m=mode,v=selection:run.renderer_window(m,v))
            usb.case("restore-ordinary-renderer",lambda:run.route("ordinary","rendererSelection",0))
        for venue in VENUES:
            usb.case("fight-"+venue,lambda v=venue:run.fight(v))
        usb.results["lower-screen-manual-healing"]={"status":"passed" if run.healing_venues else "unverified",
                                                    "verified_venues":sorted(run.healing_venues),
                                                    "unverified_venues":sorted(set(VENUES)-run.healing_venues),
                                                    "missed_windows":run.missed_treatments}
        usb.save_results()
        usb.case("sleep-wake-pad",run.sleep_wake)
        if "twelve-rounds-two-minutes" in run.refs and usb.case("endurance-settings",run.endurance_settings):
            def extended():
                result=run.fight("aragon",endurance=True)
                if result["bell_to_verdict_seconds"]<1200 or len(result["rounds"])<2:
                    raise RuntimeError("The bout ended after "+str(result["bell_to_verdict_seconds"])+" seconds. See its measured performance summary.")
                return result
            usb.case("continuous-20-minute-fight",extended)
        if getattr(usb,"wants",lambda name:True)("rocked-stamina-row"):
            usb.results["rocked-stamina-row"]=rocked_verdict(getattr(usb,"rocked_events",[]))
            usb.save_results()
    finally:
        pad.close()
