#include <stdio.h>
#include <unistd.h>
#include <string.h>

void entry_point();
int main(int argc, char ** argv) {
  entry_point();
  return 0;
}

void dorp_print(int value) {
  char buffer[12]; // "-2147483648\0"
  snprintf(buffer, sizeof(buffer), "%d", value);
  write(1, buffer, strlen(buffer));
  // uh... and then write a newline
  char newline[] = { '\n' };
  write(1, newline, sizeof(newline));
}
