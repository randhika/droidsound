#include <sys/select.h>
#include <errno.h>

#include <unixatomic.h>
#include <android/log.h>

int atomic_close(int fd)
{
  while (1) {
    if (close(fd) < 0) {
      if (errno == EINTR)
	continue;
      return -1;
    }
    break;
  }
  return 0;
}


int atomic_dup2(int oldfd, int newfd)
{
  while (1) {
    if (dup2(oldfd, newfd) < 0) {
      if (errno == EINTR)
	continue;
      return -1;
    }
    break;
  }
  return newfd;
}


ssize_t atomic_read(int fd, const void *buf, size_t count)
{
  char *b = (char *) buf;
  ssize_t bytes_read = 0;
  int ret;
  while (bytes_read < count) {
    ret = read(fd, &b[bytes_read], count - bytes_read);
    if (ret < 0) {
      if (errno == EINTR)
        continue;
      if (errno == EAGAIN) {
	fd_set s;
	FD_ZERO(&s);
	FD_SET(fd, &s);
	if (select(fd + 1, &s, NULL, NULL, NULL) == 0)
	  __android_log_print(ANDROID_LOG_VERBOSE, "UADE", "atomic_read: very strange. infinite select() returned 0. report this!\n");
	continue;
      }
      return -1;
    } else if (ret == 0) {
      return 0;
    }
    bytes_read += ret;
  }
  return bytes_read;
}


ssize_t atomic_write(int fd, const void *buf, size_t count)
{
  char *b = (char *) buf;
  ssize_t bytes_written = 0;
  int ret;
  while (bytes_written < count) {
    ret = write(fd, &b[bytes_written], count - bytes_written);
    if (ret < 0) {
      if (errno == EINTR)
        continue;
      if (errno == EAGAIN) {
	fd_set s;
	FD_ZERO(&s);
	FD_SET(fd, &s);
	if (select(fd + 1, NULL, &s, NULL, NULL) == 0)
	  __android_log_print(ANDROID_LOG_VERBOSE, "UADE", "atomic_write: very strange. infinite select() returned 0. report this!\n");
	continue;
      }
      return -1;
    }
    bytes_written += ret;
  }
  return bytes_written;
}
