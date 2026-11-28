/* Vulkan 1.1 path for the renderer (Alpha 84). Included by vulkan_android_dispatch.h before vkr.c.
 *
 * The renderer is written against Vulkan 1.3: dynamic rendering, synchronization2 and extended dynamic
 * state (depth/stencil/cull set per draw). Crash reports showed every "no usable Vulkan" exit came from
 * Vulkan 1.1 drivers (Mali-G57/G76, Adreno 640/710). On such a device each feature is taken from its
 * extension when the driver has it, otherwise emulated here:
 *   - dynamic rendering  -> a VkRenderPass + VkFramebuffer per begin (cached), compatible render passes
 *                           for pipelines;
 *   - synchronization2   -> vkCmdPipelineBarrier with the equivalent legacy stages and accesses;
 *   - extended dyn state -> the vkCmdSet* calls record state, and the draw binds a pipeline variant
 *                           with that state baked in (created on first use, kept until the base dies).
 * FNR3_VULKAN11=1 forces every emulation on any GPU (instance API 1.1, no 1.3 features) so the path
 * can be exercised on a PC under the Khronos validation layer. */
#pragma once
#include <vulkan/vulkan.h>
#include "vkr_thread.h"          /* the renderer's pthread subset (slim locks on Windows) */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int fight_compat;                 /* device below 1.3, or forced */
static int fight_native_dyn = 1, fight_native_sync2 = 1, fight_native_eds = 1;
static VkDevice fight_compat_dev;
static pthread_mutex_t fight_compat_lock = PTHREAD_MUTEX_INITIALIZER;

static int fight_vulkan11_forced(void) {
    const char *e = getenv("FNR3_VULKAN11");
    return e && *e && *e != '0';
}

/* ---- synchronization2 -> legacy barriers ---- */
static VkPipelineStageFlags fight_stage1(VkPipelineStageFlags2 s, int src) {
    VkPipelineStageFlags out = (VkPipelineStageFlags)(s & 0x7fffffffull);
    if (s & (VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_RESOLVE_BIT | VK_PIPELINE_STAGE_2_BLIT_BIT |
             VK_PIPELINE_STAGE_2_CLEAR_BIT)) out |= VK_PIPELINE_STAGE_TRANSFER_BIT;
    if (s & (VK_PIPELINE_STAGE_2_INDEX_INPUT_BIT | VK_PIPELINE_STAGE_2_VERTEX_ATTRIBUTE_INPUT_BIT))
        out |= VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
    if (s & VK_PIPELINE_STAGE_2_PRE_RASTERIZATION_SHADERS_BIT) out |= VK_PIPELINE_STAGE_VERTEX_SHADER_BIT;
    if (!out) out = src ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
    return out;
}
static VkAccessFlags fight_access1(VkAccessFlags2 a) {
    VkAccessFlags out = (VkAccessFlags)(a & 0x7fffffffull);
    if (a & (VK_ACCESS_2_SHADER_SAMPLED_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_READ_BIT)) out |= VK_ACCESS_SHADER_READ_BIT;
    if (a & VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT) out |= VK_ACCESS_SHADER_WRITE_BIT;
    return out;
}
static void VKAPI_CALL fight_barrier2_v1(VkCommandBuffer cmd, const VkDependencyInfo *d) {
    VkMemoryBarrier mb[8]; VkBufferMemoryBarrier bb[8]; VkImageMemoryBarrier ib[8];
    uint32_t nm = d->memoryBarrierCount < 8 ? d->memoryBarrierCount : 8;
    uint32_t nb = d->bufferMemoryBarrierCount < 8 ? d->bufferMemoryBarrierCount : 8;
    uint32_t ni = d->imageMemoryBarrierCount < 8 ? d->imageMemoryBarrierCount : 8;
    VkPipelineStageFlags2 src = 0, dst = 0;
    for (uint32_t i = 0; i < nm; i++) {
        const VkMemoryBarrier2 *s = &d->pMemoryBarriers[i];
        mb[i] = (VkMemoryBarrier){VK_STRUCTURE_TYPE_MEMORY_BARRIER, NULL, fight_access1(s->srcAccessMask), fight_access1(s->dstAccessMask)};
        src |= s->srcStageMask; dst |= s->dstStageMask;
    }
    for (uint32_t i = 0; i < nb; i++) {
        const VkBufferMemoryBarrier2 *s = &d->pBufferMemoryBarriers[i];
        bb[i] = (VkBufferMemoryBarrier){VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER, NULL, fight_access1(s->srcAccessMask),
            fight_access1(s->dstAccessMask), s->srcQueueFamilyIndex, s->dstQueueFamilyIndex, s->buffer, s->offset, s->size};
        src |= s->srcStageMask; dst |= s->dstStageMask;
    }
    for (uint32_t i = 0; i < ni; i++) {
        const VkImageMemoryBarrier2 *s = &d->pImageMemoryBarriers[i];
        ib[i] = (VkImageMemoryBarrier){VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, NULL, fight_access1(s->srcAccessMask),
            fight_access1(s->dstAccessMask), s->oldLayout, s->newLayout, s->srcQueueFamilyIndex, s->dstQueueFamilyIndex,
            s->image, s->subresourceRange};
        src |= s->srcStageMask; dst |= s->dstStageMask;
    }
    vkCmdPipelineBarrier(cmd, fight_stage1(src, 1), fight_stage1(dst, 0), d->dependencyFlags, nm, mb, nb, bb, ni, ib);
}
static void VKAPI_CALL fight_timestamp2_v1(VkCommandBuffer cmd, VkPipelineStageFlags2 stage, VkQueryPool pool, uint32_t query) {
    VkPipelineStageFlags s = fight_stage1(stage, 0);
    /* One stage bit only: the latest one asked for. */
    VkPipelineStageFlags bit = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
    if (s != VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT && s) { bit = 1u; while (s >>= 1) bit <<= 1; }
    vkCmdWriteTimestamp(cmd, (VkPipelineStageFlagBits)bit, pool, query);
}

