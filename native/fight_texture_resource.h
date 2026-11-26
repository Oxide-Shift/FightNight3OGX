/* Both RGBA and block-compressed uploads must have fully bound, sampleable images
 * before they can enter a descriptor or the texture cache. */
static int fight_create_texture_image(VkrContext *c, VkrTex *texture, const VkImageCreateInfo *info)
{
    VkImage image;
    VkDeviceMemory memory;
    VkImageView view;
    VkMemoryRequirements requirements;
    VkMemoryAllocateInfo allocation = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    VkImageViewCreateInfo sample = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};

    if (vkCreateImage(c->dev, info, NULL, &image) != VK_SUCCESS)
        return 0;
    vkGetImageMemoryRequirements(c->dev, image, &requirements);
    int type = find_mem(c, requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (type < 0)
        type = find_mem(c, requirements.memoryTypeBits, 0);
    allocation.allocationSize = requirements.size;
    allocation.memoryTypeIndex = (uint32_t)type;
    if (type < 0 || vkAllocateMemory(c->dev, &allocation, NULL, &memory) != VK_SUCCESS) {
        vkDestroyImage(c->dev, image, NULL);
        return 0;
    }
    sample.image = image;
    sample.viewType = VK_IMAGE_VIEW_TYPE_2D;
    sample.format = info->format;
    sample.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    sample.subresourceRange.levelCount = info->mipLevels;
    sample.subresourceRange.layerCount = 1;
    if (vkBindImageMemory(c->dev, image, memory, 0) != VK_SUCCESS ||
        vkCreateImageView(c->dev, &sample, NULL, &view) != VK_SUCCESS) {
        vkDestroyImage(c->dev, image, NULL);
        vkFreeMemory(c->dev, memory, NULL);
        return 0;
    }
    texture->img = image;
    texture->mem = memory;
    texture->view = view;
    return 1;
}
