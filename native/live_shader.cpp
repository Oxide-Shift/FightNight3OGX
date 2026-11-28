/* Specialize the GPU state actually submitted by the player's game. */
#include <shaderc/shaderc.h>
#include <spirv-tools/optimizer.hpp>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>
#include <condition_variable>
#include <atomic>
#include <memory>
#include <deque>
#include <mutex>
#include <thread>
#include <unistd.h>
#include "live_vertex_template.h"

static size_t copy_code(const void *source, size_t bytes, uint32_t **out) {
    if (!bytes) return 0;
    *out = static_cast<uint32_t *>(malloc(bytes));
    if (!*out) return 0;
    memcpy(*out, source, bytes);
    return bytes;
}

extern "C" __attribute__((visibility("default")))
size_t fight_compile_vertex(const uint32_t *words, unsigned count, unsigned inputs, uint32_t **out) {
    if (!count || count > 136 || inputs > 16) return 0;
    std::string source(vertex_prefix);
    for (unsigned i = 0; i < inputs; ++i) {
        std::string slot = std::to_string(i);
        source += "layout(location="+slot+") in vec4 attr"+slot+";\n";
    }
    source += "vec4 input_at(uint i){switch(i){\n";
    for (unsigned i = 0; i < inputs; ++i) {
        std::string slot = std::to_string(i);
        source += "case "+slot+"u: return attr"+slot+";\n";
    }
    source += "default: return vec4(0.0);}}\n";
    source += vertex_init;
    for (unsigned i = 0; i < count; ++i, words += 8) {
        char literal[192];
        snprintf(literal, sizeof literal,
            "{ const uvec4 i0=uvec4(%uu,%uu,%uu,%uu), i1=uvec4(%uu,%uu,%uu,%uu);\n",
            words[0], words[1], words[2], words[3], words[4], words[5], words[6], words[7]);
        source += literal;
        source += vertex_op;
        source += "}\n";
        if (words[0] & (1u << 26)) break;
    }
    source += vertex_finish;
    shaderc_compiler_t compiler = shaderc_compiler_initialize();
    shaderc_compile_options_t options = shaderc_compile_options_initialize();
    size_t bytes = 0;
    if (compiler && options) {
        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_1);
        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
        auto result = shaderc_compile_into_spv(compiler, source.data(), source.size(), shaderc_vertex_shader,
                                                "disc.vert", "main", options);
        if (result && shaderc_result_get_compilation_status(result) == shaderc_compilation_status_success)
            bytes = copy_code(shaderc_result_get_bytes(result), shaderc_result_get_length(result), out);
        else if (result) fprintf(stderr, "[Fight Night] vertex compiler: %.250s\n", shaderc_result_get_error_message(result));
        shaderc_result_release(result);
    }
    shaderc_compile_options_release(options);
    shaderc_compiler_release(compiler);
    return bytes;
}

extern "C" __attribute__((visibility("default")))
size_t fight_compile_fragment(const uint32_t *source, size_t bytes, const uint32_t *words, uint32_t **out) {
    std::unordered_map<uint32_t, std::vector<uint32_t>> values;
    for (unsigned i = 0; i < 39; ++i) values[i] = {words[i]};
    spvtools::Optimizer opt(SPV_ENV_VULKAN_1_1);
    opt.RegisterPass(spvtools::CreateSetSpecConstantDefaultValuePass(values));
    opt.RegisterPass(spvtools::CreateFreezeSpecConstantValuePass());
    opt.RegisterPass(spvtools::CreateFoldSpecConstantOpAndCompositePass());
    opt.RegisterPerformancePasses();
    opt.RegisterPass(spvtools::CreateAggressiveDCEPass());
    opt.RegisterPass(spvtools::CreateCompactIdsPass());
    std::vector<uint32_t> result;
    if (!opt.Run(source, bytes / 4, &result)) return 0;
    return copy_code(result.data(), result.size() * 4, out);
}

/* App-private cache. Verify the complete key even on a filename collision.
 * A reader sees either the old file or the complete new module. */
