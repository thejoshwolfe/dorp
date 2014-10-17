package com.wolfesoftware.dorp;

import java.util.ArrayList;

import com.wolfesoftware.dorp.Parser.SyntaxNode;

public class SemanticAnalyzer
{
    private final SyntaxNode rootNode;
    private final String moduleName;
    private final CompilationUnit compilationUnit;
    public SemanticAnalyzer(SyntaxNode rootNode, String moduleName)
    {
        this.rootNode = rootNode;
        this.moduleName = moduleName;
        compilationUnit = new CompilationUnit();
    }

    public CompilationUnit analyze()
    {
        FunctionDefinition moduleFunction = new FunctionDefinition(new FunctionSignature(voidType), moduleName, rootNode);
        compilationUnit.functions.add(moduleFunction);
        for (int i = 0; i < compilationUnit.functions.size(); i++) {
            // the size of the function list can change
            FunctionDefinition functionDefinition = compilationUnit.functions.get(i);
            analyzeFunctionDefinition(functionDefinition);
        }
        return compilationUnit;
    }

    private void analyzeFunctionDefinition(FunctionDefinition functionDefinition)
    {
        for (SyntaxNode statement : functionDefinition.blockContentsNode.children)
            evaluate(functionDefinition, statement);
    }

    private StaticValue evaluate(FunctionDefinition context, SyntaxNode syntaxNode)
    {
        switch (syntaxNode.type) {
            case CALL: {
                StaticValue function = evaluate(context, syntaxNode.children[0]);
                SyntaxNode argumentList = syntaxNode.children[1];
                throw null;
            }
            case IDENTIFIER: {
                String name = syntaxNode.getSimpleText();
                throw null;
            }
            default:
                throw null;
        }
    }

    private class StaticValue
    {
    }

    public class CompilationUnit
    {
        public final ArrayList<FunctionDefinition> functions = new ArrayList<>();
    }

    public class FunctionDefinition
    {
        public final FunctionSignature signature;
        public final String name;
        private final SyntaxNode blockContentsNode;
        public final ArrayList<Instruction> instructions = new ArrayList<>();
        public FunctionDefinition(FunctionSignature signature, String name, SyntaxNode blockContentsNode)
        {
            this.signature = signature;
            this.name = name;
            this.blockContentsNode = blockContentsNode;
        }
        @Override
        public String toString()
        {
            return signature.toString() + " " + name;
        }
    }

    public class Instruction
    {
    }

    public class FunctionSignature
    {
        public final DorpType returnType;
        public final DorpType[] argumentTypes;
        public FunctionSignature(DorpType returnType, DorpType... argumentTypes)
        {
            this.returnType = returnType;
            this.argumentTypes = argumentTypes;
        }
        @Override
        public String toString()
        {
            return returnType.toString() + " (" + Main.join(argumentTypes, ", ") + ")";
        }
    }

    private final DorpType voidType = new DorpType("Void");

    public class DorpType
    {
        public final String name;
        public DorpType(String name)
        {
            this.name = name;
        }
        @Override
        public String toString()
        {
            return name;
        }
    }
}
