/* Resolve Vulkan 1.3 device commands at device creation. Android API 30's
 * loader exports only earlier core entry points, even with a newer GPU driver. */
#include <vulkan/vulkan.h>
#include <string.h>
#include <stdlib.h>
static int fight_gpu_extensions;
#define DEVICE_COMMANDS(X) \
    X(CmdBeginRendering) X(CmdEndRendering) X(CmdPipelineBarrier2) \
    X(CmdSetDepthTestEnable) X(CmdSetDepthWriteEnable) X(CmdSetDepthCompareOp) \
    X(CmdSetStencilTestEnable) X(CmdSetStencilOp) X(CmdWriteTimestamp2) X(CmdSetCullMode) X(CmdSetFrontFace)
#define DECLARE(name) static PFN_vk##name fnr##name;
DEVICE_COMMANDS(DECLARE)
#undef DECLARE

static VkResult create_fight_device(VkPhysicalDevice physical,const VkDeviceCreateInfo *info,
                                    const VkAllocationCallbacks *allocator,VkDevice *out) {
    VkDeviceCreateInfo optional=*info;
    const char *names[16];
    int ahb=0,foreign=0;
    uint32_t count=0;
    if(!getenv("FNR3_LEGACY_SCANOUT") && info->enabledExtensionCount+2<=16 &&
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
        for(uint32_t i=0;i<info->enabledExtensionCount;i++)names[i]=info->ppEnabledExtensionNames[i];
        names[optional.enabledExtensionCount++]="VK_ANDROID_external_memory_android_hardware_buffer";
        names[optional.enabledExtensionCount++]="VK_EXT_queue_family_foreign";
        optional.ppEnabledExtensionNames=names;
        fight_gpu_extensions=1;
    }
    VkResult result=vkCreateDevice(physical,&optional,allocator,out);
    if(result!=VK_SUCCESS && fight_gpu_extensions){
        fight_gpu_extensions=0;result=vkCreateDevice(physical,info,allocator,out);
    }
    if(result!=VK_SUCCESS)return result;
#define LOAD(name) fnr##name=(PFN_vk##name)vkGetDeviceProcAddr(*out,"vk" #name); if(!fnr##name)goto unsupported;
    DEVICE_COMMANDS(LOAD)
#undef LOAD
    return VK_SUCCESS;
unsupported:
    vkDestroyDevice(*out,allocator);*out=VK_NULL_HANDLE;return VK_ERROR_FEATURE_NOT_PRESENT;
}
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
