/* Host-only fault fixtures using official Vulkan types. No graphics driver required. */
#include "../native/fight_device_probe.h"
#include "../native/fight_descriptor_sets.h"
#include <assert.h>
#include <stdio.h>

static uint32_t api, limit, queueBits;
static int dynamic, sync2, push, creates, allocations, resets, destroys, updates, binds;
static VkResult allocError;
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceProperties(VkPhysicalDevice d,VkPhysicalDeviceProperties *p) {
    memset(p,0,sizeof *p); p->apiVersion=api;
}
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceFeatures2(VkPhysicalDevice d,VkPhysicalDeviceFeatures2 *p) {
    VkPhysicalDeviceVulkan13Features *f=p->pNext; f->dynamicRendering=dynamic; f->synchronization2=sync2;
}
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceProperties2(VkPhysicalDevice d,VkPhysicalDeviceProperties2 *p) {
    ((VkPhysicalDevicePushDescriptorPropertiesKHR*)p->pNext)->maxPushDescriptors=limit;
}
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceExtensionProperties(VkPhysicalDevice d,const char *l,uint32_t *n,VkExtensionProperties *p) {
    *n=1; if(p) strcpy(p->extensionName,push?VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME:"fixture"); return VK_SUCCESS;
}
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceQueueFamilyProperties(VkPhysicalDevice d,uint32_t *n,VkQueueFamilyProperties *p) {
    *n=1; if(p){p->queueCount=1; p->queueFlags=queueBits;}
}
VKAPI_ATTR VkResult VKAPI_CALL vkCreateDescriptorPool(VkDevice d,const VkDescriptorPoolCreateInfo *i,const VkAllocationCallbacks *a,VkDescriptorPool *p) {
    assert(i->poolSizeCount==3); *p=(VkDescriptorPool)(uintptr_t)++creates; return VK_SUCCESS;
}
VKAPI_ATTR VkResult VKAPI_CALL vkAllocateDescriptorSets(VkDevice d,const VkDescriptorSetAllocateInfo *i,VkDescriptorSet *s) {
    allocations++;
    if(allocError) return allocError;
    *s=(VkDescriptorSet)(uintptr_t)allocations; return VK_SUCCESS;
}
VKAPI_ATTR void VKAPI_CALL vkUpdateDescriptorSets(VkDevice d,uint32_t n,const VkWriteDescriptorSet *w,uint32_t c,const VkCopyDescriptorSet *copies) {
    assert(n==1 && w[0].dstSet); updates++;
}
VKAPI_ATTR void VKAPI_CALL vkCmdBindDescriptorSets(VkCommandBuffer c,VkPipelineBindPoint b,VkPipelineLayout l,uint32_t f,uint32_t n,const VkDescriptorSet *s,uint32_t dc,const uint32_t *o) {
    assert(n==1 && s[0]); binds++;
}
VKAPI_ATTR VkResult VKAPI_CALL vkResetDescriptorPool(VkDevice d,VkDescriptorPool p,VkDescriptorPoolResetFlags f) {
    assert(p); resets++; return VK_SUCCESS;
}
VKAPI_ATTR void VKAPI_CALL vkDestroyDescriptorPool(VkDevice d,VkDescriptorPool p,const VkAllocationCallbacks *a) {
    assert(p); destroys++;
}
int main(void) {
    FightGpuDevice result={0}; api=VK_API_VERSION_1_0;
    assert(fight_check_device(0,&result)==FIGHT_GPU_API);
    /* Alpha 84: Vulkan 1.1/1.2 run through fight_vk_compat.h; the 1.3 feature struct is never queried there. */
    api=VK_API_VERSION_1_1; assert(fight_check_device(0,&result)==FIGHT_GPU_QUEUE);
    queueBits=VK_QUEUE_GRAPHICS_BIT; api=VK_API_VERSION_1_2;
    assert(fight_check_device(0,&result)==FIGHT_GPU_OK && !result.push);
    queueBits=0;
    api=VK_API_VERSION_1_3; dynamic=1; assert(fight_check_device(0,&result)==FIGHT_GPU_FEATURES);
    sync2=1; assert(fight_check_device(0,&result)==FIGHT_GPU_QUEUE);
    queueBits=VK_QUEUE_GRAPHICS_BIT;
    assert(fight_check_device(0,&result)==FIGHT_GPU_OK && !result.push);
    push=1;limit=5; assert(fight_check_device(0,&result)==FIGHT_GPU_OK && !result.push);
    limit=6; assert(fight_check_device(0,&result)==FIGHT_GPU_OK && result.push);
    VkPhysicalDeviceMemoryProperties mem={0}; mem.memoryTypeCount=3;
    mem.memoryTypes[0].propertyFlags=VK_MEMORY_PROPERTY_PROTECTED_BIT;
    mem.memoryTypes[1].propertyFlags=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
    mem.memoryTypes[2].propertyFlags=VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
    assert(fight_memory_type(&mem,3,0,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)==1);
    assert(fight_memory_type(&mem,7,0,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)==2);
    assert(fight_memory_type(&mem,1,0,0)==UINT32_MAX);
    FightFrameSets f={0}; VkWriteDescriptorSet write={.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    assert(fight_bind_sets(&f,0,0,0,0,&write,1)==VK_SUCCESS);
    assert(!write.dstSet && updates==1 && binds==1);
    allocError=VK_ERROR_DEVICE_LOST;
    assert(fight_bind_sets(&f,0,0,0,0,&write,1)==VK_ERROR_DEVICE_LOST);
    assert(updates==1 && binds==1);
    allocError=VK_ERROR_OUT_OF_POOL_MEMORY;
    assert(fight_bind_sets(&f,0,0,0,0,&write,1)==VK_ERROR_OUT_OF_POOL_MEMORY);
    assert(creates==FIGHT_POOLS_PER_FRAME && f.index==f.count);
    assert(fight_recycle_sets(&f,0)==VK_SUCCESS && resets==creates && f.index==0);
    allocError=VK_SUCCESS; assert(fight_bind_sets(&f,0,0,0,0,&write,1)==VK_SUCCESS);
    for(unsigned i=1;i<FIGHT_POOLS_PER_FRAME*FIGHT_SETS_PER_POOL;++i)
        assert(fight_bind_sets(&f,0,0,0,0,&write,1)==VK_SUCCESS);
    assert(fight_bind_sets(&f,0,0,0,0,&write,1)==VK_ERROR_OUT_OF_POOL_MEMORY);
    fight_destroy_sets(&f,0); assert(destroys==creates && f.count==0);
    puts("GPU probe, legal memory types, descriptor writes, pool cap and device-loss paths passed.");
    return 0;
}
