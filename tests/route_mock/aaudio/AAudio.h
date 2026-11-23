/* Deterministic AAudio service for the Windows audio-route regression. */
#include <stdint.h>
typedef int32_t aaudio_result_t;
typedef int32_t aaudio_data_callback_result_t;
typedef struct { int id; } AAudioStream;
typedef struct { int unused; } AAudioStreamBuilder;
enum { AAUDIO_OK=0, AAUDIO_CALLBACK_RESULT_CONTINUE=0, AAUDIO_DIRECTION_OUTPUT=0,
       AAUDIO_FORMAT_PCM_I16=1, AAUDIO_USAGE_GAME=14, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY=12 };
typedef void (*ErrorCallback)(AAudioStream *,void *,aaudio_result_t);
typedef aaudio_data_callback_result_t (*DataCallback)(AAudioStream *,void *,void *,int32_t);
static ErrorCallback error_callback;
static DataCallback data_callback;
static AAudioStreamBuilder builder_instance;
static AAudioStream streams[16];
static int opens,closes,unavailable;
static aaudio_result_t AAudio_createStreamBuilder(AAudioStreamBuilder **out){*out=&builder_instance;return 0;}
#define ignore_builder_setting(name) static void AAudioStreamBuilder_set##name(AAudioStreamBuilder *b,int value){(void)b;(void)value;}
ignore_builder_setting(Direction)
ignore_builder_setting(Format)
ignore_builder_setting(ChannelCount)
ignore_builder_setting(SampleRate)
ignore_builder_setting(Usage)
ignore_builder_setting(PerformanceMode)
static void AAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder *b,ErrorCallback fn,void *u){error_callback=fn;}
static void AAudioStreamBuilder_setDataCallback(AAudioStreamBuilder *b,DataCallback fn,void *u){data_callback=fn;}
static int AAudioStreamBuilder_openStream(AAudioStreamBuilder *b,AAudioStream **out){
    if(unavailable){*out=0;return -1;}*out=&streams[opens++];return 0;
}
static int AAudioStreamBuilder_delete(AAudioStreamBuilder *b){return 0;}
static int AAudioStream_getFramesPerBurst(AAudioStream *s){return 192;}
static int AAudioStream_getSampleRate(AAudioStream *s){return 48000;}
static int AAudioStream_getChannelCount(AAudioStream *s){return 2;}
static int AAudioStream_getFormat(AAudioStream *s){return AAUDIO_FORMAT_PCM_I16;}
static int AAudioStream_setBufferSizeInFrames(AAudioStream *s,int n){return n;}
static int AAudioStream_requestStart(AAudioStream *s){return 0;}
static int AAudioStream_requestStop(AAudioStream *s){return 0;}
static int AAudioStream_close(AAudioStream *s){closes++;return 0;}
static int AAudioStream_getXRunCount(AAudioStream *s){return 0;}
