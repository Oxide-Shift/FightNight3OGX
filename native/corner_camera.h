/* Estimate translation from textured background patches at the sides of a shot.
 * Measurements use a fixed 256 x 144 coordinate system at every render preset. */
#ifndef CORNER_CAMERA_H
#define CORNER_CAMERA_H
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <math.h>
enum { PATCH_W=23,PATCH_H=19,PATCHES=12,RADIUS=4 };
typedef struct {
    uint8_t last[PATCHES][PATCH_H][PATCH_W];
    int sequence,shot;
    float x,y;
} CornerCamera;

static float camera_middle(float *v,int n){
    for(int i=1;i<n;i++){
        float value=v[i];int j=i;
        while(j && value<v[j-1]){v[j]=v[j-1];--j;}
        v[j]=value;
    }
    return (v[n/2]+v[(n-1)/2])*.5f;
}
static float camera_subpixel(int left,int mid,int right){
    int curvature=left+right-2*mid;
    return curvature>0?(left-right)*.5f/curvature:0;
}
static void camera_step(CornerCamera *c,const uint8_t *pixel,int w,int h,int stride,int seq,int shot){
    uint32_t gap=(uint32_t)seq-(uint32_t)c->sequence;
    int first=c->shot!=shot || !c->sequence || gap>4;
    float xs[PATCHES],ys[PATCHES],dx[PATCHES],dy[PATCHES];int count=0;
    static const int xcenter[]={31,76,180,225},ycenter[]={31,81,110};
    for(int p=0;p<PATCHES;p++){
        uint8_t current[PATCH_H][PATCH_W];
        int x0=xcenter[p%4]-PATCH_W/2,y0=ycenter[p/4]-PATCH_H/2;
        for(int y=0;y<PATCH_H;y++){
            const uint8_t *row=pixel+(size_t)((2*(y+y0)+1)*h/288)*w*stride;
            for(int x=0;x<PATCH_W;x++)current[y][x]=row[((2*(x+x0)+1)*w/512)*stride];
        }
        if(!first){
            int cost[9][9],smallest=INT32_MAX,bx=0,by=0,contrast=0;
            for(int y=5;y<PATCH_H-5;y+=2)for(int x=5;x<PATCH_W-5;x+=2)
                contrast+=abs(c->last[p][y][x+1]-c->last[p][y][x])+abs(c->last[p][y+1][x]-c->last[p][y][x]);
            if(contrast>165){
                for(int sy=-RADIUS;sy<=RADIUS;sy++)for(int sx=-RADIUS;sx<=RADIUS;sx++){
                    int sum=0;
                    for(int y=5;y<PATCH_H-5;y+=2)for(int x=5;x<PATCH_W-5;x+=2)
                        sum+=abs(c->last[p][y][x]-current[y+sy][x+sx]);
                    cost[sy+4][sx+4]=sum;
                    if(sum<smallest || (sum==smallest && abs(sx)+abs(sy)<abs(bx)+abs(by))){
                        smallest=sum;bx=sx;by=sy;
                    }
                }
                if(smallest<380 && abs(bx)<RADIUS && abs(by)<RADIUS){
                    int x=bx+4,y=by+4;
                    xs[count]=dx[count]=bx+camera_subpixel(cost[y][x-1],smallest,cost[y][x+1]);
                    ys[count]=dy[count]=by+camera_subpixel(cost[y-1][x],smallest,cost[y+1][x]);
                    count++;
                }
            }
        }
        memcpy(c->last[p],current,sizeof current);
    }
    c->shot=shot;c->sequence=seq;
    if(first || count<3){c->x=c->y=0;return;}
    float mx=camera_middle(xs,count),my=camera_middle(ys,count),sx=0,sy=0;int agree=0;
    for(int i=0;i<count;i++)if(fabsf(dx[i]-mx)<1.1f && fabsf(dy[i]-my)<1.1f){
        sx+=dx[i];sy+=dy[i];agree++;
    }
    if(agree<3 || 2*agree<count){c->x=c->y=0;return;}
    /* Follow sustained movement while taking the short oscillation out of the shot. */
    float follow=1.f/(1.f+.13f*gap);
    c->x=fminf(4.2f,fmaxf(-4.2f,(c->x+sx/agree)*follow));
    c->y=fminf(2.35f,fmaxf(-2.35f,(c->y+sy/agree)*follow));
}
#endif
