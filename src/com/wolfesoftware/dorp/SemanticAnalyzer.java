package com.wolfesoftware.dorp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.wolfesoftware.dorp.Parser.BlockNode;
import com.wolfesoftware.dorp.Parser.NodeType;
import com.wolfesoftware.dorp.Parser.SyntaxNode;

public class SemanticAnalyzer
{
    private final SyntaxNode rootNode;
    private CompilationUnit compilationUnit;
    public SemanticAnalyzer(SyntaxNode rootNode)
    {
        this.rootNode = rootNode;
    }

    public CompilationUnit analyze()
    {
        compilationUnit = new CompilationUnit(createBuiltinContext());
        DorpNamespace namespace = new DorpNamespace(createBuiltinContext());

        FunctionDefinition moduleFunction = new FunctionDefinition(new FunctionSignature(voidType), "entry_point", rootNode, namespace);
        compilationUnit.functions.add(moduleFunction);
        analyzeFunctionDefinition(moduleFunction);

        return compilationUnit;
    }

    private DorpNamespace createBuiltinContext()
    {
        DorpNamespace result = new DorpNamespace(null);
        result.defineConstant("print", new FunctionPrototype(new FunctionSignature(voidType, intType), "dorp_print"));
        result.defineConstant("void", voidValue);
        return result;
    }

    private StaticValue analyzeFunctionDefinition(FunctionDefinition functionDefinition)
    {
        StaticValue lastValue = voidValue;
        for (SyntaxNode statement : functionDefinition.blockContentsNode.children) {
            lastValue = evaluate(functionDefinition, statement);
            functionDefinition.expressions.add(lastValue);
        }
        return lastValue;
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
                for (int i = 0; i < argumentValues.length; i++) {
                    StaticValue argument = evaluate(context, argumentList.children[i]);
                    argumentValues[i] = argument;
                }
                if (signature.returnType != null) {
                    // concrete function
                    for (int i = 0; i < argumentValues.length; i++)
                        if (argumentValues[i].getType() != signature.argumentTypes[i])
                            throw new RuntimeException();
                } else {
                    // instantiate this function template
                    FunctionDefinition functionTemplate = (FunctionDefinition)function;
                    signature = new FunctionSignature(null, new DorpType[argumentValues.length]);
                    for (int i = 0; i < argumentValues.length; i++)
                        signature.argumentTypes[i] = argumentValues[i].getType();
                    FunctionDefinition functionInstance = new FunctionDefinition(signature, generateBlockName(), functionTemplate.blockContentsNode, functionTemplate.namespace);
                    StaticValue returnValue = analyzeFunctionDefinition(functionInstance);
                    // the signature is now complete
                    signature.returnType = returnValue.getType();
                    compilationUnit.functions.add(functionInstance);
                    function = functionInstance;
                }
                return new FunctionCall(function, signature, argumentValues);
            }
            case BLOCK: {
                BlockNode blockNode = (BlockNode)syntaxNode;
                DorpType[] argumentTypes;
                if (blockNode.argumentDeclarations != null)
                    argumentTypes = new DorpType[blockNode.argumentDeclarations.children.length];
                else
                    argumentTypes = new DorpType[0];
                // all we really know is the number of parameters, not any of the types
                FunctionSignature signature = new FunctionSignature(null, argumentTypes);
                SyntaxNode blockContentsNode = blockNode.children[0];
                DorpNamespace namespace = new DorpNamespace(context.namespace);
                return new FunctionDefinition(signature, null, blockContentsNode, namespace);
            }
            case IDENTIFIER: {
                String name = syntaxNode.getSimpleText();
                return context.namespace.lookup(name);
            }
            case VARIABLE_DECLARATION: {
                SyntaxNode nameNode = syntaxNode.children[0].children[0];
                if (nameNode.type != NodeType.IDENTIFIER)
                    throw new RuntimeException();
                String name = nameNode.getSimpleText();
                StaticValue value = evaluate(context, syntaxNode.children[0].children[1]);
                VariableDefinition definition = context.namespace.defineVariable(name, value.getType());
                return new Assignment(definition, value);
            }
            case NUMBER:
                return new ConstantValue(intType, syntaxNode.getSimpleText());
            default:
                throw null;
        }
    }

    private int nextBlockIndex = 0;
    private String generateBlockName()
    {
        return "block" + nextBlockIndex++;
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

    public class Assignment extends StaticValue
    {
        public final VariableDefinition definition;
        public final StaticValue value;
        public Assignment(VariableDefinition definition, StaticValue value)
        {
            this.definition = definition;
            this.value = value;
        }
        @Override
        public DorpType getType()
        {
            return value.getType();
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
            for (VariableDefinition builtinDefinition : builtinNamespace.names.values())
                if (builtinDefinition.constantValue instanceof FunctionPrototype)
                    result.add((FunctionPrototype)builtinDefinition.constantValue);
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
        public List<VariableDefinition> getLocalVariableDefinitions()
        {
            return new ArrayList<>(namespace.names.values());
        }
        @Override
        public String toString()
        {
            return signature.toString() + " " + name;
        }
    }

    public class FunctionSignature extends DorpType
    {
        public DorpType returnType;
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
        private final HashMap<String, VariableDefinition> names = new HashMap<>();
        public DorpNamespace(DorpNamespace parent)
        {
            this.parent = parent;
        }
        public VariableDefinition lookup(String name)
        {
            for (DorpNamespace namespace = this; namespace != null; namespace = namespace.parent) {
                VariableDefinition definition = namespace.names.get(name);
                if (definition != null)
                    return definition;
            }
            throw new RuntimeException();
        }
        public VariableDefinition defineConstant(String name, StaticValue constantValue)
        {
            return define(name, constantValue.getType(), constantValue);
        }
        public VariableDefinition defineVariable(String name, DorpType type)
        {
            return define(name, type, null);
        }
        private VariableDefinition define(String name, DorpType type, StaticValue constantValue)
        {
            if (names.containsKey(name))
                throw new RuntimeException();
            VariableDefinition definition = new VariableDefinition(name, type, this, constantValue);
            names.put(name, definition);
            return definition;
        }
    }

    public class VariableDefinition extends StaticValue
    {
        public final StaticValue constantValue;
        public final String name;
        public final DorpType type;
        public final DorpNamespace namespace;
        public VariableDefinition(String name, DorpType type, DorpNamespace namespace, StaticValue constantValue)
        {
            this.name = name;
            this.type = type;
            this.namespace = namespace;
            this.constantValue = constantValue;
        }
        @Override
        public DorpType getType()
        {
            return type;
        }
        @Override
        public String toString()
        {
            return type.toString() + " " + name;
        }
    }

    private final DorpType voidType = new DorpType("Void");
    private final StaticValue voidValue = new ConstantValue(voidType, "void");
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
