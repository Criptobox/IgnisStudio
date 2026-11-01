// Puente JNI entre la app (com.ignis.studio.LlamaNative) y llama.cpp.
// Un solo modelo cargado a la vez. Las llamadas llegan desde un hilo de trabajo de Kotlin,
// nunca desde el hilo de la interfaz.

#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <mutex>
#include <random>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "IgnisLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#define LOGE(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#endif

namespace {

std::mutex g_mu;                      // protege modelo y contexto
llama_model * g_model = nullptr;
llama_context * g_ctx = nullptr;
std::atomic<bool> g_stop{false};
bool g_backend_ready = false;

#ifdef __ANDROID__
void log_cb(ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "%s", text);
}
#else
void log_cb(ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) std::fputs(text, stderr);
}
#endif

std::string jstr(JNIEnv * env, jstring s) {
    if (!s) return {};
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

// Java's NewStringUTF usa "UTF-8 modificado" y rompe emojis: los textos viajan como bytes UTF-8.
std::string jbytes(JNIEnv * env, jbyteArray a) {
    if (!a) return {};
    const jsize n = env->GetArrayLength(a);
    std::string out(static_cast<size_t>(n), '\0');
    env->GetByteArrayRegion(a, 0, n, reinterpret_cast<jbyte *>(out.data()));
    return out;
}

jbyteArray to_jbytes(JNIEnv * env, const std::string & s) {
    jbyteArray a = env->NewByteArray(static_cast<jsize>(s.size()));
    if (a) env->SetByteArrayRegion(a, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return a;
}

// Cuántos bytes del final forman un carácter UTF-8 todavía incompleto (se guardan para el siguiente trozo).
size_t incomplete_utf8_tail(const std::string & s) {
    const size_t n = s.size();
    for (size_t i = 1; i <= std::min<size_t>(4, n); ++i) {
        const unsigned char c = static_cast<unsigned char>(s[n - i]);
        if ((c & 0xC0) == 0x80) continue;          // byte de continuación
        size_t need = 1;
        if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        return need > i ? i : 0;
    }
    return 0;
}

void free_all() {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

std::string token_piece(const llama_vocab * vocab, llama_token t) {
    char buf[256];
    int n = llama_token_to_piece(vocab, t, buf, sizeof(buf), 0, false);
    if (n < 0) {
        std::string big(static_cast<size_t>(-n), '\0');
        n = llama_token_to_piece(vocab, t, big.data(), static_cast<int32_t>(big.size()), 0, false);
        return n > 0 ? big.substr(0, static_cast<size_t>(n)) : std::string();
    }
    return std::string(buf, static_cast<size_t>(n));
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_ignis_studio_LlamaNative_init(JNIEnv * env, jobject, jstring jlibdir) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (g_backend_ready) return;
    llama_log_set(log_cb, nullptr);
    const std::string dir = jstr(env, jlibdir);
    // Carga la variante del motor más rápida que soporte el procesador
    if (!dir.empty()) ggml_backend_load_all_from_path(dir.c_str());
    else ggml_backend_load_all();
    llama_backend_init();
    g_backend_ready = true;
    LOGI("llama.cpp listo (%zu backends)", ggml_backend_reg_count());
}

// Devuelve "" si todo fue bien, o el motivo del error.
JNIEXPORT jstring JNICALL
Java_com_ignis_studio_LlamaNative_load(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_backend_ready) return env->NewStringUTF("El motor no está iniciado");
    free_all();
    const std::string path = jstr(env, jpath);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;                    // solo CPU: estable en todos los teléfonos
    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) return env->NewStringUTF("No se pudo abrir el modelo (¿archivo .gguf dañado o no compatible?)");

    llama_context_params cp = llama_context_default_params();
    const int trained = llama_model_n_ctx_train(g_model);
    cp.n_ctx = static_cast<uint32_t>(trained > 0 ? std::min<int>(n_ctx, trained) : n_ctx);
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) {
        free_all();
        return env->NewStringUTF("No hay memoria suficiente para el contexto del modelo");
    }
    LOGI("Modelo cargado: ctx=%u hilos=%d", llama_n_ctx(g_ctx), n_threads);
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_ignis_studio_LlamaNative_unload(JNIEnv *, jobject) {
    g_stop = true;
    std::lock_guard<std::mutex> lk(g_mu);
    free_all();
}

JNIEXPORT jboolean JNICALL
Java_com_ignis_studio_LlamaNative_isLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_mu);
    return g_model && g_ctx ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_ignis_studio_LlamaNative_stop(JNIEnv *, jobject) { g_stop = true; }

