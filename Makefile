
SHELL = bash

.PHONY: build clean
build:
	@mkdir -p bin
	javac -g -d bin -cp src src/com/wolfesoftware/dorp/Main.java
	@echo -e '#!/bin/bash\njava -cp "$$(dirname "$$0")"/bin com.wolfesoftware.dorp.Main "$$@"' > dorp
	@chmod +x dorp

clean:
	rm -rf bin/ dorp
