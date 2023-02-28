package lexicalAnalysis;

import java.util.ArrayList;
import java.util.HashMap;

public class Lexer {
    private String source;
    private ArrayList<Lexeme> lexemes = new ArrayList<>();

    private int currentPosition = 0;
    private int startOfCurrentLexeme = 0;
    private int lineNumber = 1;

    private final HashMap<String, Types> keywords;

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

    private boolean match(char expected) {
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

    private void eror(String message) {
        C10H15N.syntaxError(message, lineNumber);
    }


    private HashMap<String, Types> getKeywords() {
        HashMap<String, Types> keywords = new HashMap<>();

        // Data types
        keywords.put("george", Types.GEORGE);
        keywords.put("integer", Types.INTERGER);
        keywords.put("string", Types.STRING);
        keywords.put("matrix", Types.MATRIX);
        keywords.put("char", Types.CHAR);
        keywords.put("dos", Types.DOS);

        // Initialization
        keywords.put("assignment", Types.ASSIGNMENT);
        keywords.put("var", Types.VAR);
        keywords.put("arraylisttoken", Types.ARRAYLISTTOKEN);
        keywords.put("matrixtoken", Types.MATRIXTOKEN);
        keywords.put("arraytoken", Types.ARRAYTOKEN);
        keywords.put("matrixsize", Types.MATRIXSIZE);

        // Looping
        keywords.put("while", Types.WHILE);
        keywords.put("indefinitlypreform", Types.INDEFINITELYPERFORM);
        keywords.put("whilethisisbasicallytrue", Types.WHILETHISISBASICALLYTRUE);
        keywords.put("for", Types.FOR);
        keywords.put("foreach", Types.FOREACH);
        keywords.put("loopincrementplus", Types.LOOPINCREMENTPLUS);
        keywords.put("loopincrementminus", Types.LOOPINCREMENTMINUS);
        keywords.put("percenterror", Types.PERCENTERROR);

        // Operators
        keywords.put("plus", Types.PLUS);
        keywords.put("plusplus", Types.PLUS_PLUS);
        keywords.put("minus", Types.MINUS);
        keywords.put("minusminus", Types.MINUS_MINUS);
        keywords.put("inverse", Types.INVERSE);
        keywords.put("multiply", Types.MULTIPLY);
        keywords.put("divide", Types.DIVIDE);
        keywords.put("exponentiate", Types.EXPONENTIATE);
        keywords.put("and", Types.AND);
        keywords.put("or", Types.OR);
        keywords.put("not", Types.NOT);
        keywords.put("minus_unary", Types.MINUS_UNARY);

        // Conditionals
        keywords.put("if", Types.IF);
        keywords.put("elseif", Types.ELSEIF);
        keywords.put("else", Types.ELSE);

        // Tokens
        keywords.put("obracket", Types.OBRACKET);
        keywords.put("cbracket", Types.CBRACKET);
        keywords.put("oparen", Types.OPAREN);
        keywords.put("cparen", Types.CPAREN);
        keywords.put("oarraylist", Types.OARRAYLIST);
        keywords.put("semi_colon", Types.SEMI_COLON);

        return keywords;
    }


}
