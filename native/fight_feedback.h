/* Included by the Vulkan renderer, after end_pass and image allocation. */
VkrTex *fight_feedback_texture(VkrContext *c)
{
    if (c->cur_off >= 0 || !c->color_valid || !begin_cmd(c)) return NULL;
    VkrTex *texture = &c->fight_feedback;
    int first = !texture->view;
    if (first) {
        if (!make_image(c, VK_FORMAT_B8G8R8A8_UNORM,
                        VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT, &texture->img, &texture->mem, &texture->view,
                        c->sw, c->sh)) {
            vkDestroyImageView(c->dev, texture->view, NULL);
            vkDestroyImage(c->dev, texture->img, NULL);
            vkFreeMemory(c->dev, texture->mem, NULL);
            memset(texture, 0, sizeof *texture);
            return NULL;
        }
        texture->w = c->sw;
        texture->h = c->sh;
        fprintf(stderr, "[Fight Night] Current color target sampled at %ux%u\n", c->sw, c->sh);
    }
    end_pass(c);
    /* Reuse the tile resolve when present. On other MSAA configurations the
     * source attachment itself must be resolved into the single-sample texture. */
    VkImage source = c->tile_resolved ? c->resolved : c->color;
    barrier(c, source, VK_IMAGE_ASPECT_COLOR_BIT,
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
            VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_READ_BIT);
    barrier(c, texture->img, VK_IMAGE_ASPECT_COLOR_BIT,
            first ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, first ? 0 : VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
            VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT);
    VkImageSubresourceLayers layer = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    VkExtent3D extent = {c->sw, c->sh, 1};
    if (!c->tile_resolved && c->samples != VK_SAMPLE_COUNT_1_BIT) {
        VkImageResolve region = {.srcSubresource=layer, .dstSubresource=layer, .extent=extent};
        vkCmdResolveImage(c->cmd, source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                          texture->img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
    } else {
        VkImageCopy region = {.srcSubresource=layer, .dstSubresource=layer, .extent=extent};
        vkCmdCopyImage(c->cmd, source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                       texture->img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
    }
    barrier(c, texture->img, VK_IMAGE_ASPECT_COLOR_BIT,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
            VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_ACCESS_2_SHADER_SAMPLED_READ_BIT);
    barrier(c, source, VK_IMAGE_ASPECT_COLOR_BIT,
            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_READ_BIT,
            VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
            VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT);
    return texture;
}
