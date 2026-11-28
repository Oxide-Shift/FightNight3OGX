/* Resolve Vulkan 1.3 device commands at device creation. Android API 30's
 * loader exports only earlier core entry points, even with a newer GPU driver.
 * Alpha 84: on a Vulkan 1.1 driver each command comes from its extension or from the emulation in
 * fight_vk_compat.h. Also included in Windows renderer test builds (FNR3_VULKAN11=1 forces the 1.1 path). */
#include <vulkan/vulkan.h>
#include <string.h>
#include <stdlib.h>
#include "fight_vk_compat.h"
static int fight_gpu_extensions;
#define DEVICE_COMMANDS(X) \
    X(CmdBeginRendering) X(CmdEndRendering) X(CmdPipelineBarrier2) \
    X(CmdSetDepthTestEnable) X(CmdSetDepthWriteEnable) X(CmdSetDepthCompareOp) \
    X(CmdSetStencilTestEnable) X(CmdSetStencilOp) X(CmdWriteTimestamp2) X(CmdSetCullMode) X(CmdSetFrontFace)
#define DECLARE(name) static PFN_vk##name fnr##name;
DEVICE_COMMANDS(DECLARE)
#undef DECLARE
static PFN_vkCmdPushDescriptorSetKHR fnrCmdPushDescriptorSet;

/* Core name on 1.3 devices; the extension's name (suffix) or the emulation on older ones. */
static PFN_vkVoidFunction fight_device_proc(VkDevice dev, const char *core, const char *suffix, int native,
                                            PFN_vkVoidFunction emulation) {
    if (fight_compat && !native) return emulation;
    if (!fight_compat) return vkGetDeviceProcAddr(dev, core);
    char name[96];
    snprintf(name, sizeof name, "%s%s", core, suffix);
    return vkGetDeviceProcAddr(dev, name);
}

