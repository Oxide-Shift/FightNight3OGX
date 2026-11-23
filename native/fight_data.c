#include <ctype.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#include "xbox_memory_layout.h"
#include "fight_data.h"

/* Retail GetRound and GetJudgesScores access these objects. Read only complete
 * judge pairs. The game stores 15 round slots for each boxer on each card. */
static const uint8_t *guest(uint32_t a, size_t n) {
    size_t limit = g_xbox_map_size ? g_xbox_map_size : g_xbox_total_ram;
    uint32_t offset = a;
    if (a >= 0x80000000u && a < 0x80000000u + g_xbox_total_ram) { offset = a - 0x80000000u; limit = g_xbox_total_ram; }
    if (a < 0x10000 || (a & 3) || (uint64_t)offset + n > limit) return NULL;
    return XBOX_HOST(a);
}
static uint32_t word(const uint8_t *p) { uint32_t v; memcpy(&v,p,4); return v; }
/* GetSelectedBoxer returns the two row IDs from the selection manager.
 * The game's roster accessor walks linked tables (including custom boxers),
 * then reads first and last names from the selected row's string columns. */
static int roster_name(uint32_t id,char hex[129],char record[32]) {
    strcpy(record,"-");
    uint32_t node=0x59d1d4;
    for(unsigned bank=0;bank<16 && node;bank++){
        const uint8_t *desc=guest(node,20);
        if(!desc)return 0;
        unsigned size=word(desc+4);
        if(size<32 || size>8u*1024*1024)return 0;
        const uint8_t *table=guest(word(desc+8),size);
        if(!table)return 0;
        unsigned cols=word(table),rows=word(table+4);
        if(cols<3 || cols>1024 || rows>65535)return 0;
        if(id>=rows){id-=rows;node=word(desc+16);continue;}
        unsigned end=word(table+8),strings=word(table+16);
        uint64_t rowBytes=((uint64_t)rows*cols*2+2)&~UINT64_C(3);
        if(rowBytes>end || end>size || strings>size || (table[24]&0x3c)!=0x14)return 0;
        unsigned row=end-(unsigned)rowBytes+id*cols*2;
        if(row+6>size)return 0;
        static const char digits[]="0123456789ABCDEF";
        unsigned used=0;
        for(int field=2;field>=1;field--){
            int16_t offset;memcpy(&offset,table+row+field*2,sizeof offset);
            int64_t from=(int64_t)strings+offset;
            if(from<0 || from>=size)return 0;
            const uint8_t *name=table+from;
            size_t room=size-(size_t)from;if(room>32)room=32;
            const uint8_t *tail=memchr(name,0,room);
            if(!tail || (field==1 && tail==name && !used))return 0;
            if(used && tail>name){hex[used++]='2';hex[used++]='0';}
            for(;name<tail;name++){
                unsigned ch=*name;if(ch<0x20 || (ch>=0x7f && ch<0xa0))return 0;
                hex[used++]=digits[ch>>4];hex[used++]=digits[ch&15];
            }
        }
        /* Wins, losses, draws and knockout wins occupy consecutive signed
         * 16-bit columns in the retail boxer table. A custom row may omit them. */
        if(cols>=35 && size-row>=70){
            int16_t counts[4];
            memcpy(counts,table+row+31*2,sizeof counts);
            if(counts[0]>=0 && counts[1]>=0 && counts[2]>=0 &&
               counts[3]>=0 && counts[3]<=counts[0])
                snprintf(record,32,"%d,%d,%d,%d",counts[0],counts[1],counts[2],counts[3]);
        }
        hex[used]=0;
        return used>0;
    }
    return 0;
}
/* SetCareerBoxers binds the player to blue and the signed contract's
 * opponent to red. Career records live in the profile, including for a
 * rebuilt legend whose exhibition roster row retains historical statistics. */
