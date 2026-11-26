/* Fight Night's main swap-chain surfaces are pitched, 32-bit and 640 x 480.
 * Other targets stay in guest RAM and can be sampled by either renderer. */
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "nv2a_backend.h"
#include "xbox_memory_layout.h"
#include "vkr.h"
#include "fight_data.h"
#include "front_surfaces.h"

static VkrContext *gpu;
static FightFrontSurfaces fronts;
static uint32_t active_address, active_depth;
static unsigned unknown_surface_calls, conflicting_surface_calls, front_replacements;
static uint32_t missed_address, presented_address;

void fight_render_hitch_stats(char *out, size_t size) {
    if (out) snprintf(out,size,"front_misses=%u target_conflicts=%u front_replacements=%u last_miss=%08x flip=%08x",
                      unknown_surface_calls,conflicting_surface_calls,front_replacements,missed_address,presented_address);
    unknown_surface_calls=conflicting_surface_calls=front_replacements=0;
    missed_address=0;
}
extern const uint32_t *fight_color_pixels(int stage);
extern VkrTex *fight_upload_color(VkrContext *,uint64_t,uint32_t,uint32_t,const uint32_t *);
extern VkrTex *fight_feedback_texture(VkrContext *);
static unsigned draws;
static int dirty;
extern unsigned fight_texture_levels(int);
extern size_t fight_texture_bytes(int);
extern VkrTex *fight_tex_bc(VkrContext *,uint64_t,uint32_t,uint32_t,uint32_t,const void *,size_t,uint32_t);
static uint64_t refs,verts,bc_bytes,rgba_bytes;
static FILE *profile;
static struct { uint32_t hash,count; } profiled[256];
static unsigned nprofile;
static void profile_program(const Nv2aVshProgram *p,uint32_t vertices){
    unsigned i=0;
    while(i<nprofile && profiled[i].hash!=p->hash)i++;
    if(i==nprofile){
        if(i==256 || !p->nins || p->nins>136)return;
        uint32_t record[2]={p->hash,p->nins};
        fwrite(record,sizeof(record),1,profile);fwrite(p->ins,32,p->nins,profile);fflush(profile);
        profiled[nprofile++].hash=p->hash;
    }
    profiled[i].count+=vertices;
}
int frame_output_high_token(const uint8_t *bgra,unsigned w,unsigned h,uint32_t address,const uint8_t *native_pixels,unsigned token);
void frame_output_forget_native(void);
static int retained_slot=-1;
static void finish_pending(void);
static void release_readback(void) {
    if(retained_slot<0)return;
    frame_output_forget_native();
    vkr_frame_release(gpu,retained_slot);
    retained_slot=-1;
}
_Static_assert(sizeof(VkrVertex) == sizeof(Nv2aVshOutput), "vertex ABI");
_Static_assert(sizeof(Nv2aCombiner) == offsetof(VkrUniforms, rc_seen), "combiner ABI");

/* Draws whose target the Vulkan path refuses are rasterized on the CPU (xf_rows/put_pixel in profiles).
 * Count them by reason and remember the most frequent refused targets; reported every 300 flips. */
