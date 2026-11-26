/* Portable alternative for the three existing vkr.c set layouts.
 * One zero-initialized arena per in-flight frame. Call only on its recording thread. */
#pragma once
#include <vulkan/vulkan.h>
#include <string.h>

enum { FIGHT_SETS_PER_POOL = 128, FIGHT_POOLS_PER_FRAME = 32 };
typedef struct {
    VkDescriptorPool pools[FIGHT_POOLS_PER_FRAME];
    unsigned count, index, used;
} FightFrameSets;

static VkResult fight_bind_sets(FightFrameSets *frame, VkDevice dev, VkCommandBuffer cmd,
                                VkPipelineLayout layout, VkDescriptorSetLayout setLayout,
                                const VkWriteDescriptorSet *writes, unsigned count) {
    if (!writes || !count || count > 3) return VK_ERROR_INITIALIZATION_FAILED;
    while (frame->index < FIGHT_POOLS_PER_FRAME) {
        if (frame->used == FIGHT_SETS_PER_POOL) {
            frame->used = 0;
            ++frame->index;
            continue;
        }
        unsigned i = frame->index;
        if (i == frame->count) {
            const VkDescriptorPoolSize sizes[] = {
                { VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 4 * FIGHT_SETS_PER_POOL },
                { VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, FIGHT_SETS_PER_POOL },
                { VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, FIGHT_SETS_PER_POOL }
            };
            VkDescriptorPoolCreateInfo ci = { .sType=VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,
                .maxSets=FIGHT_SETS_PER_POOL, .poolSizeCount=3, .pPoolSizes=sizes };
            VkResult r=vkCreateDescriptorPool(dev,&ci,NULL,&frame->pools[i]);
            if (r!=VK_SUCCESS) return r;
            ++frame->count;
        }
        VkDescriptorSet set;
        VkDescriptorSetAllocateInfo ai = { .sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
            .descriptorPool=frame->pools[i], .descriptorSetCount=1, .pSetLayouts=&setLayout };
        VkResult r=vkAllocateDescriptorSets(dev,&ai,&set);
        if (r==VK_ERROR_FRAGMENTED_POOL || r==VK_ERROR_OUT_OF_POOL_MEMORY) {
            frame->used = 0;
            ++frame->index;
            continue;
        }
        if (r!=VK_SUCCESS) return r;
        VkWriteDescriptorSet copy[3];
        memcpy(copy,writes,count*sizeof(copy[0]));
        for (unsigned j=0;j<count;++j) copy[j].dstSet=set;
        vkUpdateDescriptorSets(dev,count,copy,0,NULL);
        vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_GRAPHICS,layout,0,1,&set,0,NULL);
        ++frame->used;
        return VK_SUCCESS;
    }
    return VK_ERROR_OUT_OF_POOL_MEMORY;
}

/* Caller must establish GPU completion for this slot before reuse. If reset
 * fails, invalidate the frame and stop recording. Never submit stale bindings. */
static VkResult fight_recycle_sets(FightFrameSets *f, VkDevice d) {
    for (unsigned i=0;i<f->count;++i) {
        VkResult r=vkResetDescriptorPool(d,f->pools[i],0);
        if (r!=VK_SUCCESS) return r;
    }
    f->used=f->index=0;
    return VK_SUCCESS;
}
static void fight_destroy_sets(FightFrameSets *f,VkDevice d) {
    for (unsigned i=0;i<f->count;++i) vkDestroyDescriptorPool(d,f->pools[i],NULL);
    memset(f,0,sizeof *f);
}

