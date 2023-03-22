package src.lexicalAnalysis;

import src.C10H15N;

import java.util.ArrayList;
import java.util.HashMap;


import static src.C10H15N.syntaxError;


public class Lexer {
    private String source;
    private ArrayList<Lexeme> lexemes = new ArrayList<>();

    private int currentPosition = 0;
    private int startOfCurrentLexeme = 0;
    private int lineNumber = 1;

    private final HashMap<String, Types> keywords;


    //Core Lexing
    public ArrayList<Lexeme> lex() {
        while (!isAtEnd()) {
            startOfCurrentLexeme = currentPosition;
            Lexeme nextLexeme = getNextLexeme();
            if (nextLexeme != null)
                lexemes.add(nextLexeme);
        }
        lexemes.add(new Lexeme(Types.END_OF_FILE, lineNumber));
        return lexemes;
    }


    private Lexeme getNextLexeme() {
        char c = advance();

        switch (c) {
            case ' ', '\t', '\n', '\r' -> {
                return null;
            }

            case '(' -> {
                return new Lexeme(Types.OPAREN, lineNumber);
            }
            case ')' -> {
                return new Lexeme(Types.CPAREN, lineNumber);
            }
            case ',' -> {
                return new Lexeme(Types.COMMA, lineNumber);
            }
            case '.' -> {
                return new Lexeme(Types.PERIOD, lineNumber);
            }
            case ':' -> {
                return new Lexeme(Types.FUNC_DEFINITION, lineNumber);
            }
            case '%' -> {
                return new Lexeme(Types.PERCENTERROR, lineNumber);
            }
            case '*' -> {
                return new Lexeme(Types.MULTIPLY, lineNumber);
            }
            case '/' -> {
                return new Lexeme(Types.DIVIDE, lineNumber);
            }
            case '˚' -> {
                return new Lexeme(Types.DOT_PRODUCT, lineNumber);
            }
            case 'î' -> {
                return new Lexeme(Types.EXPONENTIATE, lineNumber);
            }
            case 'í' -> {
                return new Lexeme(Types.INVERSE, lineNumber);
            }
            case '|' -> {
                return new Lexeme(Types.OR, lineNumber);
            }
            case '&' -> {
                return new Lexeme(Types.AND, lineNumber);
            }
            case '!' -> {
                return new Lexeme(Types.NOT, lineNumber);
            }
            case '\\' -> {
                return new Lexeme(Types.MATRIX_SEPERATOR, lineNumber);
            }
            case ';' -> {
                return new Lexeme(Types.SEMI_COLON, lineNumber);
            }
            case '`' -> {
                return new Lexeme(Types.MATRIXSIZE, lineNumber);
            }
            case '{' -> {
                return new Lexeme(Types.OBRACE, lineNumber);
            }
            case '}' -> {
                return new Lexeme(Types.CBRACE, lineNumber);
            }
            case '∆' -> {
                return new Lexeme(Types.FOREACH_DELTA, lineNumber);
            }
            case '+' -> {
                if (match('+'))
                    return new Lexeme(Types.PLUS_PLUS, lineNumber);
                else
                    return new Lexeme(Types.PLUS, lineNumber);
            }
            case '-' -> {
                if (match('-'))
                    return new Lexeme(Types.MINUS_MINUS, lineNumber);
                else
                    return new Lexeme(Types.MINUS, lineNumber);
            }
            case '[' -> {
                if (match('['))
                    return new Lexeme(Types.OMATRIX, lineNumber);

                else
                    return new Lexeme(Types.OBRACKET, lineNumber);
            }
            case ']' -> {
                if (match(']'))
                    return new Lexeme(Types.CMATRIX, lineNumber);

                else
                    return new Lexeme(Types.CBRACKET, lineNumber);
            }
            case '<' -> {
                if (match('='))
                    return new Lexeme(Types.LESS_EQ, lineNumber);
                else if (match('<'))
                    return new Lexeme(Types.OARRAYLIST, lineNumber);
                else
                    return new Lexeme(Types.LESS, lineNumber);
            }
            case '>' -> {
                if (match('='))
                    return new Lexeme(Types.GREATER_EQ, lineNumber);
                else if (match('>'))
                    return new Lexeme(Types.CARRAYLIST, lineNumber);
                else
                    return new Lexeme(Types.GREATER, lineNumber);
            }
            case '¬' -> {
                if (match('+'))
                    return new Lexeme(Types.LOOPINCREMENTPLUS, lineNumber);
                else if (match('-'))
                    return new Lexeme(Types.LOOPINCREMENTMINUS, lineNumber);
                else
                    return new Lexeme(Types.GREATER, lineNumber);
            }
            case '=' -> {
                if (match('='))
                    return new Lexeme(Types.EQUALS, lineNumber);
                else
                    return new Lexeme(Types.ASSIGNMENT, lineNumber);
            }
            case '"' -> {
                return lexString();
            }
            case 'ç' -> {
                return lexComment();
            }
            default -> {
                if (isDigit(c)) {
                    return lexNumber();
                } else if (isAlpha(c)) {
                    return lexIdentifierOrKeyword();
                } else {
                    error("Unrecognized character '" + c + "'");
                    return null;
                }
            }
        }
    }