// Descripción corta del modelo cargado ("qwen3 4B Q4_K - Medium").
JNIEXPORT jstring JNICALL
Java_com_ignis_studio_LlamaNative_describe(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_model) return env->NewStringUTF("");
    char buf[128] = {0};
    llama_model_desc(g_model, buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

// Genera la respuesta a una conversación. roles/contents en UTF-8 (system, user, assistant…).
// Llama a callback.onToken(byte[]) con cada trozo de texto; si devuelve false, se detiene.
// Devuelve "" si terminó bien o el motivo del error.
JNIEXPORT jstring JNICALL
Java_com_ignis_studio_LlamaNative_generate(JNIEnv * env, jobject, jobjectArray jroles, jobjectArray jcontents,
                                           jint max_tokens, jfloat temp, jobject callback) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_model || !g_ctx) return env->NewStringUTF("No hay ningún modelo cargado");
    g_stop = false;

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_cls, "onToken", "([B)Z");
    if (!on_token) return env->NewStringUTF("Callback no válido");

    // 1. Conversación -> prompt con la plantilla del propio modelo
    const jsize n_msg = env->GetArrayLength(jroles);
    std::vector<std::string> roles, contents;
    roles.reserve(n_msg); contents.reserve(n_msg);
    size_t total = 0;
    for (jsize i = 0; i < n_msg; ++i) {
        auto r = static_cast<jbyteArray>(env->GetObjectArrayElement(jroles, i));
        auto c = static_cast<jbyteArray>(env->GetObjectArrayElement(jcontents, i));
        roles.push_back(jbytes(env, r));
        contents.push_back(jbytes(env, c));
        total += contents.back().size();
        env->DeleteLocalRef(r); env->DeleteLocalRef(c);
    }
    std::vector<llama_chat_message> msgs;
    for (jsize i = 0; i < n_msg; ++i) msgs.push_back({roles[i].c_str(), contents[i].c_str()});

    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    std::vector<char> buf(total * 2 + 1024);
    int len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
    if (len < 0 && tmpl) {
        // Plantilla que llama.cpp no reconoce: se usa ChatML, la más común
        tmpl = "chatml";
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (len > static_cast<int>(buf.size())) {
        buf.resize(static_cast<size_t>(len) + 1);
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (len < 0) return env->NewStringUTF("La plantilla de chat del modelo no es compatible");
    const std::string prompt(buf.data(), static_cast<size_t>(len));

    // 2. Tokenizar
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int n_tok = -llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (n_tok <= 0) return env->NewStringUTF("No se pudo procesar el texto");
    std::vector<llama_token> tokens(static_cast<size_t>(n_tok));
    if (llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), n_tok, true, true) < 0)
        return env->NewStringUTF("No se pudo procesar el texto");

    const int n_ctx = static_cast<int>(llama_n_ctx(g_ctx));
    if (n_tok >= n_ctx - 16) return env->NewStringUTF("La conversación es demasiado larga para este modelo");
    const int budget = std::min<int>(max_tokens, n_ctx - n_tok - 1);

    // 3. Procesar el prompt por bloques (memoria limpia: cada pedido empieza de cero)
    llama_memory_clear(llama_get_memory(g_ctx), true);
    const int n_batch = static_cast<int>(llama_n_batch(g_ctx));
    for (int i = 0; i < n_tok; i += n_batch) {
        if (g_stop) return env->NewStringUTF("");
        const int n = std::min(n_batch, n_tok - i);
        if (llama_decode(g_ctx, llama_batch_get_one(tokens.data() + i, n)) != 0)
            return env->NewStringUTF("El modelo no pudo procesar el pedido");
    }

    // 4. Muestreo: repetición moderada, top-k/top-p/min-p y temperatura
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    // penalización tras top-k: sobre el vocabulario completo sería lenta
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.08f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(std::random_device{}()));

    std::string pending;                 // bytes de un carácter UTF-8 a medias
    std::string err;
    for (int i = 0; i < budget && !g_stop; ++i) {
        llama_token t = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, t)) break;
        pending += token_piece(vocab, t);
        const size_t tail = incomplete_utf8_tail(pending);
        if (pending.size() > tail) {
            jbyteArray chunk = to_jbytes(env, pending.substr(0, pending.size() - tail));
            const jboolean go = env->CallBooleanMethod(callback, on_token, chunk);
            env->DeleteLocalRef(chunk);
            if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
            if (!go) break;
            pending.erase(0, pending.size() - tail);
        }
        if (llama_decode(g_ctx, llama_batch_get_one(&t, 1)) != 0) { err = "El modelo se detuvo por falta de memoria"; break; }
    }
    llama_sampler_free(smpl);
    return env->NewStringUTF(err.c_str());
}

} // extern "C"
