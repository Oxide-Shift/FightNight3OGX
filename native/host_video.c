/* Present the completed Xbox swap, paced to the game's display clock. */
#include <stdint.h>
#include <stdio.h>
#include <stdatomic.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <time.h>
#include <errno.h>
#endif
#include "video/video_player.h"
#include "fight_data.h"
#include "flip_pacing.h"

static atomic_uint presentations;
static atomic_uint announced;
void frame_output_present(uint32_t, uint32_t);

/* The GPU executor can now finish early. Present at Xbox NTSC vblank and
   discard missed deadlines so a slow frame never triggers a catch-up burst. */
static void pace_flip(void) {
    static int64_t deadline;
    int64_t now=fight_now_ns();
    if(deadline>now){
#ifdef _WIN32
        Sleep((DWORD)((deadline-now+500000)/1000000));
#else
        struct timespec until={deadline/1000000000LL,deadline%1000000000LL};
        while(clock_nanosleep(CLOCK_MONOTONIC,TIMER_ABSTIME,&until,NULL)==EINTR){}
#endif
    }
    unsigned interval=fight_present_interval();
    static unsigned previous=1;
    if(interval!=previous){
        fprintf(stderr,"[Fight Night] presentation interval %u vblank(s)\n",interval);
        previous=interval;
    }
    deadline=fight_next_flip(deadline,fight_now_ns(),interval);
}

void xbox_FramebufferWindowStart(void) {
    if (!atomic_exchange(&announced, 1))
        fprintf(stderr, "[Fight Night] framebuffer requested\n");
}

void xbox_FramebufferWindowSet(uint32_t address, uint32_t pitch) {
    if (address && pitch && !atomic_load(&presentations))
        fprintf(stderr, "[Fight Night] framebuffer configured 0x%08X pitch %u\n", address, pitch);
}

void xbox_FramebufferWindowPresent(uint32_t address, uint32_t pitch) {
    if (!address || !pitch) return;
    int64_t before=fight_now_ns();
    pace_flip();
    int64_t after=fight_now_ns();
    frame_output_present(address,pitch);
    fight_score_sample();
    fight_timing_flip(before,after,fight_now_ns());
    unsigned count = atomic_fetch_add(&presentations, 1) + 1;
    extern void fight_worker_watch(uint32_t frame);
    fight_worker_watch(count);
    if (count <= 2 || count % 60 == 0)
        fprintf(stderr, "[Fight Night] framebuffer submission %u\n", count);
#ifdef __ANDROID__
    if (count % 180 == 0) {
        extern void fight_audio_report(void);
        extern void fight_pad_report(void);
        fight_audio_report();
        fight_pad_report();
    }
#endif
}

void xbox_FramebufferWindowFrameStats(uint32_t draws) { (void)draws; }
void xbox_FramebufferWindowSetTitle(const uint16_t *title, int length) {
    (void)title; (void)length;
}
int xbox_VideoIsPlaying(void) { return 0; }
int xbox_VideoPlayFile(const char *path) {
    fprintf(stderr, "[Fight Night] movie playback unavailable in headless host: %s\n", path);
    return 0;
}
