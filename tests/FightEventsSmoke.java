package org.oxideshift.corner;
public class FightEventsSmoke {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        if(args.length>0){
            if(args[0].equals("41")){
                System.err.println("[Fight Night] entering XBE at 0x002D8865");
                System.err.println("[Fight Night] framebuffer submission 120");
                System.err.println("Kernel thunk bridge: 150/150 resolved");
                System.err.println("[Fight Night] renderer software (Vulkan unavailable)");
                System.err.println("[Fight Night] audio gaps 3 drops 4 xruns 5");
                System.err.println("[Fight Night] invalid indirect target 0x00000000, ESP 0x03FFFE80");
                System.err.println("[Fight Night] guest stack 002DABCD 00000001 00000002 00000003 00000004 00000005 00000006 00000007");
                System.exit(41);
            }
            if(args[0].equals("42")){
                System.err.println("[Fight Night] unsupported test at 0x0034ABCD");
                System.exit(42);
            }
            if(args[0].equals("40")){
                System.err.println("[Fight Night] missing indirect target 0x0040CAFE, ESP 0x03FFFE80");
                System.exit(40);
            }
            System.out.println("FNR3/1 BOUTBEGIN");
            System.out.println("FNR3/1 BOUT 2 0 87 60 70 45 23 44 15 35");
            System.out.println("FNR3/1 COUNT 1");
            System.err.println("FNR3/1 FAULT 020493cc");
            System.exit(Integer.parseInt(args[0]));
        }
        BoutAnnouncements cues=new BoutAnnouncements();
        check(cues.count(4,20) && cues.ownsDisplay(3000));
        check(!cues.event("HAYMAKER",3100));
        check(cues.event("WON",3200) && cues.ownsDisplay(4799) && !cues.ownsDisplay(4800));
        cues.reset();
        check(cues.event("STAGGER",100) && !cues.ownsDisplay(1700));
        for(int code:new int[]{0,40,41,42,139}){
            java.io.File java=new java.io.File(System.getProperty("java.home"),"bin/java");
            CornerSession child=new CornerSession(()->0L,new java.io.File("."),java.getPath(),
                    "-cp",System.getProperty("java.class.path"),FightEventsSmoke.class.getName(),String.valueOf(code));
            int[] reports={0};
            child.setVideo(900,1,false);
            child.onCrash((exit,pc,event,round,height,software,state)->{
                check(exit==code && height==900 && !software);
                check(state.elapsedMs>=0 && state.samples==1 && state.anisotropy==2);
                if(code==42)check(pc.equals("0034abcd") && "guest_instruction".equals(state.details.get("address_kind")));
                else if(code==41)check(pc.equals("002dabcd") && "00000000".equals(state.target) && event.equals("STARTUP")
                        && "guest_stack_word".equals(state.details.get("address_kind")) && "150/150".equals(state.details.get("kernel_imports"))
                        && "software".equals(state.details.get("active_renderer")) && Long.valueOf(5).equals(state.details.get("audio_xruns"))
                        && round==0 && state.milestone.equals("frame") && Long.valueOf(120).equals(state.lastFrame));
                else if(code==40)check(pc.equals("unknown") && state.target.equals("0040cafe") && state.milestone.equals("launch") && state.lastFrame==null);
                else check(pc.equals("020493cc") && state.target==null && event.equals("COUNT") && round==2 && state.milestone.equals("bout")
                        && "native_module_offset".equals(state.details.get("address_kind")) && Integer.valueOf(11).equals(state.details.get("signal_from_exit")));
                reports[0]++;
            });
            child.run();
            check(reports[0]==(code==0?0:1));
        }
        CornerSession menu=new CornerSession(()->0L,new java.io.File("."));
        java.util.Set<String> pages=new java.util.HashSet<>();
        for(CareerDesk.Page p:CareerDesk.PAGES){
            check(pages.add(p.group+" "+p.index));
            menu.line("FNR3/1 CAREERPAGE "+p.group+" "+p.index);
            check(menu.career.page==p && !menu.career.text(p,false).isEmpty() && !menu.career.text(p,true).isEmpty());
        }
        menu.line("FNR3/1 CAREERPAGE mcon 1");
        menu.line("FNR3/1 OFFER 57617264 3750 50 82 70 85 72 80");
        check(menu.career.text(menu.career.page,false).contains("They have the power edge"));
        check(menu.career.text(menu.career.page,true).contains("Matchup lean   Opponent"));
        menu.line("FNR3/1 OFFER 4761747469 5000 80 90 72 76 60 72");
        check(menu.career.text(menu.career.page,false).contains("Your hands are quicker"));
        menu.line("FNR3/1 CAREERPAGE mcon 2");check(menu.career.text(menu.career.page,true).contains("Gatti"));
        menu.line("FNR3/1 CAREERPAGE fightstore 1");menu.line("FNR3/1 SHOP 3750 7000");
        check(menu.career.text(menu.career.page,false).contains("$3,250"));
        menu.line("FNR3/1 SHOP 7500 7000");check(menu.career.text(menu.career.page,false).contains("Short by  $500"));
        menu.line("FNR3/1 CAREERPAGE train 3");menu.line("FNR3/1 TRAINER 1000 5000");
        check(menu.career.text(menu.career.page,false).contains("$4,000"));
        menu.line("FNR3/1 CAREERPAGE trophycase 1");menu.line("FNR3/1 CASE 8193");menu.line("FNR3/1 TROPHY 13");
        check(menu.career.text(menu.career.page,false).contains("2 of 14") && menu.career.text(menu.career.page,false).contains("Earned"));
        menu.line("FNR3/1 TROPHY 1");check(menu.career.text(menu.career.page,false).contains("Locked"));
        menu.line("FNR3/1 CAREERPAGE -");check(menu.career.page==null);
        menu.line("FNR3/1 CAREERPAGE mcon 1");check(!menu.career.text(menu.career.page,false).contains("Gatti"));
        BoutAnnouncements a=new BoutAnnouncements();
        for(String result:new String[]{"WON","LOST"})for(String ko:new String[]{"KO","TKO"}){
            a.reset();check(a.current(0)==null);a.event("HAYMAKER",1);
            a.event("STAGGER",2);check(a.current(2).words.equals("STAGGER!"));
            check(a.event("ROCKED",2) && a.current(2).words.equals("ROCKED!!"));
            check(!a.event("STAGGER",2) && !a.event("HAYMAKER",2));
            a.event("GETUP",3);check(!a.event("HAYMAKER",4));
            for(int n=1;n<=10;n++){check(a.count(n,1000*n));check(!a.count(n,1000*n+5));}
            a.event(ko,10500);a.event(result,10520);check(!a.count(1,10530));
            check(a.current(12099).words.equals(ko.equals("KO")?"KNOCKOUT!!":"TKO!!"));
            check(a.current(12100).words.equals(result+"!!"));
            a.reset();a.event(result,100);a.event(ko,105);check(a.current(1705).words.equals(result+"!!"));
        }
        a.reset();a.count(1,4);a.count(2,8);a.count(0,11);check(a.current(11)==null);
        check(a.count(1,14));check(!a.count(12,20));a.reset();a.event("WON",3);check(a.current(3).words.equals("WON!!"));
        long[] time={20000};int[] hapticCount={0},hits={0};
        CornerSession s=new CornerSession(()->time[0],new java.io.File("."));
        CornerSession musicMenu=new CornerSession(()->time[0],new java.io.File("."));
        musicMenu.line("FNR3/1 FRONT 1");check(musicMenu.atRoot() && musicMenu.frontPage==CornerSession.FRONT_ROOT);
        musicMenu.line("FNR3/1 MUSICMENU OPEN");check(musicMenu.takeMusicRequest() && !musicMenu.browsingTrax);
        java.lang.reflect.Field running=CornerSession.class.getDeclaredField("running");
        running.setAccessible(true);running.setBoolean(musicMenu,true);
        musicMenu.cabinetOpen=true;
        check(musicMenu.requestPlaylist() && !musicMenu.requestPlaylist());
        musicMenu.line("FNR3/1 FRONT 2");check(musicMenu.browsingTrax && !musicMenu.atRoot());
        musicMenu.cabinetOpen=false;musicMenu.line("FNR3/1 FRONT 1");
        check(musicMenu.atRoot() && musicMenu.takeMusicRequest() && musicMenu.takePlaylistReturn() && !musicMenu.browsingTrax);
        musicMenu.line("FNR3/1 FRONT 0");check(!musicMenu.takeMusicRequest());