/* ---- attachment formats (render passes need what dynamic rendering takes from the views) ---- */
typedef struct { uint64_t handle; VkFormat format; VkSampleCountFlagBits samples; } FightAttach;
#define FIGHT_ATTACH_SLOTS 1024
static FightAttach fight_images[FIGHT_ATTACH_SLOTS], fight_views[FIGHT_ATTACH_SLOTS];
static FightAttach *fight_attach_find(FightAttach *t, uint64_t h, int insert) {
    if (!h) return NULL;
    uint32_t i = (uint32_t)((h >> 4) ^ (h >> 20)) & (FIGHT_ATTACH_SLOTS - 1), tomb = UINT32_MAX;
    for (uint32_t n = 0; n < FIGHT_ATTACH_SLOTS; n++, i = (i + 1) & (FIGHT_ATTACH_SLOTS - 1)) {
        if (t[i].handle == h) return &t[i];
        if (t[i].handle == 1 && tomb == UINT32_MAX) tomb = i;       /* removed */
        if (!t[i].handle) { if (!insert) return NULL; if (tomb != UINT32_MAX) i = tomb; t[i].handle = h; return &t[i]; }
    }
    if (insert && tomb != UINT32_MAX) { t[tomb].handle = h; return &t[tomb]; }
    return NULL;
}
static VkResult VKAPI_CALL fight_create_image(VkDevice d, const VkImageCreateInfo *ci, const VkAllocationCallbacks *a, VkImage *img) {
    VkResult r = vkCreateImage(d, ci, a, img);
    if (r == VK_SUCCESS && fight_compat && !fight_native_dyn &&
        (ci->usage & (VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT))) {
        pthread_mutex_lock(&fight_compat_lock);
        FightAttach *e = fight_attach_find(fight_images, (uint64_t)(uintptr_t)*img, 1);
        if (e) { e->format = ci->format; e->samples = ci->samples; }
        pthread_mutex_unlock(&fight_compat_lock);
    }
    return r;
}
static void VKAPI_CALL fight_destroy_image(VkDevice d, VkImage img, const VkAllocationCallbacks *a) {
    if (fight_compat && !fight_native_dyn && img) {
        pthread_mutex_lock(&fight_compat_lock);
        FightAttach *e = fight_attach_find(fight_images, (uint64_t)(uintptr_t)img, 0);
        if (e) e->handle = 1;
        pthread_mutex_unlock(&fight_compat_lock);
    }
    vkDestroyImage(d, img, a);
}
static VkResult VKAPI_CALL fight_create_view(VkDevice d, const VkImageViewCreateInfo *ci, const VkAllocationCallbacks *a, VkImageView *v) {
    VkResult r = vkCreateImageView(d, ci, a, v);
    if (r == VK_SUCCESS && fight_compat && !fight_native_dyn) {
        pthread_mutex_lock(&fight_compat_lock);
        FightAttach *img = fight_attach_find(fight_images, (uint64_t)(uintptr_t)ci->image, 0);
        if (img) {
            FightAttach *e = fight_attach_find(fight_views, (uint64_t)(uintptr_t)*v, 1);
            if (e) { e->format = ci->format; e->samples = img->samples; }
        }
        pthread_mutex_unlock(&fight_compat_lock);
    }
    return r;
}

/* ---- render passes and framebuffers ---- */
typedef struct {
    uint32_t words[16];          /* formats, samples, ops and layouts that define the pass */
    VkRenderPass pass;
} FightPass;
static FightPass fight_passes[96];
static int fight_npasses;
typedef struct { VkImageView views[3]; uint32_t nviews, width, height; VkRenderPass pass; VkFramebuffer fb; } FightFb;
#define FIGHT_FB_MAX 512
static FightFb fight_fbs[FIGHT_FB_MAX];
static int fight_nfbs;

