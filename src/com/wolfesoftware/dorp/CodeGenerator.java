package com.wolfesoftware.dorp;

import com.wolfesoftware.dorp.SemanticAnalyzer.CompilationUnit;

public class CodeGenerator
{
    private final StringBuilder result = new StringBuilder();
    private final String moduleName = "asdf";
    public CodeGenerator(CompilationUnit compilationUnit)
    {
    }
    public String generate()
    {
        generateFunction(moduleName);
        result.append("@entry_point = alias void()* @").append(moduleName).append("\n");
        return result.toString();
    }
    private void generateFunction(String functionName)
    {
        result.append("define void @").append(functionName).append("() {\n");
        result.append("  ret void\n");
        result.append("}\n");
    }
}
