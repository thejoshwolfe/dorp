#!/usr/bin/env python

import os
import sys
import subprocess
import re
import shutil

def main():
  compile_cmd = ["java", "-cp", "bin", "com.wolfesoftware.dorp.Main"]
  if not os.path.exists("bin"):
    sys.exit("ERROR: bin/ not found. did you run make?")

  assembler = "llc"
  try: subprocess.Popen([assembler, "-help"], stdout=subprocess.PIPE, stderr=subprocess.PIPE).communicate()
  except OSError: sys.exit("ERROR: llc not found. is llvm installed?")

  linker = "gcc"
  try: subprocess.Popen([linker, "--help"], stdout=subprocess.PIPE, stderr=subprocess.PIPE).communicate()
  except OSError: sys.exit("ERROR: gcc not found. is gcc installed?")

  tmp_dir = "test-tmp"
  if os.path.exists(tmp_dir):
    shutil.rmtree(tmp_dir)
  os.makedirs(tmp_dir)

  runtime_lib = os.path.join(tmp_dir, "main.s")
  subprocess.Popen([assembler, "lib/main.ll", "-o", runtime_lib])

  tests = os.listdir("test")
  if sys.argv[1:]:
    tests = [test for test in tests if test in sys.argv[1:]]
  print("tests: " + " ".join(tests))
  failures = []
  for test in tests:
    test_path = os.path.join("test", test)
    assembly_file = os.path.join(tmp_dir, test + ".ll")
    subprocess.check_call(compile_cmd + [test_path, "-o", assembly_file])

    object_file = os.path.join(tmp_dir, test + ".s")
    subprocess.check_call([assembler, assembly_file, "-o", object_file])

    executable = os.path.join(tmp_dir, test + ".exe")
    subprocess.check_call([linker, runtime_lib, object_file, "-o", executable])

    test_output = subprocess.check_output([executable])
    expected_output = "".join(line + "\n" for line in re.findall("# (.*)", open(test_path).read()))
    if test_output != expected_output:
      failures.append("FAIL: " + test + "\n" +
          "expected: " + repr(expected_output) + "\n" +
          "actual:   " + repr(test_output))
      sys.stdout.write("F")
    else:
      sys.stdout.write(".")
    sys.stdout.flush()
  sys.stdout.write("\n")
  if len(failures) > 0:
    sys.exit("\n".join(failures))

if __name__ == "__main__":
  main()
