
SHELL = bash

.PHONY: build test clean
build:
	@mkdir -p bin
	javac -d bin -cp src src/com/wolfesoftware/dorp/{Main,Test}.java
	@echo -e '#!/bin/bash\njava -cp "$$(dirname "$$0")"/bin com.wolfesoftware.dorp.Main "$$@"' > dorp
	@chmod +x dorp

test: build
	java -cp bin com.wolfesoftware.dorp.Test

clean:
	rm -rf bin/ dorp