enum {SOFT_FORMAT,SOFT_FRONT,SOFT_CONFLICT,SOFT_DEPTH,SOFT_REASONS};
static unsigned soft_reason[SOFT_REASONS];
static struct {uint32_t address,pitch,size;uint8_t bpp,swizzled,reason;unsigned count;} soft_targets[8];
static void note_soft(const Nv2aTarget *t,int reason) {
    soft_reason[reason]++;
    uint32_t size=(uint32_t)t->width<<16|t->height;
    unsigned i,low=0;
    for(i=0;i<8;i++){
        if(soft_targets[i].count && soft_targets[i].address==t->color_offset && soft_targets[i].pitch==t->pitch &&
           soft_targets[i].size==size && soft_targets[i].reason==reason){soft_targets[i].count++;return;}
        if(soft_targets[i].count<soft_targets[low].count)low=i;
    }
    soft_targets[low].address=t->color_offset;soft_targets[low].pitch=t->pitch;soft_targets[low].size=size;
    soft_targets[low].bpp=(uint8_t)t->bpp;soft_targets[low].swizzled=(uint8_t)!!t->swizzled;
    soft_targets[low].reason=(uint8_t)reason;soft_targets[low].count=1;
}
static void report_soft(void) {
    unsigned total=soft_reason[0]+soft_reason[1]+soft_reason[2]+soft_reason[3];
    if(!total)return;
    char line[640];int n=snprintf(line,sizeof line,"[Fight Night] soft targets format=%u front=%u conflict=%u depth=%u:",
                                  soft_reason[0],soft_reason[1],soft_reason[2],soft_reason[3]);
    for(unsigned i=0;i<8 && n<(int)sizeof line-64;i++)if(soft_targets[i].count)
        n+=snprintf(line+n,sizeof line-n," r%u@%08x/p%u/b%u%s/%ux%u=%u",soft_targets[i].reason,soft_targets[i].address,
                    soft_targets[i].pitch,soft_targets[i].bpp,soft_targets[i].swizzled?"s":"",
                    soft_targets[i].size>>16,soft_targets[i].size&0xffff,soft_targets[i].count);
    fprintf(stderr,"%s\n",line);
    memset(soft_reason,0,sizeof soft_reason);memset(soft_targets,0,sizeof soft_targets);
}
static int select_surface(const Nv2aTarget *t) {
    if (t->swizzled || t->bpp != 4 || t->pitch != 2560 || !t->width || !t->height ||
        t->x >= 640 || t->width > 640-t->x || t->y >= 480 || t->height > 480-t->y){
        note_soft(t,SOFT_FORMAT);
        return 0;
    }
    if (fight_front_index(&fronts,t->color_offset) == fronts.count) {
        ++unknown_surface_calls;
        missed_address=t->color_offset;
        note_soft(t,SOFT_FRONT);
        return 0;
    }
    if (dirty && active_address != t->color_offset) {
        ++conflicting_surface_calls;note_soft(t,SOFT_CONFLICT); return 0;
    }
    if(retained_slot>=0)release_readback();
    if (active_depth != t->zmax) {
        finish_pending();              /* vkr_set_target waits for every slot to be released */
        release_readback();
        if (!t->zmax || !vkr_set_target(gpu,640,480,(float)t->zmax)) {note_soft(t,SOFT_DEPTH);return 0;}
        active_depth=t->zmax;
    }
    active_address=t->color_offset;
    vkr_set_scissor(gpu,t->x,t->y,t->width,t->height);
    return 1;
}

static void unpack(uint32_t argb, float rgba[4]) {
    rgba[0]=((argb>>16)&255)/255.f; rgba[1]=((argb>>8)&255)/255.f;
    rgba[2]=(argb&255)/255.f; rgba[3]=(argb>>24)/255.f;
}

/* The loading animation restores its background with a 64x64 RGB565 copy.
 * The copy can have a one-texel blue top row and left column. Extend the
 * nearest background pixels through just those edges before filtering. */
static void repair_loading_backdrop(uint32_t *pixels) {
    const uint32_t border = 0xff0004e6u;
    if (pixels[0] != border || pixels[65] == border) return;
    for (unsigned i = 1; i < 64; ++i)
        if (pixels[i] != border || pixels[i*64] != border) return;
    for (unsigned y = 1; y < 64; ++y) pixels[y*64] = pixels[y*64+1];
    memcpy(pixels, pixels+64, 64*sizeof(*pixels));
}

