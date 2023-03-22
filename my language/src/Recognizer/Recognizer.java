package src.Recognizer;

import src.C10H15N;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.util.ArrayList;

import static java.lang.System.exit;
import static src.lexicalAnalysis.Types.*;

public class Recognizer {
    private static final boolean printDebugMessages = true;
    //instance var
    private Lexeme currentLexeme;
    private final ArrayList<Lexeme> lexemes;
    private int nextLexemeIndex;

    //core support
    private Types peek() {
        return currentLexeme.getType();
    }

    private Types peekNext() {
        if (nextLexemeIndex >= lexemes.size()) return null;
        return lexemes.get(nextLexemeIndex).getType();
    }

    private boolean check(Types type) {
        return currentLexeme.getType() == type;
    }

    private boolean checkNext(Types type) {
        if (nextLexemeIndex > lexemes.size()) return false;
        return lexemes.get(nextLexemeIndex).getType() == type;
    }

    private Lexeme consume(Types expected) {
        if (check(expected)) return advance();

        error("Expected " + expected + " but found " + currentLexeme + ".");
        return new Lexeme(ERROR, currentLexeme.getLineNumber());
    }

    private Lexeme advance() {
        Lexeme toReturn = currentLexeme;
        currentLexeme = lexemes.get(nextLexemeIndex);
        nextLexemeIndex++;
        return toReturn;
    }

    //constructor
    public Recognizer(ArrayList<Lexeme> lexemes) {
        this.lexemes = lexemes;
        this.nextLexemeIndex = 0;
        System.out.println(lexemes);
        advance();
        program();


    }

    private void program() {
        statementList();

    }

    private void statementList() {
        statement();
        while (statementPending()) {

            statement();
        }


    }

    private boolean statementPending() {

        return check(VAR) ||
                checkNext(FUNC_DEFINITION) ||
                check(IDENTIFIER) ||
                check(IF) ||
                check(WHILE) ||
                check(INDEFINITELYPERFORM) ||
                check(WHILETHISISBASICALLYTRUE) ||
                check(FOR) ||
                check(FOREACH) ||
                check(OBRACE) ||
                check(RETURN) ||
                check(BREAK) ||
                check(COLLECTION) ||
                check(GET);
    }

    private void statement() {

        if (check(VAR)) {

            varInitialization();

        } else if (check(IDENTIFIER)) {

            if (checkNext(ASSIGNMENT)) {
                consume(IDENTIFIER);
                consume(ASSIGNMENT);
                expression();

            } else if (functionCallPending()) {
                functionCall();
            } else if (checkNext(OBRACKET)) {
                consume(OBRACKET);
                number();
                consume(CBRACKET);
            } else if (checkNext(OARRAYLIST)) {
                consume(OARRAYLIST);
                if (numberPending()) {
                    number();
                } else if (check(PLUS)) {
                    consume(PLUS);
                    if (currentLexeme.getIntValue() == 0) {
                        consume(INTERGER);
                        consume(CARRAYLIST);
                        consume(ASSIGNMENT);
                        expression();
                        consume(SEMI_COLON);
                    } else {
                        if (check(CARRAYLIST)) {
                            consume(CARRAYLIST);
                            consume(ASSIGNMENT);
                            expression();
                            consume(SEMI_COLON);
                        } else {
                            error("Idk how you got there but, Malformed arraylist Assinment");
                        }
                    }

                }
            } else if (check(OMATRIX)) {
                number();
                while (check(COMMA)) {
                    consume(COMMA);
                    number();
                }
                consume(CMATRIX);
                consume(ASSIGNMENT);
                expression();

            } else if (checkNext(FUNC_DEFINITION)) {
                functionDefinition();
            } else {

                error("malformed assignment");
                advance();
            }


        } else if (check(IF)) {

            conditional();

        } else if (check(WHILE)) {
            whileLoop();
            block();
        } else if (check(INDEFINITELYPERFORM)) {
            indefinitleyPreform();
            block();
        } else if (check(WHILETHISISBASICALLYTRUE)) {
            whileThisIsBasicallyTrue();
            block();
        } else if (check(FOR)) {
            forLoop();
        } else if (check(FOREACH)) {
            forEach();
        } else if (check(OBRACE)) {
            block();
        } else if (check(RETURN)) {
            returnStatement();
            consume(SEMI_COLON);
        } else if (check(BREAK)) {
            consume(BREAK);
            consume(SEMI_COLON);
        } else if (check(COLLECTION)) {
            collectionInitialization();
            consume(SEMI_COLON);
        } else if (check(GET)) {
            collectionGetter();

        } else {

            error("Expecting a statement");
            advance();
        }
    }

    private void collectionGetter() {
        consume(GET);
        consume(IDENTIFIER);
        if (check(OBRACKET)) {
            consume(OBRACKET);
            number();
            consume(CBRACKET);
        } else if (check(OARRAYLIST)) {
            consume(OARRAYLIST);
            number();
            consume(CARRAYLIST);
        } else if (check(OMATRIX)) {
            number();
            while (check(COMMA)) {
                consume(COMMA);
                number();
            }
            consume(CMATRIX);
        } else {
            error("malformed collection getter");
            advance();
        }
    }

