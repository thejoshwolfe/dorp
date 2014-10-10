package com.wolfesoftware.dorp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tokenizer
{
    private static class TokenTypeAndPattern
    {
        public final String tokenType;
        public final String pattern;
        public TokenTypeAndPattern(String tokenType, String pattern)
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
    private static final List<TokenTypeAndPattern> tokenTypesAndPatterns = Arrays.asList( //
            new TokenTypeAndPattern(IDENTIFIER, "[A-Za-z_][A-Za-z_0-9]*"), //
            new TokenTypeAndPattern(NUMBER, "[0-9]+(?:[.][0-9]+)?(:?[Ee][+-]?[0-9]+)?"), //
            new TokenTypeAndPattern(STRING, "\"(:?\\\\[^\n]|[^\"\\\n])*\""), //
            new TokenTypeAndPattern(OPERATOR, "\\{|\\}|\\(|\\)|\\+|-|\\*|/|=|;|,"), //
            new TokenTypeAndPattern(NEWLINE, "\n"), //
            new TokenTypeAndPattern(SPACE, " +"), //
            new TokenTypeAndPattern(COMMENT, "#[^\n]*"), //
            new TokenTypeAndPattern(INVALID, "."));
    private static final String grandPattern;
    static {
        ArrayList<String> patternGroups = new ArrayList<>();
        for (TokenTypeAndPattern tokenTypeAndPattern : tokenTypesAndPatterns)
            patternGroups.add("(?<" + tokenTypeAndPattern.tokenType + ">" + tokenTypeAndPattern.pattern + ")");
        grandPattern = Main.join(patternGroups, "|");
    }
    private static final Pattern grandRegex = Pattern.compile(grandPattern);
    public static final HashSet<String> keywords = new HashSet<>(Arrays.asList( //
            "def", //
            "do", //
            "var"));

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
            String tokenType = getTokenType(matcher);
            tokens.add(new Token(tokenType, tokenText, tokenStart, tokenEnd));
            tokenStart = tokenEnd;
        }
        return tokens;
    }
    private String getTokenType(Matcher matcher)
    {
        for (TokenTypeAndPattern tokenTypeAndPattern : tokenTypesAndPatterns)
            if (matcher.group(tokenTypeAndPattern.tokenType) != null)
                return tokenTypeAndPattern.tokenType;
        throw null;
    }
}
