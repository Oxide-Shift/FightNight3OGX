#include <ctype.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <time.h>
#endif
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
/* The interrupt can retire a voice between polls. Check early completions
 * immediately, then give the mixer short slices of CPU while the stop is pending.
 * The caller takes a fresh clock and rechecks guest ownership after each wait. */
static void wait_pending_voice(uint64_t elapsed, uint64_t remaining) {
    if (elapsed < 1000) return; /* first 100 us */
    static _Thread_local int allow_wait;
    if (!allow_wait) {
        const char *lock = getenv("RECOMP_GUEST_LOCK");
        allow_wait = lock && *lock == '1' ? -1 : 1;
    }
    if (allow_wait < 0) return; /* the optional serialized guest must yield through its scheduler */
    /* Leave the last quarter millisecond for the ordinary poll to limit
     * deadline overshoot. Guest time is in units of 100 ns. */
    if (remaining <= 2500) return;
    remaining -= 2500;
    uint64_t slice = remaining < 5000 ? remaining : 5000;
#ifdef _WIN32
    HANDLE timer = CreateWaitableTimerExW(NULL, NULL, CREATE_WAITABLE_TIMER_HIGH_RESOLUTION,
                                          TIMER_MODIFY_STATE | SYNCHRONIZE);
    if (!timer) return;
    LARGE_INTEGER due;
    due.QuadPart = -(LONGLONG)slice;
    if (SetWaitableTimer(timer, &due, 0, NULL, NULL, FALSE))
        WaitForSingleObject(timer, INFINITE);
    CloseHandle(timer);
#else
    struct timespec duration = {0, (long)(slice * 100)};
    nanosleep(&duration, NULL);
#endif
}
/* The synchronous DirectSound stop waits for an idle interrupt before it can
 * rebind a buffer. VOICE_OFF clears ACTIVE for each hardware channel. Only
 * run the title's locked cleanup early when the voice still owns those slots. */
int fight_voice_stop_ready(uint32_t address,uint64_t now){
    const uint8_t *voice=guest(address,0x80);
    if(!voice || (voice[0x12]&1)==0 || (voice[0x13]&0x80)==0)return 0;
    unsigned count=voice[0x64];
    uint32_t next=word(voice+0x4c),previous=word(voice+0x50);
    if(count==0 || count>3 || next==address+0x4c || !guest(next,8) || !guest(previous,8))return 0;
    const uint8_t *owner=guest(word(voice+8),0x488);
    uint32_t table=word(XBOX_HOST(0x471bd0));
    /* The tiled aperture aliases the contiguous allocation in this runtime. */
    if(table>=0xf0000000u && table-0xf0000000u<g_xbox_total_ram)
        table=0x80000000u+(table-0xf0000000u);
    const uint8_t *hardware=guest(table,256*128);
    if(!owner || !hardware)return 0;
    int idle=word(owner+0x84)==0;
    for(unsigned i=0;i<count;i++){
        uint16_t slot;
        memcpy(&slot,voice+0xc+i*2,sizeof(slot));
        if(slot>=256 || word(owner+0x88+slot*4)!=address)return 0;
        const uint8_t *channel=hardware+slot*128;
        /* MCPX PAR_STATE.ACTIVE applies to each channel. The retail idle ISR
         * also excludes CFG_FMT.PERSIST on its final notification handle. */
        if(word(channel+0x54)&(1u<<21))idle=0;
        if(i+1==count && (word(channel+4)&(1u<<23)))idle=0;
    }
    // A synchronous Stop has already been requested. An active or persistent
    // channel can miss its idle notification and leave the caller spinning for
    // 500 ms per sound. Allow two 256-sample mixer blocks before invoking the
    // same locked cleanup used by the retail watchdog, only for owned slots.
    if(!idle){
        const uint64_t two_blocks=(2ULL*256*10000000+48000-1)/48000;
        uint64_t requested=((uint64_t)word(voice+0x7c)<<32)|word(voice+0x78);
        if (now < requested) return 0;
        uint64_t elapsed = now - requested;
        if (elapsed < two_blocks) {
            wait_pending_voice(elapsed, two_blocks - elapsed);
            return 0;
        }
        static atomic_uint recovered;
        unsigned n=atomic_fetch_add_explicit(&recovered,1,memory_order_relaxed)+1;
        if(n==1 || (n&127)==0)
            fprintf(stderr,"[Fight Night] voice stop recovered count=%u channels=%u\n",n,count);
        return 1;
    }
    static atomic_uint completed;
    unsigned n=atomic_fetch_add_explicit(&completed,1,memory_order_relaxed)+1;
    if(n==1 || (n&127)==0)
        fprintf(stderr,"[Fight Night] voice stop completed count=%u channels=%u\n",n,count);
    return 1;
}
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