static const uint8_t *active_profile(void){
    const uint8_t *manager=guest(0x59db10,0x198);
    if(!manager)return NULL;
    unsigned slot=word(manager+0x168)&0xffff;
    if(slot>=4 || word(manager+0xf0+slot*4)!=1)return NULL;
    const uint8_t *wrapper=guest(0x59db10+0x3a4c+0x38a4*slot,12);
    return wrapper && word(wrapper)==0x4ab240?guest(word(wrapper+8),0x38a4):NULL;
}
static int profile_record(const uint8_t *profile,unsigned boxer,char out[32]){
    if(!profile || boxer>=(0x1eb4-0x2bc)/0x38)return 0;
    const uint8_t *r=profile+0x2bc+boxer*0x38+14;
    if(r[3]>r[0])return 0;
    snprintf(out,32,"%u,%u,%u,%u",r[0],r[1],r[2],r[3]);
    return 1;
}
static void career_records(char blue[32],char red[32],int32_t *purse) {
    const uint8_t *manager=guest(0x59db10,0x198);
    if(!manager || word(manager+0x190)!=4)return;
    strcpy(blue,"-");strcpy(red,"-");
    const uint8_t *profile=active_profile();
    if(!profile)return;
    unsigned boxer=word(profile+0xa0)&0xffff;
    unsigned contract=word(profile+0x2584)&0xffff;
    enum { BOXERS=(0x1eb4-0x2bc)/0x38 };
    if(boxer>=BOXERS)return;
    const uint8_t *player=profile+0x2bc+boxer*0x38;
    /* GetCareerInfo tests the player's scheduled-bout word. SignContract
     * copies the accepted offer to slot 5. Both survive independently. */
    unsigned opponent=0xffff;
    if((!purse || word(player+0x30)) && contract<=5)
        opponent=word(profile+0x258c+contract*0x34)&0xffff;
    unsigned ids[2]={boxer,opponent};char *out[2]={blue,red};
    for(unsigned side=0;side<2;side++){
        if(ids[side]>=BOXERS || (side==1 && opponent==boxer))continue;
        if(!profile_record(profile,ids[side],out[side]))continue;
        /* GetContractInfo sends the word at offer + 4 to the purse formatter. */
        if(side==1 && purse)*purse=(int32_t)word(profile+0x258c+contract*0x34+4);
    }
}
/* Read the front end's own screen-stack identifier. The leading digit picks a
 * page inside an APT group, including transitions that reuse all the artwork. */
static _Atomic int career_frontend;
static int career_mode(void) {
    unsigned mode=word(XBOX_HOST(0x59db10+0x190));
    /* Career Central uses 1 before signing in, the bout uses 4, and a signed
     * career switches to 13 for trainer selection, practice and results. */
    return mode==4 || ((mode==1 || mode==13) && atomic_load(&career_frontend));
}

static int32_t career_wallet(void) {
    const uint8_t *p=career_mode()?active_profile():NULL;
    return p?(int32_t)word(p+0x170):-1;
}

static int same_property(const uint8_t *string,const char *name) {
    size_t n=strlen(name);
    for(size_t i=0;i<n;i++)if(tolower(string[i])!=tolower((unsigned char)name[i]))return 0;
    return string[n]==0;
}

static void career_page(const char *id) {
    static char previous[64];
    static const char prefix[]="screengroups/";
    static const char *const groups[]={"careercentral","mcon","train","fightstore","trophycase",
        "rankings","careersummary","records","cac","options","load","mainmenu"};
    static const char *const drills[]={"lp-Training-HeavyBag","lp-Training-ComboDummy","lp-Training-WeightLifting","lp-Tutorial-SparringInfo"};
    char route[64]="-";
    if(id && ((id[0]>='1' && id[0]<='9') || id[0]=='A') && !strncmp(id+1,prefix,sizeof prefix-1)){
        const char *group=id+sizeof prefix;
        if(!strcmp(group,"careercentral/careercentral"))atomic_store(&career_frontend,1);
        else if(!strcmp(group,"mainmenu/mainmenu") || !strcmp(group,"playnow/playnow"))atomic_store(&career_frontend,0);
        for(unsigned i=0;i<sizeof groups/sizeof *groups;i++){
            size_t n=strlen(groups[i]);
            if(strncmp(group,groups[i],n) || group[n]!='/')continue;
            if((i==2 || i>=7) && !career_mode())break;
            if(!strcmp(groups[i],"load") && id[0]=='1'){
                for(unsigned k=0;k<sizeof drills/sizeof *drills;k++)
                    if(!strcmp(group+n+1,drills[k])){snprintf(route,sizeof route,"drill %u",k+1);break;}
            }
            if(strcmp(group+n+1,groups[i]) && strcmp(group,"load/loadscreens"))break;
            snprintf(route,sizeof route,"%s %d",groups[i],id[0]=='A'?10:id[0]-'0');
            break;
        }
    }
    if(strcmp(route,previous)){
        strcpy(previous,route);
        printf("FNR3/1 CAREERPAGE %s\n",route);fflush(stdout);
    }
}