    //I only support block comments, starting with ç and ending with ø
    private Lexeme lexComment() {
        while (!(isAtEnd() || match('ø'))) {
            advance();
        }
        return null;
    }

    private Lexeme lexIdentifierOrKeyword() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        //is thi
        String text = source.substring(startOfCurrentLexeme, currentPosition);
        Types type = keywords.get(text);


        if (type == null) {
            return new Lexeme(Types.IDENTIFIER, lineNumber, text);
        } else if (type == Types.TRUE) {
            return new Lexeme(Types.GEORGE, lineNumber, true);
        } else if (type == Types.FALSE) {
            return new Lexeme(Types.GEORGE, lineNumber, false);
        }


        return new Lexeme(type, lineNumber);
    }


    private Lexeme lexNumber() {
        boolean isInteger = true;
        while (isDigit(peek())) advance();
        if (peek() == '.') {
            isInteger = false;
            if (!isDigit(peekNext())) {
                String malformedReal = source.substring(startOfCurrentLexeme, currentPosition + 1);
                syntaxError("Malformed Real Number: " + malformedReal + " ", currentPosition);
            }
            advance();

            while (isDigit(peek())) advance();
        }
        String numberString = source.substring(startOfCurrentLexeme, currentPosition);
        if (isInteger) {
            int intValue = Integer.parseInt(numberString);
            return new Lexeme(Types.INTERGER, lineNumber, intValue);
        } else {
            double realValue = Double.parseDouble(numberString);
            return new Lexeme(Types.DOS, lineNumber, realValue);
        }

    }

    private Lexeme lexString() {
        while (!(isAtEnd() || peek() == '"')) {
            advance();
        }

        String str = source.substring(startOfCurrentLexeme + 1, currentPosition);

        if (isAtEnd()) {
            error("Unterminated string: '" + str + "'");
        } else {
            advance();
        }

        return new Lexeme(Types.STRING, lineNumber, str);
    }


    public Lexer(String source) {
        this.source = source;
        this.lexemes = new ArrayList<>();
        this.keywords = getKeywords();
        this.currentPosition = 0;
        this.startOfCurrentLexeme = 0;
        this.lineNumber = 1;

    }


    private boolean isAtEnd() {
        return currentPosition >= source.length();
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(currentPosition);
    }

    private char peekNext() {
        if (currentPosition + 1 >= source.length()) return '\0';
        return source.charAt(currentPosition + 1);
    }

    boolean match(char expected) {
        if (isAtEnd() || source.charAt(currentPosition) != expected)
            return false;
        currentPosition++;
        return true;
    }


    private char advance() {
        char currentChar = source.charAt(currentPosition);
        if (currentChar == '\n' || currentChar == '\r')
            lineNumber++;
        currentPosition++;
        return currentChar;
    }


    // Supplemental Helper Methods
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void error(String message) {
        syntaxError(message, lineNumber);
    }


    private HashMap<String, Types> getKeywords() {
        HashMap<String, Types> keywords = new HashMap<>();

        // Data types
        keywords.put("george", Types.GEORGE);
        keywords.put("interger", Types.INTERGER);
        keywords.put("string", Types.STRING);
        keywords.put("matrix", Types.MATRIX);
        keywords.put("char", Types.CHAR);
        keywords.put("dos", Types.DOS);
        keywords.put("true", Types.TRUE);
        keywords.put("false", Types.FALSE);

        // Initialization

        keywords.put("var", Types.VAR);
        keywords.put("return", Types.RETURN);
        keywords.put("collection", Types.COLLECTION);
        keywords.put("matrixsize", Types.MATRIXSIZE);

        // Looping
        keywords.put("while", Types.WHILE);
        keywords.put("indefinitlypreform", Types.INDEFINITELYPERFORM);
        keywords.put("whilethisisbasicallytrue", Types.WHILETHISISBASICALLYTRUE);
        keywords.put("for", Types.FOR);
        keywords.put("foreach", Types.FOREACH);
        keywords.put("loopincrementplus", Types.LOOPINCREMENTPLUS);
        keywords.put("loopincrementminus", Types.LOOPINCREMENTMINUS);
        keywords.put("break", Types.BREAK);
        keywords.put("get", Types.GET);

        // Conditionals
        keywords.put("if", Types.IF);
        keywords.put("elseif", Types.ELSEIF);
        keywords.put("else", Types.ELSE);

        return keywords;
    }

}
