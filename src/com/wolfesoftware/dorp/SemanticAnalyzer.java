package com.wolfesoftware.dorp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.wolfesoftware.dorp.Parser.SyntaxNode;

public class SemanticAnalyzer
{
    private final SyntaxNode rootNode;
    private final String moduleName;
    private CompilationUnit compilationUnit;
    public SemanticAnalyzer(SyntaxNode rootNode, String moduleName)
    {
        this.rootNode = rootNode;
        this.moduleName = moduleName;
    }

    public CompilationUnit analyze()
    {
        compilationUnit = new CompilationUnit(createBuiltinContext());
        DorpNamespace namespace = new DorpNamespace(createBuiltinContext());

        FunctionDefinition moduleFunction = new FunctionDefinition(new FunctionSignature(voidType), moduleName, rootNode, namespace);
        compilationUnit.functions.add(moduleFunction);
        for (int i = 0; i < compilationUnit.functions.size(); i++) {
            // the size of the function list can change
            FunctionPrototype function = compilationUnit.functions.get(i);
            if (function instanceof FunctionDefinition)
                analyzeFunctionDefinition((FunctionDefinition)function);
        }
        return compilationUnit;
    }

    private DorpNamespace createBuiltinContext()
    {
        DorpNamespace result = new DorpNamespace(null);
        result.define("print", new FunctionPrototype(new FunctionSignature(voidType, intType), "dorp_print"));
        return result;
    }

    private void analyzeFunctionDefinition(FunctionDefinition functionDefinition)
    {
        for (SyntaxNode statement : functionDefinition.blockContentsNode.children)
            functionDefinition.expressions.add(evaluate(functionDefinition, statement));
    }

    private StaticValue evaluate(FunctionDefinition context, SyntaxNode syntaxNode)
    {
        switch (syntaxNode.type) {
            case CALL: {
                StaticValue function = evaluate(context, syntaxNode.children[0]);
                DorpType functionType = function.getType();
                if (!(functionType instanceof FunctionSignature))
                    throw new RuntimeException();
                FunctionSignature signature = (FunctionSignature)functionType;
                SyntaxNode argumentList = syntaxNode.children[1];
                if (argumentList.children.length != signature.argumentTypes.length)
                    throw new RuntimeException();
                StaticValue[] argumentValues = new StaticValue[argumentList.children.length];
                for (int i = 0; i < argumentList.children.length; i++) {
                    StaticValue argument = evaluate(context, argumentList.children[i]);
                    DorpType expectedType = signature.argumentTypes[i];
                    if (argument.getType() != expectedType)
                        throw new RuntimeException();
                    argumentValues[i] = argument;
                }
                return new FunctionCall(function, signature, argumentValues);
            }
            case IDENTIFIER: {
                String name = syntaxNode.getSimpleText();
                return context.namespace.lookup(name);
            }
            case NUMBER:
                return new ConstantValue(intType, syntaxNode.getSimpleText());
            default:
                throw null;
        }
    }

    public abstract class StaticValue
    {
        public abstract DorpType getType();
    }

    public class ConstantValue extends StaticValue
    {
        public final DorpType type;
        public final String text;
        public ConstantValue(DorpType type, String text)
        {
            this.type = type;
            this.text = text;
        }
        @Override
        public DorpType getType()
        {
            return type;
        }
    }

    public class CompilationUnit
    {
        public final ArrayList<FunctionDefinition> functions = new ArrayList<>();
        private DorpNamespace builtinNamespace;
        public CompilationUnit(DorpNamespace builtinNamespace)
        {
            this.builtinNamespace = builtinNamespace;
        }
        public List<FunctionPrototype> getFunctionPrototypes()
        {
            ArrayList<FunctionPrototype> result = new ArrayList<>();
            for (StaticValue builtinValue : builtinNamespace.names.values())
                if (builtinValue instanceof FunctionPrototype)
                    result.add((FunctionPrototype)builtinValue);
            return result;
        }
    }

    public class FunctionPrototype extends StaticValue
    {
        public final FunctionSignature signature;
        public final String name;
        public FunctionPrototype(FunctionSignature signature, String name)
        {
            this.signature = signature;
            this.name = name;
        }
        @Override
        public DorpType getType()
        {
            return signature;
        }
    }

    public class FunctionDefinition extends FunctionPrototype
    {
        private final SyntaxNode blockContentsNode;
        private final DorpNamespace namespace;
        public final ArrayList<StaticValue> expressions = new ArrayList<>();
        public FunctionDefinition(FunctionSignature signature, String name, SyntaxNode blockContentsNode, DorpNamespace namespace)
        {
            super(signature, name);
            this.blockContentsNode = blockContentsNode;
            this.namespace = namespace;
        }
        @Override
        public String toString()
        {
            return signature.toString() + " " + name;
        }
    }

    public class FunctionSignature extends DorpType
    {
        public final DorpType returnType;
        public final DorpType[] argumentTypes;
        public FunctionSignature(DorpType returnType, DorpType... argumentTypes)
        {
            super(null);
            this.returnType = returnType;
            this.argumentTypes = argumentTypes;
        }
        @Override
        public String toString()
        {
            return returnType.toString() + " (" + Main.join(argumentTypes, ", ") + ")";
        }
    }

    public class FunctionCall extends StaticValue
    {
        public final StaticValue function;
        public final FunctionSignature signature;
        public final StaticValue[] argumentValues;
        public FunctionCall(StaticValue function, FunctionSignature signature, StaticValue[] argumentValues)
        {
            this.function = function;
            this.signature = signature;
            this.argumentValues = argumentValues;
        }
        @Override
        public DorpType getType()
        {
            return signature.returnType;
        }
    }

    private class DorpNamespace
    {
        public final DorpNamespace parent;
        private final HashMap<String, StaticValue> names = new HashMap<>();
        public DorpNamespace(DorpNamespace parent)
        {
            this.parent = parent;
        }
        public StaticValue lookup(String name)
        {
            for (DorpNamespace namespace = this; namespace != null; namespace = namespace.parent) {
                StaticValue value = namespace.names.get(name);
                if (value != null)
                    return value;
            }
            throw new RuntimeException();
        }
        public void define(String name, StaticValue value)
        {
            names.put(name, value);
        }
    }

    private final DorpType voidType = new DorpType("Void");
    private final DorpType intType = new DorpType("Int");

    public class DorpType
    {
        /** just a clue to help debugging */
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