void fight_training_ratings(uint32_t address,int estimate) {
    const uint8_t *source=guest(address,16);
    if(!source || !career_mode())return;
    int16_t ratings[8];
    memcpy(ratings,source,sizeof ratings);
    printf("FNR3/1 TRAINSTATS %d %d %d %d %d %d %d %d %d\n",estimate,ratings[0],ratings[1],
           ratings[2],ratings[3],ratings[4],ratings[5],ratings[6],ratings[7]);
    fflush(stdout);
}

/* GetEstimatedRatings takes the highlighted training game's value. */
void fight_training_choice(unsigned game) {
    if(!career_mode())return;
    unsigned drill=game==0?1:game==1?2:game==6?3:game==3?4:0;
    printf("FNR3/1 TRAINCHOICE %u\n",drill);fflush(stdout);
}

void fight_store_price(int32_t price) {
    if(career_mode()) {
        printf("FNR3/1 SHOP %d %d\n",(int)price,(int)career_wallet());fflush(stdout);
    }
}

void fight_career_property(uint32_t key,uint32_t value) {
    const uint8_t *k=guest(key,32),*v=guest(value,12);
    if(!k || !v)return;
    uint32_t type=word(v+4)>>25;
    if(same_property(k+8,"m_sCurrentScreenId")){
        const uint8_t *s=type==1?guest(word(v+8),72):NULL;
        const char *page=s && memchr(s+8,0,64)?(const char *)s+8:NULL;
        if(page && !strcmp(page,"fnr3/achievements")){puts("FNR3/1 CABINET OPEN");fflush(stdout);}
        else career_page(page);
        return;
    }
    if(!career_mode())return;
    int number=-1;
    if(type==7)number=(int32_t)word(v+8);
    if(type==6){
        float f;memcpy(&f,v+8,sizeof f);
        if(f>=0 && f<1000000000.0f)number=(int)f;
    }
    if(same_property(k+8,"m_trophyId")){
        printf("FNR3/1 TROPHY %d\n",number);fflush(stdout);
    }else if(same_property(k+8,"m_trainerActualCost")){
        printf("FNR3/1 TRAINER %d %d\n",number,(int)career_wallet());fflush(stdout);
    }
}

void fight_trophy_status(unsigned slot,unsigned owned) {
    static _Thread_local unsigned mask;
    if(slot>=14)return;
    if(slot==0)mask=0;
    if(owned)mask|=1u<<slot;
    if(slot==13 && career_mode()){printf("FNR3/1 CASE %u\n",mask);fflush(stdout);}
}

static int rating(const uint8_t *p) {
    int16_t n;memcpy(&n,p,2);return n>=0 && n<=100?n:-1;
}

/* GetContractInfo has assembled the highlighted offer on its own stack here.
 * Its purse, first and last name and both boxers' displayed ratings all refer
 * to the same offer, including while the player is still comparing contracts. */
void fight_career_offer(uint32_t stack) {
    const uint8_t *s=guest(stack,0x4ce);
    const uint8_t *offer=s?guest(word(s+0x1c),8):NULL;
    if(!offer || !career_mode())return;
    char name[128];unsigned used=0;
    static const char digits[]="0123456789ABCDEF";
    const unsigned names[]={0x2ae,0x28e};
    for(unsigned i=0;i<2;i++){
        const uint8_t *part=s+names[i],*end=memchr(part,0,32);
        if(!end || end==part)continue;
        if(used){name[used++]='2';name[used++]='0';}
        for(;part<end;part++){
            if(*part<32 || (*part>=127 && *part<160))break;
            name[used++]=digits[*part>>4];name[used++]=digits[*part&15];
        }
    }
    name[used]=0;
    int values[6];
    const unsigned base[]={0x4ba,0x306};
    for(unsigned i=0;i<2;i++){
        const uint8_t *p=s+base[i];
        int left=rating(p),right=rating(p+2);
        values[i*3]=left<0 || right<0?-1:(left+right)/2;
        values[i*3+1]=rating(p+4);
        values[i*3+2]=rating(p+8);
    }
    char yours[32]="-",rival[32]="-";
    const uint8_t *profile=active_profile();
    if(profile){
        unsigned own=word(profile+0xa0)&0xffff,opponent=word(offer)&0xffff;
        profile_record(profile,own,yours);
        if(opponent!=own)profile_record(profile,opponent,rival);
    }
    printf("FNR3/1 OFFER %s %d %d %d %d %d %d %d %s %s\n",used?name:"-",(int32_t)word(offer+4),
            values[0],values[1],values[2],values[3],values[4],values[5],yours,rival);fflush(stdout);
}

