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
        public final TokenType tokenType;
        public final String pattern;
        public TokenTypeAndPattern(TokenType tokenType, String pattern)
        {
            this.tokenType = tokenType;
            this.pattern = pattern;
        }
    }

    public static enum TokenType
    {
        IDENTIFIER, //
        NUMBER, //
        STRING, //
        OPERATOR, //
        NEWLINE, //
        SPACE, //
        COMMENT, //
        INVALID;
    }
    private static final List<TokenTypeAndPattern> tokenTypesAndPatterns = Arrays.asList( //
            new TokenTypeAndPattern(TokenType.IDENTIFIER, "[A-Za-z_][A-Za-z_0-9]*"), //
            new TokenTypeAndPattern(TokenType.NUMBER, "[0-9]+(?:[.][0-9]+)?(:?[Ee][+-]?[0-9]+)?"), //
            new TokenTypeAndPattern(TokenType.STRING, "\"(:?\\\\[^\n]|[^\"\\\n])*\""), //
            new TokenTypeAndPattern(TokenType.OPERATOR, "\\{|\\}|\\(|\\)|\\+|-|\\*|/|=|;|,"), //
            new TokenTypeAndPattern(TokenType.NEWLINE, "\n"), //
            new TokenTypeAndPattern(TokenType.SPACE, " +"), //
            new TokenTypeAndPattern(TokenType.COMMENT, "#[^\n]*"), //
            new TokenTypeAndPattern(TokenType.INVALID, "."));
    private static final String grandPattern;
    static {
        ArrayList<String> patternGroups = new ArrayList<>();
        for (TokenTypeAndPattern tokenTypeAndPattern : tokenTypesAndPatterns)
            patternGroups.add("(?<" + tokenTypeAndPattern.tokenType.name() + ">" + tokenTypeAndPattern.pattern + ")");
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
            TokenType tokenType = getTokenType(matcher);
            if (tokenType == TokenType.IDENTIFIER && keywords.contains(tokenText)) {
                // keywords, like "if", are really considered operators
                tokenType = TokenType.OPERATOR;
            }
            tokens.add(new Token(tokenType, tokenText, tokenStart, tokenEnd));
            tokenStart = tokenEnd;
        }
        return tokens;
    }
    private TokenType getTokenType(Matcher matcher)
    {
        for (TokenTypeAndPattern tokenTypeAndPattern : tokenTypesAndPatterns)
            if (matcher.group(tokenTypeAndPattern.tokenType.name()) != null)
                return tokenTypeAndPattern.tokenType;
        throw null;
    }
}
