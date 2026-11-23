/* Resolve Vulkan 1.3 device commands at device creation. Android API 30's
 * loader exports only earlier core entry points, even with a newer GPU driver. */
#include <vulkan/vulkan.h>
#define DEVICE_COMMANDS(X) \
    X(CmdBeginRendering) X(CmdEndRendering) X(CmdPipelineBarrier2) \
    X(CmdSetDepthTestEnable) X(CmdSetDepthWriteEnable) X(CmdSetDepthCompareOp) \
    X(CmdSetStencilTestEnable) X(CmdSetStencilOp) X(CmdWriteTimestamp2) X(CmdSetCullMode) X(CmdSetFrontFace)
#define DECLARE(name) static PFN_vk##name fnr##name;
DEVICE_COMMANDS(DECLARE)
#undef DECLARE

static VkResult create_fight_device(VkPhysicalDevice physical,const VkDeviceCreateInfo *info,
                                    const VkAllocationCallbacks *allocator,VkDevice *out) {
    VkResult result=vkCreateDevice(physical,info,allocator,out);
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
