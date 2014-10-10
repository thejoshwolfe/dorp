package com.wolfesoftware.dorp;

public class Token
{
    public final String type;
    public final String text;
    public final int start;
    public final int end;
    public Token(String type, String text, int start, int end)
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
