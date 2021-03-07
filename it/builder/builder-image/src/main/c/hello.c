#if defined(WIN32)
#include <tchar.h>
#endif

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

static const char *message = "Hello World!";

#if defined(WIN32)
int _tmain(int argc, _TCHAR **argv) {
#else
int main(int argc, char **argv) {
#endif
  size_t len = strlen(message);
  size_t written = fwrite(message, sizeof(char), len, stdout);
  return written == len ? EXIT_SUCCESS : EXIT_FAILURE;
}
