# dorp

Just horp dorpin around making a programming language.

Not cool yet.

## Design Principles

* Statically typed.
  Compile errors are better than runtime errors.
* First class functions and closures.
* Function definitions and function invocations should use the same syntax.
  It should be possible to copypaste from one to the other without changing something about every parameter.
  * Implicit types and function templates.
* Readability is king.
  * There should be only 1 obvious way to do things.
  * Curly braces and semicolons AND newlines and indentation: they have to agree, or it's an error.
  * Naming conventions are enforced by the compiler.

Inspirations:

* Python
* CoffeeScript and Coco
* Haskell
* Java

## Objectives

Self hosting using LLVM.
