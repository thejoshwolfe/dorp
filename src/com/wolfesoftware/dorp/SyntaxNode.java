package com.wolfesoftware.dorp;

public class SyntaxNode
{
    public static final String DEFINITION = "DEFINITION";

    public final int startTokenIndex;
    public final int endTokenIndex;
    public SyntaxNode[] children;
    public String type = null;
    public SyntaxNode(int startTokenIndex, int endTokenIndex)
    {
        this(startTokenIndex, endTokenIndex, null);
    }
    public SyntaxNode(int startTokenIndex, int endTokenIndex, SyntaxNode[] children)
    {
        this.startTokenIndex = startTokenIndex;
        this.endTokenIndex = endTokenIndex;
        this.children = children;
    }
}
