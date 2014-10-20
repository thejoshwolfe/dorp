package com.wolfesoftware.dorp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.wolfesoftware.dorp.Parser.SyntaxNode;
import com.wolfesoftware.dorp.SemanticAnalyzer.CompilationUnit;

public class Main
{
    public static void main(String[] args) throws IOException
    {
        String sourcePath = null;
        String outputPath = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-") && !args[i].equals("-")) {
                switch (args[i]) {
                    case "-o":
                    case "--output":
                        i++;
                        outputPath = args[i];
                        break;
                    default:
                        throw new RuntimeException("wtf");
                }
            } else {
                if (sourcePath != null)
                    throw new RuntimeException("too many source files");
                sourcePath = args[i];
            }
        }
        if (sourcePath == null)
            throw new RuntimeException("no source files");
        if (outputPath == null)
            outputPath = "-";

        compile(sourcePath, outputPath);
    }

    private static void compile(String sourcePath, String outputPath) throws IOException
    {
        String contents = readPath(sourcePath);
        List<Token> tokens = new Tokenizer(contents).tokenize();
        SyntaxNode rootNode = new Parser(contents, tokens).parse();
        CompilationUnit compilationUnit = new SemanticAnalyzer(rootNode).analyze();
        String outputContents = new CodeGenerator(compilationUnit).generate();
        writePath(outputPath, outputContents);
    }

    private static void writePath(String path, String contents) throws IOException
    {
        try (OutputStream output = openOutputPath(path)) {
            output.write(contents.getBytes());
        }
    }

    private static OutputStream openOutputPath(String path) throws IOException
    {
        if (path.equals("-"))
            return System.out;
        return new FileOutputStream(new File(path));
    }

    public static String readPath(String path) throws IOException
    {
        try (InputStream input = openInputPath(path)) {
            StringBuilder result = new StringBuilder();
            byte[] buffer = new byte[0x1000];
            int count;
            while ((count = input.read(buffer)) != -1)
                result.append(new String(buffer, 0, count));
            return result.toString();
        }
    }
    private static InputStream openInputPath(String path) throws IOException
    {
        if (path.equals("-"))
            return System.in;
        return new FileInputStream(new File(path));
    }

    public static <T> String join(T[] array, String delimiter)
    {
        if (array.length == 0)
            return "";
        StringBuilder result = new StringBuilder();
        result.append(array[0]);
        for (int i = 1; i < array.length; i++)
            result.append(delimiter).append(array[i]);
        return result.toString();
    }
    public static String join(Iterable<?> iterable, String delimiter)
    {
        Iterator<?> iterator = iterable.iterator();
        if (!iterator.hasNext())
            return "";
        StringBuilder result = new StringBuilder();
        result.append(iterator.next());
        while (iterator.hasNext())
            result.append(delimiter).append(iterator.next());
        return result.toString();
    }

    /** why is there no Arrays.reverse()? */
    public static <T> void reverse(T[] array)
    {
        for (int low = 0, hi = array.length - 1; low < hi; low++, hi--) {
            T tmp = array[low];
            array[low] = array[hi];
            array[hi] = tmp;
        }
    }

    public static <T extends Comparable<T>> List<T> sorted(List<T> list)
    {
        ArrayList<T> copy = new ArrayList<>(list);
        Collections.sort(copy);
        return copy;
    }
    public static <T extends Comparable<T>> List<T> sorted(T[] array)
    {
        return sorted(Arrays.asList(array));
    }

    public static <T> T nullCheck(T value)
    {
        if (value == null)
            throw new NullPointerException();
        return value;
    }
    public static <T> T[] nullCheckArrayElemnts(T[] value)
    {
        for (T element : value)
            nullCheck(element);
        return value;
    }
}
