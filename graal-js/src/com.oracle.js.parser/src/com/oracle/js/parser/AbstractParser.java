/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.js.parser;

import static com.oracle.js.parser.TokenType.COMMENT;
import static com.oracle.js.parser.TokenType.DIRECTIVE_COMMENT;
import static com.oracle.js.parser.TokenType.EOF;
import static com.oracle.js.parser.TokenType.EOL;
import static com.oracle.js.parser.TokenType.IDENT;

import java.math.BigInteger;

import java.util.function.Function;

import com.oracle.js.parser.Lexer.LexerToken;
import com.oracle.js.parser.ir.IdentNode;
import com.oracle.js.parser.ir.LiteralNode;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Base class for parsers.
 */
public abstract class AbstractParser {

    private static final String MSG_EXPECTED = "expected";
    private static final String MSG_EXPECTED_STMT = "expected.stmt";
    private static final String MSG_PARSER_ERROR = "parser.error.";

    /** Source to parse. */
    protected final Source source;

    /** Error manager to report errors. */
    protected final ErrorManager errors;

    /** Stream of lex tokens to parse. */
    protected TokenStream stream;

    /** Index of current token. */
    protected int k;

    /** Previous token - accessible to sub classes */
    protected long previousToken;

    /** Descriptor of current token. */
    protected long token;

    /** Type of current token. */
    protected TokenType type;

    /** Type of last token. */
    protected TokenType last;

    /** Start position of current token. */
    protected int start;

    /** Finish position of previous token. */
    protected int finish;

    /** Current line number. */
    protected int line;

    /** Position of last EOL + 1. */
    protected int linePosition;

    /** Lexer used to scan source content. */
    protected Lexer lexer;

    /** Is this parser running under strict mode? */
    protected boolean isStrictMode;

    /** What should line numbers be counted from? */
    protected final int lineOffset;

    /**
     * Construct a parser.
     *
     * @param source Source to parse.
     * @param errors Error reporting manager.
     * @param strict True if we are in strict mode
     * @param lineOffset Offset from which lines should be counted
     */
    protected AbstractParser(final Source source, final ErrorManager errors, final boolean strict, final int lineOffset) {
        if (source.getLength() > Token.LENGTH_MASK) {
            throw new RuntimeException("Source exceeds size limit of " + Token.LENGTH_MASK + " bytes");
        }
        this.source = source;
        this.errors = errors;
        this.k = -1;
        this.token = Token.toDesc(EOL, 0, 1);
        this.type = EOL;
        this.last = EOL;
        this.isStrictMode = strict;
        this.lineOffset = lineOffset;
    }

    /**
     * Get the ith token.
     *
     * @param i Index of token.
     *
     * @return the token
     */
    protected final long getToken(final int i) {
        // Make sure there are enough tokens available.
        while (i > stream.last()) {
            // If we need to buffer more for lookahead.
            if (stream.isFull()) {
                stream.grow();
            }

            // Get more tokens.
            lexer.lexify();
        }

        return stream.get(i);
    }

    // Checkstyle: stop
    /**
     * Return the tokenType of the ith token.
     *
     * @param i Index of token
     *
     * @return the token type
     */
    protected final TokenType T(final int i) {
        // Get token descriptor and extract tokenType.
        return Token.descType(getToken(i));
    }

    // Checkstyle: resume
    /**
     * Seek next token that is not an EOL or comment.
     *
     * @return tokenType of next token.
     */
    protected final TokenType next() {
        do {
            nextOrEOL();
        } while (type == EOL || type == COMMENT);

        return type;
    }

    /**
     * Seek next token or EOL (skipping comments.)
     *
     * @return tokenType of next token.
     */
    protected final TokenType nextOrEOL() {
        do {
            nextToken();
            if (type == DIRECTIVE_COMMENT) {
                checkDirectiveComment();
            }
        } while (type == COMMENT || type == DIRECTIVE_COMMENT);

        return type;
    }

    // sourceURL= after directive comment
    private static final TruffleString SOURCE_URL_PREFIX = ParserStrings.constant("sourceURL=");

    // currently only @sourceURL=foo supported
    private void checkDirectiveComment() {
        // if already set, ignore this one
        if (source.getExplicitURL() != null) {
            return;
        }

        final TruffleString comment = ((TruffleString) lexer.getValueOf(token, isStrictMode));
        // 4 characters for directive comment marker //@\s or //#\s
        if (ParserStrings.startsWith(comment, SOURCE_URL_PREFIX, 4)) {
            source.setExplicitURL(ParserStrings.lazySubstring(comment, 4 + ParserStrings.length(SOURCE_URL_PREFIX)).toJavaStringUncached());
        }
    }

    /**
     * Seek next token.
     *
     * @return tokenType of next token.
     */
    private TokenType nextToken() {
        if (type != EOF) {
            // Set up next token.
            k++;
            final long lastToken = token;
            final boolean comment = type == COMMENT;
            // Capture last token type, but ignore comments (which are irrelevant for the purpose of
            // newline detection).
            if (!comment) {
                last = type;
                previousToken = token;
            }
            token = getToken(k);
            type = Token.descType(token);

            // do this before the start is changed below
            if (!comment && last != EOL) {
                finish = start + Token.descLength(lastToken);
            }

            if (type == EOL) {
                line = Token.descLength(token);
                linePosition = Token.descPosition(token);
            } else {
                start = Token.descPosition(token);
            }

        }

        return type;
    }

