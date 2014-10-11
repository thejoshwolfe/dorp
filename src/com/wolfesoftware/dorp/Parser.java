package com.wolfesoftware.dorp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.wolfesoftware.dorp.Tokenizer.TokenType;

public class Parser
{
    private enum RuleName
    {
        BLOCK_CONTENTS, //
        STATEMENT, //
        DEFINITION, //
        EXPRESSION, //
        ASSIGNMENT, //
        DO, //
        SUM, //
        TERM, //
        TRAILABLE, //
        ATOM, //
        TRAILER, //
        PARENS, //
        EXPRESSION_LIST, //
        BLOCK, //
        NUMBER, //
        IDENTIFIER;
    }

    private final HashMap<RuleName, ParserRule> nameToRule = new HashMap<>();
    {
        nameToRule.put(RuleName.BLOCK_CONTENTS, listWithOptionalElements(NodeType.BLOCK_CONTENTS, RuleName.STATEMENT, ";"));
        nameToRule.put(RuleName.STATEMENT, new ParserRule(any(rule(RuleName.DEFINITION), rule(RuleName.EXPRESSION))));
        nameToRule.put(RuleName.DEFINITION, new ParserRule(sequence(operator("def"), rule(RuleName.ASSIGNMENT))) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                // discard the "def" operator
                node.children = new SyntaxNode[] { node.children[1] };
                node.type = NodeType.DEFINITION;
                return node;
            }
        });
        nameToRule.put(RuleName.EXPRESSION, new ParserRule(any(rule(RuleName.DO), rule(RuleName.ASSIGNMENT))));
        nameToRule.put(RuleName.DO, new ParserRule(sequence(operator("do"), rule(RuleName.EXPRESSION))) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                SyntaxNode doOperator = node.children[0];
                SyntaxNode block = node.children[1];
                SyntaxNode emptyArgumentList = new SyntaxNode(doOperator.startTokenIndex, doOperator.endTokenIndex, NodeType.ARGUMENT_LIST, new SyntaxNode[0]);
                return new SyntaxNode(node.startTokenIndex, node.endTokenIndex, NodeType.CALL, new SyntaxNode[] { block, emptyArgumentList });
            }
        });
        // operator precedence
        nameToRule.put(RuleName.ASSIGNMENT, new ParserRule(sequence(rule(RuleName.SUM), repeat(sequence(operator("="), rule(RuleName.SUM))))) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                // sum ( "=" sum )*
                SyntaxNode[] addendums = node.children[1].children;
                SyntaxNode[] elements = new SyntaxNode[1 + addendums.length];
                elements[0] = node.children[0];
                for (int i = 0; i < addendums.length; i++) {
                    // "=" sum
                    elements[i + 1] = addendums[i].children[1];
                }
                // right-to-left
                Main.reverse(elements);
                SyntaxNode right = elements[0];
                for (int i = 1; i < elements.length; i++) {
                    SyntaxNode left = elements[i];
                    // some assignment constructs are really function declarations
                    switch (left.type) {
                        case PARENS: {
                            // (a, b) = {} becomes {}
                            SyntaxNode argumentList = left.children[0];
                            if (right.type != NodeType.BLOCK)
                                throw new ParserError();
                            BlockNode block = (BlockNode)right;
                            if (block.argumentDeclarations != null)
                                throw new ParserError();
                            argumentList.type = NodeType.ARGUMENT_LIST;
                            block.argumentDeclarations = argumentList;
                            // no real assignment here
                            right = block;
                            continue;
                        }
                        case CALL: {
                            // f(a, b) = {} becomes f = {}
                            SyntaxNode functionName = left.children[0];
                            SyntaxNode argumentList = left.children[1];
                            if (right.type != NodeType.BLOCK)
                                throw new ParserError();
                            BlockNode block = (BlockNode)right;
                            if (block.argumentDeclarations != null)
                                throw new ParserError();
                            block.argumentDeclarations = argumentList;
                            left = functionName;
                            break;
                        }
                    }
                    right = new SyntaxNode(left.startTokenIndex, right.endTokenIndex, NodeType.ASSIGNMENT, new SyntaxNode[] { left, right });
                }
                return right;
            }
        });
        nameToRule.put(RuleName.SUM, operatorChain(RuleName.TERM, //
                new String[] { "+", "-" }, //
                new NodeType[] { NodeType.PLUS, NodeType.MINUS }));
        nameToRule.put(RuleName.TERM, operatorChain(RuleName.TRAILABLE, //
                new String[] { "*", "/" }, //
                new NodeType[] { NodeType.TIMES, NodeType.DIVIDED_BY }));
        nameToRule.put(RuleName.TRAILABLE, new ParserRule(sequence(rule(RuleName.ATOM), repeat(rule(RuleName.TRAILER)))) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                // atom ( trailer )*
                SyntaxNode base = node.children[0];
                for (SyntaxNode trailer : node.children[1].children) {
                    switch (trailer.type) {
                        case PARENS: {
                            // translate to a function call
                            SyntaxNode argumentList = trailer.children[0];
                            argumentList.type = NodeType.ARGUMENT_LIST;
                            base = new SyntaxNode(base.startTokenIndex, trailer.endTokenIndex, NodeType.CALL, new SyntaxNode[] { base, argumentList });
                            break;
                        }
                        default:
                            throw null;
                    }
                }
                return base;
            }
        });
        nameToRule.put(RuleName.TRAILER, new ParserRule(rule(RuleName.PARENS)));
        nameToRule.put(RuleName.EXPRESSION_LIST, listWithOptionalElements(NodeType.EXPRESSION_LIST, RuleName.EXPRESSION, ","));
        // terminals and groups
        nameToRule.put(RuleName.ATOM, new ParserRule(any( //
                rule(RuleName.BLOCK), //
                rule(RuleName.PARENS), //
                rule(RuleName.NUMBER), //
                rule(RuleName.IDENTIFIER))));
        nameToRule.put(RuleName.PARENS, new ParserRule(sequence(operator("("), rule(RuleName.EXPRESSION_LIST), operator(")"))) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                // discard "(" and ")" operators
                node.children = new SyntaxNode[] { node.children[1] };
                node.type = NodeType.PARENS;
                return node;
            }
        });
        nameToRule.put(RuleName.BLOCK, new ParserRule(sequence(operator("{"), rule(RuleName.BLOCK_CONTENTS), operator("}"))) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                return new BlockNode(node);
            }
        });
        nameToRule.put(RuleName.NUMBER, new ParserRule(token(TokenType.NUMBER)) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                node.type = NodeType.NUMBER;
                return node;
            }
        });
        nameToRule.put(RuleName.IDENTIFIER, new ParserRule(token(TokenType.IDENTIFIER)) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                node.type = NodeType.IDENTIFIER;
                return node;
            }
        });
    }

    private class ParserRule
    {
        public final ParserRuleMatcher matcher;
        public ParserRule(ParserRuleMatcher matcher)
        {
            this.matcher = matcher;
        }
        public SyntaxNode postProcess(SyntaxNode node)
        {
            return node;
        }
        @Override
        public String toString()
        {
            for (Entry<RuleName, ParserRule> entry : nameToRule.entrySet())
                if (entry.getValue() == this)
                    return entry.getKey().name();
            return super.toString();
        }
    }

    private static abstract class ParserRuleMatcher
    {
        public abstract SyntaxNode match(int tokenIndex, boolean throwFailure);
    }

    private ParserRuleMatcher operator(final String text)
    {
        return new ParserRuleMatcher() {
            @Override
            public SyntaxNode match(int tokenIndex, boolean throwFailure)
            {
                tokenIndex = findToken(tokenIndex, TokenType.OPERATOR, text);
                if (tokenIndex == -1) {
                    if (throwFailure)
                        throw new ParserError();
                    return null;
                }
                return new SyntaxNode(tokenIndex, tokenIndex + 1);
            }
        };
    }
    /** leaf pattern that matches a single token */
    private ParserRuleMatcher token(final TokenType tokenType)
    {
        return new ParserRuleMatcher() {
            @Override
            public SyntaxNode match(int tokenIndex, boolean throwFailure)
            {
                tokenIndex = findToken(tokenIndex, tokenType, null);
                if (tokenIndex == -1) {
                    if (throwFailure)
                        throw new ParserError();
                    return null;
                }
                return new SyntaxNode(tokenIndex, tokenIndex + 1);
            }
        };
    }
    private int findToken(int tokenIndex, TokenType tokenType, String exactText)
    {
        while (tokenIndex < tokens.size()) {
            Token token = tokens.get(tokenIndex);
            switch (token.type) {
                case SPACE:
                case NEWLINE:
                case COMMENT:
                    tokenIndex += 1;
                    continue;
            }
            if (token.type != tokenType)
                break;
            if (exactText != null && !token.text.equals(exactText))
                break;
            return tokenIndex;
        }
        return -1;
    }
    /** a matcher (as part of a rule) that matches another rule */
    private ParserRuleMatcher rule(final RuleName ruleName)
    {
        return new ParserRuleMatcher() {
            @Override
            public SyntaxNode match(int tokenIndex, boolean throwFailure)
            {
                return parseNode(ruleName, tokenIndex, throwFailure);
            }
        };
    }
    /** like regex a* */
    private ParserRuleMatcher repeat(final ParserRuleMatcher matcher)
    {
        return new ParserRuleMatcher() {
            @Override
            public SyntaxNode match(int tokenIndex, boolean throwFailure)
            {
                int startTokenIndex = tokenIndex;
                ArrayList<SyntaxNode> children = new ArrayList<>();
                while (true) {
                    SyntaxNode child = matcher.match(tokenIndex, false);
                    if (child == null)
                        break;
                    children.add(child);
                    tokenIndex = child.endTokenIndex;
                }
                return new SyntaxNode(startTokenIndex, tokenIndex, children.toArray(new SyntaxNode[0]));
            }
        };
    }
    /** like regex abc */
    private ParserRuleMatcher sequence(final ParserRuleMatcher... subMatchers)
    {
        return new ParserRuleMatcher() {
            @Override
            public SyntaxNode match(int tokenIndex, boolean throwFailure)
            {
                int startTokenIndex = tokenIndex;
                SyntaxNode[] children = new SyntaxNode[subMatchers.length];
                for (int i = 0; i < subMatchers.length; i++) {
                    children[i] = subMatchers[i].match(tokenIndex, throwFailure);
                    if (children[i] == null) {
                        // polite failure of any children is a polite failure of the whole
                        return null;
                    }
                    tokenIndex = children[i].endTokenIndex;
                }
                return new SyntaxNode(startTokenIndex, tokenIndex, children);
            }
        };
    }
    /** like regex a? */
    private ParserRuleMatcher maybe(final ParserRuleMatcher matcher)
    {
        return new ParserRuleMatcher() {
            @Override
            public SyntaxNode match(int tokenIndex, boolean throwFailure)
            {
                SyntaxNode result = matcher.match(tokenIndex, false);
                if (result != null)
                    return result;
                // dummy syntax node that spans 0 tokens
                return new SyntaxNode(tokenIndex, tokenIndex);
            }
        };
    }
    /** like regex a|b|c */
    private static ParserRuleMatcher any(final ParserRuleMatcher... subMatchers)
    {
        return new ParserRuleMatcher() {
            @Override
            public SyntaxNode match(int tokenIndex, boolean throwFailure)
            {
                for (ParserRuleMatcher subMatcher : subMatchers) {
                    SyntaxNode result = subMatcher.match(tokenIndex, false);
                    if (result != null)
                        return result;
                }
                if (throwFailure)
                    throw new ParserError();
                return null;
            }
        };
    }

    private ParserRule listWithOptionalElements(final NodeType listType, RuleName elementTypeName, String separator)
    {
        // allow empty lists, missing elements, and trailing separators
        ParserRuleMatcher matcher = maybe(sequence(rule(elementTypeName), repeat(sequence(operator(separator), maybe(rule(elementTypeName))))));
        return new ParserRule(matcher) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                // pull the simple sequence of elements out of the complex pattern
                ArrayList<SyntaxNode> children = new ArrayList<>();
                // ( [a-c] ( ; [a-c]? )* )?
                if (node.children != null) {
                    // [a-c] ( ; [a-c]? )*
                    // node is a sequence
                    // [a-c]
                    SyntaxNode firstElement = node.children[0];
                    children.add(firstElement);
                    // ( ; [a-c]? )*
                    SyntaxNode[] otherElements = node.children[1].children;
                    // otherElements is a repeat
                    for (SyntaxNode elementNode : otherElements) {
                        // ; [a-c]?
                        // elementNode is a sequence
                        SyntaxNode element = elementNode.children[1];
                        // [a-c]?
                        if (element.startTokenIndex < element.endTokenIndex) {
                            // [a-c]
                            children.add(element);
                        }
                    }
                }
                node.children = children.toArray(new SyntaxNode[0]);
                node.type = listType;
                return node;
            }
        };
    }

    private ParserRule operatorChain(RuleName elementRuleName, final String[] operators, final NodeType[] typeOverrides)
    {
        ParserRuleMatcher[] operatorMatchers = new ParserRuleMatcher[operators.length];
        for (int i = 0; i < operators.length; i++)
            operatorMatchers[i] = operator(operators[i]);
        ParserRuleMatcher matcher = sequence(rule(elementRuleName), repeat(sequence(any(operatorMatchers), rule(elementRuleName))));
        return new ParserRule(matcher) {
            @Override
            public SyntaxNode postProcess(SyntaxNode node)
            {
                // from the complex parse tree pattern, extract a binary syntax tree of operations.
                // a ( [+-] a )*
                SyntaxNode left = node.children[0];
                for (SyntaxNode addendum : node.children[1].children) {
                    // [+-] a
                    String operatorText = addendum.children[0].getSimpleText();
                    NodeType operatorType = null;
                    for (int i = 0; i < operators.length; i++) {
                        if (operators[i].equals(operatorText)) {
                            operatorType = typeOverrides[i];
                            break;
                        }
                    }
                    SyntaxNode right = addendum.children[1];
                    SyntaxNode operation = new SyntaxNode(left.startTokenIndex, right.endTokenIndex, new SyntaxNode[] { left, right });
                    operation.type = operatorType;
                    left = operation;
                }
                return left;
            }
        };
    }

    public static class ParserError extends RuntimeException
    {
    }

    private final List<Token> tokens;
    public Parser(List<Token> tokens)
    {
        this.tokens = tokens;
    }

    public SyntaxNode parse()
    {
        return parseNode(RuleName.BLOCK_CONTENTS, 0, true);
    }

    private SyntaxNode parseNode(RuleName ruleName, int tokenIndex, boolean throwFailure)
    {
        ParserRule rule = nameToRule.get(ruleName);
        SyntaxNode node = rule.matcher.match(tokenIndex, throwFailure);
        if (node != null)
            node = rule.postProcess(node);
        return node;
    }

    public enum NodeType
    {
        /** this value should always be replaced in a {@link ParserRule#postProcess(SyntaxNode)} */
        PARSER_INTERMEDIATE, //
        DEFINITION, //
        CALL, //
        ASSIGNMENT, //
        PLUS, //
        MINUS, //
        TIMES, //
        DIVIDED_BY, //
        ARGUMENT_LIST, //
        PARENS, //
        BLOCK_CONTENTS, //
        EXPRESSION_LIST, //
        BLOCK, //
        NUMBER, //
        IDENTIFIER;
    }

    public class SyntaxNode
    {
        public final int startTokenIndex;
        public final int endTokenIndex;
        public SyntaxNode[] children;
        public NodeType type;
        public SyntaxNode(int startTokenIndex, int endTokenIndex)
        {
            this(startTokenIndex, endTokenIndex, null);
        }
        public SyntaxNode(int startTokenIndex, int endTokenIndex, SyntaxNode[] children)
        {
            this(startTokenIndex, endTokenIndex, NodeType.PARSER_INTERMEDIATE, children);
        }
        public SyntaxNode(int startTokenIndex, int endTokenIndex, NodeType type, SyntaxNode[] children)
        {
            this.startTokenIndex = startTokenIndex;
            this.endTokenIndex = endTokenIndex;
            this.type = type;
            this.children = children;
        }
        public String getSimpleText()
        {
            if (startTokenIndex + 1 == endTokenIndex)
                return tokens.get(startTokenIndex).text;
            return null;
        }
        @Override
        public String toString()
        {
            StringBuilder result = new StringBuilder();
            writeNodeToBuffer(result, this, 0);
            return result.toString();
        }
    }

    private static void writeNodeToBuffer(StringBuilder builder, SyntaxNode node, int indentation)
    {
        builder.append("[").append(node.type.name());
        if (node.children == null) {
            String simpleText = node.getSimpleText();
            if (simpleText != null) {
                builder.append(" \"").append(simpleText).append('"');
            }
        } else {
            if (node.type == NodeType.BLOCK) {
                builder.append("\n");
                for (int i = 0; i < indentation + 1; i++)
                    builder.append("  ");
                SyntaxNode argumentDeclarations = ((BlockNode)node).argumentDeclarations;
                writeNodeToBuffer(builder, argumentDeclarations, indentation + 1);
            }
            for (SyntaxNode child : node.children) {
                builder.append("\n");
                for (int i = 0; i < indentation + 1; i++)
                    builder.append("  ");
                writeNodeToBuffer(builder, child, indentation + 1);
            }
            if (node.children.length > 0) {
                builder.append("\n");
                for (int i = 0; i < indentation; i++)
                    builder.append("  ");
            }
        }
        builder.append("]");
    }

    public class BlockNode extends SyntaxNode
    {
        public SyntaxNode argumentDeclarations = null;
        public BlockNode(SyntaxNode node)
        {
            super(node.startTokenIndex, node.endTokenIndex, NodeType.BLOCK, new SyntaxNode[] { node.children[1] });
        }
    }
}
