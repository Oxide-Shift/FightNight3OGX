/* CPU-side cache of the inputs to a draw. Constants, texture state and the
 * draw itself are always submitted again. An address alone never identifies
 * a mesh: guest code can rewrite a vertex array without notifying the GPU. */
enum { FIGHT_MESH_SETS=128, FIGHT_MESH_LIMIT=128*1024 };
typedef struct { uint32_t address, bytes; } FightSpan;
typedef struct {
    uint8_t *data;
    uint64_t token;
    uint32_t id;
    uint32_t capacity, key, count, unique, packed_bytes, retry;
    VertexAttr attributes[NV2A_VSH_INPUTS];
    uint8_t slots[NV2A_VSH_INPUTS];
    float immediate[NV2A_VSH_INPUTS][4];
    FightSpan spans[NV2A_VSH_INPUTS];
    unsigned ranges;
} FightMesh;

static FightMesh fight_meshes[FIGHT_MESH_SETS][2];
static uint8_t fight_victim[FIGHT_MESH_SETS];

static const uint16_t *fight_mesh_order(const FightMesh *m) {
    return (const uint16_t *)(m->data+m->packed_bytes);
}

static FightMesh *fight_mesh_find(const Nv2aVshProgram *p, unsigned count, FightMesh **store) {
    *store=NULL;
    if (s_gpu.inline_active || count<32 || count>8192 || !p->nin) return NULL;
    const uint16_t *indices=s_gpu.idx;
    uint32_t key=s_gpu.attr[0].offset ^ p->hash;
    key=(key^count)*16777619u;
    key=(key^indices[0])*16777619u;
    key=(key^indices[count/2])*16777619u;
    key=(key^indices[count-1])*16777619u;
    unsigned set=(key^(key>>16))&(FIGHT_MESH_SETS-1);
    for(unsigned way=0;way<2;way++){
        FightMesh *m=&fight_meshes[set][way];
        if(!m->count || m->key!=key || m->count!=count)continue;
        if(m->retry && (int32_t)(m->retry-s_gpu.flips)>0)return NULL;
        const uint16_t *order=fight_mesh_order(m);
        if(memcmp(order+count,indices,count*sizeof *indices) ||
           memcmp(m->slots,p->in_slot,sizeof m->slots) ||
           memcmp(m->attributes,s_gpu.attr,sizeof m->attributes))continue;
        for(unsigned a=0;a<NV2A_VSH_INPUTS;a++)
            if(p->in_slot[a]!=0xff && !s_gpu.attr[a].size &&
               memcmp(m->immediate[a],s_gpu.imm_attr[a],16))goto changed;
        const uint8_t *copy=(const uint8_t *)(order+2*count);
        for(unsigned j=0;j<m->ranges;j++){
            FightSpan span=m->spans[j];
            if(memcmp(copy,XBOX_HOST(span.address),span.bytes))goto changed;
            copy+=span.bytes;
        }
        fight_victim[set]=(uint8_t)(1-way);
        return m;
changed:
        /* Animated CPU buffers can change on every draw. Give them several
         * frames to settle before paying to copy another snapshot. */
        if(!m->retry){m->retry=s_gpu.flips+8;return NULL;}
        *store=m;
        return NULL;
    }
    unsigned way=fight_victim[set];
    *store=&fight_meshes[set][way];
    fight_victim[set]=(uint8_t)(1-way);
    (*store)->id=set*2+way;(*store)->count=0;(*store)->key=key;(*store)->retry=0;
    return NULL;
}

/* Include every byte the decoder can read. Merge interleaved attributes into
 * a single comparison and only inspect ranges inside mapped guest RAM. */
static unsigned fight_mesh_spans(const Nv2aVshProgram *p, unsigned first, unsigned last,
                                 FightSpan *spans, unsigned *bytes) {
    unsigned nr=0;
    *bytes=0;
    for(unsigned a=0;a<NV2A_VSH_INPUTS;a++){
        const VertexAttr *v=&s_gpu.attr[a];
        if(p->in_slot[a]==0xff || !v->size || !v->stride || !v->offset)continue;
        static const uint8_t widths[]={0,2,4,0,1,2,0};
        if(v->type>=sizeof widths || v->type==3)return 0;
        unsigned size=widths[v->type] ? widths[v->type]*(v->size<4?v->size:4) : 4;
        uint64_t start=(uint64_t)v->offset+(uint64_t)first*v->stride;
        uint64_t end=(uint64_t)v->offset+(uint64_t)last*v->stride+size;
        uint32_t base=v->offset & 0xf0000000u;
        if(base!=0 && base!=0x80000000u && base!=0xf0000000u)return 0;
        size_t limit=base?g_xbox_total_ram:(g_xbox_map_size?g_xbox_map_size:g_xbox_total_ram);
        if(start<base+0x10000u || end>(uint64_t)base+limit || end-start>FIGHT_MESH_LIMIT)return 0;
        for(unsigned i=0;i<nr;){
            uint64_t x=spans[i].address,y=x+spans[i].bytes;
            if(y<start || x>end){i++;continue;}
            if(x<start)start=x;
            if(y>end)end=y;
            spans[i]=spans[--nr];i=0;
        }
        spans[nr++]=(FightSpan){(uint32_t)start,(uint32_t)(end-start)};
    }
    for(unsigned i=0;i<nr;i++)*bytes+=spans[i].bytes;
    return nr;
}

static void fight_mesh_save(FightMesh *m,const Nv2aVshProgram *p,const uint16_t *unique_indices,
                            unsigned unique,unsigned count,const float *packed,const uint16_t *order) {
    unsigned first=65535,last=0;
    m->count=0;
    for(unsigned i=0;i<unique;i++){
        if(unique_indices[i]<first)first=unique_indices[i];
        if(unique_indices[i]>last)last=unique_indices[i];
    }
    uint32_t bytes=unique*p->nin*16;
    if(bytes+4*count>FIGHT_MESH_LIMIT)return;
    unsigned snapshot;
    m->ranges=fight_mesh_spans(p,first,last,m->spans,&snapshot);
    if(!m->ranges || snapshot+bytes+4*count>FIGHT_MESH_LIMIT)return;
    unsigned total=snapshot+bytes+count*4;
    if(total>m->capacity){
        uint8_t *next=realloc(m->data,total);
        if(!next)return;
        m->data=next;m->capacity=total;
    }
    memcpy(m->data,packed,bytes);
    memcpy(m->data+bytes,order,count*2);
    memcpy(m->data+bytes+count*2,s_gpu.idx,count*2);
    uint8_t *dst=m->data+bytes+count*4;
    for(unsigned j=0;j<m->ranges;j++){
        FightSpan span=m->spans[j];
        memcpy(dst,XBOX_HOST(span.address),span.bytes);dst+=span.bytes;
    }
    memcpy(m->attributes,s_gpu.attr,sizeof m->attributes);
    memcpy(m->slots,p->in_slot,sizeof m->slots);
    memcpy(m->immediate,s_gpu.imm_attr,sizeof m->immediate);
    static uint64_t version;
    m->token=(++version<<8)|m->id;
    m->count=count;m->unique=unique;m->packed_bytes=bytes;m->retry=0;
}