static void sample_career(void) {
    static char previous[96];
    char own[32]="-", opponent[32]="-", line[96];
    int32_t purse=-1;
    career_records(own,opponent,&purse);
    snprintf(line,sizeof line,"FNR3/1 CAMP %s %s purse=%d",own,opponent,(int)purse);
    if(strcmp(line,previous)){strcpy(previous,line);puts(line);fflush(stdout);}
}
static void sample_fighters(int round) {
    static char previous[344];
    char line[344]="FNR3/1 FIGHTERS - -",blue[129],red[129],blueRecord[32],redRecord[32];
    if(round && roster_name(word(XBOX_HOST(0x59dcac)),blue,blueRecord) &&
       roster_name(word(XBOX_HOST(0x59dcb0)),red,redRecord)){
        career_records(blueRecord,redRecord,NULL);
        snprintf(line,sizeof line,"FNR3/1 FIGHTERS %s %s %s %s",blue,red,blueRecord,redRecord);
    }
    if(strcmp(line,previous)){strcpy(previous,line);puts(line);fflush(stdout);}
}
static float real32(const uint8_t *p) {
    float value;
    memcpy(&value, p, sizeof value);
    return value;
}

/* The bout owns a pair of boxer objects. Their condition components contain
 * the 0..100 values that also drive the retail HUD. Validate both components
 * before publishing a snapshot, including during a paused simulation. */
static int boxer_levels(uint32_t address, int *health, int *stamina) {
    const uint8_t *boxer = guest(address, 0x334);
    if (!boxer) return 0;
    const uint8_t *condition = guest(word(boxer + 0x330), 0x20);
    if (!condition || word(condition) != 0x48ae80) return 0;
    float h = real32(condition + 0x14), s = real32(condition + 0x1c);
    if (!(h >= 0 && h <= 100 && s >= 0 && s <= 100)) return 0;
    *health = (int)(h + .5f);
    *stamina = (int)(s + .5f);
    return 1;
}

/* The cutman installs his own countdown while the corner choice or treatment
 * is active. Its lifetime is independent of the fight clock. */
static void sample_recovery(const uint8_t *root) {
    static int previous=-1;
    int seconds=-1;
    const uint8_t *ring=root?guest(word(root+0x10),0x70):NULL;
    const uint8_t *cutman=ring && word(ring)==0x47f638 ? guest(word(ring+0x6c),0x50):NULL;
    if(cutman && word(cutman)==0x47ffa0){
        const uint8_t *timer=guest(word(cutman+0x4c),16);
        if(timer && word(timer)==0x47c0d8){
            float ticks=real32(timer+12);
            unsigned rate=word(XBOX_HOST(0x499694));
            if(rate && rate<=240 && ticks>=0 && ticks<=rate*3600)
                seconds=(int)ticks/rate;
        }
    }
    if(seconds!=previous){
        previous=seconds;
        printf("FNR3/1 RECOVERY %d\n",seconds);fflush(stdout);
    }
}

/* GetKoScores reads counter 14 for each scorer. GetControllerSides reads the
 * controller port at manager+1ac/+1b0 for offline bouts, including career. */
static _Atomic uint32_t callout_bout;
static uint32_t knockdowns[2], haymakers[2];
static atomic_int finished_callout;

// The retail result screen reads the winner from the round manager at +0x24.
static int reported_winner, recent_scorer=-1, hit_age, stagger_window;
static atomic_int referee_count, stumble_cue;
static uint32_t landed_total[2];

static int player_side(void) {
    int blue=word(XBOX_HOST(0x59dcbc))==0, red=word(XBOX_HOST(0x59dcc0))==0;
    return blue==red ? -1 : red;
}

