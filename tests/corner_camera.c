#include <assert.h>
#include <stdio.h>
#include "../native/corner_camera.h"

static uint8_t source[144][256], frame[144][256];
static CornerCamera camera;
static void publish(int seq,int shot,int dx,int dy){
    for(int y=0;y<144;y++)for(int x=0;x<256;x++)
        frame[y][x]=source[(144+y-dy)%144][(256+x-dx)%256];
    camera_step(&camera,&frame[0][0],256,144,1,seq,shot);
}

int main(void){
    uint32_t noise=1;
    for(int y=0;y<144;y++)for(int x=0;x<256;x++){
        noise=noise*1664525+1013904223;source[y][x]=noise>>24;
    }
    publish(1,1,0,0);assert(camera.x==0 && camera.y==0);
    float before=0,after=0,last_x=0;
    int last=0;
    for(int n=2;n<180;n++){
        int x=(n/3)%2?1:-1;
        publish(n,1,x,-x);
        before+=abs(x-last);after+=fabsf((x-last)-(camera.x-last_x));
        assert(fabsf(camera.x)<=4.2f && fabsf(camera.y)<=2.35f);
        last=x;last_x=camera.x;
    }
    assert(after<before*.4f);
    publish(180,2,0,0);assert(camera.x==0 && camera.y==0);
    publish(200,2,2,1);assert(camera.x==0 && camera.y==0);
    memset(source,82,sizeof source);
    publish(201,2,0,0);publish(202,2,0,0);assert(camera.x==0 && camera.y==0);
    puts("Camera movement, cuts, flat frames and skipped presentations passed");
}
