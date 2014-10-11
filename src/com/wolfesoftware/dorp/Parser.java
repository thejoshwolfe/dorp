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
        nameToRule.put(RuleName.BLOCK_CONTENTS, listWithOptionalElements(RuleName.STATEMENT, ";"));
        nameToRule.put(RuleName.STATEMENT, new ParserRule(any(rule(RuleName.DEFINITION), rule(RuleName.EXPRESSION)), null));
        nameToRule.put(RuleName.DEFINITION, new ParserRule(sequence(token(TokenType.OPERATOR, "def"), rule(RuleName.ASSIGNMENT)), NodeType.DEFINITION));
        nameToRule.put(RuleName.EXPRESSION, new ParserRule(any(rule(RuleName.DO), rule(RuleName.ASSIGNMENT)), null));
        nameToRule.put(RuleName.DO, new ParserRule(sequence(token(TokenType.OPERATOR, "do"), rule(RuleName.EXPRESSION)), NodeType.CALL));
        // operator precedence
        nameToRule.put(RuleName.ASSIGNMENT, operatorChain(RuleName.SUM, OperatorDirection.RIGHT_TO_LEFT, //
                new String[] { "=" }, //
                new NodeType[] { NodeType.ASSIGNMENT }));
        nameToRule.put(RuleName.SUM, operatorChain(RuleName.TERM, OperatorDirection.LEFT_TO_RIGHT, //
                new String[] { "+", "-" }, //
                new NodeType[] { NodeType.PLUS, NodeType.MINUS }));
        nameToRule.put(RuleName.TERM, operatorChain(RuleName.TRAILABLE, OperatorDirection.LEFT_TO_RIGHT, //
                new String[] { "*", "/" }, //
                new NodeType[] { NodeType.TIMES, NodeType.DIVIDED_BY }));
        nameToRule.put(RuleName.TRAILABLE, new ParserRule(sequence(rule(RuleName.ATOM), repeat(rule(RuleName.TRAILER))), null));
        nameToRule.put(RuleName.TRAILER, new ParserRule(rule(RuleName.PARENS), NodeType.CALL));
        nameToRule.put(RuleName.EXPRESSION_LIST, listWithOptionalElements(RuleName.EXPRESSION, ","));
        // terminals and groups
        nameToRule.put(RuleName.ATOM, new ParserRule(any( //
                rule(RuleName.BLOCK), //
                rule(RuleName.PARENS), //
                rule(RuleName.NUMBER), //
                rule(RuleName.IDENTIFIER)), null));
        nameToRule.put(RuleName.PARENS, new ParserRule(sequence(token(TokenType.OPERATOR, "("), rule(RuleName.EXPRESSION_LIST), token(TokenType.OPERATOR, ")")), null));
        nameToRule.put(RuleName.BLOCK, new ParserRule(sequence(token(TokenType.OPERATOR, "{"), rule(RuleName.BLOCK_CONTENTS), token(TokenType.OPERATOR, "}")), null));
        nameToRule.put(RuleName.NUMBER, new ParserRule(token(TokenType.NUMBER, null), null));
        nameToRule.put(RuleName.IDENTIFIER, new ParserRule(token(TokenType.IDENTIFIER, null), null));
    }

    private class ParserRule
    {
        public final ParserRuleMatcher matcher;
        private final NodeType typeOverride;
        public ParserRule(ParserRuleMatcher matcher, NodeType typeOverride)
        {
            this.matcher = matcher;
            this.typeOverride = typeOverride;
        }
        public SyntaxNode postProcess(SyntaxNode node)
        {
            if (typeOverride != null)
                node.type = typeOverride;
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
    /** leaf pattern that matches a single token */
    private ParserRuleMatcher token(final TokenType tokenType, final String exactText)
    {
        return new ParserRuleMatcher() {
            @Override
            public SyntaxNode match(int tokenIndex, boolean throwFailure)
            {
                if (tokenIndex >= tokens.size()) {
                    if (throwFailure)
                        throw new ParserError();
                    return null;
                }
                Token token = tokens.get(tokenIndex);
                if (token.type != tokenType) {
                    if (throwFailure)
                        throw new ParserError();
                    return null;
                }
                if (exactText != null && !token.text.equals(exactText)) {
                    if (throwFailure)
                        throw new ParserError();
                    return null;
                }
                return new SyntaxNode(tokenIndex, tokenIndex + 1);
            }
        };
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

    private ParserRule listWithOptionalElements(RuleName elementTypeName, String separator)
    {
        // allow empty lists, missing elements, and trailing separators
        ParserRuleMatcher matcher = maybe(sequence(rule(elementTypeName), repeat(sequence(token(TokenType.OPERATOR, separator), maybe(rule(elementTypeName))))));
        return new ParserRule(matcher, null) {
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
                return super.postProcess(node);
            }
        };
    }

    private enum OperatorDirection
    {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT;
    }
    private ParserRule operatorChain(RuleName elementRuleName, OperatorDirection direction, final String[] operators, final NodeType[] typeOverrides)
    {
        ParserRuleMatcher[] operatorMatchers = new ParserRuleMatcher[operators.length];
        for (int i = 0; i < operators.length; i++)
            operatorMatchers[i] = token(TokenType.OPERATOR, operators[i]);
        ParserRuleMatcher matcher = sequence(rule(elementRuleName), repeat(sequence(any(operatorMatchers), rule(elementRuleName))));
        return new ParserRule(matcher, null) {
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
        DIVIDED_BY;
    }

    public class SyntaxNode
    {
        public final int startTokenIndex;
        public final int endTokenIndex;
        public SyntaxNode[] children;
        public NodeType type = NodeType.PARSER_INTERMEDIATE;
        public SyntaxNode(int startTokenIndex, int endTokenIndex)
        {
            this(startTokenIndex, endTokenIndex, null);
        }
        public SyntaxNode(int startTokenIndex, int endTokenIndex, SyntaxNode[] children)
        {
            this.startTokenIndex = startTokenIndex;
            this.endTokenIndex = endTokenIndex;
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
            if (children == null)
                return getSimpleText();
            StringBuilder result = new StringBuilder();
            result.append("[").append(type.name());
            for (SyntaxNode child : children)
                result.append(" ").append(child.toString());
            result.append("]");
            return result.toString();
        }
    }
}