void fight_ref_count(uint32_t count) {
    if (!callout_bout || finished_callout || count>11) return;
    if (count==11) count=0;
    if (atomic_exchange(&referee_count,(int)count)==(int)count) return;
    printf("FNR3/1 COUNT %u\n",count);
    fflush(stdout);
}

void fight_stumble(void) {
    if (callout_bout && !finished_callout) atomic_store(&stumble_cue,1);
}

static void sample_winner(const uint8_t *clock) {
    if (!callout_bout || reported_winner || !clock || word(clock)!=0x47db40) return;
    uint32_t winner=word(clock+0x24);
    int player=player_side();
    if (winner>2) return;
    reported_winner=1;
    puts(winner>1 || player<0 ? "FNR3/1 CALLOUT FINAL" :
         (int)winner==player ? "FNR3/1 CALLOUT WON" : "FNR3/1 CALLOUT LOST");
    /* The career match manager stores the signed event at +0x165. The retail
     * sponsor-art and trophy routines use this same byte. Weekly ESPN undercards
     * and main events share an ID, while each night and PPV have a distinct ID. */
    if (player>=0 && (int)winner==player) {
        const uint8_t *manager=XBOX_HOST(0x59db10);
        unsigned event=manager[0x165];
        if (word(manager+0x190)==4 && event>=2 && event<=9)
            printf("FNR3/1 EVENTWIN %u\n",event);
    }
    fflush(stdout);
}

void fight_cinematic(uint32_t sequence) {
    const char *result = sequence >= 100 && sequence <= 112 ? "KO" :
                         sequence >= 125 && sequence <= 136 ? "TKO" : NULL;
    if (!callout_bout || finished_callout) return;
    if (!result) {
        if(sequence<32 || (sequence>=113 && sequence<=124)){
            printf("FNR3/1 SHOT %u\n",sequence);fflush(stdout);
        }
        return;
    }
    const uint8_t *root = guest(word(XBOX_HOST(0x576aa0)), 0x30);
    if (!root || word(root + 0x14) != callout_bout) return;
    if (atomic_exchange(&finished_callout, 1)) return;
    printf("FNR3/1 CALLOUT %s\n", result);
    fflush(stdout);
}

static void sample_player_stats(const uint8_t *root) {
    static uint32_t last_id, last_down, last_against, last_hay;
    static int last_side=-1;
    uint32_t id=atomic_load(&callout_bout);
    int side=root && id && word(root+0x14)==id ? player_side() : -1;
    if(side<0){
        if(last_side>=0){puts("FNR3/1 PLAYERSTATS -");fflush(stdout);}
        last_side=-1;last_id=0;return;
    }
    uint32_t down=knockdowns[side],against=knockdowns[1-side],hay=haymakers[side];
    if(id==last_id && side==last_side && down==last_down && against==last_against && hay==last_hay)return;
    last_id=id;last_side=side;last_down=down;last_against=against;last_hay=hay;
    printf("FNR3/1 PLAYERSTATS %d %u %u %u\n",side,down,against,hay);fflush(stdout);
}

