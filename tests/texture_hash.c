#include "../native/texture_hash.h"
#include <assert.h>
#include <stdio.h>
int main(void){
    unsigned char data[1026]={0};
    for(unsigned n=1;n<=1025;n++){
        uint64_t h=fight_checked_texture(data+1,n,27);
        for(unsigned i=1;i<=n;i++){
            data[i]=1;assert(fight_checked_texture(data+1,n,27)!=h);data[i]=0;
        }
        assert(fight_checked_texture(data+1,n,28)!=h);
    }
    puts("All texture bytes affect the cache key, including tiny and unaligned levels");
}
