#define _GNU_SOURCE

#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <string.h>
#include <unistd.h>
#include <err.h>
#include <errno.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdarg.h>
#include "dirtree.h"

#define RESPONSE_MAX_SIZE 2000000
#define DIR_SIZE 10000
#define BUF_SIZE 10000

void set_network(void);

void remote(int sockfd, char *request, int request_length, char *response, int *response_length);

void open_encode(char *file, int flags, mode_t mode, char *request, size_t request_length);

void close_encode(int fd, char *request, size_t request_length);

void write_encode(int fd, void *buf, size_t count, char *request, size_t request_length);

void read_encode(int fd, void *buf, size_t count, char *request, size_t request_length);

void lseek_encode(int fd, off_t offset, int whence, char *request, size_t request_length);

void xstat_encode(int var, const char *path, struct stat *buf, char *request, int request_length);

void unlink_encode(const char *pathname, char *request, int request_length);

void dirtree_encode(struct dirtreenode *dt, char *request_body, int *offset);

void getdirentries_encode(int fd, char *buf, size_t nbytes, off_t *basep, char *request, int request_length);

void getdirtree_encode(const char *path, char *request, int request_length);

struct dirtreenode *getdirtree_decode(char *response, int *offset);


/* The following line declares a function pointer with the same prototype as the open function. */
int (*orig_open)(const char *pathname, int flags, ...);

int (*orig_close)(int fd);

ssize_t (*orig_read)(int fd, void *buf, size_t count);

ssize_t (*orig_write)(int fd, const void *buf, size_t count);

off_t (*orig_lseek)(int fd, off_t offset, int whence);

int (*orig_xstat)(int ver, const char *path, struct stat *stat_buf);

int (*orig_unlink)(const char *pathname);

ssize_t (*orig_getdirentries)(int fd, char *buf, size_t nbytes, off_t *basep);

struct dirtreenode *(*orig_getdirtree)(const char *path);


/* socket file descripter connect to the server for this function call */
int sock;

/* fd_set to store opened file descriptor in server*/
fd_set fd_pool;

/*
 * This is the replacement for the open function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * Decode (unmarshalling) rules:
 * int size
 * int opcode
 * int return
 * int errno
 */
int open(const char *pathname, int flags, ...) {

    mode_t m = 0;
    if (flags & O_CREAT) {
        va_list a;
        va_start(a, flags);
        m = va_arg(a, mode_t);
        va_end(a);
    }

    size_t request_length = strlen(pathname) + sizeof(int) * 3 + sizeof(mode_t);
    char *request = malloc(request_length); //TODO free it
    open_encode((char *) pathname, flags, m, request, request_length);

    // send request and receive response
    int response_length;
    char response[RESPONSE_MAX_SIZE];
    remote(sock, request, request_length, response, &response_length);

    // decode return value and errno if necessary
    int ret = *(int *) (response + 2 * sizeof(int));
    if (ret == -1) {
        errno = *(int *) (response + 3 * sizeof(int));
        return -1;
    }
    if (ret >= 0) {
        FD_SET(ret + 512, &fd_pool);
    }
    return ret + 512;
}

/*
 * encode open function (marshalling)
 *
 * client -> server
 * int size
 * int opcode
 * int flag
 * mode_t mode
 * string path
 */

void open_encode(char *file, int flags, mode_t mode, char *request, size_t request_length) {
    int opcode = 0; // 0 for open
    int offset = 0;
    memcpy(request, &request_length, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &flags, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &mode, sizeof(mode_t));
    offset += sizeof(mode_t);
    memcpy(request + offset, file, strlen(file));
}

/*
 * This is the replacement for the close function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * Decode (unmarshalling) rules:
 * int size
 * int opcode
 * int fd
 * int errno
 */
