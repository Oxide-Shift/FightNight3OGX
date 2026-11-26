/* Include from patched Android vkr.c. Caller owns the VkInstance. */
#pragma once
#include <vulkan/vulkan.h>
#include <stdlib.h>
#include <string.h>

typedef enum {
    FIGHT_GPU_OK, FIGHT_GPU_API, FIGHT_GPU_FEATURES, FIGHT_GPU_PUSH,
    FIGHT_GPU_QUEUE, FIGHT_GPU_ENUM, FIGHT_GPU_HOST_OOM
} FightGpuCheck;

typedef struct {
    VkPhysicalDevice physical;
    VkPhysicalDeviceProperties props;
    uint32_t queue;
    int push;
} FightGpuDevice;

static FightGpuCheck fight_check_device(VkPhysicalDevice phys, FightGpuDevice *out) {
    VkPhysicalDeviceProperties props;
    vkGetPhysicalDeviceProperties(phys, &props);
    if (props.apiVersion < VK_API_VERSION_1_3) return FIGHT_GPU_API;
    VkPhysicalDeviceVulkan13Features f13 = { .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES };
    VkPhysicalDeviceFeatures2 features = { .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2, .pNext = &f13 };
    vkGetPhysicalDeviceFeatures2(phys, &features);
    if (!f13.dynamicRendering || !f13.synchronization2) return FIGHT_GPU_FEATURES;

    uint32_t count = 0;
    if (vkEnumerateDeviceExtensionProperties(phys, NULL, &count, NULL) != VK_SUCCESS ||
        count > 4096) return FIGHT_GPU_ENUM;
    VkExtensionProperties *extensions = calloc(count ? count : 1, sizeof *extensions);
    if (!extensions) return FIGHT_GPU_HOST_OOM;
    VkResult result = vkEnumerateDeviceExtensionProperties(phys, NULL, &count, extensions);
    int push = 0;
    if (result == VK_SUCCESS)
        for (uint32_t i = 0; i < count; ++i)
            if (!strcmp(extensions[i].extensionName, VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME)) push = 1;
    free(extensions);
    if (result != VK_SUCCESS) return FIGHT_GPU_ENUM;
    VkPhysicalDevicePushDescriptorPropertiesKHR limits = {
        .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PUSH_DESCRIPTOR_PROPERTIES_KHR };
    VkPhysicalDeviceProperties2 p2 = { .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, .pNext = &limits };
    if (push) {
        vkGetPhysicalDeviceProperties2(phys, &p2);
        push = limits.maxPushDescriptors >= 6;
    }
    count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(phys, &count, NULL);
    if (!count || count > 4096) return FIGHT_GPU_QUEUE;
    VkQueueFamilyProperties *queues = calloc(count, sizeof *queues);
    if (!queues) return FIGHT_GPU_HOST_OOM;
    vkGetPhysicalDeviceQueueFamilyProperties(phys, &count, queues);
    FightGpuCheck status = FIGHT_GPU_QUEUE;
    for (uint32_t i = 0; i < count; ++i) {
        if (queues[i].queueCount && (queues[i].queueFlags & VK_QUEUE_GRAPHICS_BIT)) {
            *out = (FightGpuDevice){ .physical = phys, .props = props, .queue = i, .push = push };
            status = FIGHT_GPU_OK;
            break;
        }
    }
    free(queues);
    return status;
}

/* Returns UINT32_MAX when caller must give up this allocation. Useful for ordinary
 * resources. AHB imports instead obey the dedicated import's memory properties. */
static uint32_t fight_memory_type(const VkPhysicalDeviceMemoryProperties *mem,
                                  uint32_t allowed, VkMemoryPropertyFlags required,
                                  VkMemoryPropertyFlags preferred) {
    uint32_t best = UINT32_MAX;
    unsigned bestScore = 0;
    for (uint32_t i = 0; i < mem->memoryTypeCount; ++i) {
        VkMemoryPropertyFlags flags = mem->memoryTypes[i].propertyFlags;
        if (flags & (VK_MEMORY_PROPERTY_PROTECTED_BIT | VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT)) continue;
        if (!(allowed & (1u << i)) || (flags & required) != required) continue;
        unsigned score = (unsigned)__builtin_popcount(flags & preferred);
        if (best == UINT32_MAX || score > bestScore) { best = i; bestScore = score; }
    }
    return best;
}
