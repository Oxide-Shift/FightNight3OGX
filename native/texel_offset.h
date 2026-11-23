/* Common path of retail 0x000FD000, called per texel by the two texture
 * blitters. The guest owns and initializes both lookup tables. */
static inline void fight_texel_offset(void) {
    const uint32_t sp = g_esp;
    const uint32_t x = MEM32(sp + 4), y = MEM32(sp + 8);
    if (x > 512 || y > 512 || !MEM32(0x598E7Cu) || g_loop_budget <= 0) {
        sub_000FD000();
        return;
    }

    /* Preserve stack scratch, volatile EDX, and the original scheduler poll. */
    ST32(sp - 4, g_esi);
    ST32(sp - 8, g_edi);
    g_edx = x;
    g_eax = MEM32(0x598678u + x * 4) + MEM32(0x597E70u + y * 4);
    --g_loop_budget;
    g_esp = sp + 4;
}
