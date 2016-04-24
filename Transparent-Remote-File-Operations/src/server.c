#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <string.h>
#include <unistd.h>
#include <err.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/stat.h>
#include <dirent.h>
#include "dirtree.h"

#define MAXMSGLEN 1100000
#define MESSAGE_MAX_LENGTH 1100000
#define DIR_SIZE 100000

int exec_open(char *request, int request_length);

int exec_close(char *request, int length);

ssize_t exec_write(char *request, int request_length);

ssize_t exec_read(char *request, int request_length, char *buf);

off_t exec_lseek(char *request, int request_length);

int exec_xstat(char *request, int request_length, struct stat *stat_buf);

int exec_unlink(char *request, int request_length);

ssize_t exec_getdirentries(char *request, int request_length, char *buf, off_t *basep);

struct dirtreenode *exec_getdirtree(char *request, int request_length);

void dirtree_encode(struct dirtreenode *dt, char *request_body, int *offset);


char *handle_open(char *request, int request_length, size_t *response_length);

char *handle_close(char *request, int request_length, size_t *response_length);

char *handle_write(char *request, int request_length, size_t *response_length);

char *handle_read(char *request, int request_length, size_t *response_length);

char *handle_lseek(char *request, int request_length, size_t *response_length);

char *handle_xstat(char *request, int request_length, size_t *response_length);

char *handle_unlink(char *request, int request_length, size_t *response_length);

char *handle_getdirentries(char *request, int request_length, size_t *response_length);

char *handle_getdirtree(char *request, int request_length, size_t *response_length);

/*
 * main function to start server. It will first set up listening socket and prepare to accept connection,
 * once a connection is set, it will fork() a child process to handle request. The set up part is basically
 * according to the sample server.
 */
int main(int argc, char **argv) {
    char buf[MAXMSGLEN];
    char message[MESSAGE_MAX_LENGTH];

    int offset = 0;
    int request_length = -1;
    int received_length = 0;
    int opcode = -1;

    char *serverport;
    unsigned short port;
    int sockfd, sessfd, rv;

    struct sockaddr_in srv, cli;
    socklen_t sa_size;

    // Get environment variable indicating the port of the server
    serverport = getenv("serverport15440");
    if (serverport) port = (unsigned short) atoi(serverport);
    else port = 15440;

    // Create socket
    sockfd = socket(AF_INET, SOCK_STREAM, 0);    // TCP/IP socket
    if (sockfd < 0) err(1, 0);            // in case of error

    // setup address structure to indicate server port
    memset(&srv, 0, sizeof(srv));            // clear it first
    srv.sin_family = AF_INET;            // IP family
    srv.sin_addr.s_addr = htonl(INADDR_ANY);    // don't care IP address
    srv.sin_port = htons(port);            // server port

    // bind to our port
    rv = bind(sockfd, (struct sockaddr *) &srv, sizeof(struct sockaddr));
    if (rv < 0) err(1, 0);

    // start listening for connections
    rv = listen(sockfd, 5);
    if (rv < 0) err(1, 0);

    // main server loop, handle clients one at a time, quit after 10 clients
    while (1) {

        // wait for next client, get session socket
        sa_size = sizeof(struct sockaddr_in);
        sessfd = accept(sockfd, (struct sockaddr *) &cli, &sa_size);
        if (sessfd < 0) continue;

        pid_t pid;
        if ((pid = fork()) == 0) {
            close(sockfd);
            ssize_t rv_num = 0;
            // get messages and send replies to this client, until it goes away
            while ((rv_num = recv(sessfd, buf, MAXMSGLEN, 0)) > 0) {
                memcpy(message + received_length, buf, rv_num);
                received_length += rv_num;

                if (request_length == -1 && received_length >= 4) {
                    request_length = *(int *) message;
                    offset += (int) sizeof(int);
                }
                if (opcode == -1 && received_length >= 8) {
                    opcode = *(int *) (message + offset);
                    offset += (int) sizeof(int);
                }
                if (received_length != request_length) continue;
                size_t response_length = 0;
                char *response;
                switch (opcode) {
                    case 0 :
                        response = handle_open(message + offset, request_length - offset, &response_length);
                        break;
                    case 1 :
                        response = handle_close(message + offset, request_length - offset, &response_length);
                        break;
                    case 2 :
                        response = handle_write(message + offset, request_length - offset, &response_length);
                        break;
                    case 3 :
                        response = handle_read(message + offset, request_length - offset, &response_length);
                        break;
                    case 4 :
                        response = handle_lseek(message + offset, request_length - offset, &response_length);
                        break;
                    case 5 :
                        response = handle_xstat(message + offset, request_length - offset, &response_length);
                        break;
                    case 6 :
                        response = handle_unlink(message + offset, request_length - offset, &response_length);
                        break;
                    case 7 :
                        response = handle_getdirentries(message + offset, request_length - offset,
                                                        &response_length);
                        break;
                    case 8 :
                        response = handle_getdirtree(message + offset, request_length - offset, &response_length);
                        break;
                }

                int sent = 0;
                while (response_length > 0) {
                    int st = send(sessfd, response + sent, response_length, 0);
                    response_length -= st;
                    sent += st;
                }
                free(response);
                request_length = -1;
                opcode = -1;
                received_length = 0;
                offset = 0;
            }
            exit(0);
        }
        close(sessfd);
    }
}