static VkResult create_fight_device(VkPhysicalDevice physical,const VkDeviceCreateInfo *info,
                                    const VkAllocationCallbacks *allocator,VkDevice *out) {
    static FightCompatDevice compat;      /* extension names and feature structs live past this call */
    VkDeviceCreateInfo base=*info;
    fight_compat_device(physical,&base,&compat);
    VkDeviceCreateInfo optional=base;
    const char *names[28];
    int ahb=0,foreign=0;
    uint32_t count=0;
    if(!getenv("FNR3_LEGACY_SCANOUT") && base.enabledExtensionCount+2<=28 &&
       vkEnumerateDeviceExtensionProperties(physical,NULL,&count,NULL)==VK_SUCCESS && count<=1024){
        VkExtensionProperties *props=calloc(count,sizeof *props);
        if(props && vkEnumerateDeviceExtensionProperties(physical,NULL,&count,props)==VK_SUCCESS){
            for(uint32_t i=0;i<count;i++){
                ahb |= strcmp(props[i].extensionName,"VK_ANDROID_external_memory_android_hardware_buffer")==0;
                foreign |= strcmp(props[i].extensionName,"VK_EXT_queue_family_foreign")==0;
            }
        }
        free(props);
    }
    fight_gpu_extensions=0;
    if(ahb && foreign){
        for(uint32_t i=0;i<base.enabledExtensionCount;i++)names[i]=base.ppEnabledExtensionNames[i];
        names[optional.enabledExtensionCount++]="VK_ANDROID_external_memory_android_hardware_buffer";
        names[optional.enabledExtensionCount++]="VK_EXT_queue_family_foreign";
        optional.ppEnabledExtensionNames=names;
        fight_gpu_extensions=1;
    }
    VkResult result=vkCreateDevice(physical,&optional,allocator,out);
    if(result!=VK_SUCCESS && fight_gpu_extensions){
        fight_gpu_extensions=0;result=vkCreateDevice(physical,&base,allocator,out);
    }
    if(result!=VK_SUCCESS)return result;
    fight_compat_dev=*out;
#define LOAD(name,suffix,native,emulation) \
    fnr##name=(PFN_vk##name)fight_device_proc(*out,"vk" #name,suffix,native,(PFN_vkVoidFunction)(emulation)); \
    if(!fnr##name)goto unsupported;
    LOAD(CmdBeginRendering,"KHR",fight_native_dyn,fight_begin_rendering_rp)
    LOAD(CmdEndRendering,"KHR",fight_native_dyn,fight_end_rendering_rp)
    LOAD(CmdPipelineBarrier2,"KHR",fight_native_sync2,fight_barrier2_v1)
    LOAD(CmdSetDepthTestEnable,"EXT",fight_native_eds,fight_set_depth_test)
    LOAD(CmdSetDepthWriteEnable,"EXT",fight_native_eds,fight_set_depth_write)
    LOAD(CmdSetDepthCompareOp,"EXT",fight_native_eds,fight_set_depth_op)
    LOAD(CmdSetStencilTestEnable,"EXT",fight_native_eds,fight_set_stencil_test)
    LOAD(CmdSetStencilOp,"EXT",fight_native_eds,fight_set_stencil_op)
    LOAD(CmdSetCullMode,"EXT",fight_native_eds,fight_set_cull)
    LOAD(CmdSetFrontFace,"EXT",fight_native_eds,fight_set_front)
#undef LOAD
    /* Profiling only: never a reason to refuse the device. */
    fnrCmdWriteTimestamp2=(PFN_vkCmdWriteTimestamp2)fight_device_proc(*out,"vkCmdWriteTimestamp2","KHR",fight_native_sync2,
                                                                       (PFN_vkVoidFunction)fight_timestamp2_v1);
    if(!fnrCmdWriteTimestamp2)fnrCmdWriteTimestamp2=fight_timestamp2_v1;
    fnrCmdPushDescriptorSet=(PFN_vkCmdPushDescriptorSetKHR)vkGetDeviceProcAddr(*out,fight_compat?"vkCmdPushDescriptorSetKHR":"vkCmdPushDescriptorSet");
    return VK_SUCCESS;
unsupported:
    fprintf(stderr,"FNR3/1 GPU_COMPAT missing device command\n");
    vkDestroyDevice(*out,allocator);*out=VK_NULL_HANDLE;fight_compat_dev=VK_NULL_HANDLE;return VK_ERROR_FEATURE_NOT_PRESENT;
}
#define vkCreateInstance fight_create_instance
#define vkCreateDevice create_fight_device
#define vkCmdBeginRendering fnrCmdBeginRendering
#define vkCmdEndRendering fnrCmdEndRendering
#define vkCmdPipelineBarrier2 fnrCmdPipelineBarrier2
#define vkCmdSetDepthTestEnable fnrCmdSetDepthTestEnable
#define vkCmdSetDepthWriteEnable fnrCmdSetDepthWriteEnable
#define vkCmdSetDepthCompareOp fnrCmdSetDepthCompareOp
#define vkCmdSetStencilTestEnable fnrCmdSetStencilTestEnable
#define vkCmdSetStencilOp fnrCmdSetStencilOp
#define vkCmdWriteTimestamp2 fnrCmdWriteTimestamp2
#define vkCmdSetCullMode fnrCmdSetCullMode
#define vkCmdSetFrontFace fnrCmdSetFrontFace
/* Vulkan 1.1 emulation hooks: pass straight through on 1.3 devices. */
#define vkCreateImage fight_create_image
#define vkDestroyImage fight_destroy_image
#define vkCreateImageView fight_create_view
#define vkDestroyImageView fight_destroy_view
#define vkCreateGraphicsPipelines fight_create_pipelines
#define vkDestroyPipeline fight_destroy_pipeline
#define vkDestroyShaderModule fight_destroy_module
#define vkCmdBindPipeline fight_bind_pipeline
#define vkCmdDraw fight_draw
#define vkCmdDrawIndexed fight_draw_indexed
#ifndef __ANDROID__
#define vkCmdPushDescriptorSet fnrCmdPushDescriptorSet
#endif