    /**
     * Get the message string for a message ID and arguments
     *
     * @param msgId The Message ID
     * @param args The arguments
     *
     * @return The message string
     */
    protected static String message(final String msgId, final String... args) {
        return ECMAErrors.getMessage(MSG_PARSER_ERROR + msgId, args);
    }

    protected static String message(final String msgId, IdentNode ident) {
        return ECMAErrors.getMessage(MSG_PARSER_ERROR + msgId, ident.getName().toJavaStringUncached());
    }

    /**
     * Report an error.
     *
     * @param message Error message.
     * @param errorToken Offending token.
     * @return ParserException upon failure. Caller should throw and not ignore
     */
    protected final ParserException error(final String message, final long errorToken) {
        return error(JSErrorType.SyntaxError, message, errorToken);
    }

    /**
     * Report an error.
     *
     * @param errorType The error type
     * @param message Error message.
     * @param errorToken Offending token.
     * @return ParserException upon failure. Caller should throw and not ignore
     */
    protected final ParserException error(final JSErrorType errorType, final String message, final long errorToken) {
        final int position = Token.descPosition(errorToken);
        final int lineNum = source.getLine(position);
        final int columnNum = source.getColumn(position);
        return new ParserException(errorType, message, source, lineNum, columnNum, errorToken);
    }

    /**
     * Report an error.
     *
     * @param message Error message.
     * @return ParserException upon failure. Caller should throw and not ignore
     */
    protected final ParserException error(final String message) {
        return error(JSErrorType.SyntaxError, message);
    }

    /**
     * Report an error.
     *
     * @param errorType The error type
     * @param message Error message.
     * @return ParserException upon failure. Caller should throw and not ignore
     */
    protected final ParserException error(final JSErrorType errorType, final String message) {
        final int position = Token.descPosition(token);
        final int column = source.getColumn(position);
        return new ParserException(errorType, message, source, line, column, token);
    }

    /**
     * Report a warning to the error manager.
     *
     * @param errorType The error type of the warning
     * @param message Warning message.
     * @param errorToken error token
     */
    protected final void warning(final JSErrorType errorType, final String message, final long errorToken) {
        errors.warning(error(errorType, message, errorToken));
    }

    /**
     * Generate 'expected' message.
     *
     * @param expected Expected tokenType.
     *
     * @return the message string
     */
    protected final String expectMessage(final TokenType expected) {
        final TruffleString tokenString = Token.toString(source, token);
        String msg;

        if (expected == null) {
            msg = AbstractParser.message(MSG_EXPECTED_STMT, tokenString.toJavaStringUncached());
        } else {
            final TruffleString expectedName = expected.getNameOrType();
            msg = AbstractParser.message(MSG_EXPECTED, expectedName.toJavaStringUncached(), tokenString.toJavaStringUncached());
        }

        return msg;
    }

    protected final String expectMessage(final TokenType expected, final long errorToken) {
        final TruffleString expectedName = expected.getNameOrType();
        final TruffleString tokenString = Token.toString(source, errorToken);
        return AbstractParser.message(MSG_EXPECTED, expectedName.toJavaStringUncached(), tokenString.toJavaStringUncached());
    }

    /**
     * Check current token and advance to the next token.
     *
     * @param expected Expected tokenType.
     *
     * @throws ParserException on unexpected token type
     */
    protected final void expect(final TokenType expected) throws ParserException {
        expectDontAdvance(expected);
        next();
    }

    /**
     * Check current token, but don't advance to the next token.
     *
     * @param expected Expected tokenType.
     *
     * @throws ParserException on unexpected token type
     */
    protected final void expectDontAdvance(final TokenType expected) throws ParserException {
        if (type != expected) {
            throw error(expectMessage(expected));
        }
    }

    /**
     * Get the value of the current token. If the current token contains an escape sequence, the
     * method does not attempt to convert it.
     *
     * @return JavaScript value of the token.
     */
    protected final Object getValueNoEscape() {
        try {
            return lexer.getValueOf(token, isStrictMode, false);
        } catch (final ParserException e) {
            errors.error(e);
        }
        return null;
    }

    /**
     * Get the value of the current token.
     *
     * @return JavaScript value of the token.
     */
    protected final Object getValue() {
        return getValue(token);
    }

    /**
     * Get the value of a specific token
     *
     * @param valueToken the token
     *
     * @return JavaScript value of the token
     */
    protected final Object getValue(final long valueToken) {
        try {
            return lexer.getValueOf(valueToken, isStrictMode);
        } catch (final ParserException e) {
            errors.error(e);
        }

        return null;
    }