static void state(const Nv2aTarget *t,VkrDrawState *s, int loading_quad) {
    s->depth_test=t->depth_test; s->depth_write=t->depth_write; s->depth_func=t->depth_func;
    s->w_buffer=t->w_buffer;
    s->stencil_test=t->stencil_test; s->stencil_func=t->stencil_func; s->stencil_ref=t->stencil_ref;
    s->stencil_read_mask=t->stencil_read_mask; s->stencil_write_mask=t->stencil_write_mask;
    s->stencil_fail=t->stencil_fail; s->stencil_zfail=t->stencil_zfail; s->stencil_zpass=t->stencil_zpass;
    s->point_params=t->point_params; s->point_size=t->point_size; s->point_sprite=t->point_sprite;
    s->blend_enable=t->blend_enable; s->blend_src=t->blend_sfactor; s->blend_dst=t->blend_dfactor;
    s->blend_eq=t->blend_equation; unpack(t->blend_color,s->blend_color);
    unsigned mask=t->color_mask;
    s->write_mask=((mask&0xff0000)?1:0)|((mask&0xff00)?2:0)|((mask&255)?4:0)|((mask>>24)?8:0);
    s->alpha_func=t->alpha_test?t->alpha_func:0; s->alpha_ref=t->alpha_ref&255;
    memcpy(&s->u,&t->rc,sizeof(t->rc)); s->u.rc_seen=t->rc_seen;
    s->u.fog_enable=t->fog_enable; unpack(t->fog_color,s->u.fog_color);
    float r=s->u.fog_color[2]; s->u.fog_color[2]=s->u.fog_color[0]; s->u.fog_color[0]=r;
    s->u.fog_param[0]=t->fog_param[0]; s->u.fog_param[1]=t->fog_param[1]; s->u.fog_param[2]=(float)t->fog_mode;
    for(int k=0;k<4;++k) {
        Nv2aTexInfo ti;
        s->u.tex_scale[k][0]=s->u.tex_scale[k][1]=1.f;
        s->addr_u[k]=s->addr_v[k]=3;
        if(!((s->u.stage_program>>(k*5))&31))continue;
        if (!nv2a_exec_tex_info(k,&ti) || !ti.width || !ti.height) continue;
        /* The CPU mirror is refreshed at flip. A draw reading the current
         * color target before flip must see the draws already submitted. */
        VkrTex *tex=NULL;
        if(dirty && ti.offset==t->color_offset && ti.format==0x12 && !ti.cube &&
           ti.width==640 && ti.height==480)
            tex=fight_feedback_texture(gpu);
        if(!tex)tex=vkr_tex_find(gpu,ti.hash);
        unsigned levels=fight_texture_levels(k);
        if(!tex && !ti.cube && (ti.format==0x0c || ti.format==0x0e || ti.format==0x0f)) {
            size_t size=fight_texture_bytes(k);
            tex=fight_tex_bc(gpu,ti.hash,ti.width,ti.height,levels,XBOX_HOST(ti.offset),size,ti.format);
            if(tex){bc_bytes+=size;rgba_bytes+=(uint64_t)ti.width*ti.height*4;}
        }
        if(!tex && !ti.cube && (ti.format==0x06 || ti.format==0x12)){
            const uint32_t *pixels=fight_color_pixels(k);
            if(pixels)tex=levels==1 ? fight_upload_color(gpu,ti.hash,ti.width,ti.height,pixels)
                                    : vkr_tex_create_ex(gpu,ti.hash,ti.width,ti.height,pixels,1);
        }
        if (!tex) {
            unsigned rows=ti.height*(ti.cube?6:1);
            if ((uint64_t)ti.width*rows > 16777216) continue;
            uint32_t *pixels=malloc((size_t)ti.width*rows*4);
            if(pixels && nv2a_exec_tex_decode(k,pixels)) {
                if(loading_quad && k==0 && ti.format==5 && !ti.cube && ti.width==64 && rows==64)
                    repair_loading_backdrop(pixels);
                tex=vkr_tex_create_ex(gpu,ti.hash,ti.width,rows,pixels,levels>1 && !ti.cube);
            }
            free(pixels);
        }
        s->tex[k]=tex; s->addr_u[k]=ti.cube?3:ti.addr_u; s->addr_v[k]=ti.cube?3:ti.addr_v;
        s->linear[k]=ti.linear_filter;
        s->u.tex_scale[k][0]=ti.normalised?1.f:1.f/ti.width;
        s->u.tex_scale[k][1]=ti.normalised?1.f:1.f/ti.height;
        s->u.tex_scale[k][2]=ti.cube?1.f:0.f; s->u.tex_scale[k][3]=(float)ti.height;
    }
}

static uint16_t *indices(uint32_t prim,uint32_t n,uint32_t *count,uint32_t *topology) {
    static uint16_t *idx;
    static uint32_t capacity;
    if(!n || n>65536 || prim<1 || prim>10) return NULL;
    if(capacity<n*6) {
        uint16_t *more=realloc(idx,n*6*sizeof(*idx)); if(!more)return NULL;
        idx=more; capacity=n*6;
    }
    unsigned k=0;
    *topology=prim==1?VKR_TOPO_POINTS:prim<=4?VKR_TOPO_LINES:VKR_TOPO_TRIANGLES;
    if(prim<=4) {
        for(unsigned i=1;i<n;i++){idx[k++]=i-1;idx[k++]=i;}
        if(prim==3 && n>2){idx[k++]=n-1;idx[k++]=0;}
    } else if(prim==7 || prim==10) {
        for(unsigned i=2;i<n;i++){idx[k++]=0;idx[k++]=i-1;idx[k++]=i;}
    } else {
        for(unsigned i=0;i+3<n;i+=prim==8?4:2){
            unsigned c=prim==8?i+2:i+3,d=prim==8?i+3:i+2;
            idx[k++]=i;idx[k++]=i+1;idx[k++]=c;idx[k++]=i;idx[k++]=c;idx[k++]=d;
        }
    }
    *count=k;return idx;
}

