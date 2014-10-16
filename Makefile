
SHELL = bash

.PHONY: build clean
build:
	@mkdir -p bin
	javac -g -d bin -cp src src/com/wolfesoftware/dorp/Main.java

clean:
	rm -rf bin/ test-tmp/