    /**
     * Certain future reserved words can be used as identifiers in non-strict mode. Check if the
     * current token is one such.
     *
     * @return true if non strict mode identifier
     */
    protected final boolean isNonStrictModeIdent() {
        return !isStrictMode && type.getKind() == TokenKind.FUTURESTRICT;
    }

    /**
     * Get ident.
     *
     * @return Ident node.
     */
    protected final IdentNode getIdent() {
        // Capture IDENT token.
        long identToken = token;

        if (type == IDENT) {
            final TruffleString ident = (TruffleString) getValue(identToken);

            next();

            return createIdentNode(identToken, finish, ident);
        } else if (type.isContextualKeyword() || isNonStrictModeIdent()) {
            final TruffleString ident = type.getName();

            next();

            return new IdentNode(identToken, finish, ident);
        } else {
            // Not an IDENT.
            throw error(expectMessage(IDENT));
        }
    }

    /**
     * Creates a new {@link IdentNode} as if invoked with a
     * {@link IdentNode#IdentNode(long, int, TruffleString) constructor} but making sure that the
     * {@code name} is deduplicated within this parse job.
     *
     * @param identToken the token for the new {@code IdentNode}
     * @param identFinish the finish for the new {@code IdentNode}
     * @param name the name for the new {@code IdentNode}. It will be de-duplicated.
     * @return a newly constructed {@code IdentNode} with the specified token, finish, and name; the
     *         name will be deduplicated.
     */
    protected IdentNode createIdentNode(final long identToken, final int identFinish, final TruffleString name) {
        assert isInterned(name) : name;
        return new IdentNode(identToken, identFinish, name);
    }

    private boolean isInterned(final TruffleString name) {
        return isSame(lexer.stringIntern(name), name);
    }

    private static boolean isSame(Object a, Object b) {
        return a == b;
    }

    /**
     * Check if current token is in identifier name
     *
     * @return true if current token is an identifier name
     */
    protected final boolean isIdentifierName() {
        return isIdentifierName(token);
    }

    /**
     * Check if token is an identifier name
     *
     * @return true if token is an identifier name
     */
    protected final boolean isIdentifierName(long currentToken) {
        final TokenType currentType = Token.descType(currentToken);
        assert currentType != IDENT; // handled before
        final TokenKind kind = currentType.getKind();
        if (kind == TokenKind.KEYWORD || kind == TokenKind.FUTURE || kind == TokenKind.FUTURESTRICT || kind == TokenKind.CONTEXTUAL) {
            return true;
        }

        // only literals allowed are null, false and true
        if (kind == TokenKind.LITERAL) {
            switch (currentType) {
                case FALSE:
                case NULL:
                case TRUE:
                    return true;
                default:
                    return false;
            }
        }

        // Fake out identifier.
        final long identToken = Token.recast(currentToken, IDENT);
        // Get IDENT.
        final TruffleString ident = (TruffleString) getValue(identToken);
        return ident != null && !ident.isEmpty() && Character.isJavaIdentifierStart(ParserStrings.charAt(ident, 0));
    }

    /**
     * Create an IdentNode from the current token
     *
     * @return an IdentNode representing the current token
     */
    protected final IdentNode getIdentifierName() {
        if (type == IDENT) {
            return getIdent();
        } else if (isIdentifierName()) {
            // Fake out identifier.
            final long identToken = Token.recast(token, IDENT);
            // Get IDENT.
            final TruffleString ident = (TruffleString) getValue(identToken);
            next();
            // Create IDENT node.
            return createIdentNode(identToken, finish, lexer.stringIntern(ident));
        } else {
            expect(IDENT);
            return null;
        }
    }

    /**
     * Create a LiteralNode from the current token
     *
     * @return LiteralNode representing the current token
     * @throws ParserException if any literals fails to parse
     */
    protected final LiteralNode<?> getLiteral() throws ParserException {
        // Capture LITERAL token.
        final long literalToken = token;

        // Create literal node.
        final Object value = getValue();
        // Advance to have a correct finish
        next();

        LiteralNode<?> node = null;

        if (value == null) {
            node = LiteralNode.newInstance(literalToken, finish);
        } else if (value instanceof BigInteger) {
            node = LiteralNode.newInstance(literalToken, finish, (BigInteger) value);
        } else if (value instanceof Number) {
            node = LiteralNode.newInstance(literalToken, finish, (Number) value, getNumberToStringConverter());
        } else if (value instanceof TruffleString) {
            node = LiteralNode.newInstance(literalToken, (TruffleString) value);
        } else if (value instanceof LexerToken) {
            validateLexerToken((LexerToken) value);
            node = LiteralNode.newInstance(literalToken, finish, (LexerToken) value);
        } else {
            assert false : "unknown type for LiteralNode: " + value.getClass();
        }

        return node;
    }

    /**
     * Lexer token validation hook for subclasses.
     *
     * @param lexerToken the lexer token to validate
     */
    protected void validateLexerToken(final LexerToken lexerToken) {
    }

    /**
     * Custom number-to-string converter used to convert numeric property names to strings.
     *
     * @return custom number-to-string converter or {@code null} to use the default converter
     */
    protected Function<Number, TruffleString> getNumberToStringConverter() {
        return null;
    }
}