/* The cinematic dispatch hook observes ECX before the retail method touches it. */
extern RECOMP_TLS uint32_t g_ecx;
/* Director objects are word-aligned. Bit zero marks an entrance with a fixed 30 Hz cadence. */
static atomic_uint paced_director;
static atomic_uint paced_movie;
static atomic_int in_music_menu=1;
static void music_scene(int menu){
    if(menu)atomic_store(&paced_director,0);
    if(atomic_exchange(&in_music_menu,menu)!=menu){
        printf("FNR3/1 MUSICSCENE %d\n",menu);fflush(stdout);
    }
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
        if(!strncmp(group,"load/",5))music_scene(0);
        else if(!strncmp(group,"mainmenu/",9) || !strncmp(group,"careercentral/",14) ||
                !strncmp(group,"playnow/",8) || !strncmp(group,"options/",8) ||
                !strncmp(group,"mcon/",5) || !strncmp(group,"fightstore/",11))music_scene(1);
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

extern void fight_pad_playlist(int),fight_pad_menu(int);
enum { PLAYLIST_IDLE, PLAYLIST_ARMED, PLAYLIST_COMMITTED };
static atomic_int playlist_action;
void fight_arm_playlist(int enabled){
    int expected=enabled?PLAYLIST_IDLE:PLAYLIST_ARMED;
    int desired=enabled?PLAYLIST_ARMED:PLAYLIST_IDLE;
    if(atomic_compare_exchange_strong(&playlist_action,&expected,desired) || expected==desired)
        fight_pad_playlist(enabled);
}

void fight_cancel_playlist(void){
    int expected=PLAYLIST_ARMED;
    if(atomic_compare_exchange_strong(&playlist_action,&expected,PLAYLIST_IDLE) || expected==PLAYLIST_IDLE){
        fight_pad_playlist(0);
        puts("FNR3/1 PLAYLIST CANCELLED");fflush(stdout);
    }
}

int fight_career_property(uint32_t key,uint32_t value) {
#ifdef __ANDROID__
    extern void fight_service_menu_cancel(void),fight_bind_menu_thread(void);
    fight_service_menu_cancel();
#endif
    const uint8_t *k=guest(key,32);
    if(!k || (k[8]|32)!='m' || k[9]!='_')return 0;
    int screen=same_property(k+8,"m_sCurrentScreenId");
    int trophy=!screen && same_property(k+8,"m_trophyId");
    int trainer=!screen && !trophy && same_property(k+8,"m_trainerActualCost");
    if(!screen && !trophy && !trainer)return 0;
    const uint8_t *v=guest(value,12);
    if(!v)return 0;
    uint32_t type=word(v+4)>>25;
    if(screen){
        // The imported root carousel publishes its selected index on a temporary object.
        if(type==7 || type==6){
            unsigned index=word(v+8);
            if(type==6){
                float f;memcpy(&f,v+8,sizeof f);
                if(!(f>=0 && f<8) || (float)(unsigned)f!=f)return 0;
                index=(unsigned)f;
            }
            if(index<8){printf("FNR3/1 MAINHOVER %u\n",index);fflush(stdout);}
            return 0;
        }
        const uint8_t *s=type==1?guest(word(v+8),72):NULL;
        const char *page=s && memchr(s+8,0,64)?(const char *)s+8:NULL;
        // Only this private assignment may skip the normal APT setter. The handler
        // tests the temporary object's member before calling the retail screen manager.
        if(page && (!strcmp(page,"fnr3/open?") || !strcmp(page,"fnr3/trax?"))){
#ifdef __ANDROID__
            fight_bind_menu_thread();
#endif
            int expected=PLAYLIST_ARMED;
            int open=atomic_compare_exchange_strong(&playlist_action,&expected,PLAYLIST_COMMITTED);
            if(open)fight_pad_playlist(0);
            puts(open?"FNR3/1 PLAYLIST OPENING":"FNR3/1 MUSICMENU OPEN");fflush(stdout);
            // Keep the previous imported script usable until its archive upgrade completes.
            return !strcmp(page,"fnr3/open?")?open:!open;
        }
#ifdef __ANDROID__
        if(page && (!strcmp(page,"fnr3/achievements") || !strcmp(page,"fnr3/music")))fight_bind_menu_thread();
#endif
        if(page && !strcmp(page,"fnr3/achievements")){puts("FNR3/1 CABINET OPEN");fflush(stdout);}
        else if(page && !strcmp(page,"fnr3/music")){puts("FNR3/1 MUSICMENU OPEN");fflush(stdout);}
        else {
            static int previous=-1;
            if(page && strlen(page)>1 && !strncmp(page+1,"screengroups/",13)){
                int front=!strcmp(page,"2screengroups/mainmenu/mainmenu")?1:
                          !strcmp(page,"5screengroups/options/options")?2:
                          !strcmp(page+1,"screengroups/playnow/playnow")?3:0;
                if(previous!=front){
                    previous=front;
                    atomic_store(&playlist_action,PLAYLIST_IDLE);
                    fight_pad_menu(front==1);
                    printf("FNR3/1 FRONT %d\n",front);fflush(stdout);
                }
            }
            career_page(page);
        }
        return 0;
    }
    if(!career_mode())return 0;
    int number=-1;
    if(type==7)number=(int32_t)word(v+8);
    if(type==6){
        float f;memcpy(&f,v+8,sizeof f);
        if(f>=0 && f<1000000000.0f)number=(int)f;
    }
    if(trophy){
        printf("FNR3/1 TROPHY %d\n",number);fflush(stdout);
    }else if(trainer){
        printf("FNR3/1 TRAINER %d %d\n",number,(int)career_wallet());fflush(stdout);
    }
    return 0;
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
static unsigned ko_moment_mask;

/* Retail 00023930 reads both boxers with 000454A0 / 0002C290.
 * A set byte enters director state 4 and dispatches Sce_KnockMoment.
 * The boxer sets and clears this byte at 000349FA and 00034A5E. */
static unsigned ko_moment_entries(const uint8_t *root) {
    const uint8_t *pair = guest(word(root + 0x0c), 8);
    if (!pair) return 0;
    unsigned mask = 0;
    for (unsigned side = 0; side < 2; ++side) {
        const uint8_t *boxer = guest(word(pair + side * 4), 0x334);
        if (!boxer) return 0;
        const uint8_t *condition = guest(word(boxer + 0x330), 4);
        if (!condition || word(condition) != 0x48ae80 || boxer[0x25c] > 1) return 0;
        mask |= (unsigned)boxer[0x25c] << side;
    }
    unsigned entered = mask & ~ko_moment_mask;
    ko_moment_mask = mask;
    return entered;
}
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

/* Read the same presentation interval that the title's cinematic update writes
 * through sub_000FF240 and sub_0029E1E0. The director must still be playing a selected retail script. */
unsigned fight_present_interval(void){
    if(atomic_load(&paced_movie))return 2;
    /* sub_00188860 advances the active replay transport. The recorder keeps running
     * during a fight, but only playback attaches a transport to the owner's +0xc. */
    uint32_t transport=word(XBOX_HOST(0x5b3318));
    const uint8_t *replay=guest(transport,12);
    if(replay && word(replay+8)>=1 && word(replay+8)<=4){
        const uint8_t *owner=guest(word(replay+4),16);
        if(owner && word(owner+12)==transport)return 2;
    }
    uint32_t address=atomic_load(&paced_director);
    if(!address)return 1;
    const uint8_t *director=guest(address&~1u,0x58);
    if(!director || !director[0x48] || word(director+0x4c)!=3)return 1;
    const uint8_t *script=guest(word(director+0x40),16);
    if(!script || word(script)!=0x01707473 || word(director+0x54)>=word(script+8))return 1;
    if(address&1)return 2;
    const uint8_t *timing=guest(word(XBOX_HOST(0x594da0)),0x1c);
    const uint8_t *display=timing?guest(word(timing+0x0c),0xe4):NULL;
    unsigned interval=display?word(display+0xe0):1;
    return interval>=1 && interval<=3?interval:1;
}

void fight_cinematic(uint32_t sequence) {
    /* The retail result director cuts between winner and loser scripts. BLR, DQ losses,
     * KOL and TKOL use the same authored timing field as the winning angles. */
    int outcome=(sequence>=57 && sequence<=112) || (sequence>=125 && sequence<=136);
    /* BCS_BLU_01 through BCS_BLU_13, including the four venue variants,
     * occupy slots 0-16. Slot 17 begins the entrance scripts. */
    int coach=sequence<=16;
    /* The between-round card presenter uses the director's display interval
     * too. Slot 113 belongs to the referee's get-up sequence. */
    int round_card=sequence>=114 && sequence<=124;
    int entrance=(sequence>=17 && sequence<=56) || (sequence>=137 && sequence<=143);
    uint32_t director=0;
    if(g_ecx && (g_ecx&3)==0 && (entrance || coach || round_card || outcome))
        director=g_ecx | (entrance?1u:0u);
    atomic_store(&paced_director,director);
    if (!callout_bout) return;
    if (!outcome) {
        if(!finished_callout && (sequence<32 || (sequence>=113 && sequence<=124))){
            printf("FNR3/1 SHOT %u\n",sequence);fflush(stdout);
        }
        return;
    }
    const uint8_t *root = guest(word(XBOX_HOST(0x576aa0)), 0x30);
    if (!root || word(root + 0x14) != callout_bout) return;
    /* Play Now can show the judges' cards with GetWinner still returning -1.
     * The result director establishes that the bout ended. The camera angle
     * does not establish the player's side or earn a win. */
    if (sequence < 86) {
        int active=0;
        if (!atomic_compare_exchange_strong(&finished_callout,&active,1)) return;
        puts("FNR3/1 CALLOUT FINAL");
    } else {
        if (atomic_exchange(&finished_callout,2)==2) return;
        puts(sequence<=112 ? "FNR3/1 CALLOUT KO" : "FNR3/1 CALLOUT TKO");
    }
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
    if (!round || !root) { callout_bout = 0; finished_callout = 0; referee_count=0; stumble_cue=0; ko_moment_mask=0; return; }
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
        atomic_store(&in_music_menu,0);
        atomic_store(&paced_director,0);
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
    if (id != callout_bout) { finished_callout = 0; ko_moment_mask = 0; }
    callout_bout = id;
    unsigned rocked = ko_moment_entries(root);
    memcpy(knockdowns, score, sizeof score);
    if (scorer < 0) {
        // The mat stumble cue is positional. Require a recent hit credited only
        // to the player's side before labeling it as an opponent stagger.
        if (stagger_window && !finished_callout && !reported_winner && recent_scorer>=0 &&
            hit_age<5 && recent_scorer==player_side()) {
            puts("FNR3/1 CALLOUT STAGGER");fflush(stdout);stagger_window=0;
        } else if (stagger_window) stagger_window--;
        if (hay) { puts("FNR3/1 CALLOUT HAYMAKER"); fflush(stdout); }
        if (rocked && !finished_callout && !reported_winner && !atomic_load(&referee_count)) {
            puts("FNR3/1 CALLOUT ROCKED"); fflush(stdout);
        }
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

static void sample_playlist(void) {
    /* The settings singleton (sub_0013A020) is shared by the EA Trax menu
     * and the stock song chooser. sub_001607B0 tests bits 3..14 in row order. */
    static int previous = -1;
    if (!(word(XBOX_HOST(0x5ad9c8)) & 1)) return;
    int enabled = (word(XBOX_HOST(0x5ac8a0 + 0x168)) >> 3) & 0xfff;
    if (enabled != previous) {
        printf("FNR3/1 TRACKMASK %d\n", enabled);
        fflush(stdout);
        previous = enabled;
    }
}

void fight_score_sample(void) {
#ifdef __ANDROID__
    extern void fight_service_menu_cancel(void);
    fight_service_menu_cancel();
#endif
    static unsigned flip;
    static char previous[512];
    if (++flip % 4) return;
    sample_playlist();
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
static atomic_int last_song, cue_override;
static atomic_uint bank_kind[16];
void fight_music_cues(int enabled){atomic_store(&cue_override,enabled);}
static uint32_t big32(const unsigned char *p) {return (uint32_t)p[0]<<24 | (uint32_t)p[1]<<16 | (uint32_t)p[2]<<8 | p[3];}
/* Streamed music and short scene cues use separate sample ranges. */
struct SampleSpan { uint32_t first,end; };
static struct SampleSpan *samples;
static size_t sample_count;
static char bank_path[1024];
static atomic_int bank_available,index_state,voice_requested;
static int index_streams(const char *path,const struct Track *entries,unsigned count,
                         struct SampleSpan **result,size_t *used){
    struct SampleSpan *spans=NULL;
    size_t nspans=0,capacity=0;
    FILE *f=fopen(path,"rb");
    if(!f)return 0;
    int ok=1;
    unsigned char header[8];
    for(unsigned i=0;ok && i<count;i++){
        uint32_t at=entries[i].begin,end=entries[i].end;
        int ended=0;
        while(at<end){
            if(end-at<8 || fseek(f,(long)at,SEEK_SET) || fread(header,1,8,f)!=8){ok=0;break;}
            uint32_t len=word(header+4);
            if(len<8 || len>end-at){ok=0;break;}
            if(!memcmp(header,"SCDl",4)){
                if(len<=20 || (nspans && at<spans[nspans-1].end)){ok=0;break;}
                if(nspans==capacity){
                    size_t larger=capacity?capacity*2:4096;
                    if(larger>1024*1024){ok=0;break;}
                    void *more=realloc(spans,larger*sizeof *spans);
                    if(!more){ok=0;break;}
                    spans=more;capacity=larger;
                }
                spans[nspans++]=(struct SampleSpan){at+20,at+len};
            }else if(!memcmp(header,"SCEl",4)){
                ended=at+len==end;break;
            }else if(memcmp(header,"SCHl",4) && memcmp(header,"SCCl",4)){ok=0;break;}
            at+=len;
        }
        if(!ended)ok=0;
    }
    fclose(f);
    if(!ok || !nspans){free(spans);return 0;}
    *result=spans;*used=nspans;return 1;
}
static struct SampleSpan *cue_spans[2];
static size_t cue_counts[2];
static int index_cue(unsigned bank,const char *leaf){
    char path[sizeof bank_path];strcpy(path,bank_path);
    char *slash=strrchr(path,'/');
    if(!slash || strlen(leaf)>strlen(slash+1))return 0;
    strcpy(slash+1,leaf);
    FILE *f=fopen(path,"rb");if(!f)return 0;
    unsigned char h[4096];size_t size=fread(h,1,sizeof h,f);fclose(f);
    if(size<16 || memcmp(h,"BIGF",4))return 0;
    struct Track entries[32];unsigned count=big32(h+8);
    if(!count || count>32)return 0;
    size_t at=16;
    for(unsigned i=0;i<count;i++){
        if(at+9>size)return 0;
        uint32_t start=big32(h+at),bytes=big32(h+at+4);at+=8;
        const unsigned char *end=memchr(h+at,0,size-at);
        if(!end || end-h<4 || end<h+at+4 || memcmp(end-4,".asf",4) || bytes>UINT32_MAX-start)return 0;
        entries[i].begin=start;entries[i].end=start+bytes;at=(size_t)(end-h)+1;
    }
    return index_streams(path,entries,count,&cue_spans[bank],&cue_counts[bank]);
}
static int index_samples(void){
    if(!index_streams(bank_path,tracks,track_count,&samples,&sample_count))return 0;
    index_cue(0,"intro.big");index_cue(1,"rgirl.big");return 1;
}
static void prepare_voice(void){
    if(!atomic_load(&bank_available) || !atomic_load(&voice_requested))return;
    int state=0;
    if(atomic_compare_exchange_strong(&index_state,&state,1)){
        state=index_samples()?2:3;
        atomic_store(&index_state,state);
    }
    if(state==2 || state==3){
        printf("FNR3/1 MUSICBANK %d\n",state==2);fflush(stdout);
    }
}
void fight_music_voice(int enabled){
    atomic_store(&voice_requested,enabled);
    if(enabled)prepare_voice();
}
static void silence_samples(const struct SampleSpan *spans,size_t count,uint64_t offset,uint32_t length,unsigned char *buffer){
    if(!buffer || !atomic_load(&voice_requested) || atomic_load(&index_state)!=2)return;
    uint64_t end=offset+length;
    size_t lo=0,hi=count;
    while(lo<hi){size_t mid=lo+(hi-lo)/2;if(spans[mid].end<=offset)lo=mid+1;else hi=mid;}
    for(size_t i=lo;i<count && spans[i].first<end;i++){
        uint64_t first=spans[i].first>offset?spans[i].first:offset;
        uint64_t last=spans[i].end<end?spans[i].end:end;
        memset(buffer+(size_t)(first-offset),0,(size_t)(last-first));
    }
}
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
    memcpy(bank_path,path,strlen(path)+1);
    atomic_store(&bank_available,1);prepare_voice();
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
        int reel=n==12 && isdigit((unsigned char)base[6]) && isdigit((unsigned char)base[7]) &&
                 (movie_name(base,6,"rivals") || movie_name(base,6,"extras"));
        if(movie || reel)atomic_store(&paced_movie,token);
        unsigned previous=atomic_exchange(&movie_token,movie?token:0);
        if(movie || previous){printf("FNR3/1 INTRO %d\n",movie);fflush(stdout);}
    }
    if(!token || !name || !length || !track_count)return;
    const char *base=name+length;
    while(base>name && base[-1]!='\\' && base[-1]!='/')--base;
    size_t n=length-(size_t)(base-name);unsigned kind=0;
    if(movie_name(base,n,"music.big"))kind=1;
    else if(base-name<6 || !movie_name(base-6,5,"music"))return;
    else if(base-name>6 && base[-7]!='\\' && base[-7]!='/')return;
    else if(movie_name(base,n,"intro.big"))kind=2;
    else if(movie_name(base,n,"rgirl.big"))kind=3;
    else {
        static const char *const loops[]={"atmo_lp1.bnk","atmo_lp2.bnk","brasclp1.bnk","brasclp2.bnk","conselp1.bnk",
            "conselp2.bnk","dilaplp1.bnk","dilaplp2.bnk","dilaplp3.bnk","dilaplp4.bnk",
            "dipselp1.bnk","dipselp2.bnk","kraytlp1.bnk","kraytlp2.bnk","ltdalp1.bnk",
            "obietlp1.bnk","obietlp2.bnk","obietlp3.bnk","obietlp4.bnk","obietlp5.bnk",
            "rocclp1.bnk","rocclp2.bnk","seab_lp1.bnk","seab_lp2.bnk","seab_lp3.bnk",
            "seab_lp4.bnk","seab_lp5.bnk","sliclp1.bnk","sliclp2.bnk","younrlp1.bnk",
            "younrlp2.bnk","younrlp3.bnk","younrlp4.bnk"};
        for(unsigned i=0;i<sizeof loops/sizeof *loops;i++)if(movie_name(base,n,loops[i])){kind=4;break;}
    }
    if(!kind)return;
    if(kind==4)music_scene(0);
    for(unsigned i=0;i<16;i++){
        uint32_t empty=0;
        if(atomic_compare_exchange_strong(&files[i],&empty,token) || empty==token){
            atomic_store(&positions[i],0);atomic_store(&bank_kind[i],kind);return;
        }
    }
}
void fight_music_close(uint32_t token){
    if(!token)return;
    unsigned playback=token;
    atomic_compare_exchange_strong(&paced_movie,&playback,0);
    unsigned movie=token;
    if(atomic_compare_exchange_strong(&movie_token,&movie,0)){puts("FNR3/1 INTRO 0");fflush(stdout);}
    for(unsigned i=0;i<16;i++){uint32_t t=token;if(atomic_compare_exchange_strong(&files[i],&t,0))return;}
}
void fight_music_seek(uint32_t token,uint64_t offset){
    if(!token)return;
    for(unsigned i=0;i<16;i++)if(atomic_load(&files[i])==token){atomic_store(&positions[i],offset);return;}
}
void fight_music_read(uint32_t token,uint64_t offset,uint32_t size,void *buffer){
    if(!token || !size || !track_count)return;
    unsigned i=0;while(i<16 && atomic_load_explicit(&files[i],memory_order_relaxed)!=token)i++;
    if(i==16)return;
    if(offset==UINT64_MAX)offset=atomic_fetch_add(&positions[i],size);
    else atomic_store(&positions[i],offset+size);
    if(offset>UINT64_MAX-size)return;
    unsigned kind=atomic_load(&bank_kind[i]);
    if(kind!=1){
        if(!buffer || !atomic_load(&cue_override) || !atomic_load(&voice_requested) || atomic_load(&index_state)!=2)return;
        if(kind==4){
            /* Verified BNKl stereo banks place both channels after their 100-byte header. */
            size_t skip=offset<100?(size_t)(100-offset):0;
            if(skip<size)memset((unsigned char *)buffer+skip,0,size-skip);
        }else if(kind==2 || kind==3)silence_samples(cue_spans[kind-2],cue_counts[kind-2],offset,size,buffer);
        return;
    }
    if(atomic_load(&in_music_menu) || atomic_load(&cue_override))silence_samples(samples,sample_count,offset,size,buffer);
    if(size<512)return;
    /* Sector-aligned streaming reads can start just before the first sample.
     * Ignore allocation/header reads and accept only the interior of a track. */
    for(unsigned t=0;t<track_count;t++)if(offset>=tracks[t].begin && offset+size<=tracks[t].end){
        if(atomic_exchange(&last_song,(int)t+1)!=(int)t+1){
            printf("FNR3/1 SONG %s\n",tracks[t].stem);fflush(stdout);
        }
        break;
    }
}
