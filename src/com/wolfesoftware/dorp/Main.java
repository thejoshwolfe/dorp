package com.wolfesoftware.dorp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.wolfesoftware.dorp.Evaluator.ExecutionOptions;
import com.wolfesoftware.dorp.Parser.SyntaxNode;

public class Main
{
    public static void main(String[] args) throws IOException
    {
        String contents = readFile(new File(args[0]));
        ExecutionOptions options = new ExecutionOptions();
        execute(contents, options);
    }

    public static void execute(String contents, ExecutionOptions options)
    {
        List<Token> tokens = new Tokenizer(contents).tokenize();
        SyntaxNode rootNode = new Parser(tokens).parse();
        new Evaluator(rootNode, options).evaluate();
    }

    public static String readFile(File file) throws IOException
    {
        try (FileInputStream stream = new FileInputStream(file)) {
            StringBuilder result = new StringBuilder();
            byte[] buffer = new byte[0x1000];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                result.append(new String(buffer, 0, count));
            }
            return result.toString();
        }
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
}
