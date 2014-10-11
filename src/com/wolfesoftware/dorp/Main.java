package com.wolfesoftware.dorp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import com.wolfesoftware.dorp.Parser.SyntaxNode;

public class Main
{
    public static void main(String[] args) throws IOException
    {
        String contents = readFile(new File(args[0]));
        List<Token> tokens = new Tokenizer(contents).tokenize();
        SyntaxNode rootNode = new Parser(tokens).parse();
        System.out.println(rootNode);
    }

    private static String readFile(File file) throws IOException
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
}
