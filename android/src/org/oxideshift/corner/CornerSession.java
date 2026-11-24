package org.oxideshift.corner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Pipe client shared by the handheld and the desktop integration test. */
public final class CornerSession {
    private static final Pattern THREAD = Pattern.compile("PsCreateSystemThreadEx #(\\d+): routine=(0x[0-9A-Fa-f]+)");
    private static final Pattern BRIDGE = Pattern.compile("Kernel thunk bridge: (\\d+/\\d+) resolved");
    private static final Pattern FRAME = Pattern.compile("framebuffer submission (\\d{1,15})(?!\\d)");
    private static final Pattern INDIRECT_CALL = Pattern.compile("\\[Fight Night\\] (?:invalid|missing) indirect target 0x([0-9A-Fa-f]{8}), ESP 0x[0-9A-Fa-f]{8}");
    private static final Pattern GUEST_STACK = Pattern.compile("\\[Fight Night\\] guest stack ([0-9A-Fa-f]{8})(?: [0-9A-Fa-f]{8}){7}");
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private final String[] command;
    private final File directory;
    private final java.util.function.LongSupplier uptime;
    private Process process;
    private boolean sleeping;
    private ScheduledExecutorService clock;
    private ScheduledFuture<?> heartbeat;

    synchronized void setSleeping(boolean value){
        if(sleeping==value || terminal || cancelled)return;
        sleeping=value;
        syncTrack();
        commands.offer(value?"SLEEP":"WAKE");
        if(!value){received=System.nanoTime();flushPad();}
        updateHeartbeat();
    }
    private void updateHeartbeat(){
        if(clock==null)return;
        if(sleeping){
            if(heartbeat!=null)heartbeat.cancel(false);
            heartbeat=null;
        }else if(heartbeat==null){
            heartbeat=clock.scheduleWithFixedDelay(this::ping,250,250,TimeUnit.MILLISECONDS);
        }
    }
    private File frameFile;
    private volatile java.nio.ByteBuffer sharedVideo;
    java.nio.ByteBuffer sharedVideo(){return sharedVideo;}
    private void receiveVideo(){
        if(frameFile==null || android.os.Build.VERSION.SDK_INT<33)return;
        try(android.net.LocalSocket socket=new android.net.LocalSocket()){
            socket.connect(new android.net.LocalSocketAddress(frameFile.getAbsolutePath()+".sock",
                    android.net.LocalSocketAddress.Namespace.FILESYSTEM));
            socket.setSoTimeout(1500);
            if(socket.getPeerCredentials().getUid()!=android.os.Process.myUid() || socket.getInputStream().read()!='F')return;
            FileDescriptor[] descriptors=socket.getAncillaryFileDescriptors();
            if(descriptors==null)return;
            try{
                if(descriptors.length!=1)return;
                try(android.os.ParcelFileDescriptor copy=android.os.ParcelFileDescriptor.dup(descriptors[0]);
                        android.os.SharedMemory region=android.os.SharedMemory.fromFileDescriptor(copy)){
                    int stride=(renderHeight*4/3)*renderHeight*4+8;
                    if(region.getSize()!=16+3*stride)return;
                    sharedVideo=region.mapReadOnly().order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    socket.getOutputStream().write('Y');
                }
            }finally{
                for(FileDescriptor fd:descriptors)android.system.Os.close(fd);
            }
        }catch(IOException | android.system.ErrnoException ignored){/* The host can still publish to frameFile. */}
    }
    private int renderHeight=480, samples=1, anisotropy=2;
    private boolean software;
    private boolean widescreen=true;
    private volatile FightBroadcast.Fighters fighters;
    FightBroadcast.Fighters fighters(){return fighters;}
    private volatile int recovery=-1;
    int recoverySeconds(){return recovery;}
    private volatile String result="", finish="";
    private volatile FightBroadcast.Card lastScored;
    private volatile FightBroadcast.Bout lastTotals;
    private volatile FightBroadcast.Fighters lastNames;
    String result(){return result;}
    String finish(){return finish;}
    private boolean resultsDismissed;
    private volatile int boutNumber;
    int boutNumber(){return boutNumber;}
    private boolean hasResult(){return !result.isEmpty() || !finish.isEmpty();}
    synchronized boolean showingResult(){return hasResult() && !resultsDismissed;}
    synchronized void leaveResults(){if(hasResult()){resultsDismissed=true;cameraShot=0;}}
    synchronized boolean inLiveBout(){return judging!=null && judging.round>0 && !hasResult();}
    FightBroadcast.Card scored(){return lastScored;}
    FightBroadcast.Bout totals(){return lastTotals;}
    FightBroadcast.Fighters lastNames(){return lastNames;}
    private final BoutAnnouncements announcements=new BoutAnnouncements();
    BoutAnnouncements.Notice callout(){return announcements.current(uptime.getAsLong());}
    volatile boolean lowerCallouts;
    private BoutAnnouncements.Notice lastPulse;
    synchronized void calloutShown(BoutAnnouncements.Notice notice){
        long now=uptime.getAsLong();
        if(notice==null || notice==lastPulse || notice.isCount() || notice!=announcements.current(now)
                || now<notice.at || now-notice.at>=BoutAnnouncements.DURATION)return;
        lastPulse=notice;
        if(motors!=null)motors.callout();
    }
    volatile boolean cabinetOpen;
    volatile boolean retailMusicRoute,browsingTrax,leaveTrax;
    private boolean cabinetRequested,musicRequested;
    synchronized boolean takeMusicRequest(){boolean pending=musicRequested;musicRequested=false;return pending;}
    private Runnable cabinetListener;
    synchronized void onCabinet(Runnable listener){cabinetListener=listener;}
    synchronized boolean takeCabinetRequest(){boolean pending=cabinetRequested;cabinetRequested=false;return pending;}
    private File menuDisc;
    interface Guide { byte[] load(String name,int length)throws IOException; }
    Guide thorGuide;
    void installMenuFrom(File disc){menuDisc=disc;}
    Achievements achievements;
    private int achievementRound,trainingDrill;
    final CareerDesk career=new CareerDesk();
    private volatile FightBroadcast.Camp camp;
    FightBroadcast.Camp camp(){return camp;}
    private java.util.function.Consumer<String> timingLog;
    private long lastTimingLog;
    private String impactTiming="";
    private volatile String punchPace="";
    String punchPace(){return punchPace;}
    private int shotNumber;
    private volatile int cameraShot;
    private volatile long shotUntil;
    int cameraShot(){int shot=cameraShot;return shot!=0 && uptime.getAsLong()<shotUntil?shot:0;}
    private void startShot(long duration){shotUntil=uptime.getAsLong()+duration;cameraShot=++shotNumber;}
    private volatile FightBroadcast.Bout bout;
    FightBroadcast.Bout bout(){return bout;}
    private int introMovie;
    private boolean starting=true;
    synchronized boolean openingSlate(){return starting || introMovie!=0;}
    synchronized void reachedMenu(){advance(Milestone.MENU);starting=false;introMovie=0;if(!inLiveBout())lastEvent="MENU";}
    private long lastSongEvent;
    private RoundMusic musicPlayer;
    private boolean fightMusic,expandedMusic,bankReady,musicMenu=true;
    private boolean roundStarted;
    boolean fullMusicLevel(){return !roundStarted;}
    android.content.res.AssetManager musicAssets;
    private String retailStem="";
    private Runnable songObserver;
    private java.util.function.Consumer<String> menuSelection;
    synchronized void onMenuSelection(java.util.function.Consumer<String> observe){menuSelection=observe;}
    synchronized void previewTrack(String stem){
        if(continuousMusic() && stem.equals(retailStem))musicPlayer.requested(stem,true);
    }
    synchronized void onSong(Runnable observer){songObserver=observer;}
    private void publishSong(FightBroadcast.Song value){
        song=value;
        if(songObserver!=null)songObserver.run();
    }
    synchronized boolean continuousMusic(){return musicPlayer!=null && bankReady && (fightMusic || musicMenu);}
    private void syncTrack(){
        if(musicPlayer!=null)musicPlayer.transport(retailStem,bankReady && !sleeping && introMovie==0 && (fightMusic || musicMenu),fullMusicLevel());
    }
    synchronized void musicOptions(boolean fight,boolean expanded){
        if(terminal || cancelled || menuDisc==null || (fightMusic==fight && expandedMusic==expanded))return;
        fightMusic=fight;expandedMusic=expanded;
        commands.offer(fight?"TRAXCUES 1":"TRAXCUES 0");
        if(musicPlayer!=null && (fight || expanded)){
            musicPlayer.expanded(expanded);syncTrack();return;
        }
        bankReady=false;
        if(!fight && !expanded){
            if(musicPlayer!=null)musicPlayer.close();
            musicPlayer=null;commands.offer("TRAXVOICE 0");publishSong(null);return;
        }
        musicPlayer=new RoundMusic(menuDisc,musicAssets,expanded,new RoundMusic.Listener(){
            public void prepared(RoundMusic player){
                synchronized(CornerSession.this){if(player==musicPlayer)commands.offer("TRAXVOICE 1");}
            }
            public void audible(RoundMusic player,String stem){
                synchronized(CornerSession.this){
                    FightBroadcast.Song track=FightBroadcast.song(stem);
                    if(player==musicPlayer && bankReady && track!=null)publishSong(track.at(uptime.getAsLong()));
                }
            }
            public void failed(RoundMusic player){
                synchronized(CornerSession.this){
                    if(player==musicPlayer){musicPlayer=null;bankReady=false;commands.offer("TRAXVOICE 0");publishSong(null);}
                }
            }
        });
        musicPlayer.start();
    }
    synchronized int introMovie(){return introMovie;}
    private int trax;
    synchronized void menuCancelSound(){
        if(running && !terminal && !sleeping)commands.offer("MENUCANCEL");
    }
    synchronized void setTrax(boolean ready){
        int route=ready?2:0;
        if(trax==route)return;
        trax=route;
        if(running && !terminal)commands.offer("TRAX "+trax);
    }
    private volatile FightBroadcast.Card judging;
    private volatile FightBroadcast.Song song;
    FightBroadcast.Card judging(){return judging;}
    FightBroadcast.Song song(){return song;}
    private String renderer="Starting",audio="Waiting";
    private final CornerCommands commands=new CornerCommands();
    private boolean hello, running, cancelled, terminal, started;
    private int sequence, replies;
    private long sent, received, latency;
    private String padState;
    private boolean padPending;
    private String phase = "Standby", bridge = "—", thread = "—", frames = "0", reason = "";