    private void expressionList() {
        expression();
        while (check(COMMA)) {
            consume(COMMA);
            expression();
        }
    }

    private void varInitialization() {
        consume(VAR);
        varIdentifierList();
        dataType();
        consume(ASSIGNMENT);
        expression();
        consume(SEMI_COLON);

    }

    private void varIdentifierList() {
        consume(IDENTIFIER);
        while (check(COMMA)) {
            consume(COMMA);
            consume(IDENTIFIER);
        }
    }

    private void functionDefinition() {
        consume(IDENTIFIER);
        consume(FUNC_DEFINITION);
        dataType();
        consume(OPAREN);
        parameterList();
        consume(CPAREN);
        block();
    }

    private void parameterList() {
        if (!check(CPAREN)) {
            dataType();
            consume(IDENTIFIER);
        }


    }

    private void whileLoop() {
        consume(WHILE);
        consume(OPAREN);
        expression();
        consume(CPAREN);
    }

    private void forLoop() {
        consume(FOR);
        consume(OPAREN);
        varInitialization();
        expression();
        consume(CPAREN);
        if (check(LOOPINCREMENTPLUS) || check(LOOPINCREMENTMINUS)) advance();
        else error("loop increment direction required malformed loop");
        block();
    }

    private void forEach() {
        consume(FOREACH);
        consume(OPAREN);
        consume(IDENTIFIER);
        consume(FOREACH_DELTA);
        consume(IDENTIFIER);
        block();
    }

    private void indefinitleyPreform() {
        consume(INDEFINITELYPERFORM);
        block();
    }

    private void whileThisIsBasicallyTrue() {
        consume(WHILETHISISBASICALLYTRUE);
        consume(OPAREN);
        expression();
        consume(CPAREN);
        number();
        consume(PERCENTERROR);
        block();
    }


    private void dataType() {
        if (check(GEORGE)) {
            advance();
        } else if (check(INTERGER)) {
            advance();
        } else if (check(STRING)) {
            advance();
        } else if (check(MATRIX)) {
            advance();
        } else if (check(CHAR)) {
            advance();
        } else if (check(DOS)) {
            advance();
        } else {
            error("Expected data type");
        }
    }


    private void block() {
        consume(OBRACE);
        if (checkNext(CBRACE)) {
            consume(CBRACE);
        } else {
            statementList();
            consume(CBRACE);
        }
    }

    private void returnStatement() {
        consume(RETURN);
        expression();
    }

    private void expression() {

        if (binaryExpressionPending()) {
            binaryExpression();
        } else if (unaryExpressionPending()) {
            unaryExpression();
        } else if (parenthesizedExpressionPending()) {
            parenthesizedExpression();
        } else {
            error("Expected an expression");
        }

    }


    private boolean binaryExpressionPending() {
        return primaryPending() || binaryOperatorPending() || primaryPending();
    }

    private void parenthesizedExpression() {
        consume(OPAREN);
        expression();
        consume(CPAREN);
    }

    private boolean parenthesizedExpressionPending() {
        return check(OPAREN);
    }

    private boolean unaryExpressionPending() {
        switch (currentLexeme.getType()) {
            case INVERSE:
            case MINUS_UNARY:
            case NOT:
            case PLUS_PLUS:
            case MINUS_MINUS:
                return true;
            case IDENTIFIER:
                if (peekNext() == PLUS_PLUS || peekNext() == MINUS_MINUS) {
                    return true;
                }
            default:
                return primaryPending();
        }

    }

    private void unaryExpression() {
        switch (currentLexeme.getType()) {
            case MINUS_UNARY:
                advance();
                consume(IDENTIFIER);
            case NOT:
                advance();
                consume(IDENTIFIER);
            case INVERSE:
                advance();
                consume(IDENTIFIER);
            case IDENTIFIER:
                if (peekNext() == PLUS_PLUS || peekNext() == MINUS_MINUS) {
                    advance();
                    advance();
                }
            default:
                primary();

        }
    }

    private boolean binaryOperatorPending() {
        return switch (currentLexeme.getType()) {
            case PLUS, MINUS, MULTIPLY, DIVIDE, EXPONENTIATE, AND, OR, GREATER, GREATER_EQ, LESS, LESS_EQ, EQUALS, NOT_EQUAL, DOT_PRODUCT -> true;
            default -> false;
        };
    }

    private void binaryOperator() {
        switch (currentLexeme.getType()) {
            case PLUS, MINUS, MULTIPLY, DIVIDE, EXPONENTIATE, AND, OR, GREATER, GREATER_EQ, LESS, LESS_EQ, EQUALS, NOT_EQUAL, DOT_PRODUCT -> advance();
            default -> error("Expecting Binary Operator");
        }

    }