int close(int fd) {
    /*
     * fd 0-511 is considered as local fd
     * fd 512-1024 is considered as remote fd
     */
    if (fd < 512) {
        return orig_close(fd);
    }
    /*
     * if a remote file is not in the pool, it is consider as invalid
     */
    if (fd > 1024 || !FD_ISSET(fd, &fd_pool)) {
        errno = EBADF;
        return -1;
    }
    fd -= 512;

    size_t request_length = sizeof(int) * 3;
    char *request = malloc(request_length);  //TODO free it
    close_encode(fd, request, request_length);

    // send request and receive response
    int response_length;
    char response[RESPONSE_MAX_SIZE]; //TODO all function can share one
    remote(sock, request, request_length, response, &response_length);

    // decode return value and errno if necessary
    int ret = *(int *) (response + 2 * sizeof(int));

    if (ret == -1) {
        errno = *(int *) (response + 3 * sizeof(int));
    }
    if (ret >= 0) {
        FD_CLR(ret + 512, &fd_pool);
    }
    return ret;
}

/*
 * encode close function (marshalling)
 *
 * int size
 * int opcode
 * int return value
 * int errno
 */
void close_encode(int fd, char *request, size_t request_length) {
    int opcode = 1; // 1 for close
    int offset = 0;
    memcpy(request, &request_length, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &fd, sizeof(int));
}

/*
 * This is the replacement for the close function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * Decode (unmarshalling) rules:
 * int size
 * int opcode
 * ssize_t return
 * int errno
 * string buf
 */
ssize_t read(int fd, void *buf, size_t count) {
    /*
     * fd 0-511 is considered as local fd
     * fd 512-1024 is considered as remote fd
     */
    if (fd < 512) {
        return orig_read(fd, buf, count);
    }
    /*
     * if a remote file is not in the pool, it is consider as invalid
     */
    if (fd > 1024 || !FD_ISSET(fd, &fd_pool)) {
        errno = EBADF;
        return -1;
    }
    fd -= 512;
    size_t request_length = sizeof(size_t) + sizeof(int) * 3;
    char *request = malloc(request_length);
    read_encode(fd, (void *) buf, count, request, request_length);

    // send request and receive response
    int response_length;
    char response[RESPONSE_MAX_SIZE];
    remote(sock, request, request_length, response, &response_length);

    // decode return value and errno if necessary
    int offset = sizeof(int) * 2;
    ssize_t ret = *(ssize_t *) (response + offset);
    offset += sizeof(ssize_t);
    if (ret < 0) {
        errno = *(int *) (response + offset);
    }
    offset += sizeof(int);
    if (ret > 0) {
        memcpy(buf, response + offset, ret);
    }
    return ret;
}

/*
 * encode read function (marshalling)
 *
 * int size
 * int opcode
 * int fd
 * size_t count
 */
void read_encode(int fd, void *buf, size_t count, char *request, size_t request_length) {
    int opcode = 3; // 3 for read
    int offset = 0;
    memcpy(request, &request_length, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &fd, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &count, sizeof(size_t));
}

/*
 * This is the replacement for the close function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * decode rules
 * int size
 * int opcode
 * ssize_t return
 * int errno
 */
ssize_t write(int fd, const void *buf, size_t count) {
    /*
     * fd 0-511 is considered as local fd
     * fd 512-1024 is considered as remote fd
     */
    if (fd < 512) {
        return orig_write(fd, buf, count);
    }
    /*
     * if a remote file is not in the pool, it is consider as invalid
     */
    if (fd > 1024 || !FD_ISSET(fd, &fd_pool)) {
        errno = EBADF;
        return -1;
    }
    fd -= 512;

    size_t request_length = sizeof(size_t) + sizeof(int) * 3 + count;
    char *request = malloc(request_length);  //TODO free it
    write_encode(fd, (void *) buf, count, request, request_length);

    // send request and receive response
    int response_length;
    char response[RESPONSE_MAX_SIZE];
    remote(sock, request, request_length, response, &response_length);

    // decode return value and errno if necessary
    ssize_t ret = *(ssize_t *) (response + 2 * sizeof(int));
    if (ret < 0) {
        errno = *(int *) (response + 2 * sizeof(int) + sizeof(ssize_t));
    }
    return ret;
}