    public CornerSession(File directory, String... command) {
        this(android.os.SystemClock::uptimeMillis,directory,command);
        timingLog=message->android.util.Log.i("FightNightTiming",message);
    }
    CornerSession(java.util.function.LongSupplier uptime,File directory,String... command){
        this.uptime=uptime;
        this.directory = directory;
        this.command = command.clone();
    }

    public synchronized Map<String, Object> snapshot() {
        boolean late = hello && System.nanoTime() - received > 2_000_000_000L;
        String link = terminal ? "Closed" : sleeping ? "Sleeping" : late ? "Stale" : replies > 0 ? "Live" : started ? "Connecting" : "Offline";
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("link", link); value.put("phase", phase); value.put("bridge", bridge);
        value.put("thread", thread); value.put("frames", frames); value.put("replies", replies);
        value.put("latency", replies == 0 ? "—" : latency + " ms");
        value.put("renderer", renderer); value.put("audio",audio);
        value.put("reason", reason); value.put("events", new ArrayList<>(events));
        return value;
    }

    public synchronized boolean active() { return started && !terminal; }
    public synchronized void setFrameFile(File file){
        if(started)throw new IllegalStateException("Session already started");
        frameFile=file;
    }
    public synchronized void setWidescreen(boolean enabled){
        if(started)throw new IllegalStateException("Session already started");
        widescreen=enabled;
    }
    synchronized int presentationHeight(){return renderHeight;}
    public synchronized void setVideo(int height,int quality,boolean compatibility){
        if(started)throw new IllegalStateException("Session already started");
        software=compatibility;renderHeight=software?480:Math.max(480,Math.min(1080,height));
        GraphicsPreset preset=GraphicsPreset.stored(quality);
        samples=preset.samples;anisotropy=preset.anisotropy;
        renderer=software?"Software":"Starting Vulkan";
    }
    public synchronized void controller(String state){
        if(state.equals(padState))return;
        padState=state;
        padPending=true;
        if(running && !terminal)flushPad();
    }
    private void flushPad() {
        if(padPending && running && !sleeping){commands.offer(padState);padPending=false;}
    }

