/* Xbox rectangular Morton texture layout. Four neighbouring pixels along a row
 * occupy two adjacent pairs, separated by one pair from the next row. */
#if defined(__aarch64__)
#include <arm_neon.h>
#endif
static unsigned fight_spread_bits(unsigned coordinate, unsigned extent, unsigned other) {
    unsigned result=0, out=1;
    for(unsigned bit=1;bit<extent;bit<<=1){
        if(coordinate&bit)result|=out;
        out<<=1;
        if(bit<other)out<<=1;
    }
    return result;
}

static void fight_unpack_argb(const uint32_t *source,uint32_t *dest,unsigned width,unsigned height) {
    unsigned columns[1024];
    for(unsigned x=0;x<width;x+=4)columns[x/4]=fight_spread_bits(x,width,height);
    for(unsigned y=0;y<height;y+=2){
        /* Y uses the odd interleaved bits until the narrower dimension ends. */
        unsigned mask=width-1;
        unsigned low=y&mask;
        unsigned offset=fight_spread_bits(low,height,width)*2;
        if(y>=width)offset+=(y-low)*width;
        for(unsigned x=0;x<width;x+=4){
            const uint32_t *tile=source+(offset|columns[x/4]);
            /* Each 4x2 Morton tile interleaves pairs from two scanlines.
             * Read its 32 bytes together and finish both rows. */
#if defined(__aarch64__)
            uint64x2x2_t rows=vld2q_u64((const uint64_t *)tile);
            vst1q_u32(dest+x,vreinterpretq_u32_u64(rows.val[0]));
            vst1q_u32(dest+width+x,vreinterpretq_u32_u64(rows.val[1]));
#else
            memcpy(dest+x,tile,8);
            memcpy(dest+x+2,tile+4,8);
            memcpy(dest+width+x,tile+2,8);
            memcpy(dest+width+x+2,tile+6,8);
#endif
        }
        dest+=width*2;
    }
}
