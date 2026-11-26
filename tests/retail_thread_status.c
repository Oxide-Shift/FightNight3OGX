/* Include the built kernel bridge in this private integration test so the
 * tokens, thread, dispatcher and translated retail SDK use the real ABI. */
#include "kernel_bridge.c"
#include <assert.h>
extern void sub_002DA2AD(void);

struct test_worker { HANDLE release; DWORD status; };
static DWORD WINAPI finish_on_signal(LPVOID arg)
{
    struct test_worker *worker = arg;
    WaitForSingleObject(worker->release, INFINITE);
    return worker->status;
}

static uint32_t retail_status(uint32_t token, uint32_t out)
{
    uint32_t sp = g_esp;
    g_esp -= 12;
    BRIDGE_MEM32(g_esp) = 0;
    BRIDGE_MEM32(g_esp + 4) = token;
    BRIDGE_MEM32(g_esp + 8) = out;
    sub_002DA2AD();
    assert(g_esp == sp && !s_exit_snapshot);
    return g_eax;
}

int retail_thread_status_test(void)
{
    uint32_t tmp = xbox_HeapAlloc(16, 16);
    assert(tmp);
    for (int i = 0; i < 2; ++i) {
        struct test_worker worker = {CreateEvent(NULL, TRUE, FALSE, NULL), i ? 37 : 0};
        HANDLE th = CreateThread(NULL, 0, finish_on_signal, &worker, 0, NULL);
        assert(th && worker.release);
        bridge_write_handle(tmp, th);
        uint32_t token = BRIDGE_MEM32(tmp);
        for (int query = 0; query < 64; ++query) {
            assert(retail_status(token, tmp + 4) && BRIDGE_MEM32(tmp + 4) == 259);
        }
        /* Duplicate using the Xbox bridge so source-close and slot ownership
         * are exercised just as they are in the title. */
        g_esp -= 12;
        BRIDGE_MEM32(g_esp) = token;
        BRIDGE_MEM32(g_esp + 4) = tmp + 8;
        BRIDGE_MEM32(g_esp + 8) = 3;
        bridge_NtDuplicateObject();
        g_esp += 12;
        uint32_t duplicate = BRIDGE_MEM32(tmp + 8);
        assert(!g_eax && duplicate && duplicate != token);
        assert(!retail_status(token, tmp + 4));
        assert(retail_status(duplicate, tmp + 4) && BRIDGE_MEM32(tmp + 4) == 259);
        SetEvent(worker.release);
        HANDLE active = bridge_resolve_handle(duplicate);
        assert(WaitForSingleObject(active, 5000) == WAIT_OBJECT_0);
        assert(retail_status(duplicate, tmp + 4) && BRIDGE_MEM32(tmp + 4) == worker.status);
        assert(CloseHandle(bridge_take_handle(duplicate)));
        CloseHandle(worker.release);
    }
    assert(retail_status(0xfffffffeu, tmp + 4) && BRIDGE_MEM32(tmp + 4) == 259);
    assert(!retail_status(0xBEEF0001u, tmp + 4));
    HANDLE event = CreateEvent(NULL, TRUE, FALSE, NULL);
    bridge_write_handle(tmp, event);
    assert(!retail_status(BRIDGE_MEM32(tmp), tmp + 4));
    CloseHandle(bridge_take_handle(BRIDGE_MEM32(tmp)));
    xbox_HeapFree(tmp);
    puts("Retail thread status passed: running, exit 0 and 37, duplicate/close, stale, current and wrong-type handles.");
    return 0;
}
