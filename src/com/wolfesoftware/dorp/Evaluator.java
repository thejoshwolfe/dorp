package com.wolfesoftware.dorp;

import java.math.BigInteger;
import java.util.HashMap;

import com.wolfesoftware.dorp.Parser.BlockNode;
import com.wolfesoftware.dorp.Parser.NodeType;
import com.wolfesoftware.dorp.Parser.SyntaxNode;

public class Evaluator
{
    private final SyntaxNode rootNode;
    private final ExecutionContext globalContext;
    public Evaluator(SyntaxNode rootNode)
    {
        this.rootNode = rootNode;
        this.globalContext = makeGlobalContext();
    }
    public void evaluate()
    {
        evaluate(rootNode, new ExecutionContext(globalContext));
    }

    private Value evaluate(SyntaxNode node, ExecutionContext context)
    {
        switch (node.type) {
            case BLOCK_CONTENTS: {
                // first, the definitions
                context.allDeclarationsAreConstants = true;
                for (SyntaxNode statement : node.children)
                    if (statement.type == NodeType.DEFINITION)
                        evaluate(statement, context);
                context.allDeclarationsAreConstants = false;
                // then everything else
                Value result = voidValue;
                for (SyntaxNode statment : node.children)
                    if (statment.type != NodeType.DEFINITION)
                        result = evaluate(statment, context);
                return result;
            }
            case DEFINITION: {
                SyntaxNode assignment = node.children[0];
                SyntaxNode target = assignment.children[0];
                if (target.type != NodeType.IDENTIFIER)
                    throw new EvaluationException();
                String id = target.getSimpleText();
                Value value = evaluate(assignment.children[1], context);
                context.define(id, value);
                return voidValue;
            }
            case ASSIGNMENT: {
                SyntaxNode target = node.children[0];
                if (target.type != NodeType.IDENTIFIER)
                    throw new EvaluationException();
                String id = target.getSimpleText();
                Value value = evaluate(node.children[1], context);
                context.assign(id, value);
                return value;
            }
            case CALL: {
                Value function = evaluate(node.children[0], context);
                if (!functionType.isAssignableFrom(function.type))
                    throw new EvaluationException();
                FunctionType signature = (FunctionType)function.type;
                SyntaxNode[] argumentNodes = node.children[1].children;
                if (signature.argumentTypes.length != argumentNodes.length)
                    throw new EvaluationException();
                Value[] arguments = new Value[argumentNodes.length];
                for (int i = 0; i < argumentNodes.length; i++) {
                    arguments[i] = evaluate(argumentNodes[i], context);
                    if (signature.argumentTypes[i] != null && !signature.argumentTypes[i].isAssignableFrom(arguments[i].type))
                        throw new EvaluationException();
                }
                return ((FunctionValue)function).run(arguments);
            }
            case BLOCK: {
                // define a function bound to a context
                SyntaxNode argumentDeclarations = ((BlockNode)node).argumentDeclarations;
                String[] argumentIds = new String[argumentDeclarations.children.length];
                for (int i = 0; i < argumentIds.length; i++) {
                    SyntaxNode argumentNode = argumentDeclarations.children[i];
                    if (argumentNode.type != NodeType.IDENTIFIER)
                        throw new EvaluationException();
                    argumentIds[i] = argumentNode.getSimpleText();
                }
                FunctionType signature = new FunctionType(argumentIds.length);
                return new BlockFunction(signature, argumentIds, node.children[0], context);
            }
            case PARENS: {
                if (node.children.length != 1)
                    throw new EvaluationException();
                return evaluate(node.children[0], context);
            }
            case NUMBER:
                return new PrimitiveValue(integerType, new BigInteger(node.getSimpleText()));
            case IDENTIFIER:
                return context.lookupSymbol(node.getSimpleText()).value;
            case PLUS:
            case MINUS:
            case TIMES:
            case DIVIDED_BY:
                throw null; // TODO
            default:
                throw null;
        }
    }

    private ExecutionContext makeGlobalContext()
    {
        ExecutionContext context = new ExecutionContext(null);
        context.allDeclarationsAreConstants = true;
        context.define("Object", objectType);
        context.define("super", new FunctionValue(new FunctionType(typeType, typeType)) {
            @Override
            public Value run(Value[] arguments)
            {
                RuntimeType arg = (RuntimeType)arguments[0];
                return valueOrNull(arg.parentType);
            }
        });
        context.define("Type", typeType);
        context.define("typeof", new FunctionValue(new FunctionType(typeType, objectType)) {
            @Override
            public Value run(Value[] arguments)
            {
                Value arg = arguments[0];
                return arg.type;
            }
        });
        context.define("Void", voidType);
        context.define("void", voidValue);
        context.define("Null", nullType);
        context.define("null", nullValue);

        context.define("print", new FunctionValue(new FunctionType(voidType, integerType)) {
            @Override
            public Value run(Value[] arguments)
            {
                Value theValue = arguments[0];
                System.out.println((BigInteger)((PrimitiveValue)theValue).value);
                return voidValue;
            }
        });
        return context;
    }

