/* Drive the production fight observers with the retail memory layout. */
#include <assert.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
static char events[8192];
static int record(const char *format,...){
    size_t end=strlen(events);va_list args;va_start(args,format);
    int n=vsnprintf(events+end,sizeof events-end,format,args);va_end(args);return n;
}
static int record_line(const char *s){return record("%s\n",s);}
#define printf record
#define puts record_line
#include "../native/fight_data.c"
#undef printf
#undef puts
void fight_trax_init(const char *root){(void)root;}
RECOMP_TLS uint32_t g_ecx;
static uint8_t ram[0x600000];
size_t g_xbox_total_ram=sizeof ram,g_xbox_map_size=sizeof ram;
ptrdiff_t xbox_GetMemoryOffset(void){return (ptrdiff_t)(uintptr_t)ram;}
static void set(uint32_t a,uint32_t v){memcpy(ram+a,&v,4);}
int main(void){
    g_ecx=0x62000;
    set(g_ecx+0x48,1);set(g_ecx+0x4c,3);set(g_ecx+0x40,0x63000);set(0x63000,0x01707473);
    set(0x63008,300);set(0x594da0,0x64000);set(0x6400c,0x65000);set(0x650e0,2);
    fight_cinematic(60);assert(fight_present_interval()==2);
    set(0x650e0,1);assert(fight_present_interval()==1);set(0x650e0,2);
    set(g_ecx+0x54,300);assert(fight_present_interval()==1);set(g_ecx+0x54,0);
    fight_cinematic(15);assert(fight_present_interval()==1);
    fight_cinematic(100);assert(fight_present_interval()==2);
    set(g_ecx+0x4c,4);assert(fight_present_interval()==1);
    set(g_ecx+0x4c,3);music_scene(1);assert(fight_present_interval()==1);
    /* Switching profiles, an empty contract or leaving career must clear stale scouting. */
    uint32_t manager=0x59db10,profile=0x40000;
    set(manager+0x190,4);set(manager+0xf0,1);set(manager+0x3a4c,0x4ab240);
    set(manager+0x3a4c+8,profile);set(profile+0xa0,1);set(profile+0x2690,2);set(profile+0x2584,5);
    uint32_t key=0x31000, value=0x31100, string=0x31200;
    strcpy((char *)ram+key+8,"m_sCurrentScreenId");
    set(value+4,1u<<25);set(value+8,string);
    strcpy((char *)ram+string+8,"fnr3/achievements");
    fight_career_property(key,value);assert(!strcmp(events,"FNR3/1 CABINET OPEN\n"));events[0]=0;
    strcpy((char *)ram+string+8,"fnr3/music");
    fight_career_property(key,value);assert(!strcmp(events,"FNR3/1 MUSICMENU OPEN\n"));events[0]=0;
    strcpy((char *)ram+string+8,"1screengroups/mcon/mcon");
    fight_career_property(key,value);fight_career_property(key,value);
    assert(!strcmp(events,"FNR3/1 CAREERPAGE mcon 1\n"));events[0]=0;
    strcpy((char *)ram+string+8,"2screengroups/mcon/mcon");
    fight_career_property(key,value);assert(!strcmp(events,"FNR3/1 CAREERPAGE mcon 2\n"));events[0]=0;
    set(manager+0x190,1);
    strcpy((char *)ram+string+8,"1screengroups/careercentral/careercentral");
    fight_career_property(key,value);assert(!strcmp(events,"FNR3/1 CAREERPAGE careercentral 1\n"));events[0]=0;
    set(manager+0x190,13);
    strcpy((char *)ram+string+8,"3screengroups/train/train");
    fight_career_property(key,value);assert(!strcmp(events,"FNR3/1 CAREERPAGE train 3\n"));events[0]=0;
    const char *drills[]={"lp-Training-HeavyBag","lp-Training-ComboDummy","lp-Training-WeightLifting","lp-Tutorial-SparringInfo"};
    for(unsigned i=0;i<4;i++){
        char expected[80];
        snprintf((char *)ram+string+8,64,"1screengroups/load/%s",drills[i]);
        snprintf(expected,sizeof expected,"%sFNR3/1 CAREERPAGE drill %u\n",i==0?"FNR3/1 MUSICSCENE 0\n":"",i+1);
        fight_career_property(key,value);assert(!strcmp(events,expected));events[0]=0;
    }
    strcpy((char *)ram+string+8,"Ascreengroups/cac/cac");
    fight_career_property(key,value);assert(!strcmp(events,"FNR3/1 CAREERPAGE cac 10\n"));events[0]=0;
    strcpy((char *)ram+string+8,"1screengroups/playnow/playnow");
    fight_career_property(key,value);assert(!strcmp(events,"FNR3/1 MUSICSCENE 1\nFNR3/1 CAREERPAGE -\n"));events[0]=0;
    strcpy((char *)ram+string+8,"3screengroups/train/train");
    fight_career_property(key,value);assert(!events[0]);
    set(manager+0x190,4);
    for(unsigned i=0;i<14;i++)fight_trophy_status(i,i==0 || i==13);
    assert(!strcmp(events,"FNR3/1 CASE 8193\n"));events[0]=0;
    uint32_t offer_stack=0x32000;
    set(offer_stack+0x1c,profile+0x258c);set(profile+0x2590,7400);
    strcpy((char *)ram+offer_stack+0x2ae,"Blue");strcpy((char *)ram+offer_stack+0x28e,"Test");
    memcpy(ram+offer_stack+0x4ba,(uint16_t[]){70,80,81,44,68},10);
    memcpy(ram+offer_stack+0x306,(uint16_t[]){90,80,70,33,72},10);
    int16_t figure[]={-2,3,4,5,6,7,8,9};
    memcpy(ram+offer_stack+128,figure,sizeof figure);
    fight_training_choice(6);assert(!strcmp(events,"FNR3/1 TRAINCHOICE 3\n"));events[0]=0;
    fight_training_ratings(offer_stack+128,1);
    assert(!strcmp(events,"FNR3/1 TRAINSTATS 1 -2 3 4 5 6 7 8 9\n"));events[0]=0;
    set(profile+0x258c,2);
    memcpy(ram+profile+0x2bc+0x38+14,(uint8_t[]){4,1,0,2},4);
    memcpy(ram+profile+0x2bc+2*0x38+14,(uint8_t[]){9,2,0,6},4);
    fight_career_offer(offer_stack);
    assert(!strcmp(events,"FNR3/1 OFFER 426C75652054657374 7400 75 81 68 85 70 72 4,1,0,2 9,2,0,6\n"));events[0]=0;
    set(profile+0x170,12500);set(profile+0x2694,3750);set(profile+0x2bc+0x38+0x30,1);
    memcpy(ram+profile+0x2bc+0x38+14,(uint8_t[]){4,1,0,2},4);
    memcpy(ram+profile+0x2bc+2*0x38+14,(uint8_t[]){9,2,0,6},4);
    sample_career();sample_career();assert(!strcmp(events,"FNR3/1 CAMP 4,1,0,2 9,2,0,6 purse=3750\n"));events[0]=0;
    set(profile+0x2bc+0x38+0x30,0);sample_career();assert(!strcmp(events,"FNR3/1 CAMP 4,1,0,2 - purse=-1\n"));events[0]=0;
    set(profile+0x258c,2);set(profile+0x2584,0);sample_career();assert(!events[0]);
    set(profile+0x2bc+0x38+0x30,1);set(profile+0x2584,6);sample_career();assert(!events[0]);
    set(profile+0x2690,1);set(profile+0x2584,5);sample_career();assert(!events[0]);
    set(profile+0x2690,2);set(profile+0x170,12700);set(profile+0x2694,4800);sample_career();assert(!strcmp(events,"FNR3/1 CAMP 4,1,0,2 9,2,0,6 purse=4800\n"));events[0]=0;
    set(manager+0x168,1);sample_career();assert(!strcmp(events,"FNR3/1 CAMP - - purse=-1\n"));events[0]=0;
    set(manager+0x168,0);set(manager+0x190,0);sample_career();assert(!events[0]);
    uint32_t root=0x20000,stats=0x25000,clock=0x28000;
    set(0x576aa0,root);set(root+0x14,stats);set(root+0x24,clock);set(clock,0x47db40);
    set(stats,0x47fb60);set(0x59dcbc,0);set(0x59dcc0,UINT32_MAX);set(clock+0x24,UINT32_MAX);
    /* During rollover the game can still display 0:30 after the raw timer reads 29. */
    uint32_t timer=0x28200,pair=0x28300,boxer=0x29000,condition=0x2a000;
    set(clock+0x18,1);set(clock+0x28,timer);set(timer,0x47c0d8);
    float ticks=1799.0f;memcpy(ram+timer+12,&ticks,sizeof ticks);set(0x499694,60);
    set(root+0x0c,pair);set(pair,boxer);set(pair+4,boxer);set(boxer+0x330,condition);set(condition,0x48ae80);
    fight_hud_time(30);sample_bout(ram+root,ram+clock,1);
    assert(strstr(events,"BOUT 1 30 "));events[0]=0;
    fight_hud_time(29);sample_bout(ram+root,ram+clock,1);
    assert(strstr(events,"BOUT 1 29 "));events[0]=0;
    fight_hud_time(0);sample_bout(ram+root,ram+clock,1);
    assert(strstr(events,"BOUT 1 0 "));events[0]=0;
    set(clock+0x18,2);sample_bout(ram+root,ram+clock,2);
    assert(strstr(events,"BOUT 2 29 "));events[0]=0;
    sample_bout(NULL,NULL,0);set(clock+0x18,1);sample_bout(ram+root,ram+clock,1);
    assert(strstr(events,"BOUT 1 29 "));events[0]=0;
    sample_knockdowns(ram+root,1);sample_winner(ram+clock);events[0]=0;
    set(stats+0x1c24+0x34*4,1);sample_knockdowns(ram+root,1);assert(!events[0]);
    set(stats+0x1c24+0x35*4,1);set(stats+0x1c28,1);sample_knockdowns(ram+root,1);
    assert(!strcmp(events,"FNR3/1 CALLOUT HAYMAKER\n"));events[0]=0;
    fight_stumble();sample_knockdowns(ram+root,1);assert(strstr(events,"STAGGER"));events[0]=0;
    set(stats+0x1d18,1);fight_stumble();sample_knockdowns(ram+root,1);assert(!events[0]);
    set(stats+0x1d4c,1);sample_knockdowns(ram+root,1);assert(strstr(events,"GETUP"));events[0]=0;
    for(int n=1;n<=10;n++){fight_ref_count(n);fight_ref_count(n);}
    assert(!strcmp(events,"FNR3/1 COUNT 1\nFNR3/1 COUNT 2\nFNR3/1 COUNT 3\nFNR3/1 COUNT 4\nFNR3/1 COUNT 5\nFNR3/1 COUNT 6\nFNR3/1 COUNT 7\nFNR3/1 COUNT 8\nFNR3/1 COUNT 9\nFNR3/1 COUNT 10\n"));
    events[0]=0;fight_cinematic(100);fight_ref_count(1);set(clock+0x24,1);sample_winner(ram+clock);sample_winner(ram+clock);
    assert(!strcmp(events,"FNR3/1 CALLOUT KO\nFNR3/1 CALLOUT LOST\n"));
    sample_knockdowns(NULL,0);set(0x59dcbc,UINT32_MAX);set(0x59dcc0,0);sample_knockdowns(ram+root,1);
    events[0]=0;sample_winner(ram+clock);assert(!strcmp(events,"FNR3/1 CALLOUT WON\n"));
    /* Play Now, losses, draws and ambiguous controllers cannot earn event medals. */
    for(int side=0;side<=1;side++){
        for(unsigned event=0;event<=10;event++){
            set(manager+0x190,4);ram[manager+0x165]=(uint8_t)event;
            set(0x59dcbc,side==0?0:UINT32_MAX);set(0x59dcc0,side==1?0:UINT32_MAX);
            for(unsigned winner=0;winner<=2;winner++){
                sample_knockdowns(NULL,0);sample_knockdowns(ram+root,1);
                events[0]=0;set(clock+0x24,winner);sample_winner(ram+clock);sample_winner(ram+clock);
                char expected[96];
                snprintf(expected,sizeof expected,"FNR3/1 CALLOUT %s\n",winner==2?"FINAL":winner==(unsigned)side?"WON":"LOST");
                if(winner==(unsigned)side && event>=2 && event<=9)
                    snprintf(expected+strlen(expected),sizeof expected-strlen(expected),"FNR3/1 EVENTWIN %u\n",event);
                assert(!strcmp(expected,events));
            }
        }
    }
    ram[manager+0x165]=3;set(manager+0x190,1);set(clock+0x24,1);
    sample_knockdowns(NULL,0);sample_knockdowns(ram+root,1);events[0]=0;sample_winner(ram+clock);
    assert(!strcmp(events,"FNR3/1 CALLOUT WON\n"));
    set(manager+0x190,4);set(0x59dcbc,0);
    sample_knockdowns(NULL,0);sample_knockdowns(ram+root,1);events[0]=0;sample_winner(ram+clock);
    assert(!strcmp(events,"FNR3/1 CALLOUT FINAL\n"));
    set(0x59dcbc,UINT32_MAX);events[0]=0;
    sample_player_stats(ram+root);sample_player_stats(ram+root);
    assert(strstr(events,"FNR3/1 PLAYERSTATS 1 ")==events && !strstr(events+1,"FNR3/1 PLAYERSTATS"));
    events[0]=0;sample_player_stats(NULL);assert(!strcmp(events,"FNR3/1 PLAYERSTATS -\n"));
    uint32_t judges=0x34000;set(root+0x2c,judges);
    for(unsigned j=0;j<3;j++){
        uint32_t card=0x35000+0x1000*j;set(judges+8+4*j,card);
        set(card+4,10);set(card+4*16,j==2?0:9);
    }
    for(int i=0;i<12;i++)fight_score_sample();
    assert(!fight_round_break());
    set(0x37000+4*16,9);
    for(int i=0;i<12;i++)fight_score_sample();
    assert(fight_round_break());
    set(clock+0x18,2);
    for(int i=0;i<4;i++)fight_score_sample();
    assert(!fight_round_break());
    puts("Native round, scorer, stumble, count, official winner and corner effect gating passed");return 0;
}