    public synchronized void stop() {
        if (!active()) return;
        cancelled = true;
        if (process != null) process.destroyForcibly();
    }

    private void record(String text) {
        if (events.size() == 5) events.removeFirst();
        events.addLast(text);
    }

    private synchronized void ping() {
        if (!hello || sleeping || terminal || cancelled || process == null || sequence != replies) return;
        sent=System.nanoTime();
        commands.offer("PING " + (++sequence));
    }

    interface Motors {
        void changed(int left,int right,int durationMs);
        default void count(int value){}
        default void callout(){}
    }
    private enum Milestone { LAUNCH, LINKED, KERNEL, GUEST, FRAME, INTRO, MENU, BOUT }
    private Milestone milestone=Milestone.LAUNCH;
    private long runStarted;
    private Long lastFrame;
    private final EnumMap<Milestone,Long> firstSeen=new EnumMap<>(Milestone.class);
    private void advance(Milestone seen){
        if(!firstSeen.containsKey(seen))firstSeen.put(seen,Math.max(0,uptime.getAsLong()-runStarted));
        if(seen.ordinal()>milestone.ordinal())milestone=seen;
    }
    static final class CrashState {
        final String milestone,target;
        final long elapsedMs;
        final Long lastFrame;
        final int samples,anisotropy;
        final Map<String,Object> details=new LinkedHashMap<>();
        String pc="unknown";
        CrashState(CornerSession s,int exit){
            milestone=s.milestone.name().toLowerCase(Locale.ROOT);
            target=exit==40 || exit==41?s.callTarget:null;
            elapsedMs=Math.max(0,s.uptime.getAsLong()-s.runStarted);
            lastFrame=s.lastFrame;samples=s.samples;anisotropy=s.anisotropy;
            String kind="unknown";
            if(exit==41 && !"unknown".equals(s.guestReturn)){pc=s.guestReturn;kind="guest_stack_word";}
            else if(exit==42 && s.unsupportedPc!=null){pc=s.unsupportedPc;kind="guest_instruction";}
            else if(!"unknown".equals(s.faultPc)){pc=s.faultPc;kind="native_module_offset";}
            details.put("address_kind",kind);
            if(s.renderer.equals("Vulkan"))details.put("active_renderer","vulkan");
            if(s.renderer.equals("software (Vulkan unavailable)"))details.put("active_renderer","software");
            if(exit>128 && exit<=192)details.put("signal_from_exit",exit-128);
            if(s.bridge.matches("[0-9]{1,6}/[0-9]{1,6}"))details.put("kernel_imports",s.bridge);
            if(s.bout!=null && s.bout.seconds>=0)details.put("round_seconds",s.bout.seconds);
            Matcher a=Pattern.compile("gaps (\\d{1,15}) drops (\\d{1,15}) xruns (-?\\d{1,15})").matcher(s.audio);
            if(a.matches()){
                details.put("audio_gaps",Long.parseLong(a.group(1)));details.put("audio_drops",Long.parseLong(a.group(2)));
                if(!a.group(3).startsWith("-"))details.put("audio_xruns",Long.parseLong(a.group(3)));
            }
            for(Map.Entry<Milestone,Long> entry:s.firstSeen.entrySet())
                details.put(entry.getKey().name().toLowerCase(Locale.ROOT)+"_ms",entry.getValue());
        }
    }
    interface CrashListener {void stopped(int exit,String pc,String lastEvent,int round,int height,boolean software,CrashState state);}
    private CrashListener crashListener;
    private String lastEvent="STARTUP",faultPc="unknown";
    private String callTarget;
    private String guestReturn="unknown";
    private String unsupportedPc;
    synchronized void onCrash(CrashListener listener){crashListener=listener;}
    private boolean retailRumble;
    private Motors motors;
    synchronized void motors(Motors listener){motors=listener;}
    synchronized void line(String line) throws IOException {
        if(line.equals("FNR3/1 MUSICSCENE 0") || line.equals("FNR3/1 MUSICSCENE 1")){
            musicMenu=line.endsWith("1");
            if(musicMenu)roundStarted=false;
            syncTrack();return;
        }
        if(line.equals("FNR3/1 MUSICMENU OPEN")){
            if(!cabinetOpen && !cabinetRequested && !musicRequested){musicRequested=true;if(cabinetListener!=null)cabinetListener.run();}
            return;
        }
        if(line.equals("FNR3/1 CABINET OPEN")){
            if(!cabinetOpen && !cabinetRequested){cabinetRequested=true;if(cabinetListener!=null)cabinetListener.run();}
            return;
        }
        if(line.startsWith("FNR3/1 FAULT ") && line.substring(13).matches("[0-9a-f]{8}")){
            faultPc=line.substring(13);return;
        }
        if(line.startsWith("[Fight Night] invalid indirect target ") || line.startsWith("[Fight Night] missing indirect target ")){
            Matcher call=INDIRECT_CALL.matcher(line);
            if(call.matches())callTarget=call.group(1).toLowerCase(Locale.ROOT);
            return;
        }
        if(line.startsWith("[Fight Night] unsupported ")){
            Matcher unsupported=Pattern.compile("\\[Fight Night\\] unsupported .{1,160} at 0x([0-9a-fA-F]{8})").matcher(line);
            if(unsupported.matches())unsupportedPc=unsupported.group(1).toLowerCase(Locale.ROOT);
        }
        if(callTarget!=null && line.startsWith("[Fight Night] guest stack ")){
            Matcher stack=GUEST_STACK.matcher(line);
            if(stack.matches())guestReturn=stack.group(1).toLowerCase(Locale.ROOT);
            return;
        }
        if(line.equals("FNR3/1 INTRO 1") || line.equals("FNR3/1 INTRO 2") || line.equals("FNR3/1 INTRO 0")){
            introMovie=line.charAt(line.length()-1)-'0';if(introMovie!=0)advance(Milestone.INTRO);starting=false;syncTrack();return;
        }
        if(line.equals("FNR3/1 ASLEEP") || line.equals("FNR3/1 AWAKE")){
            if(timingLog!=null)timingLog.accept(line.substring(7));
            return;
        }
        if(line.startsWith("FNR3/1 SHOT ")){
            if(!hasResult() && bout!=null && bout.seconds==0 && judging!=null && judging.last()==bout.round)startShot(15000);
            return;
        }
        if(line.equals("FNR3/1 BOUTBEGIN")){
            cameraShot=0;
            boutNumber++;musicMenu=false;roundStarted=false;bout=null;syncTrack();
            reachedMenu();advance(Milestone.BOUT);lastEvent="BOUT";
            result=finish="";resultsDismissed=false;lastScored=null;lastTotals=null;lastNames=fighters;
            announcements.reset();lastPulse=null;achievementRound=0;
            if(achievements!=null)achievements.beginBout();
            return;
        }
        if(line.startsWith("FNR3/1 EVENTWIN ")){
            if(achievements!=null && boutNumber>0 && "WON!!".equals(result)){
                String id=line.substring("FNR3/1 EVENTWIN ".length());
                if(id.length()==1 && id.charAt(0)>='2' && id.charAt(0)<='9')achievements.careerEventWin(id.charAt(0)-'0');
            }
            return;
        }
        if(line.startsWith("FNR3/1 PLAYERSTATS ")){
            if(achievements!=null && boutNumber>0){
                String[] fields=line.substring("FNR3/1 PLAYERSTATS ".length()).split(" ");
                int side=-1,scored=0,suffered=0,haymakers=0;
                if(fields.length==4)try{
                    side=Integer.parseInt(fields[0]);scored=Integer.parseInt(fields[1]);
                    suffered=Integer.parseInt(fields[2]);haymakers=Integer.parseInt(fields[3]);
                }catch(NumberFormatException e){side=-1;}
                if((side!=0 && side!=1) || scored<0 || suffered<0 || haymakers<0)side=-1;
                achievements.playerStats(side,scored,suffered,haymakers);
                achievements.result(result,finish,achievementRound);
            }
            return;
        }
        if(line.startsWith("FNR3/1 CALLOUT ")){
            String event=line.substring(15);
            if(event.matches("DOWN|GETUP|KO|TKO|HAYMAKER|STAGGER|WON|LOST|FINAL"))lastEvent=event;
            if(event.equals("WON") || event.equals("LOST")){cameraShot=0;result=event+"!!";}
            if(event.equals("FINAL")){cameraShot=0;result="Final bell";}
            if(event.equals("KO") || event.equals("TKO")){cameraShot=0;finish=event;}
            if(achievements!=null && boutNumber>0 && (event.equals("WON") || event.equals("KO") || event.equals("TKO")))
                achievements.result(result,finish,achievementRound);
            if(achievements!=null && boutNumber>0 && event.equals("STAGGER"))achievements.stagger();
            if(announcements.event(event,uptime.getAsLong()) && !lowerCallouts && !retailRumble && motors!=null &&
                    (event.equals("DOWN") || event.equals("GETUP") || event.equals("KO") || event.equals("TKO")))
                motors.changed(49000,32000,145);
            return;
        }
        if(line.startsWith("FNR3/1 COUNT ")){
            try{
                int n=Integer.parseInt(line.substring(13));
                if(n>=1 && n<=10)lastEvent="COUNT";
                if(announcements.count(n,uptime.getAsLong()) && motors!=null)motors.count(n);
            }catch(NumberFormatException ignored){}
            return;
        }
        if(line.startsWith("FNR3/1 RUMBLE ")){
            String[] v=line.substring(14).split(" ");
            try{
                if(v.length==2){
                    int a=Integer.parseInt(v[0]),b=Integer.parseInt(v[1]);
                    if((a|b)>=0 && a<=65535 && b<=65535){
                        if((a|b)!=0)retailRumble=true;
                        if(motors!=null)motors.changed(a,b,0);
                    }
                }
            }catch(NumberFormatException ignored){}
            return;
        }
        if(line.startsWith("FNR3/1 CAREERPAGE ") || line.startsWith("FNR3/1 OFFER ") ||
                line.startsWith("FNR3/1 TRAINCHOICE ") || line.startsWith("FNR3/1 TRAINSTATS ") || line.startsWith("FNR3/1 SHOP ") || line.startsWith("FNR3/1 TRAINER ") || line.startsWith("FNR3/1 TROPHY ") || line.startsWith("FNR3/1 CASE ")){
            int split=line.indexOf(' ',7);
            career.accept(line.substring(7,split),line.substring(split+1));
            CareerDesk.Page page=career.page;
            if(line.startsWith("FNR3/1 CAREERPAGE ")){
                if(page!=null && page.is("drill"))trainingDrill=page.index;
                else if(page==null || !page.is("train") || page.index!=1)trainingDrill=0;
            }
            if(achievements!=null && page!=null){
                if(page.is("train") && page.index==1)achievements.training(career.training(false),trainingDrill);
                if(career.page.is("trophycase"))achievements.trophies(career.trophies());
            }
            return;
        }
        if(line.startsWith("FNR3/1 CAMP ")){
            camp=FightBroadcast.Camp.parse(line.substring(12));
            if(achievements!=null)achievements.record(camp);
            return;
        }
        if(line.startsWith("FNR3/1 FIGHTERS ")){
            String payload=line.substring("FNR3/1 FIGHTERS ".length());
            FightBroadcast.Fighters names=FightBroadcast.Fighters.parse(payload);
            if(names!=null || payload.equals("- -"))fighters=names;
            if(names!=null && (lastNames==null || (result.isEmpty() && finish.isEmpty())))lastNames=names;
            return;
        }
        if(line.startsWith("FNR3/1 CARD ")){
            FightBroadcast.Card card=FightBroadcast.Card.parse(line.substring(12));
            if(card!=null){
                achievementRound=Math.max(achievementRound,card.round);
                if(achievements!=null && boutNumber>0)achievements.scores(card);
                if(card.last()>=0)lastScored=card;
                judging=card;if(card.round==0){bout=null;recovery=-1;punchPace="";impactTiming="";}}
            return;
        }
        if(line.startsWith("FNR3/1 RECOVERY ")){
            try{
                int seconds=Integer.parseInt(line.substring("FNR3/1 RECOVERY ".length()));
                if(seconds>=-1 && seconds<=3600)recovery=seconds;
            }catch(NumberFormatException ignored){}
            return;
        }
        if(line.startsWith("FNR3/1 PACE ")){
            String[] fields=line.substring(12).split(" ");
            if(fields.length==6)try{
                int n=Integer.parseInt(fields[0]),work=Integer.parseInt(fields[1]),wait=Integer.parseInt(fields[2]);
                int quiet=Integer.parseInt(fields[4]),rest=Integer.parseInt(fields[5]);
                if(n>=6 && work>=0 && wait>=0 && quiet>=0 && rest>=0){
                    impactTiming="punches="+n+" work_us="+work+" gpu_wait_us="+wait+" interval_us="+Integer.parseInt(fields[3]);
                    punchPace=String.format(java.util.Locale.ROOT,"Punch work %.1f ms  (GPU wait %.1f ms)",work/1000.0,wait/1000.0);
                    if(quiet>=6)punchPace+=String.format(java.util.Locale.ROOT,"  /  Moving %.1f ms",rest/1000.0);
                }
            }catch(NumberFormatException ignored){}
            return;
        }
        if(line.startsWith("FNR3/1 BOUT ")){
            FightBroadcast.Bout update=FightBroadcast.Bout.parse(line.substring(12));
            if(update!=null){
                if(!retailRumble && motors!=null && bout!=null && bout.round==update.round &&
                   bout.punches!=null && update.punches!=null){
                    int hits=update.punches[0]-bout.punches[0]+update.punches[2]-bout.punches[2];
                    if(hits>0 && hits<=4)motors.changed(20500,11000,40);
                }
                // The clock exists throughout introductions and only counts down once boxing starts.
                if(!roundStarted && !musicMenu && !hasResult() && bout!=null &&
                        bout.round==update.round && update.seconds>0 && update.seconds<bout.seconds){
                    roundStarted=true;syncTrack();
                }
                if(update.seconds>0 && !hasResult())cameraShot=0;
                achievementRound=Math.max(achievementRound,update.round);
                if(achievements!=null && boutNumber>0)achievements.punches(update);
                bout=update;
                if(update.punches!=null)lastTotals=update;
            }
            return;
        }
        if(line.equals("FNR3/1 MUSICBANK 1") || line.equals("FNR3/1 MUSICBANK 0")){
            if(musicPlayer!=null){
                bankReady=line.endsWith("1");
                if(!bankReady){musicPlayer.close();musicPlayer=null;commands.offer("TRAXVOICE 0");}
                syncTrack();
            }
            return;
        }
        if(line.startsWith("FNR3/1 SONG ")){
            String stem=line.substring("FNR3/1 SONG ".length());
            if(FightBroadcast.song(stem)!=null){
                boolean changed=!retailStem.isEmpty() && !retailStem.equals(stem);
                retailStem=stem;syncTrack();
                if(changed && musicPlayer!=null){
                    musicPlayer.requested(stem,false);
                    if(menuSelection!=null)menuSelection.accept(stem);
                }
            }
            return;
        }
        if(line.startsWith("FNR3/1 TRAX ")){
            if(continuousMusic())return;
            long now=uptime.getAsLong();
            FightBroadcast.Song update=FightBroadcast.Song.parse(line.substring(12),now);
            if(update!=null){
                if(song==null || !song.title.equals(update.title) || !song.artist.equals(update.artist) ||
                        now-lastSongEvent>=30000)publishSong(update);
                lastSongEvent=now;
            }
            return;
        }
        if(line.equals("FNR3/1 FRAMEFD")){receiveVideo();return;}
        if (line.equals("FNR3/1 HELLO") && !hello) {
            advance(Milestone.LINKED);hello = true; received = System.nanoTime(); phase = "Link check"; ping(); return;
        }
        if (hello && sequence > replies && line.equals("FNR3/1 PONG " + sequence)) {
            received = System.nanoTime(); latency = (received - sent) / 1_000_000;
            replies++;
            if (replies == 1) record("Runtime answered");
            if (replies == 2 && !running) {
                running = true; phase = "Starting Xbox"; if(trax!=0)commands.offer("TRAX "+trax); commands.offer("RUN");
                flushPad();
            }
            return;
        }
        if (line.equals("FNR3/1 LINK-CHECK")) { phase = "Link check"; record("Native link check active"); return; }
        if(line.startsWith("[Fight Night] renderer ")){renderer=line.substring(23);return;}
        if(line.startsWith("[Fight Night] pad received ")){
            if(timingLog!=null && line.matches("\\[Fight Night\\] pad received [0-9]+ sampled [0-9]+"))
                timingLog.accept(line.substring(14));
            return;
        }
        if(line.startsWith("[Fight Night] slow flip ") || line.startsWith("[Fight Night] audio stream error ") || line.startsWith("[Fight Night] voice stop completed ")){
            if(timingLog!=null)timingLog.accept(line.substring(14));
            return;
        }
        if(line.startsWith("[Fight Night] audio ")){
            audio=line.substring(20);
            long now=uptime.getAsLong();
            if(timingLog!=null && now-lastTimingLog>=3000 && audio.matches("gaps [0-9]+ drops [0-9]+ xruns -?[0-9]+")){
                timingLog.accept(audio+" "+impactTiming);
                lastTimingLog=now;
            }
            return;
        }
        if(line.startsWith("[Fight Night] entering XBE at "))advance(Milestone.GUEST);
        Matcher m = BRIDGE.matcher(line);
        if (m.find()) { advance(Milestone.KERNEL);bridge = m.group(1); record("Xbox imports  " + bridge); return; }
        m = THREAD.matcher(line);
        if (m.find()) { thread = m.group(2); phase = "Xbox startup"; record("Thread " + m.group(1) + "  " + thread); return; }
        m = FRAME.matcher(line);
        if (m.find()) { advance(Milestone.FRAME);frames = m.group(1);lastFrame=Long.valueOf(frames); phase = "Video submission"; return; }
        if (line.startsWith("[Fight Night]") && (line.contains("target ") || line.contains("unsupported "))) {
            reason = line.substring(14).split(", ESP")[0]; record(reason);
        } else if (line.contains("CACHE\\LocalCache") && phase.equals("Xbox startup")) {
            phase = "Cache access"; record("Xbox cache accessed");
        }
    }