static void sample_knockdowns(const uint8_t *root, int round) {
    if (!round || !root) { callout_bout = 0; finished_callout = 0; referee_count=0; stumble_cue=0; return; }
    uint32_t id = word(root + 0x14);
    const uint8_t *stats = guest(id, 0x1d14 + 56 * 4);
    if (!stats || word(stats) != 0x47fb60) return;
    uint32_t score[2] = {word(stats + 0x1c5c), word(stats + 0x1d4c)};
    /* The retail m_sHayLanded field adds counters 0x35 and 0x37 in the
     * scorer's 60-word bank. Adjacent counters 0x34 and 0x36 are thrown. */
    uint32_t landed[2];
    for (unsigned side=0; side<2; side++){
        const uint8_t *bank=stats+0x1c24+side*0xf0;
        landed[side]=word(bank+0x35*4)+word(bank+0x37*4);
    }
    uint32_t hits[2]={word(stats+0x1c28),word(stats+0x1d18)};
    int same=callout_bout==id;
    if (!same) {
        puts("FNR3/1 BOUTBEGIN");fflush(stdout);
        reported_winner=0;recent_scorer=-1;hit_age=0;stagger_window=0;stumble_cue=0;referee_count=0;
    } else {
        int blue_hit=hits[0]>landed_total[0],red_hit=hits[1]>landed_total[1];
        if (blue_hit || red_hit) {
            recent_scorer=blue_hit==red_hit ? -1 : red_hit;
            hit_age=0;
            if (referee_count) fight_ref_count(0);
        } else if (hit_age<6) hit_age++;
    }
    memcpy(landed_total,hits,sizeof hits);
    if (atomic_exchange(&stumble_cue,0)) stagger_window=3;
    int hay=callout_bout==id && !finished_callout &&
            (landed[0]>haymakers[0] || landed[1]>haymakers[1]);
    memcpy(haymakers,landed,sizeof landed);
    int scorer = -1;
    if (callout_bout == id && !finished_callout) {
        if (score[0] == knockdowns[0] + 1 && score[1] == knockdowns[1]) scorer = 0;
        if (score[1] == knockdowns[1] + 1 && score[0] == knockdowns[0]) scorer = 1;
    }
    if (id != callout_bout) finished_callout = 0;
    callout_bout = id;
    memcpy(knockdowns, score, sizeof score);
    if (scorer < 0) {
        // The mat stumble cue is positional. Require a recent hit credited only
        // to the player's side before labeling it as an opponent stagger.
        if (stagger_window && !finished_callout && !reported_winner && recent_scorer>=0 &&
            hit_age<5 && recent_scorer==player_side()) {
            puts("FNR3/1 CALLOUT STAGGER");fflush(stdout);stagger_window=0;
        } else if (stagger_window) stagger_window--;
        if (hay) { puts("FNR3/1 CALLOUT HAYMAKER"); fflush(stdout); }
        return;
    }
    stagger_window=0;
    int player=player_side();
    if (player<0) return;
    puts(scorer == player ? "FNR3/1 CALLOUT DOWN" : "FNR3/1 CALLOUT GETUP");
    fflush(stdout);
}

/* The HUD can retain its last painted second while the simulation advances.
 * Keep the value passed to its format string, scoped to this clock and round. */
static _Atomic uint64_t hud_time;
void fight_hud_time(uint32_t seconds) {
    const uint8_t *root=guest(word(XBOX_HOST(0x576aa0)),0x30);
    if(!root || seconds>3600)return;
    uint32_t address=word(root+0x24);
    const uint8_t *clock=guest(address,0x2c);
    if(clock){
        uint32_t round=word(clock+0x18);
        if(round>0 && round<=15)
            atomic_store(&hud_time,((uint64_t)address<<32)|(round<<16)|(seconds+1));
    }
}

static void sample_bout(const uint8_t *root, const uint8_t *clock, int round) {
    static char previous[96];
    if (!round || !root || !clock) { previous[0] = 0; atomic_store(&hud_time,0); return; }
    const uint8_t *timer = guest(word(clock + 0x28), 16);
    const uint8_t *pair = guest(word(root + 0x0c), 8);
    if (!timer || word(timer) != 0x47c0d8 || !pair) return;
    float ticks = real32(timer + 12);
    unsigned rate = word(XBOX_HOST(0x499694));
    int blueHealth, blueStamina, redHealth, redStamina;
    if (rate == 0 || rate > 240 || !(ticks >= 0 && ticks <= 3600 * rate) ||
        !boxer_levels(word(pair), &blueHealth, &blueStamina) ||
        !boxer_levels(word(pair + 4), &redHealth, &redStamina)) return;
    uint64_t stamp=atomic_load(&hud_time);
    unsigned seconds=(unsigned)ticks/rate;
    if((uint32_t)(stamp>>32)==word(root+0x24) && ((stamp>>16)&0xffff)==(unsigned)round && (stamp&0xffff))
        seconds=(unsigned)(stamp&0xffff)-1;
    char line[96];
    snprintf(line, sizeof line, "FNR3/1 BOUT %d %u %d %d %d %d", round,
             seconds, blueHealth, blueStamina, redHealth, redStamina);
    /* The retail Fight Totals screen asks for cumulative total-thrown (0)
     * and total-landed (1) in each boxer's 60-counter bank. */
    const uint8_t *stats=guest(word(root+0x14),0x1d1c);
    if(stats && word(stats)==0x47fb60){
        unsigned bt=word(stats+0x1c24),bl=word(stats+0x1c28);
        unsigned rt=word(stats+0x1d14),rl=word(stats+0x1d18);
        if(bl<=bt && rl<=rt && bt<=100000 && rt<=100000){
            size_t at=strlen(line);
            snprintf(line+at,sizeof line-at," %u %u %u %u",bl,bt,rl,rt);
        }
    }
    if (strcmp(previous, line)) {
        strcpy(previous, line);
        puts(line);
        fflush(stdout);
    }
}