    private Value valueOrNull(Value value)
    {
        if (value != null)
            return value;
        return nullValue;
    }

    private class ExecutionContext
    {
        public final HashMap<String, DefinedSymbol> names = new HashMap<>();

        public boolean allDeclarationsAreConstants = false;
        public final ExecutionContext parentContext;
        public ExecutionContext(ExecutionContext parentContext)
        {
            this.parentContext = parentContext;
        }
        @Override
        public String toString()
        {
            return names.keySet().toString();
        }

        public void define(String id, Value value)
        {
            if (names.containsKey(id))
                throw new EvaluationException();
            names.put(id, new DefinedSymbol(allDeclarationsAreConstants, value.type, value));
        }
        public void assign(String id, Value value)
        {
            DefinedSymbol symbol = lookupSymbol(id);
            if (symbol.isConstant)
                throw new EvaluationException();
            if (!symbol.type.isAssignableFrom(value.type))
                throw new EvaluationException();
            symbol.value = value;
        }
        protected DefinedSymbol lookupSymbol(String id)
        {
            for (ExecutionContext context = this; context != null; context = context.parentContext) {
                DefinedSymbol result = context.names.get(id);
                if (result != null)
                    return result;
            }
            throw new EvaluationException();
        }
    }

    private static class DefinedSymbol
    {
        public final boolean isConstant;
        public RuntimeType type;
        public Value value;
        public DefinedSymbol(boolean isConstant, RuntimeType type, Value value)
        {
            this.isConstant = isConstant;
            this.type = type;
            this.value = value;
        }
    }

    private class Value
    {
        public RuntimeType type;
        public Value(RuntimeType type)
        {
            this.type = type;
        }
    }

    private abstract class FunctionValue extends Value
    {
        public FunctionValue(FunctionType type)
        {
            super(type);
        }
        public abstract Value run(Value[] arguments);
    }

    private class BlockFunction extends FunctionValue
    {
        private final String[] argumentIds;
        private final SyntaxNode blockContents;
        private final ExecutionContext surroundingContext;
        public BlockFunction(FunctionType type, String[] argumentIds, SyntaxNode blockContents, ExecutionContext surroundingContext)
        {
            super(type);
            this.argumentIds = argumentIds;
            this.blockContents = blockContents;
            this.surroundingContext = surroundingContext;
        }
        @Override
        public Value run(Value[] arguments)
        {
            ExecutionContext context = new ExecutionContext(surroundingContext);
            for (int i = 0; i < argumentIds.length; i++)
                context.define(argumentIds[i], arguments[i]);
            return evaluate(blockContents, context);
        }
    }
    private final RuntimeType objectType;
    private final RuntimeType typeType;
    {
        // super(Object) == null
        objectType = new RuntimeType(null);
        // super(Type) == Object
        typeType = new RuntimeType(objectType);
        // typeof(Type) == Type
        typeType.type = typeType;
        // typoef(Object) == Type
        objectType.type = typeType;
    }
    private final RuntimeType integerType = new RuntimeType(objectType);
    private final RuntimeType voidType = new RuntimeType(objectType);
    private final Value voidValue = new Value(voidType);
    private final RuntimeType nullType = new RuntimeType(objectType);
    private final Value nullValue = new Value(nullType);
    private final RuntimeType functionType = new RuntimeType(objectType);

    private class PrimitiveValue extends Value
    {
        private Object value;
        public PrimitiveValue(RuntimeType type, Object value)
        {
            super(type);
            this.value = value;
        }
    }

    private class RuntimeType extends Value
    {
        public RuntimeType parentType;
        public RuntimeType(RuntimeType parentType)
        {
            super(typeType);
            this.parentType = parentType;
        }
        public boolean isAssignableFrom(RuntimeType type)
        {
            while (true) {
                if (this == type)
                    return true;
                type = type.parentType;
                if (type == null)
                    return false;
            }
        }
    }

    private class FunctionType extends RuntimeType
    {
        private RuntimeType returnType;
        private RuntimeType[] argumentTypes;
        public FunctionType(int argumentCount)
        {
            this(null, new RuntimeType[argumentCount]);
        }
        public FunctionType(RuntimeType returnType, RuntimeType... argumentTypes)
        {
            super(functionType);
            this.returnType = returnType;
            this.argumentTypes = argumentTypes;
        }
    }

    public static class EvaluationException extends RuntimeException
    {
    }
}