/*
 * encode write function (marshalling)
 *
 * int size
 * int opcode
 * int fd
 * size_t count
 * string buf - random length
 */
void write_encode(int fd, void *buf, size_t count, char *request, size_t request_length) {
    int opcode = 2; // 2 for write
    int offset = 0;
    memcpy(request, &request_length, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &fd, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &count, sizeof(size_t));
    offset += sizeof(size_t);
    memcpy(request + offset, buf, count);
}

/*
 * This is the replacement for the lseek function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * decode rules
 * int size
 * int opcode - 4
 * off_t return
 * int errno
 */
off_t lseek(int fd, off_t offset, int whence) {
    /*
     * fd 0-511 is considered as local fd
     * fd 512-1024 is considered as remote fd
     */
    if (fd < 512) {
        return lseek(fd, offset, whence);
    }
    /*
     * if a remote file is not in the pool, it is consider as invalid
     */
    if (fd > 1024 || !FD_ISSET(fd, &fd_pool)) {
        errno = EBADF;
        return -1;
    }
    fd -= 512;
    size_t request_length = sizeof(int) * 4 + sizeof(off_t);
    char *request = malloc(request_length);
    lseek_encode(fd, offset, whence, request, request_length);

    int response_length;
    char response[RESPONSE_MAX_SIZE];
    remote(sock, request, request_length, response, &response_length);

    off_t ret = *(off_t *) (response + sizeof(int) * 2);
    if (ret < 0) {
        errno = *(int *) (response + sizeof(int) * 2 + sizeof(off_t));
    }
    return ret;
}

/*
 * encode lseek function (marshalling)
 * int 4 size
 * int 4 opcode - 4
 * int fildes
 * off_t offset
 * int whence
 */
void lseek_encode(int fd, off_t offset, int whence, char *request, size_t request_length) {
    int opcode = 4;
    int encode_offset = 0;
    memcpy(request + encode_offset, &request_length, sizeof(int));
    encode_offset += sizeof(int);
    memcpy(request + encode_offset, &opcode, sizeof(int));
    encode_offset += sizeof(int);
    memcpy(request + encode_offset, &fd, sizeof(int));
    encode_offset += sizeof(int);
    memcpy(request + encode_offset, &offset, sizeof(off_t));
    encode_offset += sizeof(off_t);
    memcpy(request + encode_offset, &whence, sizeof(int));
}

/*
 * This is the replacement for the __xstat function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * Decoding rules:
 * int size
 * int opcode - 4
 * off_t return
 * int errno
 */
int __xstat(int var, const char *path, struct stat *buf) {
    size_t request_length = sizeof(int) * 3 + strlen(path);
    char *request = malloc(request_length);
    xstat_encode(var, path, buf, request, request_length);

    int response_length;
    char response[RESPONSE_MAX_SIZE];
    remote(sock, request, request_length, response, &response_length);

    int ret = *(int *) (response + sizeof(int) * 2);
    if (ret < 0) {
        errno = *(int *) (response + sizeof(int) * 3);
    }
    memcpy(buf, response + sizeof(int) * 4, sizeof(struct stat));
    return ret;
}

/*
 * encode __xstat function (marshalling)
 * int size
 * int opcode
 * int var
 * struct stat
 * string path - random length
 */
void xstat_encode(int var, const char *path, struct stat *buf, char *request, int request_length) {
    int opcode = 5;
    int offset = 0;
    memcpy(request + offset, &request_length, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &var, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, path, strlen(path));
}

/*
 * This is the replacement for the unlink function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * decoding rule:
 *
 * int size
 * int opcode - 5
 * int return
 * int error
 */
