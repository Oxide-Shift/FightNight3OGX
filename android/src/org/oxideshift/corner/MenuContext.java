package org.oxideshift.corner;

/** Identifies fixed retail lettering. Each recognized selection has an independent corner note. */
final class MenuContext {
    static final class Page {
        final String title, heading;
        final String[] tips;
        final long[] edges;
        Page(String title,String heading,String[] tips,long... edges){
            this.title=title;this.heading=heading;this.tips=tips;this.edges=edges;
        }
    }
    static final Page PLAYLIST=new Page("EA Trax", "Set your rotation", new String[]{
            "Use the arrows beside a song to change whether it plays in the rotation."},
            0xedc0a35L, 0xc558e31L, 0xc558e21L, 0xc5d8035L, 0xd558029L, 0xd558069L);
    private static final Page[] PAGES={
        new Page("Play Now", "Jump straight into the ring.", new String[]{
            "Choose two boxers and a venue for a single exhibition fight. Pick your corner and take the opening bell."},
            0x69564498deL, 0x202d5ac69857L, 0xad5ac29157L, 0xaf594bb557L, 0x40b75943b557L, 0x401353433557L),
        new Page("ESPN Classic", "Relive boxing’s defining rivalries.", new String[]{"Replay the same rivalry from the other corner. Look for the openings you gave your opponent the first time.",
            "Switch corners on the rematch and try to counter the punch that gave you trouble.",
            "Watch the first exchange before you commit. A jab that falls short tells you a lot about this matchup."},
            0xb4adef31a8dad5eL, 0xb4b56b35ec5a967L, 0xd4b5e75d8cdadc7L, 0x4b586598c6a9dbL, 0x1b56969ed6a967L, 0x4a56b48a96a943L),
        new Page("Career Mode", "Fight your way from prospect to champion.", new String[]{"Between fights, visit Career Central to review your record, scout your next opponent and choose a contract.",
            "Check your cash before visiting the Fight Store. Come back after a bout to see what the purse added.",
            "Career Central shows your opponent once a bout is scheduled. Between contracts, your own record stays on the sheet."},
            0x35eacc6aaa994L, 0x4d4aec4996bb7L, 0x355acd6896a97L, 0x154a846a82b47L, 0x55ab449d6b17L, 0x354ab56aa6a57L),
        new Page("Game Modes", "Hit the gym or take a shot at Hard Hits.", new String[]{"Take a punch into training first. Then try landing it in Hard Hits against a moving opponent.",
            "Training gives you room to repeat the same move. Try a jab to the body, then one upstairs.",
            "In Hard Hits, see how far one step back takes you out of hook range."},
            0x54aa9b949994L, 0x5b6a5ac6dbb7L, 0x1756b5b94da97L, 0x646b5b9ecb53L, 0xd96a5586ab57L, 0xd40a5592aa57L),
        new Page("Create Champ", "Build a boxer with your own look and fighting style.", new String[]{"Orthodox leads with the left hand. Southpaw leads with the right. Your jab comes from that lead side.",
            "Try a southpaw against an orthodox boxer. Pay attention to where the two lead hands meet.",
            "Take your created fighter into Play Now to feel the reach and stance you picked."},
            0x6b576a854b2b54L, 0x6b576a53b39b57L, 0x16b556a45b2ab57L, 0x2a954845b6ab47L, 0x2ab56a41b6ab57L, 0x2aad6a45b6cb57L),
        new Page("Options", "Set the rules, sound and controls for your fights.", new String[]{"For clearer corner advice, open Audio. Speech and crowd volume have separate sliders.",
            "To pick which songs play, open EA Trax from the main Music menu.",
            "Try your round-length setting in a Play Now bout before committing to a career fight."},
            0xf4bd4b54L, 0x2002b4b5b657L, 0x275b5b357L, 0xe4b5bb57L, 0x400096b5b057L, 0x4002b6b5b157L),
        new Page("Hard Hits", "Mind your stamina", new String[]{"Leave enough stamina after a combination to defend the counter. A clean single can be worth more than a missed flurry.",
            "Bring your hands back up after the rear hand. The return punch often comes from the open side.",
            "When a hook hits your gloves, take a half step out before you answer."},
            0x206a52c6a9b6L, 0x206c92cd6bb7L, 0xb8d2cd6ab7L, 0x3092cd2b53L, 0x406c928d6b57L, 0x4068d2946a57L),
        new Page("Training", "Find your timing", new String[]{"Repeat one short combination. Watch how much stamina it costs and how quickly your guard comes back.",
            "Practice a body jab from just outside reach. Step in as you throw and step out as your hand comes back.",
            "Throw the same two punches in reverse order. Notice which one leaves you in range for a counter."},
            0x2002b4a5b356L, 0x2006b4adbb4bL, 0x6acad2b69L, 0x6acb52b69L, 0x4006b4b56b69L, 0x4006b4b56b69L),
        new Page("Career Central", "Scout the contract", new String[]{"Compare your record with your next opponent\u2019s before signing. Your fight history is here between bouts.",
            "A signed contract shows the fight purse. The store uses the money already in your wallet.",
            "The small win estimate compares both records. A debut has too little history for a line."},
            0xb58a58a4aa5L, 0xb56a58a4bb5L, 0xd56a48a4ad5L, 0xd56e48a4ad1L, 0xd56248a4ad5L, 0xf56258a4af5L),
        new Page("Start a New Career", "Choose your starting point", new String[]{"Create a boxer to build your own identity, or rebuild a legend to take an established fighter through a new career.",
            "A rebuilt legend starts a new career record. The exhibition record belongs to Play Now.",
            "Pick a stance you like using. You will feel the choice on every lead jab."},
            0x5b4031594622b55L, 0x527869554612badL, 0x5278e9450412b23L, 0x5b4289098512b26L, 0x527aa985c512a24L, 0x527ba8a5c512aa9L),
        new Page("Create Champ", "Build around your reach", new String[]{"Longer arms give the jab room to work. A shorter fighter needs to get inside before throwing hooks.",
            "Test your reach in Play Now after you finish building your boxer.",
            "Try the same jab from two distances. A longer fighter has more room to land on the way in."},
            0x12d5495ae5L, 0x1ded54699d5L, 0x1ded54698d5L, 0x1eb84a9ae1L, 0x5eb54690d5L, 0x56354684d5L),
        new Page("Create Champ Info", "Set your ring identity", new String[]{"Your ring name and hometown go with your boxer into introductions. You can preview the look as you build.",
            "Check the spoken name as well as the name on the card before you finish your boxer.",
            "Set your stance, then preview the fighter from both sides before you leave the editor."},
            0x2d9812d5c95ae5L, 0x29b9ded54699d5L, 0x29b9de954698d5L, 0x29981eb84a9ae1L, 0x29985eb54690d5L, 0x29d856354684d5L),
        new Page("Select Rivalry", "Read the matchup", new String[]{"Try one round at long range and one in close. The same two fighters can feel very different when the distance changes.",
            "A rematch is a good time to try the other corner. Look for the punch that caught you last time.",
            "Use the first round to find out whose jab reaches farther."},
            0x55632256a95L, 0x5561ae29695L, 0x5571ae29693L, 0x2575aa20a96L, 0x2555ae29695L, 0x2555ae29691L),
        new Page("Select Boxer", "Find your range", new String[]{"Start with a jab to judge distance. If it falls short, step in before committing your rear hand.",
            "Compare each fighter\u2019s losses as well as the wins. A long record tells a different story from a debut.",
            "Try the opposite stance for a round. Your lead hand and the angle of your rear cross both change."},
            0x64aa256a95L, 0x72aae29695L, 0x76eae7be93L, 0x644a220e96L, 0x726ae29695L, 0x72aae29699L),
        new Page("Select Corner", "Know your corner", new String[]{"Blue and red match the two fighter panels below during the bout. Your corner follows the side you take here.",
            "Remember your corner color. It matches the health and stamina panel during a fight.",
            "Switch sides on the next exhibition to practice reading the ring from the opposite corner."},
            0x3a55aa46295L, 0x297faa29695L, 0x2979aa29693L, 0x1a35a220a96L, 0x291faa29694L, 0x2919aa29691L),
        new Page("Select Venue", "Pick your ring", new String[]{"Take the rivalry from a quiet gym to the lights at Madison Square Garden.",
            "Choose The Warehouse for a gym fight or take your boxers to a packed arena.",
            "The crowd and music vary by venue. In Audio, you can turn the crowd down to hear the corner."},
            0x3bd6a46295L, 0xbc2a29695L, 0xbc2a29693L, 0x1be6c20a96L, 0xbf2c29694L, 0xbf2c29691L),
        new Page("Options", "Hear your trainer", new String[]{"In Audio, Speech Volume controls voices. Lower Crowd Volume if the arena is drowning out your corner.",
            "Check Gameplay for round length, then play a short exhibition to try it.",
            "Visit Music on the main menu and open EA Trax to set your song rotation."},
            0x5558d5L, 0x555955L, 0x175955L, 0x375955L, 0x675915L, 0x555915L),
        PLAYLIST,
        new Page("Audio", "Bring the voice forward", new String[]{"Raise Speech Volume for the coach between rounds. If the crowd competes with the dialogue, bring Crowd Volume down.",
            "Crowd volume and speech have separate controls. Try a change in the same venue to hear the difference.",
            "Open Music on the main menu to choose which EA Trax songs are in rotation."},
            0xae56L, 0xad56L, 0xad56L, 0xad54L, 0xad54L, 0xad51L),
        new Page("Gameplay", "Make rounds count", new String[]{"Shorter rounds leave less time to recover from a knockdown or win a close round back.",
            "A shorter round gives you fewer exchanges to win it back. Check the clock after a knockdown.",
            "Use a quick exhibition to see how the new settings feel before your next career bout."},
            0xacf9db5L, 0xaca9db5L, 0x2aa98d5L, 0x6af98d5L, 0x4ac98d5L, 0x4ac9ad5L)
    };
    private static final int ROOT_PAGES=8;
    private static final Page ACHIEVEMENTS=new Page("Achievements","See what you’ve earned and pick your next challenge.",
            new String[]{"Read the unlock condition on a badge before a bout so you know what to work on.",
                    "Fight Score adds up across your unlocked badges. Check your progress after a career milestone."});
    private static final Page MUSIC=new Page("Music","Build the soundtrack for your corner.",
            new String[]{"Open EA Trax to pick which songs from the original soundtrack are in rotation.",
                    "Turn on Music during fights to carry the soundtrack into the ring. Expanded Music has its own switch."});
    // Lit strokes on the selected carousel row, independent of the moving film behind it.
    private static final long[][] LETTERING={
        {0x157b2b2eL,0x75a2b3aL,0x11d12b2L,0xa6916f2L},
        {0x1c7bab3e4f56L,0x1c188b124d12L,0x5a229a6942L,0x6baede497eL},
        {0x1bb68fe76aL,0x2ee0a4562L,0x2ef0a4d7aL,0x13b50be59eL},
        {0x6fdb4eb6aL,0x256742362L,0xc563c2f6aL,0x6ddaced9eL},
        {0x1f6d78fd77aL,0x176d444a952L,0x7bd604b91aL,0x7b358caf5eL},
        {0xaaf76aL,0x2ab2baL,0x8ab23aL,0xaad23eL},
        {0x1dfa276aL,0x4ba456aL,0x10ba4d6aL,0x1cba259aL},
        {0x3ab327eL,0xbb7754L,0x2abb714L,0x1ab3554L},
        {0x1de7eb5f564L,0x4ace15351aL,0x10b4f16359eL,0x1ca7d32f56aL}
    };
    private static final int[] ROWS={295,299,305,310};
    private static final long[] OPTIONS_BELOW={0x140,0x2854a,0x1416a,0x96c,0x296a,0x22b6a};
    private static final long[] OPTIONS_BELOW_HD={0x4a,0x4552,0x1454a,0x54c,0x2542,0x22142};
    private static final long[] ACHIEVEMENTS_ABOVE={0x60a542140L,0x54a524942L,0x1428544150L,0x42a525b40L,0x16a5b5942L,0x5285a4152L};
    // Visible custom-row lettering at guest and presentation resolutions.
    // For the short Music caption, ignore the moving film beyond the last letter.
    private static final long[][] MUSIC_CAPTION={
        {0x1a94b66L,0x1ab6b6fL,0x52ceb6fL,0x29ab4fL,0x1ab6b57L,0x5ab6b57L},
        {0x1e95b6cL,0x5ab5b6dL,0x568db6dL,0x699b6dL,0x1ab5b55L,0x5ab6b55L}};
    private static final long[][] AWARDS_CAPTION={
        {0x2a5aac93556adcL,0x6c936ecb656a5dL,0xa8d6acc1556b55L,0x32da2955554955L,0x6adb2b4dd56b57L,0x68daab55956b17L},
        {0x2a5aacb2554a98L,0x68d26c9b4d4addL,0xbcd62dab454a55L,0x34d2adb9156855L,0x44db6b8dc54a55L,0x16cdaaaa5b56a37L}};
    private final long[] sample=new long[6];