        // The child can already be visible behind an older Music page. Adopt it without a second command.
        musicMenu.line("FNR3/1 FRONT 2");musicMenu.cabinetOpen=true;
        check(musicMenu.requestPlaylist() && !musicMenu.waitingForPlaylist());
        musicMenu.cabinetOpen=false;musicMenu.line("FNR3/1 FRONT 1");
        check(musicMenu.takeMusicRequest() && musicMenu.takePlaylistReturn());
        musicMenu.cabinetOpen=true;
        check(musicMenu.requestPlaylist());musicMenu.cabinetOpen=false;
        time[0]+=8001;musicMenu.checkPlaylistArrival();
        check(musicMenu.waitingForPlaylist() && !musicMenu.takeMusicRequest());
        musicMenu.line("FNR3/1 PLAYLIST CANCELLED");check(!musicMenu.waitingForPlaylist());
        musicMenu.cabinetOpen=true;check(musicMenu.requestPlaylist());
        musicMenu.line("FNR3/1 PLAYLIST OPENING");check(!musicMenu.waitingForPlaylist());
        musicMenu.line("FNR3/1 FRONT 1");check(!musicMenu.takeMusicRequest());
        musicMenu.line("FNR3/1 FRONT 0");musicMenu.line("FNR3/1 FRONT 1");
        check(musicMenu.takeMusicRequest() && musicMenu.takePlaylistReturn());
        musicMenu.cabinetOpen=true;check(musicMenu.requestPlaylist());
        musicMenu.line("FNR3/1 PLAYLIST OPENING");check(!musicMenu.waitingForPlaylist());
        musicMenu.line("FNR3/1 FRONT 2");check(musicMenu.playlistOpening());
        musicMenu.line("FNR3/1 FRONT 1");check(musicMenu.takeMusicRequest() && musicMenu.takePlaylistReturn());
        musicMenu.cabinetOpen=true;check(musicMenu.requestPlaylist());musicMenu.setSleeping(true);
        time[0]+=10000;musicMenu.checkPlaylistArrival();check(!musicMenu.takeMusicRequest());
        musicMenu.setSleeping(false);musicMenu.checkPlaylistArrival();check(musicMenu.waitingForPlaylist());
        musicMenu.line("FNR3/1 FRONT 2");musicMenu.cabinetOpen=false;musicMenu.line("FNR3/1 FRONT 1");
        check(musicMenu.takeMusicRequest() && musicMenu.takePlaylistReturn());
        time[0]-=18001;