int unlink(const char *pathname) {
    size_t request_length = sizeof(int) * 2 + strlen(pathname);
    char *request = malloc(request_length);
    unlink_encode(pathname, request, request_length);

    int response_length;
    char response[RESPONSE_MAX_SIZE];
    remote(sock, request, request_length, response, &response_length);

    int ret = *(int *) (response + sizeof(int) * 2);

    if (ret < 0) {
        errno = *(int *) (response + sizeof(int) * 2 + sizeof(int));
    }
    return ret;
}

/*
 * encode unlink function (marshalling)
 * int size
 * int opcode - 6
 * string pathname - random length
 */
void unlink_encode(const char *pathname, char *request, int request_length) {
    int opcode = 6;
    int offset = 0;
    memcpy(request + offset, &request_length, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, pathname, strlen(pathname));
}

/*
 * This is the replacement for the getdirentries function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * decoding rules:
 * int size
 * int opcode
 * ssize_t return
 * int errno
 */
ssize_t getdirentries(int fd, char *buf, size_t nbytes, off_t *basep) {
    /*
     * fd 0-511 is considered as local fd
     * fd 512-1024 is considered as remote fd
     */
    if (fd < 512) {
        return orig_getdirentries(fd, buf, nbytes, basep);
    }
    /*
     * if a remote file is not in the pool, it is consider as invalid
     */
    if (fd > 1024 || !FD_ISSET(fd, &fd_pool)) {
        errno = EBADF;
        return -1;
    }
    fd -= 512;
    size_t request_length = sizeof(size_t) + sizeof(int) * 3 + sizeof(off_t);
    char *request = malloc(request_length);

    getdirentries_encode(fd, buf, nbytes, basep, request, request_length);

    int response_length;
    char response[RESPONSE_MAX_SIZE];
    remote(sock, request, request_length, response, &response_length);

    ssize_t ret = *(ssize_t *) (response + sizeof(int) * 2);

    if (ret < 0) {
        errno = *(int *) (response + sizeof(int) * 2 + sizeof(ssize_t));
    }
    memcpy(basep, response + sizeof(int) * 3 + sizeof(ssize_t), sizeof(off_t));

    memcpy(buf, response + sizeof(int) * 3 + sizeof(ssize_t) + sizeof(off_t), ret);
    return ret;
}

/*
 * encode getdirentries function (marshalling)
 *
 * size_t size
 * int opcode - 7
 * int fd
 * size_t nbytes
 * off_t basep
 */
void getdirentries_encode(int fd, char *buf, size_t nbytes, off_t *basep, char *request, int request_length) {

    int opcode = 7;
    int offset = 0;
    memcpy(request + offset, &request_length, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &fd, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &nbytes, sizeof(size_t));
    offset += sizeof(size_t);
    memcpy(request + offset, basep, sizeof(off_t));
}

/*
 * This is the replacement for the close function from libc.
 *
 * It will first marshall request from pointer to value, send request and decode request. Return value will be checked.
 * If -1 is returned, errno will set to the same of what it is on server side.
 *
 * decode rule
 *
 * int size
 * int opcode - 8
 * int success - 0 or 1
 * int errno
 * string path
 * int number
 * string path
 * int number
 * ...
 * ...
 */
struct dirtreenode *getdirtree(const char *path) {
    size_t request_length = sizeof(int) * 2 + strlen(path);
    char *request = malloc(request_length);
    getdirtree_encode(path, request, request_length);

    int response_length;
    char response[RESPONSE_MAX_SIZE];
    remote(sock, request, request_length, response, &response_length);

    int success = *(int *) (response + sizeof(int) * 2);
    if (!success) {
        errno = *(int *) (response + sizeof(int) * 3);
        return NULL;
    }

    int offset = 0;
    struct dirtreenode *ret = getdirtree_decode(response + sizeof(int) * 4, &offset);
    return ret;
}

