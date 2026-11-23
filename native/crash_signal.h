/* Final fallback for fatal signals, installed beneath the normal MMIO adapters.
 * Record only a build-relative host instruction offset, then let Android collect
 * its usual tombstone. The signal path never allocates or tries to unwind. */
#include <link.h>
#include <signal.h>
#include <stdint.h>
#include <ucontext.h>
#include <unistd.h>

static uintptr_t crash_base,crash_text_start,crash_text_end;
static struct sigaction crash_previous[NSIG];
static int crash_find_text(struct dl_phdr_info *info,size_t size,void *unused){
    (void)size;(void)unused;
    uintptr_t here=(uintptr_t)&crash_find_text;
    for(unsigned i=0;i<info->dlpi_phnum;i++){
        const ElfW(Phdr) *p=info->dlpi_phdr+i;
        uintptr_t start=info->dlpi_addr+p->p_vaddr;
        if(p->p_type==PT_LOAD && (p->p_flags&PF_X) && here>=start && here-start<p->p_memsz){
            crash_base=info->dlpi_addr;crash_text_start=start;crash_text_end=start+p->p_memsz;return 1;
        }
    }
    return 0;
}
static void crash_signal(int sig,siginfo_t *info,void *context){
    uintptr_t pc=0;
#if defined(__aarch64__)
    pc=((ucontext_t*)context)->uc_mcontext.pc;
#elif defined(__x86_64__)
    pc=((ucontext_t*)context)->uc_mcontext.gregs[REG_RIP];
#endif
    if(pc>=crash_text_start && pc<crash_text_end){
        char line[]="FNR3/1 FAULT 00000000\n";
        uint32_t offset=(uint32_t)(pc-crash_base);
        for(int i=7;i>=0;i--){line[13+i]="0123456789abcdef"[offset&15];offset>>=4;}
        (void)write(STDERR_FILENO,line,sizeof(line)-1);
    }
    struct sigaction previous=crash_previous[sig];
    if(previous.sa_handler==SIG_IGN)previous.sa_handler=SIG_DFL;
    sigaction(sig,&previous,NULL);
    /* A synchronous fault retries at the original PC, preserving Android's
       fault address and registers. Repost process-generated signals explicitly. */
    if(!info || info->si_code<=0 || sig==SIGABRT)raise(sig);
}
static void fight_crash_signals(void){
    dl_iterate_phdr(crash_find_text,NULL);
    const int signals[]={SIGSEGV,SIGBUS,SIGILL,SIGFPE,SIGABRT};
    struct sigaction action={0};action.sa_sigaction=crash_signal;action.sa_flags=SA_SIGINFO|SA_ONSTACK;
    sigemptyset(&action.sa_mask);
    for(unsigned i=0;i<sizeof(signals)/sizeof(signals[0]);i++)
        sigaction(signals[i],&action,&crash_previous[signals[i]]);
}
