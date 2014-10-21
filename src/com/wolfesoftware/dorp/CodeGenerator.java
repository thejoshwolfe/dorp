package com.wolfesoftware.dorp;

import com.wolfesoftware.dorp.SemanticAnalyzer.Assignment;
import com.wolfesoftware.dorp.SemanticAnalyzer.CompilationUnit;
import com.wolfesoftware.dorp.SemanticAnalyzer.DorpExpression;
import com.wolfesoftware.dorp.SemanticAnalyzer.DorpType;
import com.wolfesoftware.dorp.SemanticAnalyzer.FunctionCall;
import com.wolfesoftware.dorp.SemanticAnalyzer.FunctionPrototype;
import com.wolfesoftware.dorp.SemanticAnalyzer.LiteralValue;
import com.wolfesoftware.dorp.SemanticAnalyzer.StatementList;
import com.wolfesoftware.dorp.SemanticAnalyzer.StaticFunctionDefinition;
import com.wolfesoftware.dorp.SemanticAnalyzer.StaticFunctionSignature;
import com.wolfesoftware.dorp.SemanticAnalyzer.TemplateFunctionType;
import com.wolfesoftware.dorp.SemanticAnalyzer.VariableDefinition;

/**
 * much of this class overlaps with functionality provided by the llvm dev library.
 * TODO: someday, consider something like this: https://gist.github.com/andrewrk/1558b3a1c4dd1c130bbf
 */
public class CodeGenerator
{
    private final CompilationUnit compilationUnit;
    public CodeGenerator(CompilationUnit compilationUnit)
    {
        this.compilationUnit = compilationUnit;
    }
    private final StringBuilder result = new StringBuilder();
    @Override
    public String toString()
    {
        return result.toString();
    }
    public String generate()
    {
        for (FunctionPrototype functionPrototype : compilationUnit.functions) {
            renderFunctionDefinition((StaticFunctionDefinition)functionPrototype);
            result.append("\n");
        }
        for (FunctionPrototype prototype : compilationUnit.functionPrototypes)
            renderFunctionPrototype(prototype);
        return result.toString();
    }
    private void renderFunctionPrototype(FunctionPrototype prototype)
    {
        result.append("declare ");
        renderType(prototype.signature.returnType);
        result.append(" @").append(prototype.signature.symbolName).append("(");
        DorpType[] argumentTypes = prototype.signature.argumentTypes;
        renderTypeListWithCommas(argumentTypes);
        result.append(")\n");
    }
    private void renderFunctionDefinition(StaticFunctionDefinition function)
    {
        result.append("define ");
        renderType(function.signature.returnType);
        result.append(" @").append(function.signature.symbolName).append("(");
        DorpType[] argumentTypes = function.signature.argumentTypes;
        // TODO: also argument names
        renderTypeListWithCommas(argumentTypes);
        result.append(") {\n");

        // alloca all local variables before anything else
        for (VariableDefinition definition : function.getLocalVariableDefinitions()) {
            String name = getVariablePointerName(definition);
            result.append("  ").append(name).append(" = alloca ");
            renderType(definition.type);
            result.append("\n");
        }

        // function body
        String returnReference = evaluateExpression(function.expression);
        DorpType returnType = function.expression.getType();

        // return statement
        result.append("  ret ");
        if (!isVoid(returnType)) {
            renderType(returnType);
            result.append(" ").append(returnReference);
        } else {
            result.append("void");
        }
        result.append("\n");
        result.append("}\n");
    }
    private String evaluateExpression(DorpExpression expression)
    {
        if (expression instanceof StatementList) {
            StatementList statementList = (StatementList)expression;
            String resultReference = null;
            for (DorpExpression childExpression : statementList.expressions)
                resultReference = evaluateExpression(childExpression);
            return resultReference;
        }
        if (expression instanceof FunctionCall) {
            FunctionCall functionCall = (FunctionCall)expression;
            String functionReference = evaluateExpression(functionCall.function);
            String[] argumentReferences = new String[functionCall.argumentValues.length];
            for (int i = 0; i < functionCall.argumentValues.length; i++)
                argumentReferences[i] = evaluateExpression(functionCall.argumentValues[i]);
            boolean isVoid = isVoid(functionCall.returnType);
            String resultReference = isVoid ? null : generateReference();
            result.append("  ");
            if (!isVoid)
                result.append(resultReference).append(" = ");
            result.append("call ");
            renderType(functionCall.returnType);
            result.append(" ").append(functionReference).append("(");
            if (argumentReferences.length > 0) {
                renderType(functionCall.argumentValues[0].getType());
                result.append(" ").append(argumentReferences[0]);
                for (int i = 1; i < argumentReferences.length; i++) {
                    result.append(", ");
                    renderType(functionCall.argumentValues[i].getType());
                    result.append(" ").append(argumentReferences[i]);
                }
            }
            result.append(")\n");
            return resultReference;
        }
        if (expression instanceof LiteralValue) {
            LiteralValue constant = (LiteralValue)expression;
            DorpType type = constant.getType();
            if (type instanceof StaticFunctionSignature) {
                StaticFunctionSignature signature = (StaticFunctionSignature)type;
                return "@" + signature.symbolName;
            }
            if (type instanceof TemplateFunctionType) {
                // TODO
                throw null;
            }
            if ("Boolean".equals(type.name))
                return constant.text.equals("true") ? "1" : "0";
            return Main.nullCheck(constant.text);
        }
        if (expression instanceof Assignment) {
            Assignment assignment = (Assignment)expression;
            String valueReference = evaluateExpression(assignment.value);
            result.append("  store ");
            renderType(assignment.value.getType());
            result.append(" ").append(valueReference).append(", ");
            renderType(assignment.definition.getType());
            result.append("* ").append(getVariablePointerName(assignment.definition)).append("\n");
            return valueReference;
        }
        if (expression instanceof VariableDefinition) {
            VariableDefinition definition = (VariableDefinition)expression;
            if (definition.constantValue != null)
                return evaluateExpression(definition.constantValue);
            String valueReference = generateReference();
            result.append("  ").append(valueReference).append(" = load ");
            renderType(definition.getType());
            result.append("* ").append(getVariablePointerName(definition));
            result.append("\n");
            return valueReference;
        }
        throw null;
    }
    private boolean isVoid(DorpType type)
    {
        return "Void".equals(type.name);
    }
    private String getVariablePointerName(VariableDefinition definition)
    {
        return "%var." + definition.name;
    }
    private int nextReferenceIndex = 0;
    private String generateReference()
    {
        return "%val" + nextReferenceIndex++;
    }
    private void renderTypeListWithCommas(DorpType[] types)
    {
        if (types.length == 0)
            return;
        renderType(types[0]);
        for (int i = 1; i < types.length; i++) {
            result.append(", ");
            renderType(types[i]);
        }
    }
    private void renderType(DorpType type)
    {
        if (type instanceof StaticFunctionSignature) {
            // functions used as first-class objects are really the pointer to the function
            StaticFunctionSignature signature = (StaticFunctionSignature)type;
            renderType(signature.returnType);
            result.append("(");
            renderTypeListWithCommas(signature.argumentTypes);
            result.append(")*");
        } else if (type instanceof TemplateFunctionType) {
            // TODO
            throw null;
        } else {
            switch (type.name) {
                case "Integer":
                    result.append("i32");
                    break;
                case "Boolean":
                    result.append("i1");
                    break;
                case "Void":
                    result.append("void");
                    break;
            }
        }
    }
}