/*
 * handle open request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_open(char *request, int request_length, size_t *response_length) {
    int open_ret = exec_open(request, request_length);
    *response_length = sizeof(int) * 4;
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 0; // 0 for open
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &open_ret, sizeof(int));
    if (open_ret == -1) {
        offset += sizeof(int);
        memcpy(response + offset, &errno, sizeof(int));
    }
    return response;
}

/*
 * execute open function on server and return it
 */
int exec_open(char *request, int request_length) {
    int offset = 0;
    int flag = *(int *) request;
    offset += sizeof(int);
    mode_t mode = *(mode_t *) (request + sizeof(int));
    offset += sizeof(mode_t);
    char *file = malloc(request_length - offset + 1);
    memcpy(file, request + offset, request_length - offset);
    file[request_length - offset] = 0;
    int ret = open(file, flag, mode);
    free(file);
    return ret;
}

/*
 * handle close request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_close(char *request, int request_length, size_t *response_length) {
    int close_ret = exec_close(request, request_length);
    *response_length = sizeof(int) * 4;
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 1; // 1 for close
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &close_ret, sizeof(int));
    if (close_ret == -1) {
        offset += sizeof(int);
        memcpy(response + offset, &errno, sizeof(int));
    }
    return response;
}

/*
 * execute close function on server and return it
 */
int exec_close(char *request, int request_length) {
    int fd = *(int *) request;
    return close(fd);
}


/*
 * handle write request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_write(char *request, int request_length, size_t *response_length) {
    ssize_t write_ret = exec_write(request, request_length);
    *response_length = sizeof(int) * 3 + sizeof(ssize_t);
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 3; // 2 for write
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &write_ret, sizeof(ssize_t));
    if (write_ret < 0) {
        offset += sizeof(ssize_t);
        memcpy(response + offset, &errno, sizeof(int));
    }
    return response;
}

/*
 * execute write function on server and return it
 */
ssize_t exec_write(char *request, int request_length) {
    int offset = 0;
    int fd = *(int *) (request + offset);
    offset += sizeof(int);
    size_t count = *(size_t *) (request + offset);
    offset += sizeof(size_t);
    char *buf = malloc(count + 1);
    memcpy(buf, request + offset, count);
    buf[count] = 0;
    ssize_t ret = write(fd, buf, count);
    free(buf);
    return ret;
}

/*
 * handle read request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_read(char *request, int request_length, size_t *response_length) {
    size_t read_size = *(size_t *) (request + sizeof(int));
    char *buf = malloc(read_size);
    ssize_t read_ret = exec_read(request, request_length, buf);

    ssize_t size = read_ret >= 0 ? read_ret : 0;
    *response_length = sizeof(int) * 3 + sizeof(ssize_t) + size;
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 3; // 3 for read
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &read_ret, sizeof(ssize_t));
    offset += sizeof(ssize_t);
    if (read_ret < 0) {
        memcpy(response + offset, &errno, sizeof(int));
    }
    offset += sizeof(int);
    if (read_ret > 0) {
        memcpy(response + offset, buf, read_ret);
    }
    free(buf);
    return response;
}

/*
 * execute read function on server and return it
 */
ssize_t exec_read(char *request, int request_length, char *buf) {
    int offset = 0;
    int fd = *(int *) (request + offset);
    offset += sizeof(int);
    size_t count = *(size_t *) (request + offset);
    return read(fd, buf, count);
}


/*
 * handle lseek request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_lseek(char *request, int request_length, size_t *response_length) {
    off_t lseek_ret = exec_lseek(request, request_length);
    *response_length = sizeof(int) * 3 + sizeof(off_t);
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 4; // 1 for close
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &lseek_ret, sizeof(off_t));
    if (lseek_ret < 0) {
        offset += sizeof(off_t);
        memcpy(response + offset, &errno, sizeof(int));
    }
    return response;
}

/*
 * execute lseek function on server and return it
 */
off_t exec_lseek(char *request, int request_length) {
    int offset_decode = 0;
    int fd = *(int *) (request + offset_decode);
    offset_decode += sizeof(int);
    off_t offset = *(off_t *) (request + offset_decode);
    offset_decode += sizeof(off_t);
    int whence = *(int *) (request + offset_decode);
    return lseek(fd, offset, whence);
}

/*
 * handle __xstat request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_xstat(char *request, int request_length, size_t *response_length) {
    struct stat *stat_buf = malloc(sizeof(struct stat));
    int xstat_ret = exec_xstat(request, request_length, stat_buf);
    *response_length = sizeof(int) * 4 + sizeof(struct stat);
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 5; // 5 for __xstat
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &xstat_ret, sizeof(int));
    offset += sizeof(int);
    if (xstat_ret == -1) {
        memcpy(response + offset, &errno, sizeof(int));
    }
    offset += sizeof(int);
    memcpy(response + offset, stat_buf, sizeof(struct stat));
    free(stat_buf);
    return response;
}

/*
 * execute __xstat function on server and return it
 */
