package com.wolfesoftware.dorp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.wolfesoftware.dorp.Tokenizer.TokenType;

public class Parser
{
    private static enum RuleName
    {
        BLOCK_CONTENTS, //
        STATEMENT, //
        DEFINITION, //
        EXPRESSION, //
        ASSIGNMENT;
    }

    private final HashMap<RuleName, ParserRule> nameToRule = new HashMap<>();
    {
        nameToRule.put(RuleName.BLOCK_CONTENTS, listWithOptionalElements(RuleName.STATEMENT, ";"));
        nameToRule.put(RuleName.STATEMENT, new ParserRule(any(rule(RuleName.DEFINITION), rule(RuleName.EXPRESSION)), null));
        nameToRule.put(RuleName.DEFINITION, new ParserRule(sequence(token(TokenType.OPERATOR, "def"), rule(RuleName.ASSIGNMENT)), SyntaxNode.DEFINITION));
    }

    private static class ParserRule
    {
        public final ParserRuleMatcher matcher;
        private final String typeOverride;
        public ParserRule(ParserRuleMatcher matcher, String typeOverride)
        {
            this.matcher = matcher;
            this.typeOverride = typeOverride;
        }
        public void postProcess(SyntaxNode node)
        {
            if (typeOverride != null)
                node.type = typeOverride;
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
                if (exactText != null && token.text != exactText) {
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
            public void postProcess(SyntaxNode node)
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
                super.postProcess(node);
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
            rule.postProcess(node);
        return node;
    }
}
