#include <jni.h>
#include <android/log.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <pthread.h>
#include <cstddef>
#include <cstring>
#include <cerrno>
#include <string>
#include "zygisk.hpp"
#include "bridge_dex.h"

static JavaVM *vm = nullptr;
static bool healthy(JNIEnv *env) {
    if (!env->ExceptionCheck()) return true;
    env->ExceptionDescribe(); env->ExceptionClear(); return false;
}
static jclass load(JNIEnv *env) {
    jclass activity = env->FindClass("android/app/ActivityThread");
    if (!healthy(env) || !activity) return nullptr;
    jmethodID current = env->GetStaticMethodID(activity, "currentApplication", "()Landroid/app/Application;");
    if (!healthy(env) || !current) return nullptr;
    jobject app = env->CallStaticObjectMethod(activity, current);
    if (!healthy(env) || !app) return nullptr;
    jclass context = env->FindClass("android/content/Context");
    if (!healthy(env) || !context) return nullptr;
    jmethodID getLoader = env->GetMethodID(context, "getClassLoader", "()Ljava/lang/ClassLoader;");
    if (!healthy(env) || !getLoader) return nullptr;
    jobject parent = env->CallObjectMethod(app, getLoader);
    if (!healthy(env) || !parent) return nullptr;
    jclass bytesClass = env->FindClass("java/nio/ByteBuffer");
    jclass loaderClass = env->FindClass("dalvik/system/InMemoryDexClassLoader");
    if (!healthy(env) || !bytesClass || !loaderClass) return nullptr;
    jmethodID wrap = env->GetStaticMethodID(bytesClass, "wrap", "([B)Ljava/nio/ByteBuffer;");
    jmethodID constructor = env->GetMethodID(loaderClass, "<init>", "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
    jmethodID loadClass = env->GetMethodID(loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (!healthy(env) || !wrap || !constructor || !loadClass) return nullptr;
    jbyteArray bytes = env->NewByteArray(sizeof(bridge_dex));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, sizeof(bridge_dex), reinterpret_cast<const jbyte *>(bridge_dex));
    jobject buffer = env->CallStaticObjectMethod(bytesClass, wrap, bytes);
    if (!healthy(env) || !buffer) return nullptr;
    jobject loader = env->NewObject(loaderClass, constructor, buffer, parent);
    if (!healthy(env) || !loader) return nullptr;
    jstring name = env->NewStringUTF("noa.bridge.Bridge");
    jobject bridge = env->CallObjectMethod(loader, loadClass, name);
    if (!healthy(env) || !bridge) return nullptr;
    return static_cast<jclass>(env->NewGlobalRef(bridge));
}
static bool sendAll(int fd, const std::string &value) {
    size_t offset = 0;
    while (offset < value.size()) {
        ssize_t count = send(fd, value.data() + offset, value.size() - offset, MSG_NOSIGNAL);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return false;
        offset += count;
    }
    return true;
}
static void *serve(void *) {
    JNIEnv *env = nullptr;
    if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    jclass bridge = nullptr;
    for (int i = 0; i < 30 && !bridge; i++) {
        env->PushLocalFrame(32);
        bridge = load(env);
        env->PopLocalFrame(nullptr);
        if (!bridge) sleep(1);
    }
    if (!bridge) { vm->DetachCurrentThread(); return nullptr; }
    jmethodID dispatch = env->GetStaticMethodID(bridge, "dispatch", "(Ljava/lang/String;)Ljava/lang/String;");
    if (!healthy(env) || !dispatch) { vm->DetachCurrentThread(); return nullptr; }
    int listener = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    sockaddr_un address{};
    address.sun_family = AF_UNIX;
    const char name[] = "noa_clone_bridge_ids_v1";
    std::memcpy(address.sun_path + 1, name, sizeof(name) - 1);
    if (listener < 0 || bind(listener, reinterpret_cast<sockaddr *>(&address),
            offsetof(sockaddr_un, sun_path) + sizeof(name)) || listen(listener, 4)) {
        if (listener >= 0) close(listener);
        vm->DetachCurrentThread(); return nullptr;
    }
    for (;;) {
        int client = accept4(listener, nullptr, nullptr, SOCK_CLOEXEC);
        if (client < 0) continue;
        ucred peer{}; socklen_t size = sizeof(peer);
        if (getsockopt(client, SOL_SOCKET, SO_PEERCRED, &peer, &size) || peer.uid != 0) {
            sendAll(client, "ERROR root required\n"); close(client); continue;
        }
        timeval timeout{5, 0};
        setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
        std::string command;
        char buffer[128];
        bool valid = true;
        for (;;) {
            ssize_t count = read(client, buffer, sizeof(buffer));
            if (count < 0 && errno == EINTR) continue;
            if (count < 0) { valid = false; break; }
            if (!count) break;
            command.append(buffer, count);
            if (command.size() > 100) { valid = false; break; }
        }
        if (valid && !command.empty() && command.find('\0') == std::string::npos) {
            env->PushLocalFrame(8);
            jstring input = env->NewStringUTF(command.c_str());
            jstring result = static_cast<jstring>(env->CallStaticObjectMethod(bridge, dispatch, input));
            if (healthy(env) && result) {
                const char *text = env->GetStringUTFChars(result, nullptr);
                if (text) { sendAll(client, std::string(text) + "\n"); env->ReleaseStringUTFChars(result, text); }
            } else sendAll(client, "ERROR JNI dispatch\n");
            env->PopLocalFrame(nullptr);
        } else sendAll(client, "ERROR invalid request\n");
        close(client);
    }
}
class Candidate final : public zygisk::ModuleBase {
    zygisk::Api *api = nullptr;
    JNIEnv *env = nullptr;
    bool target = false;
public:
    void onLoad(zygisk::Api *value, JNIEnv *jni) override { api = value; env = jni; env->GetJavaVM(&vm); }
    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        if (args && args->nice_name) {
            const char *name = env->GetStringUTFChars(args->nice_name, nullptr);
            target = name && std::strcmp(name, "com.polestar.super.clone:x") == 0;
            if (name) env->ReleaseStringUTFChars(args->nice_name, name);
        }
        if (!target) api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }
    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (!target) return;
        pthread_t thread{};
        if (!pthread_create(&thread, nullptr, serve, nullptr)) pthread_detach(thread);
    }
};
REGISTER_ZYGISK_MODULE(Candidate)
