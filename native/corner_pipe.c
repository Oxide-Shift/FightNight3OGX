/* The Android activity owns both ends of this child's standard streams. */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#ifdef __ANDROID__
#include <sys/socket.h>
#include <unistd.h>
extern void fight_sleep_supervisor(void);
extern void fight_audio_sleep(int sleeping);
extern void fight_audio_check_route(void);
#endif
#endif

#ifndef CORNER_LINK_ONLY
extern int pad_message(const char *);
#endif

static int answer_ping(const char *line) {
#ifdef __ANDROID__
    if (!strncmp(line,"SLEEP ",6) && line[6] && line[7]=='\n') {
        fight_audio_sleep(1);
        send(STDIN_FILENO,line+6,1,MSG_NOSIGNAL);
        return 1;
    }
    if (!strcmp(line,"WAKE\n")) { fight_audio_sleep(0); puts("FNR3/1 AWAKE"); fflush(stdout); return 1; }
#endif
#ifndef CORNER_LINK_ONLY
    extern void fight_trax_route(int);
    extern void fight_music_voice(int),fight_music_cues(int);
    if (!strcmp(line,"TRAXCUES 1\n") || !strcmp(line,"TRAXCUES 1\r\n")) { fight_music_cues(1); return 1; }
    if (!strcmp(line,"TRAXCUES 0\n") || !strcmp(line,"TRAXCUES 0\r\n")) { fight_music_cues(0); return 1; }
    if (!strcmp(line,"TRAXVOICE 1\n") || !strcmp(line,"TRAXVOICE 1\r\n")) { fight_music_voice(1); return 1; }
    if (!strcmp(line,"TRAXVOICE 0\n") || !strcmp(line,"TRAXVOICE 0\r\n")) { fight_music_voice(0); return 1; }
    if (!strcmp(line,"TRAX 2\n") || !strcmp(line,"TRAX 2\r\n")) { fight_trax_route(2); return 1; }
    if (!strcmp(line,"TRAX 1\n") || !strcmp(line,"TRAX 1\r\n")) { fight_trax_route(1); return 1; }
    if (!strcmp(line,"TRAX 0\n") || !strcmp(line,"TRAX 0\r\n")) { fight_trax_route(0); return 1; }
    if (!strncmp(line,"PAD ",4)) return pad_message(line);
#endif
    if (strncmp(line, "PING ", 5)) return 0;
    const char *number = line + 5;
    size_t digits = strspn(number, "0123456789");
    if (digits == 0 || digits > 9 || strspn(number + digits, "\r\n") != strlen(number + digits))
        return 0;
#ifdef __ANDROID__
    fight_audio_check_route();
#endif
    printf("FNR3/1 PONG %.*s\n", (int)digits, number);
    fflush(stdout);
    return 1;
}

#ifdef _WIN32
static DWORD WINAPI listen_for_pings(void *arg)
#else
static void *listen_for_pings(void *arg)
#endif
{
    (void)arg;
    char line[80];
    while (fgets(line, sizeof(line), stdin)) answer_ping(line);
#ifndef CORNER_LINK_ONLY
    pad_message("PAD 000000000000000000000000000000000000");
#endif
    return 0;
}

int corner_connect(void) {
#ifdef __ANDROID__
    fight_sleep_supervisor();
#endif
    char line[80];
    puts("FNR3/1 HELLO");
    while (fgets(line, sizeof(line), stdin)) {
        if (strcspn(line, "\r\n") == 3 && !strncmp(line, "RUN", 3)) {
#ifdef _WIN32
            HANDLE reader = CreateThread(NULL, 0, listen_for_pings, NULL, 0, NULL);
            if (!reader) return 0;
            CloseHandle(reader);
#else
            pthread_t reader;
            if (pthread_create(&reader, NULL, listen_for_pings, NULL)) return 0;
            pthread_detach(reader);
#endif
            return 1;
        }
        if (!answer_ping(line)) return 0;
    }
    return 0;
}

/* Native link check used by the dashboard when no game host is packaged. */
#ifdef CORNER_LINK_ONLY
int main(void) {
    char line[80];
    setvbuf(stdout, NULL, _IONBF, 0);
    puts("FNR3/1 HELLO");
    while (fgets(line, sizeof(line), stdin)) {
        if (strcspn(line, "\r\n") == 3 && !strncmp(line, "RUN", 3)) {
            puts("FNR3/1 LINK-CHECK");
        } else if (!answer_ping(line)) return 4;
    }
    return 0;
}
#endif
