package com.wolfesoftware.dorp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tokenizer
{
    private static class Asdf
    {
        public final String tokenType;
        public final String pattern;
        public Asdf(String tokenType, String pattern)
        {
            this.tokenType = tokenType;
            this.pattern = pattern;
        }
    }
    public static final String IDENTIFIER = "IDENTIFIER";
    public static final String NUMBER = "NUMBER";
    public static final String STRING = "STRING";
    public static final String OPERATOR = "OPERATOR";
    public static final String NEWLINE = "NEWLINE";
    public static final String SPACE = "SPACE";
    public static final String COMMENT = "COMMENT";
    public static final String INVALID = "INVALID";
    private static final List<Asdf> asdfs = Arrays.asList( //
            new Asdf(IDENTIFIER, "[A-Za-z_][A-Za-z_0-9]*"), //
            new Asdf(NUMBER, "[0-9]+(?:[.][0-9]+)?(:?[Ee][+-]?[0-9]+)?"), //
            new Asdf(STRING, "\"(:?\\\\[^\n]|[^\"\\\n])*\""), //
            new Asdf(OPERATOR, "\\{|\\}|\\(|\\)|\\+|-|\\*|/|=|;|,"), //
            new Asdf(NEWLINE, "\n"), //
            new Asdf(SPACE, " +"), //
            new Asdf(COMMENT, "#[^\n]*"), //
            new Asdf(INVALID, "."));

    private static final String grandPattern;
    static {
        ArrayList<String> patternGroups = new ArrayList<>();
        for (Asdf asdf : asdfs)
            patternGroups.add("(?<" + asdf.tokenType + ">" + asdf.pattern + ")");
        grandPattern = Main.join(patternGroups, "|");
    }
    private static final Pattern grandRegex = Pattern.compile(grandPattern);

    private final String string;
    public Tokenizer(String string)
    {
        this.string = string;
    }
    public List<Token> tokenize()
    {
        ArrayList<Token> tokens = new ArrayList<>();
        Matcher matcher = grandRegex.matcher(string);
        int tokenStart = 0;
        while (tokenStart < string.length()) {
            matcher.find();
            String tokenText = matcher.group();
            int tokenEnd = matcher.end();
            if (tokenStart + tokenText.length() != tokenEnd)
                throw null;
            String tokenType = getTokenType(matcher);
            tokens.add(new Token(tokenType, tokenText, tokenStart, tokenEnd));
            tokenStart = tokenEnd;
        }
        return tokens;
    }
    private String getTokenType(Matcher matcher)
    {
        for (Asdf asdf : asdfs)
            if (matcher.group(asdf.tokenType) != null)
                return asdf.tokenType;
        throw null;
    }
}
