package src.Recognizer;

import src.C10H15N;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;


import java.util.ArrayList;

import static java.lang.invoke.MethodHandles.loop;
import static src.lexicalAnalysis.Types.*;


public class Recognizer {
    private static final boolean printDebugMessages = true;
    //instance var
    private Lexeme currentLexeme;
    private ArrayList<Lexeme> lexemes;
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

        error("Expected "  + expected + "but found " + currentLexeme + ".");
        return new Lexeme(ERROR, currentLexeme.getLineNumber()));
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
        advance();
    }

    private void statement() {
        if (check(VAR)) {
            varInitialization();
            consume(SEMI_COLON);
        } else if (check(IF)) {
            conditional();
            consume(SEMI_COLON);
        } else if (checkNext(FUNC_DEFINITION)) {

            functionDefinition();
            consume(SEMI_COLON);
        } else if (check(WHILE)) {
            whileLoop();
            block();
        } else if (check(INDEFINITELYPERFORM)) {
            indefinitleyPreform();
            block();

        } else if (check(OBRACE)) {
            block();
        } else if (check(RETURN)) {
            returnStatement();
            consume(SEMI_COLON);
        } else if (check(BREAK)) {
            consume(BREAK);
            consume(SEMI_COLON);
        } else if (check(OBRACKET) || check(LEFT_CURLY_BRACE)) {
            collectionActivities();
            consume(SEMI_COLON);
        } else {
            error("Expecting a statement");
        }
    }

    private void expressionList() {
        expression();
        while(check(COMMA)) {
            consume(COMMA);
            expression();
        }
    }

    private void varInitialization() {
        consume(VAR);
        varIdentifierList();

    }

    private void varIdentifierList() {
    }

    private void functionDefinition() {
        consume(IDENTIFIER);
        consume(FUNC_DEFINITION);
        dataType();
        consume(OPAREN);
        consume(PARAMETERLIST);
        consume (CPAREN);
        block();
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
        conditional();
        consume(CPAREN);
        if(check(LOOPINCREMENTPLUS) || check(LOOPINCREMENTMINUS)) advance();
        else error("loop increment direction required");
        block();
    }
    private void indefinitleyPreform() {
        consume(INDEFINITELYPERFORM);
        block();
    }
    private void whileThisIsBasicallyTrue() {
        consume(WHILETHISISBASICALLYTRUE);
        consume(OPAREN)
        expression();
        consume(CPAREN)
        number();
        consume(PERCENTERROR);
        block();
    }
    private void statr
    private void array() {
        consume(OBRACKET);
        expressionList();
        consume(CBRACKET);
    }
    private void linkedList() {
        consume(OARRAYLIST,);
        expressionList();
        consume(CARRAYLIST);
    }
    private void Matrix() {
        consume(OMATRIX);
        expressionList();
        if(peekNext() != CMATRIX || peekNext() != CBRACKET) {
            error("improper end of a matrix");
        } else if (peekNext() == CMATRIX) {
            advance();
        } else {
            while(check(OBRACKET) ||check(CBRACKET)) {
                if(check(OBRACKET)) {
                    consume(OBRACKET);
                    expressionList();
                } else {
                    consume(CBRACKET);
                    consume(COMMA);
                }
            }
        }
    }
    private void collectionInitializationPending() {


    }

    private boolean dataType() {
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
        statement();
        consume(CBRACE);
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
        return primaryPending() && binaryOperatorPending() && primaryPending();
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
        };
    }
    private void binaryExpression() {
        primary();
        while (binaryOperatorPending()) {
            binaryOperator();
            primary();
        }
    }


    private void functionCallPending() {


    }

    private void functionCall() {

    }


    private Lexeme primary() {
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
            return error("Expected a primary expression.");
        }
    }

    private void number() {
            if (check(DOS))  {
                consume(DOS);
            } else consume(INTERGER);
        }
    }

    private boolean primaryPending() {
        return number(); || check(STRING) || check(IDENTIFIER) || booleanLiteralPending()
                || functionCallPending() || collectionPending() || check(CHAR) || parenthesizedExpressionPending();
    }



    private boolean collectionPending() {
    }


    private boolean booleanLiteralPending() {
        return (check(TRUE) || check(FALSE));
    }
    private void booleanLiteral() {
        if(booleanLiteralPending()) {
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
            a+=;
        }
        if (a == 0) {
            error("Expected conditional keyword (IF, ELSEIF, or ELSE)");
        }
    }
    private void ifConditional() {
        consume(IF);
        consume(OPAREN);
        expression();
        consume(CPAREN)
        block();
    }
    private void elseIfConditional() {
        consume(ELSEIF;
        consume(OPAREN);
        expression();
        consume(CPAREN)
        block();
    }
    private void elseConditional() {
        consume(ELSE);
        consume(OPAREN);
        expression();
        consume(CPAREN)
        block();
    }


    private void ifElseConditional() {
    }



    private boolean collectionActivitiesPending() {
        return collectionInitializationPending() || collectionDeclarationPending() || collectionAssignmentPending() || collectionGettersPending();
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
            System.out.println("--------" + "heading" + "--------")
    }


}