    private static int light(int pixel){
        return LiveFrame.red(pixel)+2*LiveFrame.green(pixel)+LiveFrame.blue(pixel);
    }
    private void read(LiveFrame frame,boolean root){
        read(frame,root?294:49,root?389:57,root?3:5);
    }
    private void read(LiveFrame frame,int top,int left,int step){
        for(int row=0;row<sample.length;row++){
            int y=top+3*row;
            long edges=0;
            if(step==3){
                // Adjacent title samples share two of their three luminance values.
                int before=light(frame.at(left-3,y)),center=light(frame.at(left,y));
                for(int col=0;col<61;col++){
                    int after=light(frame.at(left+(col+1)*3,y));
                    if(center-Math.min(before,after)>63)edges|=1L<<col;
                    before=center;center=after;
                }
            }else{
                for(int col=0;col<61;col++){
                    int x=left+col*step;
                    int background=Math.min(light(frame.at(x-3,y)),light(frame.at(x+3,y)));
                    if(light(frame.at(x,y))-background>63)edges|=1L<<col;
                }
            }
            sample[row]=edges;
        }
    }
    // Career Central marks the focused row with a green A. Its store, trophy,
    // editor and options rows share this layout below the main fight action.
    private static final int[] CAREER_ROWS={91,119,146,173,200};
    int careerRow(LiveFrame frame){
        int found=-1;
        for(int i=0;i<CAREER_ROWS.length;i++){
            int green=0;
            for(int y=CAREER_ROWS[i]-8;y<=CAREER_ROWS[i]+8;y+=2)for(int x=400;x<420;x+=2){
                int c=frame.at(x,y);
                int g=LiveFrame.green(c);
                if(g>70 && g>LiveFrame.red(c)*1.15 && g>LiveFrame.blue(c)*1.5)green++;
            }
            if(green>=5){
                if(found>=0)return -1;
                found=i;
            }
        }
        return found;
    }
    static Page root(int index){
        if(index>=0 && index<=4)return PAGES[index];
        if(index==5)return ACHIEVEMENTS;
        if(index==6)return MUSIC;
        return index==7?PAGES[5]:null;
    }
    // APT can repeat the previous index while the carousel is scrolling. Trust the
    // visible title first and keep the last confirmed row through blank frames.
    static Page rootSelection(Page visible,Page previous,int index){
        if(visible!=null)return visible;
        if(isMain(previous))return previous;
        return root(index);
    }
    static boolean isMain(Page selection){
        if(selection==ACHIEVEMENTS || selection==MUSIC)return true;
        // The last two carousel captions belong to the Game Modes submenu.
        for(int i=0;i<6;i++)if(selection==PAGES[i])return true;
        return false;
    }
    boolean mainMenu(LiveFrame frame){return isMain(identify(frame));}
    private static boolean orange(int pixel){
        int r=LiveFrame.red(pixel),g=LiveFrame.green(pixel);
        return r>=212 && g>=72 && g<=135 && LiveFrame.blue(pixel)<25 && Math.abs(2*r-5*g)<72;
    }
    private int likeness(long[] known,long mask){
        int overlap=0,union=0;
        for(int i=0;i<known.length;i++){
            overlap+=Long.bitCount(known[i]&sample[i]&mask);union+=Long.bitCount((known[i]|sample[i])&mask);
        }
        return union==0?0:100*overlap/union;
    }
    private Page carousel(LiveFrame f){
        int best=-1,score=0,runner=0,span=0,ink=0;
        for(int shift=-1;shift<=1;shift++){
            long whole=0;
            for(int i=0;i<ROWS.length;i++){
                long bits=0;
                for(int col=0;col<46;col++){
                    int x=372+3*col+shift;
                    if(orange(f.at(x,ROWS[i])) || orange(f.at(x+1,ROWS[i])))bits|=1L<<col;
                }
                sample[i]=bits;whole|=bits;
            }
            if(shift==0){span=64-Long.numberOfLeadingZeros(whole);ink=Long.bitCount(whole);}
            for(int i=0;i<LETTERING.length;i++){
                int value=likeness(LETTERING[i],-1L);
                if(value>score){if(i!=best)runner=score;score=value;best=i;}
                else if(i!=best && value>runner)runner=value;
            }
        }
        // Music is the short caption between Achievements and Options. Check the visible
        // neighbouring lettering as well so an animation or unrelated short word cannot select it.
        if(span>=13 && span<=21 && ink>=10){
            read(f,325,389,3);
            int after=Math.max(likeness(OPTIONS_BELOW,0x7ffffL),likeness(OPTIONS_BELOW_HD,0x7ffffL));
            read(f,264,389,3);
            if(after>=56 || (after>=32 && likeness(ACHIEVEMENTS_ABOVE,0x3fffffffffL)>=35))return MUSIC;
        }
        return score>=62 && score-runner>=5?(best==ROOT_PAGES?ACHIEVEMENTS:PAGES[best]):null;
    }
    Page identify(LiveFrame frame){return identify(frame,false);}
    Page identify(LiveFrame frame,boolean root){
        int best=-1,score=0,second=0;
        for(int group=0;group<(root?1:2);group++){
            read(frame,group==0);
            if(root){
                int resolution=frame.width>640?1:0;
                if(likeness(MUSIC_CAPTION[resolution],0x7ffffffL)>=78)return MUSIC;
                if(likeness(AWARDS_CAPTION[resolution],-1L)>=78)return ACHIEVEMENTS;
            }
            for(int i=group==0?0:ROOT_PAGES;i<(group==0?ROOT_PAGES:PAGES.length);i++){
                int overlap=0,union=0;
                for(int k=0;k<sample.length;k++){
                    long known=PAGES[i].edges[k];
                    overlap+=Long.bitCount(known&sample[k]);
                    union+=Long.bitCount(known|sample[k]);
                }
                int match=union<12?0:100*overlap/union;
                if(match>score){second=score;score=match;best=i;}
                else if(match>second)second=match;
            }
        }
        // The root carousel lettering moves slightly while settling between frames.
        boolean clearRoot=best>=0 && best<ROOT_PAGES && score>=63 && score-second>=15;
        if(clearRoot || (score>=66 && score-second>=7))return PAGES[best];
        return carousel(frame);
    }
}