/*
 * encode getdirtree request
 */
void getdirtree_encode(const char *path, char *request, int request_length) {
    int opcode = 8;
    int offset = 0;
    memcpy(request + offset, &request_length, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(request + offset, path, strlen(path));
}

/*
 * decode tree result from server
 * use DFS to restore tree from string recursively
 */
struct dirtreenode *getdirtree_decode(char *response, int *offset) {

    size_t path_length = strlen(response + *offset);
    char *path = malloc(path_length + 1);
    memcpy(path, response + *offset, path_length);
    path[path_length] = 0;
    *offset += path_length + 1;
    int number = *(int *) (response + *offset);
    *offset += sizeof(int);

    struct dirtreenode **subdirs = malloc(number * sizeof(struct dirtreenode *));

    int i;
    for (i = 0; i < number; i++) {
        subdirs[i] = getdirtree_decode(response, offset);
    }
    struct dirtreenode *ret = (struct dirtreenode *) malloc(sizeof(struct dirtreenode));
    ret->name = path;
    ret->num_subdirs = number;
    ret->subdirs = subdirs;
    return ret;
}


/*
 * his function is automatically called when program is started
 * it will set network connection
 * initilize fd_set
 */
void _init(void) {
    memset(&fd_pool, 0, sizeof(fd_pool));
    //printf("%d", FD_SETSIZE);
    orig_open = dlsym(RTLD_NEXT, "open");
    orig_close = dlsym(RTLD_NEXT, "close");
    orig_read = dlsym(RTLD_NEXT, "read");
    orig_write = dlsym(RTLD_NEXT, "write");
    orig_lseek = dlsym(RTLD_NEXT, "lseek");
    orig_xstat = dlsym(RTLD_NEXT, "__xstat");
    orig_unlink = dlsym(RTLD_NEXT, "unlink");
    orig_getdirentries = dlsym(RTLD_NEXT, "getdirentries");
    orig_getdirtree = dlsym(RTLD_NEXT, "getdirtree");
    set_network();
}

/*
 * helper function
 * send request to server and get response
 */
void remote(int sockfd, char *request, int request_length, char *response, int *response_length) {
    int sent = 0;
    while (request_length > 0) {
        int st = send(sockfd, request + sent, request_length, 0);
        request_length -= st;
        sent += st;
    }
    free(request);

    int offset = 0;
    *response_length = -1;
    int rv;
    int received = 0;
    char buf[BUF_SIZE];

    while ((rv = recv(sockfd, buf, BUF_SIZE, 0)) > 0) {
        memcpy(response + received, buf, rv);
        received += rv;
        if (*response_length == -1 && received >= 4) {
            *response_length = *(int *) response;
            offset += sizeof(int);
        }

        if (received >= *response_length) {
            break;
        }
    }
}

/*
 * network initialization
 */
void set_network(void) {
    char *serverip;
    char *serverport;
    unsigned short port;
    int sockfd, rv;
    struct sockaddr_in srv;

    // Get environment variable indicating the ip address of the server
    serverip = getenv("server15440");
    if (!serverip) {
        serverip = "127.0.0.1";
    }

    serverport = getenv("serverport15440");
    if (serverport) port = (unsigned short) atoi(serverport);
    else port = 15440;
    // Create socket
    sockfd = socket(AF_INET, SOCK_STREAM, 0);    // TCP/IP socket
    if (sockfd < 0) err(1, 0);            // in case of error

    // setup address structure to point to server
    memset(&srv, 0, sizeof(srv));            // clear it first
    srv.sin_family = AF_INET;            // IP family
    srv.sin_addr.s_addr = inet_addr(serverip);    // IP address of server
    srv.sin_port = htons(port);            // server port

    // actually connect to the server
    rv = connect(sockfd, (struct sockaddr *) &srv, sizeof(struct sockaddr));
    if (rv < 0) err(1, 0);
    sock = sockfd;
}