        s.motors(new CornerSession.Motors(){
            public void changed(int l,int r,int ms){hits[0]++;}
            public void count(int value){hapticCount[0]+=value;}
        });
        check(s.openingSlate());s.line("FNR3/1 INTRO 1");check(s.openingSlate());
        s.line("FNR3/1 INTRO 2");check(s.openingSlate() && s.introMovie()==2);
        s.line("FNR3/1 INTRO 0");check(!s.openingSlate() && s.introMovie()==0);
        MenuTips tips=new MenuTips();String[] choices={"one","two","three"};
        check("one".equals(tips.select("career",choices,0)));
        check("one".equals(tips.select("career",choices,10999)));
        check(tips.due(11000) && "two".equals(tips.select("career",choices,11000)));
        tips.select("options",choices,12000);check("three".equals(tips.select("career",choices,13000)));
        tips.leave();check(!tips.due(30000) && "one".equals(tips.select("career",choices,30000)));
        s.line("FNR3/1 CABINET OPEN");s.line("FNR3/1 CABINET OPEN");
        check(s.takeCabinetRequest() && !s.takeCabinetRequest());
        s.cabinetOpen=true;s.line("FNR3/1 CABINET OPEN");check(!s.takeCabinetRequest());s.cabinetOpen=false;
        s.line("FNR3/1 CAREERPAGE train 2");
        s.line("FNR3/1 TRAINCHOICE 2");check(s.career.selectedDrill().title.equals("Combo dummy"));
        s.line("FNR3/1 TRAINCHOICE 1");check(s.career.selectedDrill().title.equals("Heavy bag"));
        s.line("FNR3/1 TRAINSTATS 1 1 2 3 4 5 6 7 8");
        s.line("FNR3/1 TRAINSTATS 0 -2 3 4 5 6 7 8 9");
        check(s.career.training(true)[7]==8 && s.career.training(false)[0]==-2);
        long trainingRevision=s.career.revision;
        s.line("FNR3/1 TRAINSTATS 1 1 2 3 4 5 6 7 8");
        check(s.career.revision==trainingRevision);
        s.line("FNR3/1 CAREERPAGE drill 1");check(s.career.training(true)[7]==8 && s.career.training(false)==null);
        s.line("FNR3/1 CAREERPAGE train 1");check(s.career.training(true)==null);
        s.line("FNR3/1 CAREERPAGE train 2");
        s.line("FNR3/1 TRAINSTATS 1 1 2 3 4 5 6 7 8");
        s.line("FNR3/1 TRAINSTATS 1 1 nope 3 4 5 6 7 8");check(s.career.training(true)==null);
        s.line("FNR3/1 CAREERPAGE careercentral 1");check(s.career.training(false)==null);
        s.line("FNR3/1 CAMP 8,1,1,5 8,1,1,5 purse=15300");
        check(s.camp().odds.contains("You 50%") && s.camp().purse.equals("Fight purse $15,300"));
        s.line("FNR3/1 CAMP 8,1,1,5 - 15300");check(s.camp().odds.isEmpty() && s.camp().opponent==null && s.camp().purse.isEmpty());
        s.line("FNR3/1 CAMP 8,1,1,5 8,1,1,5 15300");check(s.camp().purse.isEmpty());
        s.line("FNR3/1 CAMP 8,1,1,5 0,0,0,0 0");check(s.camp().odds.isEmpty());
        s.line("FNR3/1 TRAX 0041 0042");FightBroadcast.Song song=s.song();
        check(song.started==time[0]);time[0]+=6601;s.line("FNR3/1 TRAX 0041 0042");
        check(s.song()==song);s.line("FNR3/1 TRAX 0042 0041");
        check(s.song()!=song && s.song().started==time[0]);
        s.line("FNR3/1 CAMP 4,1,0,2 9,2,0,6");
        check(s.camp().notes().contains("67% of opponent wins by KO"));
        s.line("FNR3/1 CAMP 0,0,0,0 -");
        check(s.camp().notes().contains("Debut") && !s.camp().notes().contains("opponent"));
        s.line("FNR3/1 CAMP - -");check(s.camp()==null);
        s.line("FNR3/1 BOUTBEGIN");
        check(s.fullMusicLevel());
        s.line("FNR3/1 MUSICSCENE 0");
        s.line("FNR3/1 BOUT 1 180 100 100 100 100");
        s.line("FNR3/1 BOUT 1 180 100 100 100 100");check(s.fullMusicLevel());
        s.line("FNR3/1 BOUT 1 179 100 100 100 100");check(!s.fullMusicLevel());
        s.line("FNR3/1 BOUT 2 180 100 100 100 100");check(!s.fullMusicLevel());
        s.line("FNR3/1 MUSICSCENE 1");check(s.fullMusicLevel());
        s.line("FNR3/1 FIGHTERS 4741545449 57415244");
        s.line("FNR3/1 CARD 2 1,10,9,9,10,10,9 2,10,9,10,9,9,10");
        check(s.inLiveBout() && !s.showingResult());
        s.line("FNR3/1 BOUT 2 0 87 60 70 45 23 44 15 35");
        s.line("FNR3/1 SHOT 117");check(s.cameraShot()!=0);
        time[0]+=16000;check(s.cameraShot()==0);
        s.line("FNR3/1 SHOT 1");check(s.cameraShot()!=0);
        s.line("FNR3/1 BOUT 3 60 100 100 100 100 23 44 15 35");check(s.cameraShot()==0);
        s.line("FNR3/1 SHOT 1");check(s.cameraShot()==0);
        s.line("FNR3/1 BOUT 2 0 87 60 70 45 23 44 15 35");
        s.line("FNR3/1 COUNT 8");s.line("FNR3/1 COUNT 8");
        check(hapticCount[0]==8 && s.callout().words.equals("8"));
        s.line("FNR3/1 COUNT 0");check(s.callout()==null);
        s.line("FNR3/1 CALLOUT TKO");s.line("FNR3/1 CALLOUT WON");
        s.line("FNR3/1 CALLOUT FINAL");
        check(s.result().equals("WON!!") && s.finish().equals("TKO"));
        s.line("FNR3/1 SHOT 117");check(s.cameraShot()==0);
        // Play Now can return to its menu while the last round card still exists.
        check(s.showingResult() && !s.inLiveBout() && s.judging().round==2);
        s.leaveResults();check(!s.showingResult() && !s.inLiveBout());
        s.line("FNR3/1 CALLOUT WON");check(!s.showingResult());
        s.line("FNR3/1 CARD 0");s.line("FNR3/1 FIGHTERS - -");
        check(s.result().equals("WON!!") && s.finish().equals("TKO") && s.lastNames().red.equals("WARD"));
        check(s.bout()==null && s.scored().total(0,0)==20 && s.scored().total(1,1)==19 && s.totals().punches[3]==35);
        check(s.callout().words.equals("TKO!!"));time[0]+=1600;check(s.callout().words.equals("WON!!"));
        s.line("FNR3/1 BOUTBEGIN");check(s.result().isEmpty() && s.finish().isEmpty() && s.scored()==null && s.totals()==null);
        s.line("FNR3/1 CARD 1");check(s.inLiveBout() && !s.showingResult());
        s.line("FNR3/1 CALLOUT FINAL");check(s.showingResult() && !s.inLiveBout());
        s.line("FNR3/1 CALLOUT LOST");check(s.result().equals("LOST!!"));
        System.out.println("Referee count deduplication, interrupted counts, official results and stoppage ordering");
    }
}
