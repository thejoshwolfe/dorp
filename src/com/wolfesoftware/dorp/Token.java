package com.wolfesoftware.dorp;

import com.wolfesoftware.dorp.Tokenizer.TokenType;

public class Token
{
    public final TokenType type;
    public final String text;
    public final int start;
    public final int end;
    public Token(TokenType type, String text, int start, int end)
    {
        this.type = type;
        this.text = text;
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString()
    {
        return text;
    }
}
