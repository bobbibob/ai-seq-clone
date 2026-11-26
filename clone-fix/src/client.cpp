#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <cerrno>
#include <cstddef>
#include <cstdio>
#include <cstring>
#include <string>

int main(int argc, char **argv) {
    if (geteuid() != 0) { std::fprintf(stderr, "Root required\n"); return 2; }
    if (argc < 2 || argc > 3) return 2;
    std::string command = argv[1];
    bool parameter = command == "rotate" || command == "delete" || command == "inspect";
    if (parameter != (argc == 3)) return 2;
    if (!parameter && command != "list" && command != "ping" && command != "capabilities" && command != "create") return 2;
    if (parameter) {
        const char *value = argv[2];
        if (std::strcmp(value, "-1") != 0) {
            if (!*value) return 2;
            for (const char *c = value; *c; c++) if (*c < '0' || *c > '9') return 2;
        } else if (command != "rotate") return 2;
        command += " "; command += value;
    }
    if (command.size() > 100) return 2;
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) { std::perror("socket"); return 1; }
    timeval timeout{140, 0};
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    sockaddr_un address{};
    address.sun_family = AF_UNIX;
    const char name[] = "noa_clone_bridge_ids_v1";
    std::memcpy(address.sun_path + 1, name, sizeof(name) - 1);
    if (connect(fd, reinterpret_cast<sockaddr *>(&address), offsetof(sockaddr_un, sun_path) + sizeof(name)) != 0) {
        std::perror("candidate bridge unavailable"); close(fd); return 1;
    }
    size_t sent = 0;
    while (sent < command.size()) {
        ssize_t count = send(fd, command.data() + sent, command.size() - sent, MSG_NOSIGNAL);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { close(fd); return 1; }
        sent += count;
    }
    shutdown(fd, SHUT_WR);
    std::string output;
    char buffer[4096];
    for (;;) {
        ssize_t count = read(fd, buffer, sizeof(buffer));
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) { std::perror("read; outcome uncertain"); close(fd); return 1; }
        if (!count) break;
        output.append(buffer, count);
        if (output.size() > 1024 * 1024) { close(fd); return 1; }
    }
    close(fd);
    std::fwrite(output.data(), 1, output.size(), stdout);
    return output.empty() || output.rfind("ERROR", 0) == 0 ? 1 : 0;
}
