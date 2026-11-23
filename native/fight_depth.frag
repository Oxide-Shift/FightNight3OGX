#version 450
// Depth/stencil-only Xbox draws that have no alpha or screen-edge rejection.
#ifdef W_DEPTH
layout(push_constant) uniform PC { layout(offset = 72) float w_scale; } pc;
#endif
void main() {
#ifdef W_DEPTH
    gl_FragDepth = clamp(pc.w_scale / gl_FragCoord.w, 0.0, 1.0);
#endif
}
