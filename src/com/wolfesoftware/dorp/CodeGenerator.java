package com.wolfesoftware.dorp;

import com.wolfesoftware.dorp.SemanticAnalyzer.Assignment;
import com.wolfesoftware.dorp.SemanticAnalyzer.CompilationUnit;
import com.wolfesoftware.dorp.SemanticAnalyzer.ConstantValue;
import com.wolfesoftware.dorp.SemanticAnalyzer.DorpType;
import com.wolfesoftware.dorp.SemanticAnalyzer.FunctionCall;
import com.wolfesoftware.dorp.SemanticAnalyzer.FunctionDefinition;
import com.wolfesoftware.dorp.SemanticAnalyzer.FunctionPrototype;
import com.wolfesoftware.dorp.SemanticAnalyzer.FunctionSignature;
import com.wolfesoftware.dorp.SemanticAnalyzer.StaticValue;
import com.wolfesoftware.dorp.SemanticAnalyzer.VariableDefinition;

public class CodeGenerator
{
    private final String moduleName = "asdf";
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
            renderFunctionDefinition((FunctionDefinition)functionPrototype);
            result.append("\n");
        }
        result.append("@entry_point = alias void()* @").append(moduleName).append("\n");
        for (FunctionPrototype prototype : compilationUnit.getFunctionPrototypes())
            renderFunctionPrototype(prototype);
        return result.toString();
    }
    private void renderFunctionPrototype(FunctionPrototype prototype)
    {
        result.append("declare ");
        renderType(prototype.signature.returnType);
        result.append(" @").append(prototype.name).append("(");
        DorpType[] argumentTypes = prototype.signature.argumentTypes;
        renderTypeListWithCommas(argumentTypes);
        result.append(")\n");
    }
    private void renderFunctionDefinition(FunctionDefinition function)
    {
        result.append("define ");
        renderType(function.signature.returnType);
        result.append(" @").append(function.name).append("(");
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

        // all the logic
        for (StaticValue expression : function.expressions)
            evaluateExpression(expression);

        result.append("  ret void\n");
        result.append("}\n");
    }
    private String evaluateExpression(StaticValue expression)
    {
        if (expression instanceof FunctionCall) {
            FunctionCall functionCall = (FunctionCall)expression;
            String functionReference = evaluateExpression(functionCall.function);
            String[] argumentReferences = new String[functionCall.argumentValues.length];
            for (int i = 0; i < functionCall.argumentValues.length; i++)
                argumentReferences[i] = evaluateExpression(functionCall.argumentValues[i]);
            boolean isVoid = "Void".equals(functionCall.signature.returnType.name);
            String resultReference = isVoid ? null : generateReference();
            result.append("  ");
            if (!isVoid)
                result.append(resultReference).append(" = ");
            result.append("call ");
            renderType(functionCall.signature.returnType);
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
        if (expression instanceof ConstantValue) {
            ConstantValue constant = (ConstantValue)expression;
            return constant.text;
        }
        if (expression instanceof FunctionPrototype) {
            FunctionPrototype functionPrototype = (FunctionPrototype)expression;
            return "@" + functionPrototype.name;
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
        if (type instanceof FunctionSignature) {
            // functions used as first-class objects are really the pointer to the function
            FunctionSignature signature = (FunctionSignature)type;
            renderType(signature.returnType);
            result.append("(");
            renderTypeListWithCommas(signature.argumentTypes);
            result.append(")*");
        } else {
            switch (type.name) {
                case "Int":
                    result.append("i32");
                    break;
                case "Void":
                    result.append("void");
                    break;
            }
        }
    }
}