int exec_xstat(char *request, int request_length, struct stat *stat_buf) {
    int offset = 0;
    int var = *(int *) (request + offset);
    offset += sizeof(int);
    char *path = malloc(request_length - offset + 1);
    memcpy(path, request + offset, request_length - offset);
    path[request_length - offset] = 0;
    int ret = __xstat(var, path, stat_buf);
    free(path);
    return ret;
}


/*
 * handle unlink request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_unlink(char *request, int request_length, size_t *response_length) {
    int unlink_ret = exec_unlink(request, request_length);
    *response_length = sizeof(int) * 4;
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 6; // 6 for unlink
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &unlink_ret, sizeof(int));
    if (unlink_ret < 0) {
        offset += sizeof(int);
        memcpy(response + offset, &errno, sizeof(int));
    }
    return response;
}

/*
 * execute unlink function on server and return it
 */
int exec_unlink(char *request, int request_length) {
    char *path = malloc(request_length + 1);

    memcpy(path, request, request_length);
    path[request_length] = 0;
    int ret = unlink(path);
    free(path);
    return ret;
}

/*
 * handle getdirentries request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_getdirentries(char *request, int request_length, size_t *response_length) {
    size_t nbytes = *(size_t *) (request + sizeof(int));
    char *buf = malloc(nbytes);
    off_t *basep = (off_t *) (request + sizeof(int) * 3 + sizeof(size_t));
    ssize_t getdirentries_ret = exec_getdirentries(request, request_length, buf, basep);

    ssize_t size = getdirentries_ret >= 0 ? getdirentries_ret : 0;

    *response_length = sizeof(int) * 3 + sizeof(ssize_t) + sizeof(off_t) + size;
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 7; // 7 for getdirentries
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &getdirentries_ret, sizeof(ssize_t));
    offset += sizeof(ssize_t);
    if (getdirentries_ret < 0) {
        memcpy(response + offset, &errno, sizeof(int));
    }
    offset += sizeof(int);
    memcpy(response + offset, basep, sizeof(off_t));
    offset += sizeof(off_t);
    memcpy(response + offset, buf, getdirentries_ret);
    free(buf);
    return response;
}

/*
 * execute getdirentries function on server and return it
 */
ssize_t exec_getdirentries(char *request, int request_length, char *buf, off_t *basep) {
    int offset = 0;
    int fd = *(int *) (request + offset);
    offset += sizeof(int);
    size_t nbytes = *(size_t *) (request + offset);
    return getdirentries(fd, buf, nbytes, basep);
}

/*
 * handle getdirtree request.
 *
 * It will first execute on server ane encode the response, then send response back to client
 */
char *handle_getdirtree(char *request, int request_length, size_t *response_length) {
    struct dirtreenode *getdirtree_ret = exec_getdirtree(request, request_length);
    char dirtree_body[DIR_SIZE];
    int offset_body = 0;
    if (getdirtree_ret != NULL) {
        dirtree_encode(getdirtree_ret, dirtree_body, &offset_body);
    }

    *response_length = sizeof(int) * 4 + offset_body;
    char *response = malloc(*response_length);
    int offset = 0;
    int opcode = 8; // 8 for getdirtree
    memcpy(response + offset, response_length, sizeof(int));
    offset += sizeof(int);
    memcpy(response + offset, &opcode, sizeof(int));
    offset += sizeof(int);
    int success;

    if (getdirtree_ret != NULL) {
        success = 1;
    } else {
        success = 0;
    }
    memcpy(response + offset, &success, sizeof(int));
    offset += sizeof(int);
    if (getdirtree_ret == NULL) {
        memcpy(response + offset, &errno, sizeof(int));
    }
    offset += sizeof(int);
    memcpy(response + offset, dirtree_body, offset_body);
    return response;
}

/*
 * dir tree node decode function
 *
 * Use DFS to decode tree
 */
void dirtree_encode(struct dirtreenode *dt, char *request_body, int *offset) {
    memcpy(request_body + *offset, dt->name, strlen(dt->name));
    *offset += strlen(dt->name);
    request_body[*offset] = 0;
    *offset += 1;
    int number = dt->num_subdirs;
    memcpy(request_body + *offset, &number, sizeof(int));
    *offset += sizeof(int);
    int i;
    for (i = 0; i < number; i++) {
        dirtree_encode(dt->subdirs[i], request_body, offset);
    }
}

/*
 * execute dirtree function on server and return it
 */
struct dirtreenode *exec_getdirtree(char *request, int request_length) {
    char *path = malloc(request_length + 1);
    memcpy(path, request, request_length);
    path[request_length] = 0;
    struct dirtreenode *ret = getdirtree(path);
    free(path);
    return ret;
}