static int submit(const Nv2aTarget *t,uint32_t prim,const void *vertices,uint32_t n,const Nv2aVshProgram *p,
                  const uint16_t *order,uint32_t references,uint64_t mesh_token) {
    if(!select_surface(t))return 0;
    VkrDrawState s={0}; uint32_t count;
    const uint16_t *idx;
    if(prim==1 || prim==2 || prim==5 || prim==6){
        /* These Vulkan topologies consume Xbox indices in their original order. */
        unsigned stride=prim==2?2:prim==5?3:1;
        s.topology=prim==1?VKR_TOPO_POINTS:prim==2?VKR_TOPO_LINES:
                   prim==5?VKR_TOPO_TRIANGLES:VKR_TOPO_TRIANGLE_STRIP;
        if((prim!=6 || !order) && (!references || references>65536))return 0;
        count=references-references%stride;
        idx=order;
        if(!idx){
            static uint16_t identity[65536];
            static unsigned initialized;
            for(;initialized<count;initialized++)identity[initialized]=(uint16_t)initialized;
            idx=identity;
        }
    } else {
        uint16_t *expanded=indices(prim,references,&count,&s.topology);
        if(!expanded)return 0;
        if(order)for(unsigned i=0;i<count;i++)expanded[i]=order[expanded[i]];
        idx=expanded;
    }
    if(!count)return 1;
    if(profile && p)profile_program(p,n);
    state(t,&s,p && p->hash==0x74dadf38u && prim==6 && n==4 && t->blend_enable);
    int ok=p?vkr_draw_mesh(gpu,vertices,n,p->nin,&p->ins[0][0],p->nins,p->hash,p->consts,idx,count,&s,mesh_token)
            :vkr_draw(gpu,vertices,n,idx,count,&s);
    if(ok){dirty=1;draws++;if(p){refs+=references;verts+=n;}}
    return ok;
}
static int draw(const Nv2aTarget *t,uint32_t prim,const Nv2aVshOutput *v,uint32_t n) {
    return submit(t,prim,v,n,NULL,NULL,n,0);
}
static int draw_program(const Nv2aTarget *t,uint32_t prim,const float *v,uint32_t n,const Nv2aVshProgram *p) {
    return submit(t,prim,v,n,p,NULL,n,0);
}
int fight_indexed_vsh(const Nv2aTarget *t,uint32_t prim,const float *vertices,uint32_t n,
                      const Nv2aVshProgram *program,const uint16_t *order,uint32_t references) {
    return submit(t,prim,vertices,n,program,order,references,0);
}
int fight_cached_vsh(const Nv2aTarget *t,uint32_t prim,const float *vertices,uint32_t n,
                     const Nv2aVshProgram *p,const uint16_t *order,uint32_t count,uint64_t token) {
    return submit(t,prim,vertices,n,p,order,count,token);
}
extern int fight_clear_load(VkrContext *,const float *,int,float,int,uint32_t);
static int clear(const Nv2aTarget *t,uint32_t flags,uint32_t argb,float depth) {
    if(!select_surface(t))return 0;
    float rgba[4];unpack(argb,rgba);
    int z=!!(flags&NV2A_CLEAR_Z),stencil=!!(flags&NV2A_CLEAR_STENCIL);
    if(!fight_clear_load(gpu,(flags&NV2A_CLEAR_COLOR)?rgba:NULL,z,depth,stencil,t->stencil_clear)){
        if(flags&NV2A_CLEAR_COLOR)vkr_clear(gpu,rgba,0,0);
        vkr_clear_ds(gpu,z,depth,stencil,t->stencil_clear);
    }
    dirty=1;return 1;
}
#ifdef __ANDROID__
extern unsigned fight_shared_take(void);
extern void fight_shared_finish(unsigned,uint32_t);
#endif
/* Wait for one submitted frame, copy it to guest memory and publish it. */
static void finish_frame(int slot,uint32_t address,int match,unsigned token,int64_t submitted,int64_t returned) {
    int64_t before=fight_now_ns();
    const uint8_t *image=vkr_frame_pixels(gpu,slot);
    int64_t ready=fight_now_ns();
    fight_note_gpu_wait(ready-before);
    uint32_t phys=address & 0x0fffffff;
    if(image && (uint64_t)phys+640*480*4<=g_xbox_total_ram) {
        memcpy(XBOX_HOST(address),image,640*480*4);
        uint32_t w=0,h=0;
        const uint8_t *full;
        if(token){full=image;w=640;h=480;}
        else full=vkr_frame_pixels_full(gpu,slot,&w,&h);
        if(full && match && frame_output_high_token(full,w,h,address,image,token))
            retained_slot=slot;
    }
#ifdef __ANDROID__
    if(token && retained_slot!=slot)fight_shared_finish(token,0);
#endif
    if(retained_slot!=slot)vkr_frame_release(gpu,slot);
    fight_note_scanout(returned-submitted,fight_now_ns()-ready);
}
/* Alpha 80: the whole frame is submitted at the guest flip. Waiting for it right away kept the GPU idle
 * while the game built a frame and the game idle while the GPU rendered (~5 ms p95 on the Thor).
 * Frame N is now finished at flip N+1, while the GPU renders N+1. VKR_FRAMES=2 slots already exist for this.
 * Default is synchronous; FNR3_SYNC_READBACK=0 opts in, and the recovery mode (FNR3_LEGACY_SCANOUT) always stays synchronous. */
