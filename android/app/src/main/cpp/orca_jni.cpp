#include <jni.h>
#include <llama.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

// One local model and inference context per ORCA process. Calls are serialized.
static std::mutex guard;
static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static const llama_vocab *vocab = nullptr;

static void clear_model() {
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }
    vocab = nullptr;
}

static std::string from_java(JNIEnv *env, jstring value) {
    if (!value) return "";
    const char *utf8 = env->GetStringUTFChars(value, nullptr);
    if (!utf8) return "";
    std::string result(utf8);
    env->ReleaseStringUTFChars(value, utf8);
    return result;
}

static jbyteArray as_bytes(JNIEnv *env, const std::string &text) {
    auto result = env->NewByteArray(static_cast<jsize>(text.size()));
    if (!result) return nullptr;
    if (!text.empty()) env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_clawd_mobile_orca_OrcaNative_loadModel(JNIEnv *env, jobject, jstring path, jint context_size, jint threads) {
    std::lock_guard<std::mutex> lock(guard);
    clear_model();
    const std::string filename = from_java(env, path);
    if (filename.empty()) return JNI_FALSE;
    llama_backend_init();
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU, ARM64 Android; no cloud dependencies.
    model = llama_model_load_from_file(filename.c_str(), mp);
    if (!model) return JNI_FALSE;
    auto cp = llama_context_default_params();
    cp.n_ctx = std::max(512, std::min(4096, static_cast<int>(context_size)));
    cp.n_batch = 512;
    cp.n_threads = std::max(1, std::min(8, static_cast<int>(threads)));
    cp.n_threads_batch = cp.n_threads;
    ctx = llama_init_from_model(model, cp);
    if (!ctx) { clear_model(); return JNI_FALSE; }
    vocab = llama_model_get_vocab(model);
    return vocab ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_clawd_mobile_orca_OrcaNative_generate(JNIEnv *env, jobject, jstring system_text, jstring user_text, jint max_tokens) {
    std::lock_guard<std::mutex> lock(guard);
    if (!ctx || !vocab) return env->NewByteArray(0);
    const std::string system = from_java(env, system_text);
    const std::string user = from_java(env, user_text);
    // Explicit Qwen3 ChatML prompt, no thinking tokens needed for short pet commands.
    std::string prompt = "<|im_start|>system\n" + system + "\n/no_think<|im_end|>\n<|im_start|>user\n" +
                         user + "<|im_end|>\n<|im_start|>assistant\n";
    const int max_context = 1800;
    std::vector<llama_token> tokens(max_context);
    int n = llama_tokenize(vocab, prompt.c_str(), static_cast<int>(prompt.size()), tokens.data(), max_context, true, true);
    if (n <= 0 || n >= max_context) return env->NewByteArray(0);
    tokens.resize(n);
    llama_memory_clear(llama_get_memory(ctx), true);
    for (int i = 0; i < n; i += 256) {
        const int count = std::min(256, n - i);
        if (llama_decode(ctx, llama_batch_get_one(tokens.data() + i, count)) != 0) return env->NewByteArray(0);
    }
    llama_sampler *sampler = llama_sampler_init_greedy();
    if (!sampler) return env->NewByteArray(0);
    std::string answer;
    for (int i = 0; i < std::max(1, std::min(320, static_cast<int>(max_tokens))); ++i) {
        const llama_token next = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) break;
        char piece[1024];
        const int len = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, false);
        if (len > 0 && len <= static_cast<int>(sizeof(piece))) answer.append(piece, len);
        llama_token token = next;
        if (llama_decode(ctx, llama_batch_get_one(&token, 1)) != 0) break;
    }
    llama_sampler_free(sampler);
    // Return UTF-8 bytes; JNI NewStringUTF cannot safely represent all model output Unicode.
    return as_bytes(env, answer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_clawd_mobile_orca_OrcaNative_unloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(guard);
    clear_model();
}
