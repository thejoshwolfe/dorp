package com.wolfesoftware.dorp;

import com.wolfesoftware.dorp.Parser.NodeType;
import com.wolfesoftware.dorp.Parser.SyntaxNode;

public class CodeGenerator
{
    private final SyntaxNode rootNode;
    private final StringBuilder result = new StringBuilder();
    private final String moduleName;
    public CodeGenerator(SyntaxNode rootNode, String moduleName)
    {
        this.rootNode = rootNode;
        this.moduleName = moduleName;
    }
    public String generate()
    {
        generateFunction(moduleName, rootNode);
        return result.toString();
    }
    private void generateFunction(String functionName, SyntaxNode contentsNode)
    {
        result.append("define void @").append(functionName).append("() {\n");
        if (contentsNode.type != NodeType.BLOCK_CONTENTS)
            throw null;
        for (SyntaxNode statementNode : contentsNode.children)
            throw null;
        result.append("  ret void\n");
        result.append("}\n");
    }
}