static struct {int slot;uint32_t address;int match;unsigned token;int64_t submitted,returned;} pending={-1};
static int pipelined(void) {
    static int mode=-1;
    if(mode<0){
        const char *sync=getenv("FNR3_SYNC_READBACK"),*legacy=getenv("FNR3_LEGACY_SCANOUT");
        /* Opt-in: only FNR3_SYNC_READBACK=0 (Fight Setup "Pipelined frames") enables it. */
        mode=(sync && !strcmp(sync,"0")) && !(legacy && *legacy=='1');
    }
    return mode;
}
static void finish_pending(void) {
    if(pending.slot<0)return;
    int slot=pending.slot;pending.slot=-1;
    finish_frame(slot,pending.address,pending.match,pending.token,pending.submitted,pending.returned);
}
static void flip(const Nv2aTarget *t) {
    presented_address=t->color_offset;
    if(t->pitch==2560) {
        if(fronts.count==FIGHT_FRONT_LIMIT && fight_front_index(&fronts,t->color_offset)==fronts.count)
            ++front_replacements;
        fight_front_present(&fronts,t->color_offset);
    }
    if(!dirty){finish_pending();return;}   /* nothing new: never leave the last frame unpublished */
    release_readback();
    int64_t submitted=fight_now_ns();
    int slot=vkr_flip_submit(gpu);
    if(slot>=0){
        int64_t returned=fight_now_ns();
        unsigned token=0;
#ifdef __ANDROID__
        token=fight_shared_take();     /* the display copy recorded in this frame's command buffer */
#endif
        int match=t->color_offset==active_address;
        if(pipelined()){
            finish_pending();
            pending.slot=slot;pending.address=active_address;pending.match=match;pending.token=token;
            pending.submitted=submitted;pending.returned=returned;
        }else finish_frame(slot,active_address,match,token,submitted,returned);
    }
    static int reported;
    static unsigned flips;
    if(++flips%300==0){
        report_soft();
        if(profile){
            for(unsigned i=0;i<nprofile;i++)if(profiled[i].count){
                fprintf(stderr,"[VSH profile] %08X %u\n",profiled[i].hash,profiled[i].count);profiled[i].count=0;
            }
        }
        fprintf(stderr,"[Fight Night] vertex uploads %llu/%llu, BC upload %llu bytes (RGBA base %llu)\n",
                (unsigned long long)verts,(unsigned long long)refs,(unsigned long long)bc_bytes,(unsigned long long)rgba_bytes);
        verts=refs=bc_bytes=rgba_bytes=0;
    }
    if(draws && !reported){fprintf(stderr,"[Fight Night] Vulkan draws reached scanout\n");reported=1;}
    dirty=0;
}
void fight_gpu_init(void) {
    static const Nv2aBackend backend={draw,clear,flip,draw_program};
    const char *mode=getenv("FNR3_RENDERER");
    if(mode && !strcmp(mode,"software")){
        fprintf(stderr,"[Fight Night] renderer software (requested)\n");
        return;
    }
    gpu=vkr_create();
    const char *capture=getenv("FNR3_VERTEX_PROFILE");
    if(capture && *capture)profile=fopen(capture,"wb");
    if(gpu){nv2a_exec_set_backend(&backend);fprintf(stderr,"[Fight Night] renderer Vulkan\n");}
    else {
#ifdef __ANDROID__
        /* Only the game child opts in, after the parent knows how to reap and retry. */
        if(getenv("FNR3_SUPERVISED_BOOT")){
            fprintf(stderr,"FNR3/1 GPU_INIT_FAILED\n");
            fflush(stderr);
            _Exit(74);
        }
#endif
        fprintf(stderr,"[Fight Night] renderer software (Vulkan unavailable)\n");
    }
}
