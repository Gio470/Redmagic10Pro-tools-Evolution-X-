#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
// Root-only mount namespace and uevent access. No input grabs or touch injection.
#include <jni.h>
#include <sys/mount.h>
#include <sys/socket.h>
#include <linux/netlink.h>
#include <sched.h>
#include <fcntl.h>
#include <unistd.h>
#include <poll.h>
#include <cerrno>
#include <string>

extern "C" JNIEXPORT jint JNICALL Java_com_elitedarkkaiser_redmagic_parts_PartsNative_enterMountNamespace(JNIEnv*, jobject) {
    int fd = open("/proc/1/ns/mnt", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return errno;
    int err = 0;
    if (unshare(CLONE_FS) || setns(fd, CLONE_NEWNS)) err = errno;
    close(fd);
    return err;
}
extern "C" JNIEXPORT jint JNICALL Java_com_elitedarkkaiser_redmagic_parts_PartsNative_bindProfile(JNIEnv* env, jobject, jstring from) {
    const char* path = env->GetStringUTFChars(from, nullptr);
    int result = mount(path, "/vendor/etc/thermal-engine.conf", nullptr, MS_BIND, nullptr) ? errno : 0;
    env->ReleaseStringUTFChars(from, path);
    return result;
}
extern "C" JNIEXPORT jint JNICALL Java_com_elitedarkkaiser_redmagic_parts_PartsNative_unmountProfile(JNIEnv*, jobject) {
    return umount2("/vendor/etc/thermal-engine.conf", MNT_DETACH) ? errno : 0;
}
extern "C" JNIEXPORT jint JNICALL Java_com_elitedarkkaiser_redmagic_parts_PartsNative_openEvents(JNIEnv*, jobject) {
    int fd = socket(AF_NETLINK, SOCK_DGRAM | SOCK_CLOEXEC, NETLINK_KOBJECT_UEVENT);
    if (fd < 0) return -errno;
    sockaddr_nl address{};
    address.nl_family = AF_NETLINK;
    address.nl_groups = 1;
    int size = 256 * 1024;
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));
    if (bind(fd, reinterpret_cast<sockaddr*>(&address), sizeof(address))) {
        int result = -errno; close(fd); return result;
    }
    return fd;
}
extern "C" JNIEXPORT jstring JNICALL Java_com_elitedarkkaiser_redmagic_parts_PartsNative_nextEvent(JNIEnv* env, jobject, jint fd) {
    pollfd watched{fd, POLLIN, 0};
    if (poll(&watched, 1, 1000) <= 0 || !(watched.revents & POLLIN)) return nullptr;
    char buffer[16384];
    ssize_t count = recv(fd, buffer, sizeof(buffer) - 1, 0);
    if (count <= 0) return nullptr;
    for (ssize_t i = 0; i < count; ++i) if (buffer[i] == '\0') buffer[i] = '\n'; else if (static_cast<unsigned char>(buffer[i]) >= 127) buffer[i] = '?';
    buffer[count] = '\0';
    return env->NewStringUTF(buffer);
}
extern "C" JNIEXPORT void JNICALL Java_com_elitedarkkaiser_redmagic_parts_PartsNative_closeEvents(JNIEnv*, jobject, jint fd) { close(fd); }
