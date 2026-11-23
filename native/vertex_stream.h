/* Gather the active vertex streams once per draw. Float arrays account for most
 * fighter inputs. Keep the Xbox decoder for inline payloads and packed types. */
static void fight_pack_streams(const Nv2aVshProgram *program, const uint16_t *indices,
                               uint32_t count, float *out)
{
    size_t step = (size_t)program->nin * 4;
    for (unsigned a = 0; a < NV2A_VSH_INPUTS; ++a) {
        if (program->in_slot[a] == 0xff) continue;
        const VertexAttr *attr = &s_gpu.attr[a];
        float *dst = out + 4 * program->in_slot[a];
        if (!attr->size) {
            for (unsigned j = 0; j < count; ++j, dst += step)
                memcpy(dst, s_gpu.imm_attr[a], 16);
        } else if (!s_gpu.inline_active && attr->type == 2 && attr->size <= 4 &&
                   attr->offset && attr->stride) {
            const uint8_t *base = XBOX_HOST(attr->offset);
            for (unsigned j = 0; j < count; ++j, dst += step) {
                const uint8_t *p = base + (size_t)indices[j] * attr->stride;
                dst[1] = 0; dst[2] = 0; dst[3] = 1;
                switch (attr->size) {
                case 1: memcpy(dst, p, 4); break;
                case 2: memcpy(dst, p, 8); break;
                case 3: memcpy(dst, p, 12); break;
                case 4: memcpy(dst, p, 16); break;
                }
            }
        } else {
            for (unsigned j = 0; j < count; ++j, dst += step)
                fetch_attr(attr, indices[j], dst);
        }
    }
}