    private void readLines(InputStream stream)throws IOException{
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            String text;
            while((text=reader.readLine())!=null)line(text);
        }
    }

    /** Runs on a worker thread. One instance owns one child process. */
    public void run() {
        synchronized (this) {
            if (started) throw new IllegalStateException("Session already started");
            runStarted=uptime.getAsLong();firstSeen.put(Milestone.LAUNCH,0L);started = true; phase = "Launching";
        }
        int exit = -1;
        try {
            if(menuDisc!=null)try{RetailMenu.install(menuDisc.toPath(),DiscFonts.ogxStencil(menuDisc));retailMusicRoute=true;}
            catch(IOException e){synchronized(this){record("Main menu extensions unavailable: "+e.getMessage());}}
            if(menuDisc!=null)try{AndroidDeviceText.install(menuDisc.toPath());}
        catch(IOException e){synchronized(this){record("Device text unavailable: "+e.getMessage());}}
            if(menuDisc!=null && thorGuide!=null)try{RetailMenu.installThorGuide(menuDisc.toPath(),thorGuide.load("thor-guide.dxt3",ThorLoadingGuide.ART_BYTES));}
            catch(IOException e){synchronized(this){record("Thor controls guide unavailable: "+e.getMessage());}}
            if(menuDisc!=null && thorGuide!=null)try{RetailMenu.installThorSelector(menuDisc.toPath(),thorGuide.load("thor-selector.dxt3",128*64));}
            catch(IOException e){synchronized(this){record("Thor side selector unavailable: "+e.getMessage());}}
            ProcessBuilder builder=new ProcessBuilder(command).directory(directory);
            if(command.length>0 && frameFile!=null){
                File shaders=new File(new File(command[0]).getParentFile(),"libfight_shader.so");
                if(shaders.isFile()){
                    builder.environment().put("FNR3_SHADER_LIBRARY",shaders.getAbsolutePath());
                    File cache=new File(frameFile.getParentFile(),"disc-shaders-v1");
                    if(cache.isDirectory() || cache.mkdirs())builder.environment().put("FNR3_SHADER_CACHE",cache.getAbsolutePath());
                }
            }
            if(frameFile!=null)builder.environment().put("FNR3_FRAME_PATH",frameFile.getAbsolutePath());
            if(frameFile!=null)builder.environment().put("FNR3_PIPELINE_CACHE",new File(frameFile.getParentFile(),"fight-v4.vkcache").getAbsolutePath());
            builder.environment().put("FNR3_VIDEO_ASPECT",widescreen?"16:9":"4:3");
            builder.environment().put("FNR3_RENDERER",software?"software":"vulkan");
            builder.environment().put("RECOMP_RES",Integer.toString(renderHeight));
            builder.environment().put("RECOMP_MSAA",Integer.toString(samples));
            builder.environment().put("RECOMP_ANISO",Integer.toString(anisotropy));
            if(frameFile!=null && android.os.Build.VERSION.SDK_INT>=33)builder.environment().put("FNR3_FRAME_SOCKET","1");
            Process child=builder.start();
            synchronized (this) {
                process = child;
                if (cancelled) child.destroyForcibly();
            }
            Thread writer=new Thread(()->{
                try{
                    commands.writeTo(new BufferedWriter(new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8)));
                }catch(IOException e){
                    commands.close();
                    synchronized(this){if(!cancelled && child.isAlive()){
                        reason="Controller link closed";record(reason);
                    }}
                }catch(InterruptedException e){Thread.currentThread().interrupt();}
            },"Fight Night commands");
            writer.setDaemon(true);writer.start();
            Thread diagnostics=new Thread(()->{
                try{readLines(child.getErrorStream());}
                catch(IOException ignored){/* Closing the host closes both pipes. */}
            },"Fight Night diagnostics");
            diagnostics.setDaemon(true);diagnostics.start();
            synchronized(this){clock=Executors.newSingleThreadScheduledExecutor();updateHeartbeat();}
            readLines(child.getInputStream());
            exit=child.waitFor();
            diagnostics.join();
        } catch (Exception e) {
            synchronized (this) { if (!cancelled) reason = e.getMessage(); }
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            commands.close();
            synchronized (this) {
                if(clock!=null){clock.shutdownNow();clock=null;}
                if(motors!=null)motors.changed(0,0,0);
                if(musicPlayer!=null){musicPlayer.close();musicPlayer=null;}
                bankReady=false;songObserver=null;menuSelection=null;
                terminal = true; phase = cancelled ? "Stopped by user" : "Host stopped";
                if (process != null && process.isAlive()) process.destroyForcibly();
                if (reason == null || reason.isEmpty()) reason = cancelled ? "Session ended" :
                    exit < -1 ? String.format(Locale.ROOT,"Host exit 0x%08X",exit) : "Host exit " + exit;
                if(!cancelled && crashListener!=null && (exit>0 || exit< -1)){
                    int round=bout!=null?bout.round:judging!=null?judging.round:0;
                    CrashState state=new CrashState(this,exit);
                    crashListener.stopped(exit,state.pc,lastEvent,round,renderHeight,software,state);
                }
                crashListener=null;
                record(phase);
            }
        }
    }
}
