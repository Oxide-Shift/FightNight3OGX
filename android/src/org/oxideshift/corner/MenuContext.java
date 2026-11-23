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
    private static final Page[] PAGES={
        new Page("Play Now", "Try a matchup", new String[]{"Use an exhibition to try a new fighter or stance before taking it into career.",
            "Fight the same opponent twice and switch corners for the rematch. See which punches are harder to defend from the other side.",
            "Pick a familiar venue when you try a new boxer. It makes changes in reach and timing easier to feel."},
            0x69564498deL, 0x202d5ac69857L, 0xad5ac29157L, 0xaf594bb557L, 0x40b75943b557L, 0x401353433557L),
        new Page("ESPN Classic", "Switch sides", new String[]{"Replay the same rivalry from the other corner. Look for the openings you gave your opponent the first time.",
            "Switch corners on the rematch and try to counter the punch that gave you trouble.",
            "Watch the first exchange before you commit. A jab that falls short tells you a lot about this matchup."},
            0xb4adef31a8dad5eL, 0xb4b56b35ec5a967L, 0xd4b5e75d8cdadc7L, 0x4b586598c6a9dbL, 0x1b56969ed6a967L, 0x4a56b48a96a943L),
        new Page("Career Mode", "Build your fighter", new String[]{"Between fights, visit Career Central to review your record, scout your next opponent and choose a contract.",
            "Check your cash before visiting the Fight Store. Come back after a bout to see what the purse added.",
            "Career Central shows your opponent once a bout is scheduled. Between contracts, your own record stays on the sheet."},
            0x35eacc6aaa994L, 0x4d4aec4996bb7L, 0x355acd6896a97L, 0x154a846a82b47L, 0x55ab449d6b17L, 0x354ab56aa6a57L),
        new Page("Game Modes", "Work on one thing", new String[]{"Take a punch into training first. Then try landing it in Hard Hits against a moving opponent.",
            "Training gives you room to repeat the same move. Try a jab to the body, then one upstairs.",
            "In Hard Hits, see how far one step back takes you out of hook range."},
            0x54aa9b949994L, 0x5b6a5ac6dbb7L, 0x1756b5b94da97L, 0x646b5b9ecb53L, 0xd96a5586ab57L, 0xd40a5592aa57L),
        new Page("Create Champ", "Pick a stance", new String[]{"Orthodox leads with the left hand. Southpaw leads with the right. Your jab comes from that lead side.",
            "Try a southpaw against an orthodox boxer. Pay attention to where the two lead hands meet.",
            "Take your created fighter into Play Now to feel the reach and stance you picked."},
            0x6b576a854b2b54L, 0x6b576a53b39b57L, 0x16b556a45b2ab57L, 0x2a954845b6ab47L, 0x2ab56a41b6ab57L, 0x2aad6a45b6cb57L),
        new Page("Options", "Tune the corner", new String[]{"For clearer corner advice, open Audio. Speech and crowd volume have separate sliders.",
            "Look under Audio for the song rotation and the individual volume controls.",
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
        new Page("Select Venue", "Give the gym room", new String[]{"On Thor, 720p with High \u00b7 no MSAA held 59.9 FPS in our Windy City punch test.",
            "Use the same pair of fighters when comparing venues so you can feel what the arena changes.",
            "The crowd and music vary by venue. In Audio, you can turn the crowd down to hear the corner."},
            0x3bd6a46295L, 0xbc2a29695L, 0xbc2a29693L, 0x1be6c20a96L, 0xbf2c29694L, 0xbf2c29691L),
        new Page("Options", "Hear your trainer", new String[]{"In Audio, Speech Volume controls voices. Lower Crowd Volume if the arena is drowning out your corner.",
            "Check Gameplay for round length, then play a short exhibition to try it.",
            "Visit EA Trax under Audio to set the music you hear in the menus."},
            0x5558d5L, 0x555955L, 0x175955L, 0x375955L, 0x675915L, 0x555915L),
        new Page("EA Trax", "Set your rotation", new String[]{"Song rotation lives under Audio. The lower banner shows the artist and title when a track plays.",
            "Look closely at the lettering down here. The port reads the original stencil font straight from your disc.",
            "The soundtrack has its own volume setting. You can keep the ring and corner voices louder."},
            0xedc0a35L, 0xc558e31L, 0xc558e21L, 0xc5d8035L, 0xd558029L, 0xd558069L),
        new Page("Audio", "Bring the voice forward", new String[]{"Raise Speech Volume for the coach between rounds. If the crowd competes with the dialogue, bring Crowd Volume down.",
            "Crowd volume and speech have separate controls. Try a change in the same venue to hear the difference.",
            "Open EA Trax here to choose which tracks are in rotation."},
            0xae56L, 0xad56L, 0xad56L, 0xad54L, 0xad54L, 0xad51L),
        new Page("Gameplay", "Make rounds count", new String[]{"Shorter rounds leave less time to recover from a knockdown or win a close round back.",
            "A shorter round gives you fewer exchanges to win it back. Check the clock after a knockdown.",
            "Use a quick exhibition to see how the new settings feel before your next career bout."},
            0xacf9db5L, 0xaca9db5L, 0x2aa98d5L, 0x6af98d5L, 0x4ac98d5L, 0x4ac9ad5L)
    };
    private static final int ROOT_PAGES=8;
    private final long[] sample=new long[6];

    private static int light(int pixel){
        return LiveFrame.red(pixel)+2*LiveFrame.green(pixel)+LiveFrame.blue(pixel);
    }
    private void read(LiveFrame frame,boolean root){
        for(int row=0;row<sample.length;row++){
            int y=(root?294:49)+3*row;
            long edges=0;
            for(int col=0;col<61;col++){
                int x=(root?389:57)+col*(root?3:5);
                int background=Math.min(light(frame.at(x-3,y)),light(frame.at(x+3,y)));
                if(light(frame.at(x,y))-background>63)edges|=1L<<col;
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
    boolean mainMenu(LiveFrame frame){
        Page selection=identify(frame);
        // The last two carousel captions belong to the Game Modes submenu.
        for(int i=0;i<6;i++)if(selection==PAGES[i])return true;
        return false;
    }
    Page identify(LiveFrame frame){
        int best=-1,score=0,second=0;
        for(int group=0;group<2;group++){
            read(frame,group==0);
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
        return clearRoot || (score>=66 && score-second>=7)?PAGES[best]:null;
    }
}
