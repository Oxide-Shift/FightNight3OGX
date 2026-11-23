/* Keep the XAudio2 queue running at device speed during silent PCM capture. */
#include "audio_startup.h"
#define xa2_init fightnight_device_init
#define xa2_submit_samples fightnight_device_submit
#include "apu/apu_xaudio2.c"
#undef xa2_init
#undef xa2_submit_samples

int xa2_init(void) {
    if (!fightnight_device_init()) return 0;
    if (getenv("FNR3_CAPTURE_ONLY")) {
        HRESULT result = IXAudio2MasteringVoice_SetVolume(g_xa2_master, 0.0f, XAUDIO2_COMMIT_NOW);
        if (FAILED(result)) {
            xa2_shutdown();
            return 0;
        }
    }
    return 1;
}

int xa2_submit_samples(const int16_t *samples, int frames) {
    static AudioStartup startup;
    int16_t buffered[XA2_BUF_SAMPLES * 2];
    if (!samples || frames <= 0 || frames > XA2_BUF_SAMPLES) return 0;
    if (startup.frames < STARTUP_QUIET + STARTUP_FADE) {
        for (int i = 0; i < frames; i++)
            audio_startup_frame(&startup, samples[i*2], samples[i*2+1], &buffered[i*2]);
        samples = buffered;
    }
    wav_tap(samples, frames);
    return fightnight_device_submit(samples, frames);
}
