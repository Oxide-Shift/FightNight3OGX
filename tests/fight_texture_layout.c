#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "../native/fight_texture_layout.h"

static unsigned morton(unsigned x,unsigned y,unsigned width,unsigned height){
    unsigned offset=0,shift=0;
    for(unsigned bit=1;bit<width || bit<height;bit<<=1){
        if(bit<width){offset|=((x&bit)!=0)<<shift;shift++;}
        if(bit<height){offset|=((y&bit)!=0)<<shift;shift++;}
    }
    return offset;
}

int main(void){
    for(unsigned w=4;w<=512;w*=2)for(unsigned h=2;h<=1024;h*=2){
        size_t bytes=(size_t)w*h*sizeof(uint32_t);
        uint32_t *src=malloc(bytes),*out=malloc(bytes);
        assert(src && out);
        for(unsigned y=0;y<h;y++)for(unsigned x=0;x<w;x++)
            src[morton(x,y,w,h)]=y*w+x;
        fight_unpack_argb(src,out,w,h);
        for(unsigned i=0;i<w*h;i++)assert(out[i]==i);
        free(src);free(out);
    }
}
