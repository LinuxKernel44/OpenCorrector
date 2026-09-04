// JNI bridge between com.opencorrector.nativebridge.LlamaNative and llama.cpp.
//
// Design notes:
//  - Only one model/context is ever alive at a time (enforced by the Java side, LlamaEngine),
//    so there is no handle table: the native "handle" is simply a pointer to an EngineContext.
//  - Never logs prompt or generated text content, only sizes/counts/error conditions, per the
//    app's privacy requirement that user text never leaves the device or lands in any log.
//  - Sampling is greedy (no randomness): a corrector must be faithful and deterministic, not
//    creative.

#include <jni.h>

#include <atomic>
#include <string>
#include <vector>

#include <android/log.h>

#include "ggml-backend.h"
#include "ggml.h"
#include "llama.h"

#define LOG_TAG "OpenCorrectorNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct EngineContext {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    llama_sampler * sampler = nullptr;
    std::atomic<bool> cancel_requested{false};
};

std::atomic<bool> g_backend_initialized{false};

void ensure_backend_initialized() {
    bool expected = false;
    if (g_backend_initialized.compare_exchange_strong(expected, true)) {
        llama_log_set([](enum ggml_log_level level, const char * text, void * /*user_data*/) {
            if (level >= GGML_LOG_LEVEL_ERROR) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", text);
            }
        }, nullptr);
        ggml_backend_load_all();
        llama_backend_init();
    }
}

} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_opencorrector_nativebridge_LlamaNative_loadModel(
        JNIEnv * env, jclass, jstring jModelPath, jint nThreads, jint nCtx, jboolean useGpu) {

    ensure_backend_initialized();

    const char * modelPathChars = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelPath(modelPathChars);
    env->ReleaseStringUTFChars(jModelPath, modelPathChars);

    llama_model_params modelParams = llama_model_default_params();
#ifdef OPENCORRECTOR_VULKAN_ENABLED
    modelParams.n_gpu_layers = useGpu ? 999 : 0;
#else
    (void) useGpu;
    modelParams.n_gpu_layers = 0;
#endif

    llama_model * model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (model == nullptr) {
        LOGE("Failed to load model (path length=%zu)", modelPath.size());
        return 0;
    }

    const llama_vocab * vocab = llama_model_get_vocab(model);

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(nCtx);
    ctxParams.n_batch = static_cast<uint32_t>(nCtx);
    ctxParams.n_threads = nThreads;
    ctxParams.n_threads_batch = nThreads;

    llama_context * ctx = llama_init_from_model(model, ctxParams);
    if (ctx == nullptr) {
        LOGE("Failed to create llama_context");
        llama_model_free(model);
        return 0;
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    auto * engineCtx = new EngineContext();
    engineCtx->model = model;
    engineCtx->ctx = ctx;
    engineCtx->vocab = vocab;
    engineCtx->sampler = sampler;

    LOGI("Model loaded: threads=%d ctx=%d gpu=%d", nThreads, nCtx, (int) useGpu);
    return reinterpret_cast<jlong>(engineCtx);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_opencorrector_nativebridge_LlamaNative_tokenCount(
        JNIEnv * env, jclass, jlong handle, jstring jText) {

    auto * engineCtx = reinterpret_cast<EngineContext *>(handle);
    if (engineCtx == nullptr || jText == nullptr) {
        return 0;
    }

    const char * textChars = env->GetStringUTFChars(jText, nullptr);
    jsize textLen = env->GetStringUTFLength(jText);

    int32_t needed = -llama_tokenize(engineCtx->vocab, textChars, textLen, nullptr, 0, true, true);

    env->ReleaseStringUTFChars(jText, textChars);
    return needed > 0 ? needed : 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_opencorrector_nativebridge_LlamaNative_generate(
        JNIEnv * env, jclass, jlong handle, jstring jPrompt, jstring jStopSequence,
        jint maxTokens, jobject callback) {

    auto * engineCtx = reinterpret_cast<EngineContext *>(handle);
    if (engineCtx == nullptr) {
        return nullptr;
    }
    engineCtx->cancel_requested.store(false);

    const char * promptChars = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(promptChars, env->GetStringUTFLength(jPrompt));
    env->ReleaseStringUTFChars(jPrompt, promptChars);

    std::string stopSequence;
    if (jStopSequence != nullptr) {
        const char * stopChars = env->GetStringUTFChars(jStopSequence, nullptr);
        stopSequence.assign(stopChars, env->GetStringUTFLength(jStopSequence));
        env->ReleaseStringUTFChars(jStopSequence, stopChars);
    }

    // Every call is an independent correction request (a different mode or chunk), never a
    // continuation of the previous one: reset the KV cache so nothing leaks between them and
    // context usage never accumulates across the 3 modes x N chunks of one session.
    llama_memory_clear(llama_get_memory(engineCtx->ctx), true);

    int32_t nPromptTokens = -llama_tokenize(
            engineCtx->vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    if (nPromptTokens <= 0) {
        LOGE("Failed to count prompt tokens");
        return env->NewStringUTF("");
    }

    std::vector<llama_token> promptTokens(nPromptTokens);
    if (llama_tokenize(engineCtx->vocab, prompt.c_str(), (int32_t) prompt.size(),
                        promptTokens.data(), (int32_t) promptTokens.size(), true, true) < 0) {
        LOGE("Prompt tokenization failed");
        return env->NewStringUTF("");
    }

    int nCtx = (int) llama_n_ctx(engineCtx->ctx);
    if ((int) promptTokens.size() >= nCtx) {
        LOGE("Prompt too long for context window (%d >= %d)", (int) promptTokens.size(), nCtx);
        return env->NewStringUTF("");
    }

    jclass callbackClass = callback != nullptr ? env->GetObjectClass(callback) : nullptr;
    jmethodID onTokenMethod = callbackClass != nullptr
            ? env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V")
            : nullptr;

    std::string response;
    response.reserve(512);

    llama_batch batch = llama_batch_get_one(promptTokens.data(), (int32_t) promptTokens.size());
    int generatedTokens = 0;

    while (true) {
        if (engineCtx->cancel_requested.load()) {
            break;
        }

        int nCtxUsed = (int) llama_memory_seq_pos_max(llama_get_memory(engineCtx->ctx), 0) + 1;
        if (nCtxUsed + batch.n_tokens > nCtx) {
            LOGE("Context size exceeded during generation");
            break;
        }

        if (llama_decode(engineCtx->ctx, batch) != 0) {
            LOGE("llama_decode failed");
            break;
        }

        llama_token newTokenId = llama_sampler_sample(engineCtx->sampler, engineCtx->ctx, -1);

        if (llama_vocab_is_eog(engineCtx->vocab, newTokenId)) {
            break;
        }

        char pieceBuf[256];
        int n = llama_token_to_piece(engineCtx->vocab, newTokenId, pieceBuf, sizeof(pieceBuf), 0, true);
        if (n < 0) {
            LOGE("token_to_piece failed");
            break;
        }

        std::string piece(pieceBuf, n);
        response += piece;
        generatedTokens++;

        if (onTokenMethod != nullptr) {
            jstring jPiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jPiece);
            env->DeleteLocalRef(jPiece);
        }

        if (!stopSequence.empty() && response.size() >= stopSequence.size() &&
            response.compare(response.size() - stopSequence.size(), stopSequence.size(), stopSequence) == 0) {
            response.erase(response.size() - stopSequence.size());
            break;
        }

        if (generatedTokens >= maxTokens) {
            break;
        }

        batch = llama_batch_get_one(&newTokenId, 1);
    }

    return env->NewStringUTF(response.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_opencorrector_nativebridge_LlamaNative_cancel(JNIEnv *, jclass, jlong handle) {
    auto * engineCtx = reinterpret_cast<EngineContext *>(handle);
    if (engineCtx != nullptr) {
        engineCtx->cancel_requested.store(true);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_opencorrector_nativebridge_LlamaNative_unload(JNIEnv *, jclass, jlong handle) {
    auto * engineCtx = reinterpret_cast<EngineContext *>(handle);
    if (engineCtx == nullptr) {
        return;
    }
    if (engineCtx->sampler != nullptr) {
        llama_sampler_free(engineCtx->sampler);
    }
    if (engineCtx->ctx != nullptr) {
        llama_free(engineCtx->ctx);
    }
    if (engineCtx->model != nullptr) {
        llama_model_free(engineCtx->model);
    }
    delete engineCtx;
    LOGI("Model unloaded");
}
