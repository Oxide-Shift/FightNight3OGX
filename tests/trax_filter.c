/* Exercise the same read hook as NtReadFile, including unaligned reads. */
#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#include "../native/fight_data.c"
void fight_trax_init(const char *root){(void)root;}
size_t g_xbox_total_ram,g_xbox_map_size;
ptrdiff_t xbox_GetMemoryOffset(void){return 0;}
static void partial_reads(void){
    struct SampleSpan spans[]={{11,21},{33,39},{47,51}};
    samples=spans;sample_count=3;track_count=1;
    tracks[0].begin=0;tracks[0].end=64;
    atomic_store(&index_state,2);atomic_store(&voice_requested,1);
    fight_music_open(23,"audio/music/music.big",strlen("audio/music/music.big"));
    for(unsigned offset=0;offset<=54;offset++)for(unsigned length=1;length<=64-offset;length++){
        unsigned char bytes[80];memset(bytes,0x77,sizeof bytes);
        fight_music_read(23,offset,length,bytes+2);
        for(unsigned n=0;n<sizeof bytes;n++){
            unsigned pos=offset+n-2;
            int muted=n>=2 && n<2+length && ((pos>=11 && pos<21)||(pos>=33 && pos<39)||(pos>=47 && pos<51));
            assert(bytes[n]==(muted?0:0x77));
        }
    }
    unsigned char bytes[64];memset(bytes,1,sizeof bytes);
    fight_music_seek(23,33);fight_music_read(23,UINT64_MAX,5,bytes);
    assert(!bytes[0] && !bytes[4] && bytes[5]);
    memset(bytes,1,sizeof bytes);fight_music_read(23,UINT64_MAX,5,bytes);
    assert(!bytes[0] && bytes[1] && bytes[4]);
    fight_music_open(27,"music.big",9);memset(bytes,1,sizeof bytes);
    fight_music_read(27,33,5,bytes);assert(!bytes[0] && !bytes[4]);fight_music_close(27);
    memset(bytes,1,sizeof bytes);fight_music_voice(0);fight_music_read(23,0,64,bytes);
    fight_music_voice(1);fight_music_read(42,0,64,bytes);
    fight_music_close(23);fight_music_read(23,0,64,bytes);
    for(unsigned i=0;i<64;i++)assert(bytes[i]==1);
    puts("EA Trax byte ranges, cursor reads, opt-out, other files and closed handles passed.");
}
static void scene_cues(void){
    struct SampleSpan span={10,16};
    unsigned char buffer[128];
    cue_spans[0]=cue_spans[1]=&span;cue_counts[0]=cue_counts[1]=1;
    const char *names[]={"audio/Music/SeaB_LP1.bnk","AUDIO/MUSIC/INTRO.BIG","audio/music/rgirl.big","audio/speech/intro.big","audio/aems/Bell.abk"};
    for(unsigned i=0;i<5;i++){
        for(int voice=0;voice<2;voice++)for(int fight=0;fight<2;fight++){
            fight_music_voice(voice);fight_music_cues(fight);memset(buffer,0x67,sizeof buffer);
            fight_music_open(40,names[i],strlen(names[i]));
            fight_music_read(40,0,sizeof buffer,buffer);fight_music_close(40);
            for(unsigned j=0;j<sizeof buffer;j++){
                int silent=voice && fight && (i==0?j>=100:(i==1 || i==2) && j>=10 && j<16);
                assert(buffer[j]==(silent?0:0x67));
            }
        }
    }
}
int main(void){partial_reads();scene_cues();return 0;}
