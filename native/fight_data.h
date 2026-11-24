#ifndef FIGHT_DATA_H
#define FIGHT_DATA_H
#include <stddef.h>
#include <stdint.h>
void fight_trax_route(int on);
void fight_trax_init(const char *root);
int fight_trax_present(uint32_t artist,uint32_t title);
void fight_data_init(const char *root);
void fight_score_sample(void);
int fight_round_break(void);
int fight_voice_stopped(uint32_t address);
unsigned fight_present_interval(void);
int fight_sample_counters(uint32_t *identity,uint32_t *tick,uint32_t *thrown,uint32_t *landed);
int64_t fight_now_ns(void);
void fight_note_scanout(int64_t submit,int64_t copy);
void fight_note_gpu_wait(int64_t duration);
void fight_timing_flip(int64_t before,int64_t after,int64_t end);
void fight_music_open(uint32_t token,const char *name,size_t length);
void fight_music_close(uint32_t token);
void fight_music_seek(uint32_t token,uint64_t offset);
void fight_music_voice(int enabled);
void fight_music_read(uint32_t token,uint64_t offset,uint32_t size,void *buffer);
#endif
