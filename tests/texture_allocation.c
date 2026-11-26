/* Compile with the Vulkan SDK include directory. No device or driver is required. */
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <assert.h>
#include <stdio.h>
#include <string.h>

typedef struct { VkDevice dev; } VkrContext;
typedef struct { VkImage img; VkDeviceMemory mem; VkImageView view; } VkrTex;
enum failure { NONE, IMAGE, MEMORY_TYPE, ALLOCATION, BIND, VIEW };
static enum failure failure;
static unsigned destroyed_images, freed_memory, bind_calls, view_calls;
static int has_device_local;

static int find_mem(VkrContext *c, uint32_t bits, VkMemoryPropertyFlags want) {
    (void)c;
    assert(bits == 4);
    return failure == MEMORY_TYPE || (want && !has_device_local) ? -1 : 2;
}
static VkResult vkCreateImage(VkDevice d, const VkImageCreateInfo *info,
                              const VkAllocationCallbacks *a, VkImage *out) {
    (void)d; (void)a; assert(info->mipLevels == 3);
    if (failure == IMAGE) return VK_ERROR_OUT_OF_DEVICE_MEMORY;
    *out = (VkImage)(uintptr_t)1;
    return VK_SUCCESS;
}
static void vkGetImageMemoryRequirements(VkDevice d, VkImage image, VkMemoryRequirements *q) {
    (void)d; assert(image); q->memoryTypeBits = 4; q->size = 4096;
}
static VkResult vkAllocateMemory(VkDevice d, const VkMemoryAllocateInfo *info,
                                 const VkAllocationCallbacks *a, VkDeviceMemory *out) {
    (void)d; (void)a; assert(info->memoryTypeIndex == 2 && info->allocationSize == 4096);
    if (failure == ALLOCATION) return VK_ERROR_OUT_OF_DEVICE_MEMORY;
    *out = (VkDeviceMemory)(uintptr_t)2;
    return VK_SUCCESS;
}
static VkResult vkBindImageMemory(VkDevice d, VkImage image, VkDeviceMemory mem, VkDeviceSize off) {
    (void)d; assert(image && mem && !off); bind_calls++;
    return failure == BIND ? VK_ERROR_OUT_OF_DEVICE_MEMORY : VK_SUCCESS;
}
static VkResult vkCreateImageView(VkDevice d, const VkImageViewCreateInfo *v,
                                  const VkAllocationCallbacks *a, VkImageView *out) {
    (void)d; (void)a; assert(v->image && v->subresourceRange.levelCount == 3); view_calls++;
    if (failure == VIEW) return VK_ERROR_OUT_OF_DEVICE_MEMORY;
    *out = (VkImageView)(uintptr_t)3;
    return VK_SUCCESS;
}
static void vkDestroyImage(VkDevice d, VkImage image, const VkAllocationCallbacks *a) {
    (void)d; (void)a; assert(image); destroyed_images++;
}
static void vkFreeMemory(VkDevice d, VkDeviceMemory mem, const VkAllocationCallbacks *a) {
    (void)d; (void)a; assert(mem && destroyed_images == 1); freed_memory++;
}
#include "../native/fight_texture_resource.h"

int main(void) {
    VkrContext c = {0};
    VkImageCreateInfo info = {.mipLevels = 3};
    for (int local = 0; local <= 1; local++) {
        has_device_local = local;
        for (failure = NONE; failure <= VIEW; failure++) {
            VkrTex texture = {0};
            destroyed_images = freed_memory = bind_calls = view_calls = 0;
            int ready = fight_create_texture_image(&c, &texture, &info);
            assert(ready == (failure == NONE));
            assert(!!texture.img == ready && !!texture.mem == ready && !!texture.view == ready);
            assert(destroyed_images == (failure >= MEMORY_TYPE));
            assert(freed_memory == (failure == BIND || failure == VIEW));
            assert(bind_calls == (ready || failure >= BIND));
            assert(view_calls == (ready || failure == VIEW));
        }
    }
    puts("Texture creation and cleanup passed with local and fallback memory and five injected failures.");
    return 0;
}