static void fight_view_info(VkImageView v, VkFormat *f, VkSampleCountFlagBits *s) {
    FightAttach *e = fight_attach_find(fight_views, (uint64_t)(uintptr_t)v, 0);
    *f = e ? e->format : VK_FORMAT_UNDEFINED;
    *s = e ? e->samples : VK_SAMPLE_COUNT_1_BIT;
}
/* Called with the lock held. color/depth formats UNDEFINED mean "absent". */
static VkRenderPass fight_render_pass(VkFormat cf, VkSampleCountFlagBits cs, VkAttachmentLoadOp cl, VkAttachmentStoreOp cst,
                                      VkImageLayout clay, VkFormat df, VkSampleCountFlagBits ds,
                                      VkAttachmentLoadOp dl, VkAttachmentStoreOp dst, VkAttachmentLoadOp sl,
                                      VkAttachmentStoreOp sst, VkImageLayout dlay, VkFormat rf, VkImageLayout rlay) {
    uint32_t key[16] = {(uint32_t)cf, (uint32_t)cs, (uint32_t)cl, (uint32_t)cst, (uint32_t)clay, (uint32_t)df, (uint32_t)ds,
                        (uint32_t)dl, (uint32_t)dst, (uint32_t)sl, (uint32_t)sst, (uint32_t)dlay, (uint32_t)rf, (uint32_t)rlay, 0, 0};
    for (int i = 0; i < fight_npasses; i++)
        if (!memcmp(fight_passes[i].words, key, sizeof key)) return fight_passes[i].pass;
    VkAttachmentDescription at[3]; VkAttachmentReference cr, dr, rr;
    uint32_t n = 0;
    VkSubpassDescription sp = {0};
    sp.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    memset(at, 0, sizeof at);
    if (cf != VK_FORMAT_UNDEFINED) {
        at[n] = (VkAttachmentDescription){0, cf, cs, cl, cst, VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_DONT_CARE, clay, clay};
        cr = (VkAttachmentReference){n++, clay};
        sp.colorAttachmentCount = 1; sp.pColorAttachments = &cr;
    }
    if (df != VK_FORMAT_UNDEFINED) {
        at[n] = (VkAttachmentDescription){0, df, ds, dl, dst, sl, sst, dlay, dlay};
        dr = (VkAttachmentReference){n++, dlay};
        sp.pDepthStencilAttachment = &dr;
    }
    if (rf != VK_FORMAT_UNDEFINED && sp.colorAttachmentCount) {
        at[n] = (VkAttachmentDescription){0, rf, VK_SAMPLE_COUNT_1_BIT, VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_STORE,
                                          VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_DONT_CARE, rlay, rlay};
        rr = (VkAttachmentReference){n++, rlay};
        sp.pResolveAttachments = &rr;
    }
    VkRenderPassCreateInfo ci = {VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
    ci.attachmentCount = n; ci.pAttachments = at; ci.subpassCount = 1; ci.pSubpasses = &sp;
    VkRenderPass pass = VK_NULL_HANDLE;
    if (vkCreateRenderPass(fight_compat_dev, &ci, NULL, &pass) != VK_SUCCESS) return VK_NULL_HANDLE;
    if (fight_npasses < (int)(sizeof fight_passes / sizeof fight_passes[0])) {
        memcpy(fight_passes[fight_npasses].words, key, sizeof key);
        fight_passes[fight_npasses++].pass = pass;
    } else fprintf(stderr, "FNR3/1 GPU_COMPAT render pass cache full\n");
    return pass;
}
static VkFramebuffer fight_framebuffer(VkRenderPass pass, const VkImageView *views, uint32_t n, uint32_t w, uint32_t h) {
    for (int i = 0; i < fight_nfbs; i++) {
        FightFb *f = &fight_fbs[i];
        if (f->pass == pass && f->nviews == n && f->width == w && f->height == h && !memcmp(f->views, views, n * sizeof *views))
            return f->fb;
    }
    if (fight_nfbs == FIGHT_FB_MAX) {
        /* Rare: every combination of targets seen so far. Start over once the GPU is idle. */
        vkDeviceWaitIdle(fight_compat_dev);
        for (int i = 0; i < fight_nfbs; i++) vkDestroyFramebuffer(fight_compat_dev, fight_fbs[i].fb, NULL);
        fight_nfbs = 0;
    }
    VkFramebufferCreateInfo ci = {VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
    ci.renderPass = pass; ci.attachmentCount = n; ci.pAttachments = views; ci.width = w; ci.height = h; ci.layers = 1;
    VkFramebuffer fb = VK_NULL_HANDLE;
    if (vkCreateFramebuffer(fight_compat_dev, &ci, NULL, &fb) != VK_SUCCESS) return VK_NULL_HANDLE;
    FightFb *f = &fight_fbs[fight_nfbs++];
    memset(f, 0, sizeof *f);
    memcpy(f->views, views, n * sizeof *views); f->nviews = n; f->width = w; f->height = h; f->pass = pass; f->fb = fb;
    return fb;
}
/* A view is only destroyed once the GPU no longer uses it, so neither do framebuffers made from it. */
static void VKAPI_CALL fight_destroy_view(VkDevice d, VkImageView v, const VkAllocationCallbacks *a) {
    if (fight_compat && !fight_native_dyn && v) {
        pthread_mutex_lock(&fight_compat_lock);
        for (int i = 0; i < fight_nfbs;) {
            int uses = 0;
            for (uint32_t k = 0; k < fight_fbs[i].nviews; k++) uses |= fight_fbs[i].views[k] == v;
            if (uses) { vkDestroyFramebuffer(d, fight_fbs[i].fb, NULL); fight_fbs[i] = fight_fbs[--fight_nfbs]; }
            else i++;
        }
        FightAttach *e = fight_attach_find(fight_views, (uint64_t)(uintptr_t)v, 0);
        if (e) e->handle = 1;
        pthread_mutex_unlock(&fight_compat_lock);
    }
    vkDestroyImageView(d, v, a);
}
static void VKAPI_CALL fight_begin_rendering_rp(VkCommandBuffer cmd, const VkRenderingInfo *ri) {
    VkFormat cf = VK_FORMAT_UNDEFINED, df = VK_FORMAT_UNDEFINED, rf = VK_FORMAT_UNDEFINED, f2;
    VkSampleCountFlagBits cs = VK_SAMPLE_COUNT_1_BIT, ds = VK_SAMPLE_COUNT_1_BIT, s2;
    VkImageView views[3]; uint32_t n = 0;
    VkClearValue clear[3]; memset(clear, 0, sizeof clear);
    const VkRenderingAttachmentInfo *c = ri->colorAttachmentCount ? &ri->pColorAttachments[0] : NULL;
    const VkRenderingAttachmentInfo *d = ri->pDepthAttachment && ri->pDepthAttachment->imageView ? ri->pDepthAttachment : NULL;
    const VkRenderingAttachmentInfo *s = ri->pStencilAttachment && ri->pStencilAttachment->imageView ? ri->pStencilAttachment : NULL;
    const VkRenderingAttachmentInfo *z = d ? d : s;
    int resolve = c && c->resolveMode != VK_RESOLVE_MODE_NONE && c->resolveImageView;
    pthread_mutex_lock(&fight_compat_lock);
    if (c) { fight_view_info(c->imageView, &cf, &cs); views[n] = c->imageView; clear[n++] = c->clearValue; }
    if (z) {
        fight_view_info(z->imageView, &df, &ds); views[n] = z->imageView;
        clear[n].depthStencil.depth = d ? d->clearValue.depthStencil.depth : 1.0f;
        clear[n++].depthStencil.stencil = s ? s->clearValue.depthStencil.stencil : 0;
    }
    if (resolve) { fight_view_info(c->resolveImageView, &rf, &s2); views[n++] = c->resolveImageView; }
    (void)f2;
    VkRenderPass pass = fight_render_pass(cf, cs, c ? c->loadOp : 0, c ? c->storeOp : 0, c ? c->imageLayout : 0,
        df, ds, d ? d->loadOp : VK_ATTACHMENT_LOAD_OP_DONT_CARE, d ? d->storeOp : VK_ATTACHMENT_STORE_OP_DONT_CARE,
        s ? s->loadOp : VK_ATTACHMENT_LOAD_OP_DONT_CARE, s ? s->storeOp : VK_ATTACHMENT_STORE_OP_DONT_CARE,
        z ? z->imageLayout : 0, rf, resolve ? c->resolveImageLayout : 0);
    uint32_t w = ri->renderArea.offset.x + ri->renderArea.extent.width, h = ri->renderArea.offset.y + ri->renderArea.extent.height;
    VkFramebuffer fb = pass ? fight_framebuffer(pass, views, n, w, h) : VK_NULL_HANDLE;
    pthread_mutex_unlock(&fight_compat_lock);
    if (!fb) { fprintf(stderr, "FNR3/1 GPU_COMPAT begin failed (format %d/%d)\n", (int)cf, (int)df); return; }
    VkRenderPassBeginInfo bi = {VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
    bi.renderPass = pass; bi.framebuffer = fb; bi.renderArea = ri->renderArea;
    bi.clearValueCount = n; bi.pClearValues = clear;
    vkCmdBeginRenderPass(cmd, &bi, VK_SUBPASS_CONTENTS_INLINE);
}
static void VKAPI_CALL fight_end_rendering_rp(VkCommandBuffer cmd) { vkCmdEndRenderPass(cmd); }

/* ---- extended dynamic state -> pipeline variants ---- */
typedef struct {
    VkBool32 depth_test, depth_write, stencil_test;
    VkCompareOp depth_op, stencil_compare;
    VkStencilOp fail, pass, depth_fail;
    VkCullModeFlags cull;
    VkFrontFace front;
} FightRaster;
static FightRaster fight_raster_now;     /* recorded by the vkCmdSet* calls on the render thread */
static uint32_t fight_raster_word(const FightRaster *r) {
    return (r->depth_test & 1) | (r->depth_write & 1) << 1 | ((uint32_t)r->depth_op & 7) << 2 | (r->stencil_test & 1) << 5 |
           ((uint32_t)r->fail & 7) << 6 | ((uint32_t)r->pass & 7) << 9 | ((uint32_t)r->depth_fail & 7) << 12 |
           ((uint32_t)r->stencil_compare & 7) << 15 | ((uint32_t)r->cull & 3) << 18 | ((uint32_t)r->front & 1) << 20;
}
static void VKAPI_CALL fight_set_depth_test(VkCommandBuffer c, VkBool32 on) { (void)c; fight_raster_now.depth_test = on; }
static void VKAPI_CALL fight_set_depth_write(VkCommandBuffer c, VkBool32 on) { (void)c; fight_raster_now.depth_write = on; }
static void VKAPI_CALL fight_set_depth_op(VkCommandBuffer c, VkCompareOp op) { (void)c; fight_raster_now.depth_op = op; }
static void VKAPI_CALL fight_set_stencil_test(VkCommandBuffer c, VkBool32 on) { (void)c; fight_raster_now.stencil_test = on; }
static void VKAPI_CALL fight_set_stencil_op(VkCommandBuffer c, VkStencilFaceFlags f, VkStencilOp fail, VkStencilOp pass,
                                           VkStencilOp dfail, VkCompareOp op) {
    (void)c; (void)f;   /* the renderer only sets both faces together */
    fight_raster_now.fail = fail; fight_raster_now.pass = pass; fight_raster_now.depth_fail = dfail; fight_raster_now.stencil_compare = op;
}
static void VKAPI_CALL fight_set_cull(VkCommandBuffer c, VkCullModeFlags m) { (void)c; fight_raster_now.cull = m; }
static void VKAPI_CALL fight_set_front(VkCommandBuffer c, VkFrontFace f) { (void)c; fight_raster_now.front = f; }

typedef struct FightVariant { uint32_t word; VkPipeline pipe; struct FightVariant *next; } FightVariant;
typedef struct FightBase {
    VkPipeline handle;                    /* what the renderer holds (its first variant) */
    VkPipelineCache cache;
    VkGraphicsPipelineCreateInfo info;    /* deep copy, extended dynamic states removed */
    VkPipelineShaderStageCreateInfo stages[4];
    VkSpecializationInfo spec[4]; VkSpecializationMapEntry *entries[4]; void *data[4];
    VkPipelineVertexInputStateCreateInfo vin; VkVertexInputBindingDescription bind[4]; VkVertexInputAttributeDescription attr[16];
    VkPipelineInputAssemblyStateCreateInfo ia; VkPipelineViewportStateCreateInfo vp; VkPipelineRasterizationStateCreateInfo rs;
    VkPipelineMultisampleStateCreateInfo ms; VkPipelineDepthStencilStateCreateInfo ds;
    VkPipelineColorBlendStateCreateInfo cb; VkPipelineColorBlendAttachmentState cba[4];
    VkPipelineDynamicStateCreateInfo dy; VkDynamicState dyn[24];
    FightVariant *variants;
    struct FightBase *next;
} FightBase;
#define FIGHT_BASE_BUCKETS 1024
static FightBase *fight_bases[FIGHT_BASE_BUCKETS];
static FightBase *fight_bound;            /* render thread: the bound base, or NULL */
static VkPipeline fight_bound_variant;
typedef struct { VkShaderModule module; int refs, dead; } FightModule;
static FightModule fight_modules[512];
static int fight_nmodules;

static uint32_t fight_base_bucket(VkPipeline p) { uint64_t h = (uint64_t)(uintptr_t)p; return (uint32_t)((h >> 4) ^ (h >> 17)) & (FIGHT_BASE_BUCKETS - 1); }
static FightBase *fight_base_find(VkPipeline p) {
    for (FightBase *b = fight_bases[fight_base_bucket(p)]; b; b = b->next) if (b->handle == p) return b;
    return NULL;
}
static void fight_module_ref(VkShaderModule m, int delta) {
    for (int i = 0; i < fight_nmodules; i++) if (fight_modules[i].module == m) {
        fight_modules[i].refs += delta;
        if (fight_modules[i].refs <= 0) {
            if (fight_modules[i].dead) vkDestroyShaderModule(fight_compat_dev, m, NULL);
            fight_modules[i] = fight_modules[--fight_nmodules];
        }
        return;
    }
    if (delta > 0 && fight_nmodules < 512) fight_modules[fight_nmodules++] = (FightModule){m, delta, 0};
}
/* The renderer frees one-off fragment modules right after creating a pipeline; variants still need them. */
static void VKAPI_CALL fight_destroy_module(VkDevice d, VkShaderModule m, const VkAllocationCallbacks *a) {
    if (fight_compat && !fight_native_eds && m) {
        pthread_mutex_lock(&fight_compat_lock);
        for (int i = 0; i < fight_nmodules; i++) if (fight_modules[i].module == m) {
            fight_modules[i].dead = 1; pthread_mutex_unlock(&fight_compat_lock); return;
        }
        pthread_mutex_unlock(&fight_compat_lock);
    }
    vkDestroyShaderModule(d, m, a);
}
static int fight_is_eds(VkDynamicState s) {
    return s == VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE || s == VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE || s == VK_DYNAMIC_STATE_DEPTH_COMPARE_OP ||
           s == VK_DYNAMIC_STATE_STENCIL_TEST_ENABLE || s == VK_DYNAMIC_STATE_STENCIL_OP || s == VK_DYNAMIC_STATE_CULL_MODE ||
           s == VK_DYNAMIC_STATE_FRONT_FACE;
}
static FightBase *fight_base_copy(const VkGraphicsPipelineCreateInfo *pi, VkPipelineCache cache) {
    if (pi->stageCount > 4 || !pi->pDepthStencilState || !pi->pRasterizationState || !pi->pColorBlendState ||
        pi->pColorBlendState->attachmentCount > 4) return NULL;
    FightBase *b = calloc(1, sizeof *b);
    if (!b) return NULL;
    b->cache = cache; b->info = *pi; b->info.pNext = NULL;
    for (uint32_t i = 0; i < pi->stageCount; i++) {
        b->stages[i] = pi->pStages[i]; b->stages[i].pNext = NULL;
        const VkSpecializationInfo *s = pi->pStages[i].pSpecializationInfo;
        if (s) {
            b->spec[i] = *s;
            b->entries[i] = malloc(s->mapEntryCount * sizeof *s->pMapEntries + 1);
            b->data[i] = malloc(s->dataSize + 1);
            if (!b->entries[i] || !b->data[i]) return b->info.stageCount = 0, b;   /* caller frees */
            memcpy(b->entries[i], s->pMapEntries, s->mapEntryCount * sizeof *s->pMapEntries);
            memcpy(b->data[i], s->pData, s->dataSize);
            b->spec[i].pMapEntries = b->entries[i]; b->spec[i].pData = b->data[i];
            b->stages[i].pSpecializationInfo = &b->spec[i];
        }
    }
    b->info.pStages = b->stages;
    if (pi->pVertexInputState) {
        const VkPipelineVertexInputStateCreateInfo *v = pi->pVertexInputState;
        if (v->vertexBindingDescriptionCount > 4 || v->vertexAttributeDescriptionCount > 16) { b->info.stageCount = 0; return b; }
        b->vin = *v; b->vin.pNext = NULL;
        memcpy(b->bind, v->pVertexBindingDescriptions, v->vertexBindingDescriptionCount * sizeof *b->bind);
        memcpy(b->attr, v->pVertexAttributeDescriptions, v->vertexAttributeDescriptionCount * sizeof *b->attr);
        b->vin.pVertexBindingDescriptions = b->bind; b->vin.pVertexAttributeDescriptions = b->attr;
        b->info.pVertexInputState = &b->vin;
    }
    if (pi->pInputAssemblyState) { b->ia = *pi->pInputAssemblyState; b->info.pInputAssemblyState = &b->ia; }
    if (pi->pViewportState) { b->vp = *pi->pViewportState; b->vp.pViewports = NULL; b->vp.pScissors = NULL; b->info.pViewportState = &b->vp; }
    b->rs = *pi->pRasterizationState; b->rs.pNext = NULL; b->info.pRasterizationState = &b->rs;
    if (pi->pMultisampleState) { b->ms = *pi->pMultisampleState; b->ms.pNext = NULL; b->ms.pSampleMask = NULL; b->info.pMultisampleState = &b->ms; }
    b->ds = *pi->pDepthStencilState; b->ds.pNext = NULL; b->info.pDepthStencilState = &b->ds;
    b->cb = *pi->pColorBlendState; b->cb.pNext = NULL;
    memcpy(b->cba, pi->pColorBlendState->pAttachments, b->cb.attachmentCount * sizeof *b->cba);
    b->cb.pAttachments = b->cba; b->info.pColorBlendState = &b->cb;
    uint32_t n = 0;
    if (pi->pDynamicState)
        for (uint32_t i = 0; i < pi->pDynamicState->dynamicStateCount && n < 24; i++)
            if (!fight_is_eds(pi->pDynamicState->pDynamicStates[i])) b->dyn[n++] = pi->pDynamicState->pDynamicStates[i];
    b->dy = (VkPipelineDynamicStateCreateInfo){VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO, NULL, 0, n, b->dyn};
    b->info.pDynamicState = &b->dy;
    return b;
}
static void fight_base_free(FightBase *b) {
    for (int i = 0; i < 4; i++) { free(b->entries[i]); free(b->data[i]); }
    free(b);
}
static VkPipeline fight_variant(FightBase *b, const FightRaster *r) {
    uint32_t word = fight_raster_word(r);
    for (FightVariant *v = b->variants; v; v = v->next) if (v->word == word) return v->pipe;
    b->ds.depthTestEnable = r->depth_test; b->ds.depthWriteEnable = r->depth_write; b->ds.depthCompareOp = r->depth_op;
    b->ds.stencilTestEnable = r->stencil_test;
    b->ds.front.failOp = b->ds.back.failOp = r->fail; b->ds.front.passOp = b->ds.back.passOp = r->pass;
    b->ds.front.depthFailOp = b->ds.back.depthFailOp = r->depth_fail;
    b->ds.front.compareOp = b->ds.back.compareOp = r->stencil_compare;
    b->rs.cullMode = r->cull; b->rs.frontFace = r->front;
    VkPipeline p = VK_NULL_HANDLE;
    if (vkCreateGraphicsPipelines(fight_compat_dev, b->cache, 1, &b->info, NULL, &p) != VK_SUCCESS) {
        fprintf(stderr, "FNR3/1 GPU_COMPAT variant failed %05x\n", word);
        return b->handle;
    }
    FightVariant *v = malloc(sizeof *v);
    if (!v) { vkDestroyPipeline(fight_compat_dev, p, NULL); return b->handle; }
    v->word = word; v->pipe = p; v->next = b->variants; b->variants = v;
    return p;
}

/* Pipelines: compatible render pass for dynamic-rendering pipelines; variants for extended dynamic state. */
static VkResult VKAPI_CALL fight_create_pipelines(VkDevice d, VkPipelineCache cache, uint32_t count,
                                                  const VkGraphicsPipelineCreateInfo *infos, const VkAllocationCallbacks *a,
                                                  VkPipeline *out) {
    if (!fight_compat || (fight_native_dyn && fight_native_eds))
        return vkCreateGraphicsPipelines(d, cache, count, infos, a, out);
    VkResult result = VK_SUCCESS;
    for (uint32_t i = 0; i < count; i++) {
        VkGraphicsPipelineCreateInfo pi = infos[i];
        if (!fight_native_dyn && !pi.renderPass) {
            const VkBaseInStructure *prev = NULL, *it = (const VkBaseInStructure *)pi.pNext;
            const VkPipelineRenderingCreateInfo *ri = NULL;
            while (it) {
                if (it->sType == VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO) { ri = (const VkPipelineRenderingCreateInfo *)it; break; }
                prev = it; it = it->pNext;
            }
            if (ri && !prev) pi.pNext = ri->pNext;            /* the renderer chains it first */
            VkFormat cf = ri && ri->colorAttachmentCount ? ri->pColorAttachmentFormats[0] : VK_FORMAT_UNDEFINED;
            VkFormat df = ri ? (ri->depthAttachmentFormat ? ri->depthAttachmentFormat : ri->stencilAttachmentFormat) : VK_FORMAT_UNDEFINED;
            VkSampleCountFlagBits s = pi.pMultisampleState ? pi.pMultisampleState->rasterizationSamples : VK_SAMPLE_COUNT_1_BIT;
            pthread_mutex_lock(&fight_compat_lock);
            pi.renderPass = fight_render_pass(cf, s, VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, df, s, VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE,
                VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                VK_FORMAT_UNDEFINED, 0);
            pthread_mutex_unlock(&fight_compat_lock);
            pi.subpass = 0;
            if (!pi.renderPass) { out[i] = VK_NULL_HANDLE; result = VK_ERROR_INITIALIZATION_FAILED; continue; }
        }
        int eds = 0;
        if (!fight_native_eds && pi.pDynamicState)
            for (uint32_t k = 0; k < pi.pDynamicState->dynamicStateCount; k++) eds |= fight_is_eds(pi.pDynamicState->pDynamicStates[k]);
        if (!eds) {
            VkResult r = vkCreateGraphicsPipelines(d, cache, 1, &pi, a, &out[i]);
            if (r != VK_SUCCESS) { out[i] = VK_NULL_HANDLE; result = r; }
            continue;
        }
        FightBase *b = fight_base_copy(&pi, cache);
        if (!b || !b->info.stageCount) { if (b) fight_base_free(b); out[i] = VK_NULL_HANDLE; result = VK_ERROR_OUT_OF_HOST_MEMORY; continue; }
        FightRaster first = {pi.pDepthStencilState->depthTestEnable, pi.pDepthStencilState->depthWriteEnable,
            pi.pDepthStencilState->stencilTestEnable, pi.pDepthStencilState->depthCompareOp, pi.pDepthStencilState->front.compareOp,
            pi.pDepthStencilState->front.failOp, pi.pDepthStencilState->front.passOp, pi.pDepthStencilState->front.depthFailOp,
            pi.pRasterizationState->cullMode, pi.pRasterizationState->frontFace};
        VkPipeline p = fight_variant(b, &first);
        if (!b->variants) { fight_base_free(b); out[i] = VK_NULL_HANDLE; result = VK_ERROR_INITIALIZATION_FAILED; continue; }
        b->handle = p; out[i] = p;
        pthread_mutex_lock(&fight_compat_lock);
        for (uint32_t k = 0; k < b->info.stageCount; k++) fight_module_ref(b->stages[k].module, 1);
        uint32_t bucket = fight_base_bucket(p); b->next = fight_bases[bucket]; fight_bases[bucket] = b;
        pthread_mutex_unlock(&fight_compat_lock);
    }
    return result;
}
static void VKAPI_CALL fight_destroy_pipeline(VkDevice d, VkPipeline p, const VkAllocationCallbacks *a) {
    if (fight_compat && !fight_native_eds && p) {
        pthread_mutex_lock(&fight_compat_lock);
        FightBase **link = &fight_bases[fight_base_bucket(p)];
        while (*link && (*link)->handle != p) link = &(*link)->next;
        FightBase *b = *link;
        if (b) {
            *link = b->next;
            if (fight_bound == b) { fight_bound = NULL; fight_bound_variant = VK_NULL_HANDLE; }
            for (FightVariant *v = b->variants, *n; v; v = n) { n = v->next; vkDestroyPipeline(d, v->pipe, a); free(v); }
            for (uint32_t k = 0; k < b->info.stageCount; k++) fight_module_ref(b->stages[k].module, -1);
            fight_base_free(b);
            pthread_mutex_unlock(&fight_compat_lock);
            return;
        }
        pthread_mutex_unlock(&fight_compat_lock);
    }
    vkDestroyPipeline(d, p, a);
}
static void VKAPI_CALL fight_bind_pipeline(VkCommandBuffer cmd, VkPipelineBindPoint point, VkPipeline p) {
    if (fight_compat && !fight_native_eds && point == VK_PIPELINE_BIND_POINT_GRAPHICS) {
        pthread_mutex_lock(&fight_compat_lock);
        fight_bound = fight_base_find(p);
        pthread_mutex_unlock(&fight_compat_lock);
        fight_bound_variant = VK_NULL_HANDLE;
        if (fight_bound) return;           /* the draw binds the variant for its state */
    }
    vkCmdBindPipeline(cmd, point, p);
}
static void fight_before_draw(VkCommandBuffer cmd) {
    if (!fight_bound) return;
    pthread_mutex_lock(&fight_compat_lock);
    VkPipeline p = fight_bound ? fight_variant(fight_bound, &fight_raster_now) : VK_NULL_HANDLE;
    pthread_mutex_unlock(&fight_compat_lock);
    if (p && p != fight_bound_variant) { vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, p); fight_bound_variant = p; }
}
static void VKAPI_CALL fight_draw(VkCommandBuffer cmd, uint32_t vc, uint32_t ic, uint32_t fv, uint32_t fi) {
    fight_before_draw(cmd); vkCmdDraw(cmd, vc, ic, fv, fi);
}
static void VKAPI_CALL fight_draw_indexed(VkCommandBuffer cmd, uint32_t n, uint32_t ic, uint32_t first, int32_t vo, uint32_t fi) {
    fight_before_draw(cmd); vkCmdDrawIndexed(cmd, n, ic, first, vo, fi);
}

/* ---- instance and device ---- */
static VkResult VKAPI_CALL fight_create_instance(const VkInstanceCreateInfo *ci, const VkAllocationCallbacks *a, VkInstance *out) {
    if (!fight_vulkan11_forced() || !ci->pApplicationInfo) return vkCreateInstance(ci, a, out);
    VkApplicationInfo app = *ci->pApplicationInfo; VkInstanceCreateInfo copy = *ci;
    app.apiVersion = VK_API_VERSION_1_1; copy.pApplicationInfo = &app;
    fprintf(stderr, "FNR3/1 GPU_COMPAT forced: instance API 1.1\n");
    return vkCreateInstance(&copy, a, out);
}
static int fight_has_ext(const VkExtensionProperties *p, uint32_t n, const char *name) {
    for (uint32_t i = 0; i < n; i++) if (!strcmp(p[i].extensionName, name)) return 1;
    return 0;
}
/* Rewrites a device create info for a Vulkan 1.1 device: drops the 1.3/1.4 feature structs, adds the extensions
 * that provide the same features where the driver has them. names/feature storage are owned by the caller. */
typedef struct {
    const char *names[24];
    VkPhysicalDeviceDynamicRenderingFeaturesKHR dyn;
    VkPhysicalDeviceSynchronization2FeaturesKHR sync2;
    VkPhysicalDeviceExtendedDynamicStateFeaturesEXT eds;
} FightCompatDevice;
static void fight_compat_device(VkPhysicalDevice phys, VkDeviceCreateInfo *info, FightCompatDevice *s) {
    VkPhysicalDeviceProperties props;
    vkGetPhysicalDeviceProperties(phys, &props);
    int forced = fight_vulkan11_forced();
    fight_compat = forced || props.apiVersion < VK_API_VERSION_1_3;
    fight_native_dyn = fight_native_sync2 = fight_native_eds = !fight_compat;
    if (!fight_compat) return;
    int push14 = 0;
    /* Drop core 1.3/1.4 feature structs from the chain (the renderer chains them first). */
    const VkBaseInStructure *it = (const VkBaseInStructure *)info->pNext;
    while (it && (it->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES ||
                  it->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_4_FEATURES)) {
        if (it->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_4_FEATURES)
            push14 = ((const VkPhysicalDeviceVulkan14Features *)it)->pushDescriptor;
        it = it->pNext;
    }
    info->pNext = it;
    uint32_t n = 0;
    for (uint32_t i = 0; i < info->enabledExtensionCount && n < 16; i++) s->names[n++] = info->ppEnabledExtensionNames[i];
    uint32_t count = 0; VkExtensionProperties *ext = NULL;
    if (vkEnumerateDeviceExtensionProperties(phys, NULL, &count, NULL) == VK_SUCCESS && count && count < 4096 &&
        (ext = calloc(count, sizeof *ext)) && vkEnumerateDeviceExtensionProperties(phys, NULL, &count, ext) != VK_SUCCESS) count = 0;
    /* Extensions only on real old drivers; FNR3_VULKAN11=1 tests the emulation itself unless FNR3_VULKAN11_EXT=1. */
    int use_ext = !forced || (getenv("FNR3_VULKAN11_EXT") && *getenv("FNR3_VULKAN11_EXT") == '1');
    VkPhysicalDeviceFeatures2 f2 = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
    s->dyn = (VkPhysicalDeviceDynamicRenderingFeaturesKHR){VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR};
    s->sync2 = (VkPhysicalDeviceSynchronization2FeaturesKHR){VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SYNCHRONIZATION_2_FEATURES_KHR};
    s->eds = (VkPhysicalDeviceExtendedDynamicStateFeaturesEXT){VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_FEATURES_EXT};
    void *chain = NULL;
    int has_dyn = use_ext && fight_has_ext(ext, count, "VK_KHR_dynamic_rendering") &&
                  fight_has_ext(ext, count, "VK_KHR_depth_stencil_resolve") && fight_has_ext(ext, count, "VK_KHR_create_renderpass2");
    int has_sync2 = use_ext && fight_has_ext(ext, count, "VK_KHR_synchronization2");
    int has_eds = use_ext && fight_has_ext(ext, count, "VK_EXT_extended_dynamic_state");
    if (has_dyn) { s->dyn.pNext = chain; chain = &s->dyn; }
    if (has_sync2) { s->sync2.pNext = chain; chain = &s->sync2; }
    if (has_eds) { s->eds.pNext = chain; chain = &s->eds; }
    f2.pNext = chain;
    if (chain) vkGetPhysicalDeviceFeatures2(phys, &f2);
    void *enable = (void *)info->pNext;
    if (has_dyn && s->dyn.dynamicRendering && n + 3 <= 24) {
        s->names[n++] = "VK_KHR_dynamic_rendering"; s->names[n++] = "VK_KHR_depth_stencil_resolve"; s->names[n++] = "VK_KHR_create_renderpass2";
        s->dyn.pNext = enable; enable = &s->dyn; fight_native_dyn = 1;
    }
    if (has_sync2 && s->sync2.synchronization2 && n < 24) {
        s->names[n++] = "VK_KHR_synchronization2"; s->sync2.pNext = enable; enable = &s->sync2; fight_native_sync2 = 1;
    }
    if (has_eds && s->eds.extendedDynamicState && n < 24) {
        s->names[n++] = "VK_EXT_extended_dynamic_state"; s->eds.pNext = enable; enable = &s->eds; fight_native_eds = 1;
    }
    if (push14 && fight_has_ext(ext, count, "VK_KHR_push_descriptor") && n < 24) s->names[n++] = "VK_KHR_push_descriptor";
    free(ext);
    info->pNext = enable;
    info->enabledExtensionCount = n; info->ppEnabledExtensionNames = s->names;
    fprintf(stderr, "FNR3/1 GPU_COMPAT %u.%u dyn=%s sync2=%s eds=%s\n", VK_VERSION_MAJOR(props.apiVersion), VK_VERSION_MINOR(props.apiVersion),
            fight_native_dyn ? "ext" : "renderpass", fight_native_sync2 ? "ext" : "barrier", fight_native_eds ? "ext" : "variants");
}