int fight_sample_counters(uint32_t *identity,uint32_t *tick,uint32_t *thrown,uint32_t *landed) {
    uint32_t root_address=word(XBOX_HOST(0x576aa0));
    const uint8_t *root=guest(root_address,0x30);
    if(!root)return 0;
    const uint8_t *clock=guest(word(root+0x24),0x2c),*stats=guest(word(root+0x14),0x1d1c);
    if(!clock || !word(clock+0x18) || word(clock+0x18)>15 || !stats || word(stats)!=0x47fb60)return 0;
    const uint8_t *timer=guest(word(clock+0x28),16);
    if(!timer || word(timer)!=0x47c0d8 || !(real32(timer+12)>0))return 0;
    *identity=word(root+0x14);*tick=word(timer+12);
    *thrown=word(stats+0x1c24)+word(stats+0x1d14);
    *landed=word(stats+0x1c28)+word(stats+0x1d18);
    return 1;
}

/* The score for the current round is posted at the bell. The round number
 * advances before the next exchange. Only that interval owns corner effects. */
static atomic_int posted_round;
int fight_round_break(void){return atomic_load_explicit(&posted_round,memory_order_relaxed)!=0;}

void fight_score_sample(void) {
    static unsigned flip;
    static char previous[512];
    if (++flip % 4) return;
    char line[512]; int used=0,round=0;
    const uint8_t *root=guest(word(XBOX_HOST(0x576aa0)),0x30),*judges=NULL,*clock=NULL;
    if(root){
        clock=guest(word(root+0x24),0x2c);
        if(clock){unsigned v=word(clock+0x18);if(v<=15)round=(int)v;}
        judges=guest(word(root+0x2c),20);
    }
    if(round!=atomic_load_explicit(&posted_round,memory_order_relaxed))
        atomic_store_explicit(&posted_round,0,memory_order_relaxed);
    if (flip % 32 == 0) sample_career();
    /* A short replacement of a retail object during a scene transition must
     * not erase the most recent complete bout on the second display. */
    static unsigned missing;
    if(round)missing=0;
    else if(missing<12){missing++;return;}
    sample_knockdowns(root,round);
    sample_player_stats(round?root:NULL);
    if (round) sample_winner(clock);
    sample_recovery(round?root:NULL);
    sample_bout(root,clock,round);
    if(flip % 12)return;
    sample_fighters(round);
    used=snprintf(line,sizeof line,"FNR3/1 CARD %d",round);
    if(round && judges){
        const uint8_t *cards[3];
        for(unsigned j=0;j<3;j++)cards[j]=guest(word(judges+8+4*j),0xfc);
        if(cards[0] && cards[1] && cards[2]){
            for(unsigned r=1;r<15 && r<=(unsigned)round;r++){
                unsigned points[6];int complete=1;
                for(unsigned j=0;j<3;j++)for(unsigned boxer=0;boxer<2;boxer++){
                    unsigned v=word(cards[j]+4*(15*boxer+r));
                    points[2*j+boxer]=v;if(!v || v>10)complete=0;
                }
                if(complete){
                    if(r==(unsigned)round)atomic_store_explicit(&posted_round,round,memory_order_relaxed);
                    used+=snprintf(line+used,sizeof line-used," %u,%u,%u,%u,%u,%u,%u",
                        r,points[0],points[1],points[2],points[3],points[4],points[5]);
                }
            }
        }
    }
    if(strcmp(line,previous)){strcpy(previous,line);puts(line);fflush(stdout);}
}

