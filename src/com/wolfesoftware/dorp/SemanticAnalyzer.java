package com.wolfesoftware.dorp;

import java.util.ArrayList;
import java.util.Arrays;
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
        compilationUnit = new CompilationUnit();
        DorpNamespace builtinContext = createBuiltinContext();
        DorpNamespace namespace = new DorpNamespace(builtinContext);

        StaticFunctionDefinition moduleFunction = new StaticFunctionDefinition(new StaticFunctionSignature(voidType, "entry_point"), rootNode, namespace);
        compilationUnit.functions.add(moduleFunction);
        moduleFunction.expression = evaluate(moduleFunction.namespace, moduleFunction.blockContentsNode);

        return compilationUnit;
    }

    private DorpNamespace createBuiltinContext()
    {
        DorpNamespace result = new DorpNamespace(null);
        StaticFunctionSignature printFunctionSignature = new StaticFunctionSignature(voidType, "dorp_print", intType);
        compilationUnit.functionPrototypes.add(new FunctionPrototype(printFunctionSignature));
        result.defineConstant("print", new LiteralValue(printFunctionSignature, null));
        result.defineConstant("void", new LiteralValue(voidType, "void"));
        return result;
    }

    private DorpExpression evaluate(DorpNamespace namespace, SyntaxNode syntaxNode)
    {
        switch (syntaxNode.type) {
            case BLOCK_CONTENTS: {
                ArrayList<DorpExpression> expressions = new ArrayList<>();
                for (SyntaxNode statement : syntaxNode.children)
                    if (statement.type == NodeType.DEFINITION)
                        expressions.add(evaluate(namespace, statement));
                for (SyntaxNode statement : syntaxNode.children)
                    if (statement.type != NodeType.DEFINITION)
                        expressions.add(evaluate(namespace, statement));
                return new StatementList(expressions);
            }
            case CALL: {
                // get the types of everything we're working with
                DorpExpression function = evaluate(namespace, syntaxNode.children[0]);
                SyntaxNode argumentList = syntaxNode.children[1];
                DorpExpression[] argumentValues = new DorpExpression[argumentList.children.length];
                DorpType[] argumentTypes = new DorpType[argumentValues.length];
                for (int i = 0; i < argumentValues.length; i++) {
                    argumentValues[i] = evaluate(namespace, argumentList.children[i]);
                    argumentTypes[i] = argumentValues[i].getType();
                }
                // what kind of function are we calling?
                DorpType functionType = function.getType();
                if (functionType instanceof StaticFunctionSignature) {
                    StaticFunctionSignature signature = (StaticFunctionSignature)functionType;
                    if (argumentValues.length != signature.argumentTypes.length)
                        throw new RuntimeException();
                    for (int i = 0; i < argumentValues.length; i++)
                        if (argumentTypes[i] != signature.argumentTypes[i])
                            throw new RuntimeException();
                    return new FunctionCall(function, signature.returnType, argumentValues);
                }
                if (functionType instanceof TemplateFunctionReference) {
                    TemplateFunctionReference templateReference = (TemplateFunctionReference)functionType;
                    if (argumentValues.length != templateReference.getArgumentCount())
                        throw new RuntimeException();
                    // instantiate all possible function bodies for this set of argument types
                    List<TemplateFunctionInstantiation> instantiations = templateReference.instantiate(argumentTypes);
                    ArrayList<DorpType> returnTypes = new ArrayList<>();
                    for (TemplateFunctionInstantiation instantiation : instantiations) {
                        DorpNamespace newNamespace = new DorpNamespace(instantiation.templateDefinition.parentNamespace);
                        for (int i = 0; i < argumentTypes.length; i++)
                            newNamespace.defineVariable(instantiation.templateDefinition.argumentNames[i], argumentTypes[i]);
                        DorpType returnType = evaluate(newNamespace, instantiation.templateDefinition.blockContentsNode).getType();
                        instantiation.returnType = returnType;
                        returnTypes.add(returnType);
                    }
                    DorpType returnType = returnTypes.get(0);
                    for (int i = 1; i < returnTypes.size(); i ++)
                        returnType = mergeTypes(returnType, returnTypes.get(i));
                    return new FunctionCall(function, returnType, argumentValues);
                }
                throw null;
            }
            case BLOCK: {
                BlockNode blockNode = (BlockNode)syntaxNode;
                // tODO: care about the argument names
                String[] argumentNames = new String[blockNode.argumentDeclarations != null ? blockNode.argumentDeclarations.children.length : 0];
                for (int i = 0; i < argumentNames.length; i++) {
                    SyntaxNode argumentNode = blockNode.argumentDeclarations.children[i];
                    if (argumentNode.type != NodeType.IDENTIFIER)
                        throw new RuntimeException();
                    argumentNames[i] = argumentNode.getSimpleText();
                }
                SyntaxNode blockContentsNode = blockNode.children[0];
                TemplateFunctionDefinition templateDefinition = new TemplateFunctionDefinition(argumentNames, blockContentsNode, namespace);
                return new LiteralValue(new TemplateFunctionReference(templateDefinition), null);
            }
            case DEFINITION:
            case VARIABLE_DECLARATION:
            case ASSIGNMENT: {
                SyntaxNode assignment = syntaxNode.type == NodeType.ASSIGNMENT ? syntaxNode : syntaxNode.children[0];
                SyntaxNode nameNode = assignment.children[0];
                if (nameNode.type != NodeType.IDENTIFIER)
                    throw new RuntimeException();
                String name = nameNode.getSimpleText();
                DorpExpression value = evaluate(namespace, assignment.children[1]);
                VariableDefinition definition;
                if (syntaxNode.type == NodeType.DEFINITION) {
                    definition = namespace.defineConstant(name, value);
                } else if (syntaxNode.type == NodeType.VARIABLE_DECLARATION) {
                    definition = namespace.defineVariable(name, value.getType());
                } else if (syntaxNode.type == NodeType.ASSIGNMENT) {
                    definition = namespace.lookup(name);
                    if (definition.constantValue != null)
                        throw new RuntimeException();
                    definition.type = mergeTypes(definition.type, value.getType());
                } else
                    throw null;
                return new Assignment(definition, value);
            }
            case IDENTIFIER: {
                String name = syntaxNode.getSimpleText();
                return namespace.lookup(name);
            }
            case NUMBER:
                return new LiteralValue(intType, syntaxNode.getSimpleText());
            default:
                throw null;
        }
    }

    private DorpType mergeTypes(DorpType type1, DorpType type2)
    {
        if (type1 == type2)
            return type1;
        throw new RuntimeException();
    }

    private int nextBlockIndex = 0;
    private String generateBlockName()
    {
        return "block" + nextBlockIndex++;
    }

    public abstract class DorpExpression
    {
        public abstract DorpType getType();
    }

    public class LiteralValue extends DorpExpression
    {
        public final DorpType type;
        public final String text;
        public LiteralValue(DorpType type, String text)
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

    public class Assignment extends DorpExpression
    {
        public final VariableDefinition definition;
        public final DorpExpression value;
        public Assignment(VariableDefinition definition, DorpExpression value)
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
        public final ArrayList<FunctionPrototype> functionPrototypes = new ArrayList<>();
        public final ArrayList<StaticFunctionDefinition> functions = new ArrayList<>();
    }

    public class FunctionPrototype
    {
        public final StaticFunctionSignature signature;
        public FunctionPrototype(StaticFunctionSignature signature)
        {
            this.signature = signature;
        }
    }

    public class StaticFunctionDefinition extends FunctionPrototype
    {
        private final SyntaxNode blockContentsNode;
        private final DorpNamespace namespace;
        public DorpExpression expression = null;
        public StaticFunctionDefinition(StaticFunctionSignature signature, SyntaxNode blockContentsNode, DorpNamespace namespace)
        {
            super(signature);
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
            return signature.toString() + " {...}";
        }
    }

    public class StaticFunctionSignature extends DorpType
    {
        public DorpType returnType;
        public final String symbolName;
        public final DorpType[] argumentTypes;
        public StaticFunctionSignature(DorpType returnType, String symbolName, DorpType... argumentTypes)
        {
            super(null);
            this.returnType = Main.nullCheck(returnType);
            this.symbolName = symbolName;
            this.argumentTypes = Main.nullCheckArrayElemnts(argumentTypes);
        }
        @Override
        public String toString()
        {
            return returnType.toString() + " " + symbolName + "(" + Main.join(argumentTypes, ", ") + ")";
        }
    }

    public abstract class TemplateFunctionType extends DorpType
    {
        public TemplateFunctionType()
        {
            super(null);
        }
        public abstract int getArgumentCount();
        public abstract List<TemplateFunctionInstantiation> instantiate(DorpType[] argumentTypes);
    }

    public class TemplateFunctionReference extends TemplateFunctionType
    {
        public final ArrayList<TemplateFunctionType> references = new ArrayList<>();
        public TemplateFunctionReference(TemplateFunctionType firstReference)
        {
            references.add(firstReference);
        }
        @Override
        public int getArgumentCount()
        {
            return references.get(0).getArgumentCount();
        }
        @Override
        public List<TemplateFunctionInstantiation> instantiate(DorpType[] argumentTypes)
        {
            ArrayList<TemplateFunctionInstantiation> result = new ArrayList<>();
            for (TemplateFunctionType reference : references)
                result.addAll(reference.instantiate(argumentTypes));
            return result;
        }
        @Override
        public String toString()
        {
            return "{ " + Main.join(references, ", ") + " }";
        }
    }

    public class TemplateFunctionDefinition extends TemplateFunctionType
    {
        private final String[] argumentNames;
        private final SyntaxNode blockContentsNode;
        private final DorpNamespace parentNamespace;
        private final ArrayList<TemplateFunctionInstantiation> instantiations = new ArrayList<>();
        public TemplateFunctionDefinition(String[] argumentNames, SyntaxNode blockContentsNode, DorpNamespace parentNamespace)
        {
            this.argumentNames = argumentNames;
            this.blockContentsNode = blockContentsNode;
            this.parentNamespace = parentNamespace;
        }
        @Override
        public List<TemplateFunctionInstantiation> instantiate(DorpType[] argumentTypes)
        {
            TemplateFunctionInstantiation instantiation = new TemplateFunctionInstantiation(this, argumentTypes);
            instantiations.add(instantiation);
            return Arrays.asList(instantiation);
        }
        @Override
        public int getArgumentCount()
        {
            return argumentNames.length;
        }
        @Override
        public String toString()
        {
            StringBuilder result = new StringBuilder();
            result.append("? (");
            for (int i = 0; i < argumentNames.length; i++)
                result.append("?");
            result.append(")");
            return result.toString();
        }
    }

    public class TemplateFunctionInstantiation
    {
        public final TemplateFunctionDefinition templateDefinition;
        public final DorpType[] argumentTypes;
        public DorpType returnType;
        public final String symbolName;
        public TemplateFunctionInstantiation(TemplateFunctionDefinition templateDefinition, DorpType[] argumentTypes)
        {
            this.templateDefinition = templateDefinition;
            this.argumentTypes = argumentTypes;
            this.symbolName = generateBlockName();
        }
        @Override
        public String toString()
        {
            return String.valueOf(returnType) + " " + symbolName + "(" + Main.join(argumentTypes, ", ") + ")";
        }
    }

    public class FunctionCall extends DorpExpression
    {
        public final DorpExpression function;
        public final DorpType returnType;
        public final DorpExpression[] argumentValues;
        public FunctionCall(DorpExpression function, DorpType returnType, DorpExpression[] argumentValues)
        {
            this.function = function;
            this.returnType = returnType;
            this.argumentValues = argumentValues;
        }
        @Override
        public DorpType getType()
        {
            return returnType;
        }
    }

    public class StatementList extends DorpExpression
    {
        public final List<DorpExpression> expressions;
        public StatementList(List<DorpExpression> expressions)
        {
            this.expressions = expressions;
        }
        @Override
        public DorpType getType()
        {
            DorpType result = voidType;
            if (expressions.size() > 0)
                result = expressions.get(expressions.size() - 1).getType();
            return result;
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
        public VariableDefinition defineConstant(String name, DorpExpression constantValue)
        {
            return define(name, constantValue.getType(), constantValue);
        }
        public VariableDefinition defineVariable(String name, DorpType type)
        {
            return define(name, type, null);
        }
        private VariableDefinition define(String name, DorpType type, DorpExpression constantValue)
        {
            if (names.containsKey(name))
                throw new RuntimeException();
            VariableDefinition definition = new VariableDefinition(name, type, this, constantValue);
            names.put(name, definition);
            return definition;
        }
    }

    public class VariableDefinition extends DorpExpression
    {
        public final DorpExpression constantValue;
        public final String name;
        public DorpType type;
        public final DorpNamespace namespace;
        public VariableDefinition(String name, DorpType type, DorpNamespace namespace, DorpExpression constantValue)
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
    private final DorpType intType = new DorpType("Integer");


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
