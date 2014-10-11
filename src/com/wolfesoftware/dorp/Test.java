package com.wolfesoftware.dorp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

import com.wolfesoftware.dorp.Evaluator.ExecutionOptions;

public class Test
{
    public static void main(String[] args) throws IOException
    {
        for (File testFile : Main.sorted(new File("test/").listFiles())) {
            ArrayList<String> expectedLines = new ArrayList<>();
            String contents = Main.readFile(testFile);
            for (String line : contents.split("\n")) {
                String[] parts = line.split("# ", 2);
                if (parts.length < 2)
                    continue;
                String expectedOutput = parts[1];
                expectedLines.add(expectedOutput);
            }
            ExecutionOptions options = new ExecutionOptions();
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            options.stdout = new PrintStream(outputBuffer);
            Main.execute(contents, options);
            String output = new String(outputBuffer.toByteArray());
            ArrayList<String> actualLines = new ArrayList<>(Arrays.asList(output.split("\n")));
            // final blank line doesn't count
            actualLines.remove(actualLines.size() - 1);
            if (expectedLines.equals(actualLines)) {
                System.out.print(".");
            } else {
                System.out.println();
                System.out.println("FAIL: " + testFile.getPath());
                System.out.println("expected lines: " + expectedLines);
                System.out.println("actual lines  : " + actualLines);
            }
        }
    }
}