    private void binaryExpression() {
        primary();
        while (binaryOperatorPending()) {
            binaryOperator();
            primary();
        }
    }


    private boolean functionCallPending() {
        return checkNext(OPAREN);
    }

    private void functionCall() {
        consume(IDENTIFIER);
        consume(OPAREN);
        expressionList();
        consume(CPAREN);

    }


    private void primary() {
        if (check(DOS) || check(INTERGER)) {
            number();
        } else if (check(STRING)) {
            consume(STRING);
        } else if (check(IDENTIFIER)) {
            consume(IDENTIFIER);
        } else if (booleanLiteralPending()) {
            booleanLiteral();
        } else if (functionCallPending()) {
            functionCall();
        } else if (collectionPending()) {
            collection();
        } else if (check(CHAR)) {
            consume(CHAR);
        } else if (check(OPAREN)) {
            parenthesizedExpression();
        } else {
            error("Expected a primary expression.");
        }
    }

    private void collectionInitialization() {
        consume(COLLECTION);
        consume(IDENTIFIER);
        dataType();
        collectionHelper();


    }

    private void collectionHelper() {


        if (check(OBRACKET)) {
            if (checkNext(CBRACKET)) {
                //must be initialiizing an array
                consume(OBRACKET);
                consume(CBRACKET);
                consume(ASSIGNMENT);
                expression();


            } else if (checkNext(INTERGER)) {
                //must be declaring an array with size (INTERGER)
                consume(OBRACKET);
                consume(INTERGER);
                consume(CBRACKET);
            } else {
                error("Malformed collection Initialization or Delcaration");
                advance();
            }
        } else if (check(OARRAYLIST)) {
            consume(OARRAYLIST);
            consume(CARRAYLIST);
            if (check(ASSIGNMENT)) {
                consume(ASSIGNMENT);
                expression();
            }
        } else if (check(OMATRIX)) {
            consume(OMATRIX);
            consume(CMATRIX);
            consume(ASSIGNMENT);
            if (checkNext(MATRIXSIZE)) {
                consume(INTERGER);
                consume(MATRIXSIZE);
                consume(INTERGER);
            } else {
                expression();
            }
        }

    }

    private void number() {
        if (check(DOS)) {
            consume(DOS);
        } else consume(INTERGER);
    }


    private boolean primaryPending() {
        return numberPending() || check(STRING) || check(IDENTIFIER) || booleanLiteralPending()
                || functionCallPending() || collectionPending() || check(CHAR) || parenthesizedExpressionPending();
    }

    private boolean numberPending() {
        return check(DOS) || check(INTERGER);
    }

    private boolean booleanLiteralPending() {
        return (check(TRUE) || check(FALSE));
    }

    private void booleanLiteral() {
        if (booleanLiteralPending()) {
            advance();
        }
    }

    private void conditional() {
        int a = 0;
        if (check(IF)) {
            ifConditional();
            a++;

        }
        while (check(ELSEIF)) {
            elseIfConditional();
            a++;
        }
        while (check(ELSE)) {

            elseConditional();
            a++;
        }
        if (a == 0) {
            error("Expected conditional keyword (IF, ELSEIF, or ELSE)");
        }
    }

    private void ifConditional() {
        consume(IF);
        consume(OPAREN);
        expression();
        consume(CPAREN);
        block();
    }

    private void elseIfConditional() {
        consume(ELSEIF);
        consume(OPAREN);
        expression();
        consume(CPAREN);
        block();
    }

    private void elseConditional() {
        consume(ELSE);
        consume(OPAREN);
        expression();
        consume(CPAREN);
        block();
    }

    private void collection() {

        if (check(OBRACKET)) {

            array();
        } else if (check(OARRAYLIST)) {
            linkedList();
        } else if (check(OMATRIX)) {

            Matrix();
        }
    }

    private boolean collectionPending() {
        if (check(OBRACKET)) {
            return true;
        } else if (check(OARRAYLIST)) {
            return true;
        } else return check(OMATRIX);
    }

    private void array() {
        consume(OBRACKET);
        expressionList();
        consume(CBRACKET);
    }

    private void linkedList() {
        consume(OARRAYLIST);
        expressionList();
        consume(CARRAYLIST);
    }

    private void Matrix() {
        consume(OMATRIX);
        expressionList();
        while (check(MATRIX_SEPERATOR)) {
            consume(MATRIX_SEPERATOR);
            expressionList();
        }
        consume(CMATRIX);
    }

    //Error Repporting
    private Lexeme error(String message) {
        C10H15N.syntaxError(message, currentLexeme);
        return new Lexeme(ERROR, currentLexeme.getLineNumber(), message);
    }

    //debugging
    private static void log(String message) {
        if (printDebugMessages) System.out.println(message);
    }

    private static void logHeading(String heading) {
        if (printDebugMessages)
            System.out.println("--------" + "heading" + "--------");
    }


}