struct Track {uint32_t begin,end;char stem[20];};
static struct Track tracks[32];
static unsigned track_count;
static _Atomic uint32_t files[16];
static _Atomic uint64_t positions[16];
static atomic_int last_song;
static uint32_t big32(const unsigned char *p) {return (uint32_t)p[0]<<24 | (uint32_t)p[1]<<16 | (uint32_t)p[2]<<8 | p[3];}
void fight_data_init(const char *root){
    fight_trax_init(root);
    static const char *paths[]={"AUDIO/MUSIC/music.big","audio/music/music.big","AUDIO/MUSIC/MUSIC.BIG","audio/Music/music.big"};
    char path[1024];FILE *f=NULL;
    for(unsigned i=0;i<sizeof paths/sizeof *paths && !f;i++){
        if(snprintf(path,sizeof path,"%s/%s",root,paths[i])<(int)sizeof path)f=fopen(path,"rb");
    }
    if(!f)return;
    unsigned char h[4096];size_t size=fread(h,1,sizeof h,f);fclose(f);
    if(size<16 || memcmp(h,"BIGF",4))return;
    unsigned n=big32(h+8);size_t at=16;
    if(n>32)return;
    for(unsigned i=0;i<n;i++){
        if(at+9>size)return;
        uint32_t start=big32(h+at),bytes=big32(h+at+4);at+=8;
        const unsigned char *end=memchr(h+at,0,size-at);
        if(!end || bytes>UINT32_MAX-start)return;
        size_t len=(size_t)(end-h)-at;
        if(len<=4 || len>=sizeof tracks[i].stem || memcmp(end-4,".asf",4))return;
        tracks[i].begin=start;tracks[i].end=start+bytes;
        for(size_t k=0;k<len-4;k++) tracks[i].stem[k]=(char)toupper(h[at+k]);
        at+=len+1;
    }
    track_count=n;
}
static atomic_uint movie_token;
static int movie_name(const char *name,size_t length,const char *expected){
    if(strlen(expected)!=length)return 0;
    for(size_t i=0;i<length;i++)if(tolower((unsigned char)name[i])!=expected[i])return 0;
    return 1;
}
void fight_music_open(uint32_t token,const char *name,size_t length){
    if(token && name && length>=4 && movie_name(name+length-4,4,".nfx")){
        const char *base=name+length;
        while(base>name && base[-1]!='\\' && base[-1]!='/')--base;
        size_t n=(size_t)(name+length-base);
        int movie=movie_name(base,n,"easports.nfx")?1:movie_name(base,n,"intro.nfx")?2:0;
        unsigned previous=atomic_exchange(&movie_token,movie?token:0);
        if(movie || previous){printf("FNR3/1 INTRO %d\n",movie);fflush(stdout);}
    }
    static const char suffix[]="music.big";
    if(!token || !name || length<sizeof suffix-1 || !track_count)return;
    size_t off=length-(sizeof suffix-1);
    if(off && name[off-1]!='\\' && name[off-1]!='/')return;
    for(size_t i=0;i<sizeof suffix-1;i++)if(tolower((unsigned char)name[off+i])!=suffix[i])return;
    for(unsigned i=0;i<16;i++){uint32_t empty=0;if(atomic_compare_exchange_strong(&files[i],&empty,token) || empty==token){atomic_store(&positions[i],0);return;}}
}
void fight_music_close(uint32_t token){
    if(!token)return;
    unsigned movie=token;
    if(atomic_compare_exchange_strong(&movie_token,&movie,0)){puts("FNR3/1 INTRO 0");fflush(stdout);}
    for(unsigned i=0;i<16;i++){uint32_t t=token;if(atomic_compare_exchange_strong(&files[i],&t,0))return;}
}
void fight_music_seek(uint32_t token,uint64_t offset){
    if(!token)return;
    for(unsigned i=0;i<16;i++)if(atomic_load(&files[i])==token){atomic_store(&positions[i],offset);return;}
}
void fight_music_read(uint32_t token,uint64_t offset,uint32_t size){
    if(!token || !size || !track_count)return;
    unsigned i=0;while(i<16 && atomic_load_explicit(&files[i],memory_order_relaxed)!=token)i++;
    if(i==16)return;
    if(offset==UINT64_MAX)offset=atomic_fetch_add(&positions[i],size);
    else atomic_store(&positions[i],offset+size);
    if(size<512 || offset>UINT64_MAX-size)return;
    /* Sector-aligned streaming reads can start just before the first sample.
     * Ignore allocation/header reads and accept only the interior of a track. */
    for(unsigned t=0;t<track_count;t++)if(offset>=tracks[t].begin && offset+size<=tracks[t].end){
        if(atomic_exchange(&last_song,(int)t+1)!=(int)t+1){
            printf("FNR3/1 SONG %s\n",tracks[t].stem);fflush(stdout);
        }
        break;
    }
}