template<class Compile>
static size_t cached(unsigned kind, const uint32_t *words, size_t n, uint32_t **out, Compile compile) {
    const char *dir = getenv("FNR3_SHADER_CACHE");
    if (!dir) return compile();
    uint64_t hash = kind;
    for (size_t i = 0; i < n; ++i) hash = (hash ^ words[i]) * 1099511628211ull;
    char name[64];
    snprintf(name, sizeof name, "/%u-%016llx.spv", kind, (unsigned long long)hash);
    const std::string path = std::string(dir) + name;
    FILE *f = fopen(path.c_str(), "rb");
    if (f) {
        uint32_t header[2];
        std::vector<uint32_t> key(n);
        if (fread(header, sizeof header, 1, f) == 1 && header[0] == n && header[1] >= 20 &&
            header[1] <= 1024 * 1024 && !(header[1] & 3) && fread(key.data(), 4, n, f) == n &&
            !memcmp(key.data(), words, n*4)) {
            std::vector<uint32_t> data(header[1]/4);
            if (fread(data.data(), 1, header[1], f) == header[1] && data[0] == 0x07230203 && fgetc(f) == EOF) {
                fclose(f);
                return copy_code(data.data(), header[1], out);
            }
        }
        fclose(f);
    }
    size_t bytes = compile();
    if (!bytes) return 0;
    std::string tmp = path + ".XXXXXX";
    int fd = mkstemp(&tmp[0]);
    if (fd < 0) return bytes;
    f = fdopen(fd, "wb");
    if (f) {
        uint32_t header[] = {(uint32_t)n, (uint32_t)bytes};
        bool written = fwrite(header, sizeof header, 1, f) == 1 && fwrite(words, 4, n, f) == n &&
                       fwrite(*out, 1, bytes, f) == bytes;
        if (fclose(f) == 0 && written) rename(tmp.c_str(), path.c_str());
    } else close(fd);
    unlink(tmp.c_str());
    return bytes;
}

#include "vertex_worker.h"

extern "C" __attribute__((visibility("default")))
size_t fight_fragment_cached(unsigned full, const uint32_t *source, size_t bytes,
                             const uint32_t *states, uint32_t **out) {
    if (!source || !states || !out || bytes < 20 || (bytes & 3) || bytes > 1024 * 1024) return 0;
    // Version the transform and include the exact shader template. The disk reader
    // compares the full key even when two filenames have the same hash.
    std::vector<uint32_t> key = { 1u, (uint32_t)(bytes / 4) };
    key.insert(key.end(), source, source + bytes / 4);
    key.insert(key.end(), states, states + 39);
    static thread_local VertexCompiler worker;
    return cached(32 + full, key.data(), key.size(), out,
                  [&]{return worker.fragment(source, bytes, states, out);});
}


struct VertexJob {
    std::vector<uint32_t> words;
    unsigned inputs;
    uint32_t *code = nullptr;
    size_t bytes = 0;
    std::atomic<bool> ready{false};
    ~VertexJob() { free(code); }
};

/* Continue drawing with the ordinary shader while a new program is being built. */
class VertexQueue {
    std::mutex mutex;
    std::condition_variable changed;
    std::deque<VertexJob*> jobs;
    std::vector<std::unique_ptr<VertexJob>> storage;
    bool stopping = false;
    std::thread worker;
public:
    VertexQueue() : worker([this] {
        VertexCompiler compiler;
        std::unique_lock<std::mutex> lock(mutex);
        for (;;) {
            changed.wait(lock, [this]{return stopping || !jobs.empty();});
            if (stopping) return;
            VertexJob *job = jobs.front();
            jobs.pop_front();
            lock.unlock();
            auto &w = job->words;
            job->bytes = cached(job->inputs, w.data(), w.size(), &job->code, [&]{
                return compiler.compile(w.data(), w.size()/8, job->inputs, &job->code);
            });
            job->ready.store(true, std::memory_order_release);
            lock.lock();
        }
    }) {}
    ~VertexQueue() {
        { std::lock_guard<std::mutex> lock(mutex); stopping = true; }
        changed.notify_one();
        worker.join();
    }
    VertexJob *add(const uint32_t *w, unsigned n, unsigned inputs) {
        std::lock_guard<std::mutex> lock(mutex);
        storage.emplace_back(new VertexJob);
        VertexJob *job = storage.back().get();
        job->words.assign(w, w+n*8);
        job->inputs = inputs;
        jobs.push_back(job);
        changed.notify_one();
        return job;
    }
};
extern "C" __attribute__((visibility("default")))
void *fight_vertex_queue(const uint32_t *words, unsigned n, unsigned inputs) {
    static VertexQueue queue;
    return queue.add(words, n, inputs);
}
extern "C" __attribute__((visibility("default")))
int fight_vertex_ready(void *handle, const uint32_t **code, size_t *bytes) {
    auto job = static_cast<VertexJob *>(handle);
    if (!job->ready.load(std::memory_order_acquire)) return 0;
    *code = job->code;
    *bytes = job->bytes;
    return 1;
}
