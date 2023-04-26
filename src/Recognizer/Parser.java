package src.Recognizer;

import src.C10H15N;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.util.ArrayList;
import java.util.Collection;

import static src.lexicalAnalysis.Types.*;
import static src.lexicalAnalysis.Types.whileTrue;

public class Parser {
    private static final boolean printDebugMessages = true;
    //instance var
    private Lexeme currentLexeme;
    private final ArrayList<Lexeme> lexemes;
    private int nextLexemeIndex;
    private int callingFromIdentifierCaseEnd;

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
    public Parser(ArrayList<Lexeme> lexemes) {
        this.lexemes = lexemes;
        this.nextLexemeIndex = 0;
        System.out.println(lexemes);
        advance();
        //program();


    }

    public Lexeme program() {
        return statementList();

    }

    public Lexeme statementList() {
        Lexeme statementList = new Lexeme(Types.statementList);
        while (statementPending()) {
            Lexeme statement = statement();
            statementList.AddChild(statement);
        }
        //(statementList.children);
        return statementList;
    }

    private boolean statementPending() {

        return check(VAR) ||
                //checkNext(FUNC_DEFINITION) ||
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

    private Lexeme statement() {


        if (check(VAR)) {

            return varInitialization();

        } else if (check(IDENTIFIER)) {


            Lexeme identifier = consume(IDENTIFIER);

            if (check(ASSIGNMENT)) {

                Lexeme assign = consume(ASSIGNMENT);
                boolean isFunctionCall = false;
                Lexeme expression = null;
                Lexeme funCall = null;
                if (functionCallPending()) {
                    funCall = functionCall();
                    isFunctionCall = !isFunctionCall;
                } else {
                    expression = expression();
                }
                consume(SEMI_COLON);
                Lexeme varAsssignment = new Lexeme(varAssignment);
                varAsssignment.AddChild(identifier);
                varAsssignment.AddChild(assign);
                if (isFunctionCall) {
                    varAsssignment.AddChild(funCall);
                } else {
                    varAsssignment.AddChild(expression);
                }

                return varAsssignment;
            } else if (check(OPAREN)) {

                return functionCall(identifier);
            } else if (checkNext(OBRACKET)) {

                consume(OBRACKET);
                Lexeme number = number();
                consume(CBRACKET);
                Lexeme arrayAssi = new Lexeme(Types.arrayAssi);
                arrayAssi.AddChild(identifier);
                arrayAssi.AddChild(number);
                return arrayAssi;
            } else if (check(OARRAYLIST)) {

                consume(OARRAYLIST);
                Lexeme arrayListAssi = new Lexeme(listAssi);
                if (numberPending()) {

                    Lexeme number = number();
                    consume(CARRAYLIST);
                    Lexeme assignment = consume(ASSIGNMENT);
                    Lexeme expression = expression();
                    consume(SEMI_COLON);
                    arrayListAssi.AddChild(identifier);
                    arrayListAssi.AddChild(new Lexeme(empty));
                    arrayListAssi.AddChild(number);
                    arrayListAssi.AddChild(assignment);
                    arrayListAssi.AddChild(expression);
                    return arrayListAssi;

                } else if (check(PLUS)) {
                    Lexeme plus = consume(PLUS);
                    if (currentLexeme.getIntValue() == 0) {
                        Lexeme interger = consume(INTERGER);
                        consume(CARRAYLIST);
                        Lexeme assignment = consume(ASSIGNMENT);
                        Lexeme expression = expression();
                        consume(SEMI_COLON);
                        arrayListAssi.AddChild(identifier);
                        arrayListAssi.AddChild(plus);
                        arrayListAssi.AddChild(interger);
                        arrayListAssi.AddChild(assignment);
                        arrayListAssi.AddChild(expression);
                        return arrayListAssi;
                    } else {
                        if (check(CARRAYLIST)) {
                            consume(CARRAYLIST);
                            Lexeme assignment = consume(ASSIGNMENT);
                            Lexeme expression = expression();
                            consume(SEMI_COLON);
                            arrayListAssi.AddChild(identifier);
                            arrayListAssi.AddChild(plus);
                            arrayListAssi.AddChild(new Lexeme(empty));
                            arrayListAssi.AddChild(assignment);
                            arrayListAssi.AddChild(expression);
                            return arrayListAssi;

                        } else {
                            error("Idk how you got there but, Malformed arraylist Assinment");
                        }
                    }

                }
            } else if (check(OMATRIX)) {

                Lexeme inlineMatrixAssi = new Lexeme(MatrixAssi);
                consume(OMATRIX);
                Lexeme number = number();
                ArrayList<Lexeme> numberList = new ArrayList<>();
                while (check(COMMA)) {
                    consume(COMMA);
                    Lexeme tempVal = number();
                    numberList.add(tempVal);
                }
                consume(CMATRIX);
                Lexeme assignment = consume(ASSIGNMENT);
                Lexeme expression = expression();
                consume(SEMI_COLON);
                inlineMatrixAssi.AddChild(identifier);
                inlineMatrixAssi.AddChild(number);
                inlineMatrixAssi.addChildren(numberList);
                inlineMatrixAssi.AddChild(assignment);
                inlineMatrixAssi.AddChild(expression);
                return inlineMatrixAssi;
            } else if (check(FUNC_DEFINITION)) {
                return functionDefinition(identifier);
            } else if (functionCallPending()) {
                return functionCall();
            } else {

                if (expressionPending()) {
                    callingFromIdentifierCaseEnd = 1;
                    return expression();
                }

                return error("malformed array assignment");
                //advance();
            }


        } else if (check(IF)) {

            return conditional();

        } else if (check(WHILE)) {
            Lexeme whileloop = new Lexeme(whileL);

            Lexeme whileBody = whileLoop();
            Lexeme block = block();
            whileloop.AddChild(whileBody);
            whileloop.AddChild(block);
            return (whileloop);

        } else if (check(INDEFINITELYPERFORM)) {
            Lexeme indefinitleyPreform = new Lexeme(whileTrue);

            Lexeme whileTrue = indefinitleyPreform();

            Lexeme block = block();
            whileTrue.AddChild(whileTrue);
            whileTrue.AddChild(block);
            return (indefinitleyPreform);
        } else if (check(WHILETHISISBASICALLYTRUE)) {
            Lexeme whilePercent = new Lexeme(Types.whilePercent);

            Lexeme whileThisIsBasicallyTrue = whileThisIsBasicallyTrue();
            Lexeme block = block();
            whilePercent.AddChild(whileThisIsBasicallyTrue);
            whilePercent.AddChild(block);
            return (whilePercent);
        } else if (check(FOR)) {
            return forLoop();
        } else if (check(FOREACH)) {
            return forEach();
        } else if (check(OBRACE)) {
            return block();
        } else if (check(RETURN)) {
            return returnStatement();
        } else if (check(BREAK)) {
            Lexeme theBreak = new Lexeme(breakstatement);
            Lexeme breakKeyword = consume(BREAK);
            consume(SEMI_COLON);
            theBreak.AddChild(breakKeyword);
            return theBreak;
        } else if (check(COLLECTION)) {
            return collectionInitialization();
        } else if (check(GET)) {
            return collectionGetter();
        } else if (expressionPending()) {

            Lexeme theExpression = expression();
            consume(SEMI_COLON);
            return theExpression;

        } else if (collectionPending()) {
            collection();

        } else {
            advance();
            return error("Expecting a statement");
        }
        return error("Expecting a statement");
    }

    private Lexeme collectionGetter() {
        Lexeme get = consume(GET);
        Lexeme identifier = consume(IDENTIFIER);
        if (check(OBRACKET)) {
            consume(OBRACKET);
            Lexeme number = number();
            consume(CBRACKET);
            Lexeme arrayGet = new Lexeme(arrayGetter);
            arrayGet.AddChild(get);
            arrayGet.AddChild(identifier);
            arrayGet.AddChild(number);
            return arrayGet;

        } else if (check(OARRAYLIST)) {
            consume(OARRAYLIST);
            Lexeme number = number();
            consume(CARRAYLIST);
            Lexeme arrayListGet = new Lexeme(arrylistGetter);
            arrayListGet.AddChild(get);
            arrayListGet.AddChild(identifier);
            arrayListGet.AddChild(number);
            return arrayListGet;
        } else if (check(OMATRIX)) {
            Lexeme number = number();
            ArrayList<Lexeme> numberList = new ArrayList<>();
            numberList.add(number);
            while (check(COMMA)) {
                consume(COMMA);
                Lexeme tempVal = number();
                numberList.add(tempVal);
            }
            consume(CMATRIX);
            Lexeme matrixGet = new Lexeme(matrixGetter);
            matrixGet.AddChild(get);
            matrixGet.AddChild(identifier);
            matrixGet.addChildren(numberList);

        } else {
            return error("malformed collection getter");
        }
        return error("malformed collection getter");
    }

    private Lexeme expressionList() {
        Lexeme expression = expression();
        ArrayList<Lexeme> expList = new ArrayList<>();
        expList.add(expression);
        while (check(COMMA)) {
            consume(COMMA);
            Lexeme tempExpression = expression();
            expList.add(tempExpression);
        }
        Lexeme expressList = new Lexeme(expressionList);
        expressList.addChildren(expList);
        return expressList;
    }

    private Lexeme varInitialization() {

        Lexeme var = consume(VAR);
        Lexeme identList = varIdentifierList();
        Lexeme type = dataType();
        Lexeme assignment = consume(ASSIGNMENT);
        Lexeme expression = null;
        Lexeme funCall = null;
        boolean isFunctionCall = false;


        if (check(IDENTIFIER) && checkNext(OPAREN)) {
            funCall = functionCall();
            isFunctionCall = !isFunctionCall;
        } else {
            expression = expression();
        }
        consume(SEMI_COLON);
        Lexeme varInt = new Lexeme(varInitialization);
        varInt.AddChild(var);
        varInt.AddChild(identList);
        varInt.AddChild(type);
        varInt.AddChild(assignment);
        if (isFunctionCall) {
            varInt.AddChild(funCall);
        } else {
            varInt.AddChild(expression);
        }
        return varInt;

    }

    private Lexeme varIdentifierList() {

        Lexeme identifier = consume(IDENTIFIER);
        ArrayList<Lexeme> varList = new ArrayList<>();
        varList.add(identifier);
        while (check(COMMA)) {
            consume(COMMA);
            Lexeme tempIdentifier = consume(IDENTIFIER);
            varList.add(tempIdentifier);
        }
        Lexeme varIdentList = new Lexeme(varIdentifierList);
        varIdentList.addChildren(varList);


        return varIdentList;

    }

    private Lexeme functionDefinition(Lexeme identifier) {

        //Lexeme identifier = consume(IDENTIFIER);
        Lexeme funcDef = consume(FUNC_DEFINITION);
        Lexeme type = dataType();
        consume(OPAREN);
        Lexeme parameterList = parameterList();
        consume(CPAREN);
        Lexeme block = block();
        Lexeme funcDefActual = new Lexeme(functionDefinitionActual);
        funcDefActual.AddChild(identifier);
        funcDefActual.AddChild(funcDef);
        funcDefActual.AddChild(type);
        funcDefActual.AddChild(parameterList);
        funcDefActual.AddChild(block);
        return funcDefActual;
    }

    private Lexeme parameterList() {
        ArrayList<Lexeme> parameters = new ArrayList<>();
        if (!check(CPAREN)) {
            Lexeme type = dataType();
            Lexeme identifier = consume(IDENTIFIER);
            Lexeme param = new Lexeme(parameter);
            param.AddChild(type);
            param.AddChild(identifier);
            parameters.add(param);
        }
        Lexeme paramList = new Lexeme(parameterList);
        paramList.addChildren(parameters);
        return paramList;
    }

    private Lexeme whileLoop() {
        Lexeme whileKeyWord = consume(WHILE);
        consume(OPAREN);
        Lexeme expression = expression();
        consume(CPAREN);
        Lexeme whileb = new Lexeme(whileBody);
        whileb.AddChild(whileKeyWord);
        whileb.AddChild(expression);
        return whileb;
    }

    private Lexeme forLoop() {

        Lexeme forKeyword = consume(FOR);
        consume(OPAREN);

        Lexeme varInt = varInitialization();
        //("hi");
        Lexeme expression = expression();

        consume(CPAREN);
        Lexeme forLoopBody = new Lexeme(foorLoopBody);
        forLoopBody.AddChild(forKeyword);
        forLoopBody.AddChild(varInt);
        forLoopBody.AddChild(expression);

        //(currentLexeme);
        if (check(LOOPINCREMENTPLUS)) {
            Lexeme loopincplus = consume(LOOPINCREMENTPLUS);
            forLoopBody.AddChild(loopincplus);


        } else if (check(LOOPINCREMENTMINUS)) {
            Lexeme loopincminus = consume(LOOPINCREMENTMINUS);
            forLoopBody.AddChild(loopincminus);

        } else {
            return error("loop increment direction required malformed loop");
        }

        Lexeme block = block();
        forLoopBody.AddChild(block);
        //(forLoopBody.children);
        return forLoopBody;
    }

    private Lexeme forEach() {
        Lexeme foreach = consume(FOREACH);
        consume(OPAREN);
        Lexeme identifier = consume(IDENTIFIER);
        Lexeme forEachDelta = consume(FOREACH_DELTA);
        Lexeme identifierCollection = consume(IDENTIFIER);
        Lexeme block = block();
        Lexeme forEachBody = new Lexeme(Types.forEachBody);
        forEachBody.AddChild(foreach);
        forEachBody.AddChild(identifier);
        forEachBody.AddChild(forEachDelta);
        forEachBody.AddChild(identifierCollection);
        forEachBody.AddChild(block);
        return forEachBody;
    }

    private Lexeme indefinitleyPreform() {
        Lexeme indefPreformBody = new Lexeme(Types.indefPreformBody);
        Lexeme idefPreformKeyword = consume(INDEFINITELYPERFORM);
        Lexeme block = block();
        indefPreformBody.AddChild(idefPreformKeyword);
        indefPreformBody.AddChild(block);
        return indefPreformBody;
    }

    private Lexeme whileThisIsBasicallyTrue() {
        Lexeme percentWhileKeyword = consume(WHILETHISISBASICALLYTRUE);
        consume(OPAREN);
        Lexeme expresison = expression();
        consume(CPAREN);
        Lexeme number = number();
        Lexeme percentSign = consume(PERCENTERROR);
        Lexeme block = block();
        Lexeme whileSortOf = new Lexeme(whileSortOfBody);
        whileSortOf.AddChild(percentWhileKeyword);
        whileSortOf.AddChild(expresison);
        whileSortOf.AddChild(number);
        whileSortOf.AddChild(percentSign);
        whileSortOf.AddChild(block);
        return whileSortOf;

    }


    private Lexeme dataType() {
        Lexeme thingToReturn = new Lexeme(empty);

        if (check(GEORGE)) {
            thingToReturn = currentLexeme;
            advance();
        } else if (check(INTERGER)) {
            thingToReturn = currentLexeme;
            advance();
        } else if (check(STRING)) {
            thingToReturn = currentLexeme;
            advance();
        } else if (check(MATRIX)) {
            thingToReturn = currentLexeme;
            advance();
        } else if (check(CHAR)) {
            thingToReturn = currentLexeme;
            advance();
        } else if (check(DOS)) {
            thingToReturn = currentLexeme;
            advance();
        } else {
            error("Expected data type");
        }
        return thingToReturn;
    }


    private Lexeme block() {
        consume(OBRACE);
        ArrayList<Lexeme> statementListSquared = new ArrayList<>();
        if (checkNext(CBRACE)) {
            consume(CBRACE);
        } else {
            statementListSquared.add(statementList());
            consume(CBRACE);
        }
        Lexeme block = new Lexeme(Types.block);
        block.addChildren(statementListSquared);
        return block;
    }

    private Lexeme returnStatement() {
        Lexeme returnKeyword = consume(RETURN);
        Lexeme expression = expression();
        Lexeme returnStatement = new Lexeme(Types.returnStatement
        );

        returnStatement.AddChild(returnKeyword);
        returnStatement.AddChild(expression);
        consume(SEMI_COLON);
        return returnStatement;
    }

    private boolean expressionPending() {
        return (binaryExpressionPending() || unaryExpressionPending() || parenthesizedExpressionPending());
    }


    private Lexeme expression() {
        if (binaryExpressionPending()) {
            return binaryExpression();
        } else if (unaryExpressionPending()) {
            return unaryExpression();
        } else if (parenthesizedExpressionPending()) {
            return parenthesizedExpression();
        } else if (primaryPending()) {
            return primary();
        } else {
            return error("Expected an expression");
        }
        //return error("Expected an expression");
    }


    private boolean binaryExpressionPending() {

        return primaryPending() || binaryOperatorPending() || primaryPending();
    }

    private Lexeme parenthesizedExpression() {

        consume(OPAREN);
        Lexeme expression = expression();
        consume(CPAREN);
        Lexeme parentheziedExpreission = new Lexeme(Types.parentheziedExpreission);
        parentheziedExpreission.AddChild(expression);
        return parentheziedExpreission;
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
            case MINUS:
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

    private Lexeme unaryExpression() {
        Lexeme unaryExpres = new Lexeme(unaryExpression);

        switch (currentLexeme.getType()) {
            case MINUS_UNARY:
                Lexeme minusunary = consume(MINUS_UNARY);
                Lexeme primary = primary();
                unaryExpres.AddChild(minusunary);
                unaryExpres.AddChild(primary);
                break;
            case MINUS:
                Lexeme minus = consume(MINUS);
                Lexeme primary2 = primary();
                unaryExpres.AddChild(minus);
                unaryExpres.AddChild(primary2);
                break;

            case NOT:
                Lexeme not = consume(NOT);
                Lexeme primary3 = primary();
                unaryExpres.AddChild(not);
                unaryExpres.AddChild(primary3);
                break;
            case INVERSE:
                Lexeme inverse = consume(INVERSE);
                Lexeme primary4 = primary();
                unaryExpres.AddChild(inverse);
                unaryExpres.AddChild(primary4);
                break;
            case IDENTIFIER:
                if (peekNext() == PLUS_PLUS || peekNext() == MINUS_MINUS) {
                    Lexeme primary5 = primary();
                    unaryExpres.AddChild(primary5);
                    if (peekNext() == PLUS_PLUS) {
                        Lexeme plusTwo = consume(IDENTIFIER);
                        unaryExpres.AddChild(plusTwo);
                    } else {
                        Lexeme minusTwo = consume(IDENTIFIER);
                        unaryExpres.AddChild(minusTwo);
                    }
                }
                break;
            default:
                Lexeme primary8 = primary();
                unaryExpres.AddChild(primary8);

        }
        return unaryExpres;
    }

    private boolean binaryOperatorPending() {
        return switch (currentLexeme.getType()) {
            case PLUS, MINUS, MULTIPLY, DIVIDE, EXPONENTIATE, AND, OR, GREATER, GREATER_EQ, LESS, LESS_EQ, EQUALS, NOT_EQUAL, DOT_PRODUCT, MOD -> true;
            default -> false;
        };
    }

    private Lexeme binaryOperator() {

        Lexeme binaryOperator;
        switch (currentLexeme.getType()) {
            case PLUS, MINUS, MULTIPLY, DIVIDE, EXPONENTIATE, AND, OR, GREATER, GREATER_EQ, LESS, LESS_EQ, EQUALS, NOT_EQUAL, DOT_PRODUCT, MOD -> binaryOperator = currentLexeme;
            default -> {
                return error("Expecting Binary Operator");
                //binaryOperator = new Lexeme(empty);
            }
        }
        advance();
        return binaryOperator;

    }

    private Lexeme binaryExpression() {
        ArrayList<Lexeme> binaryOperatorChain = new ArrayList<>();
        if (callingFromIdentifierCaseEnd == 1) {

            currentLexeme = lexemes.get(nextLexemeIndex - 2);
            callingFromIdentifierCaseEnd--;
            Lexeme primary = primary();
            binaryOperatorChain.add(primary);

            currentLexeme = lexemes.get(nextLexemeIndex - 2);
            while (binaryOperatorPending()) {
                Lexeme binaryOperator = binaryOperator();
                currentLexeme = lexemes.get(nextLexemeIndex - 2); //no idea why this is done
                Lexeme tempPrimary = primary();
                binaryOperatorChain.add(binaryOperator);
                binaryOperatorChain.add(tempPrimary);
            }
            Lexeme binaryExpres = new Lexeme(binaryExpression);
            binaryExpres.addChildren(binaryOperatorChain);
            return binaryExpres;
        } else {
            Lexeme primary = primary();
            binaryOperatorChain.add(primary);

        }


        while (binaryOperatorPending()) {
            Lexeme binaryOperator = binaryOperator();
            Lexeme tempPrimary = primary();
            binaryOperatorChain.add(binaryOperator);
            binaryOperatorChain.add(tempPrimary);
        }
        Lexeme binaryExpres = new Lexeme(binaryExpression);
        binaryExpres.addChildren(binaryOperatorChain);
        return binaryExpres;
    }


    private boolean functionCallPending() {
        return check(OPAREN);
    }

    private Lexeme functionCall(Lexeme identifier) {

        Lexeme functionCall = new Lexeme(funcCall);
        consume(OPAREN);
        Lexeme expressionList = expressionList();
        consume(CPAREN);
        functionCall.AddChild(identifier);
        functionCall.AddChild(expressionList);
        return functionCall;
    }

    private Lexeme functionCall() {

        Lexeme functionCall = new Lexeme(funcCall);
        consume(OPAREN);
        Lexeme identifier = consume(IDENTIFIER);
        Lexeme expressionList = expressionList();
        consume(CPAREN);
        functionCall.AddChild(identifier);
        functionCall.AddChild(expressionList);
        return functionCall;
    }


    private Lexeme primary() {
        Lexeme primary = new Lexeme(Types.primary);
        if (check(DOS) || check(INTERGER)) {
            Lexeme number = number();
            primary.AddChild(number);
            return primary;
        } else if (check(STRING)) {
            Lexeme string = consume(STRING);
            primary.AddChild(string);
            return primary;
        } else if (check(IDENTIFIER)) {
            Lexeme identifier = consume(IDENTIFIER);
            primary.AddChild(identifier);
            return primary;
        } else if (booleanLiteralPending()) {
            Lexeme booleanLiteral = booleanLiteral();
            primary.AddChild(booleanLiteral);
            return primary;
        } else if (functionCallPending()) {
            Lexeme funcCall = functionCall();
            primary.AddChild(funcCall);
            return primary;
        } else if (collectionPending()) {
            Lexeme collection = collection();
            primary.AddChild(collection);
            return primary;
        } else if (check(CHAR)) {
            Lexeme charector = consume(CHAR);
            primary.AddChild(charector);
            return primary;
        } else if (check(OPAREN)) {
            Lexeme parenthExp = parenthesizedExpression();
            primary.AddChild(parenthExp);
            return primary;
        } else {
            return error("Expected a primary expression.");
        }

    }

    private Lexeme collectionInitialization() {
        Lexeme collectionInitialization = new Lexeme(Types.collectionInitialization);
        Lexeme collectionKeyWord = consume(COLLECTION);
        Lexeme identifier = consume(IDENTIFIER);
        Lexeme collectionType = new Lexeme(whatCollectionType());
        Lexeme dataType = dataType();
        Lexeme collectionDefinition = collectionHelper();
        collectionInitialization.AddChild(collectionType);
        collectionInitialization.AddChild(identifier);
        collectionInitialization.AddChild(dataType);
        collectionInitialization.AddChild(collectionDefinition);
        consume(SEMI_COLON);
        return collectionInitialization;
    }

    private Types whatCollectionType() {

        if (checkNext(OBRACKET)) {
            return array;
        } else if (checkNext(OARRAYLIST)) {
            return linkedList;
        } else if (checkNext(OMATRIX)) {
            return matrix;
        } else {
            if (check(OBRACKET)) {
                return array;
            } else if (check(OARRAYLIST)) {
                return linkedList;
            } else if (check(OMATRIX)) {
                return matrix;
            } else {
                throw new RuntimeException("no valid collectionTypes");
            }
        }


    }

    private Lexeme collectionHelper() {
        Lexeme collectionHelper = new Lexeme(Types.collectionHelper);
        if (check(OBRACKET)) {
            if (checkNext(CBRACKET)) {
                //must be initialiizing an array
                consume(OBRACKET);
                consume(CBRACKET);
                Lexeme assignment = consume(ASSIGNMENT);
                Lexeme expression = expression();
                collectionHelper.AddChild(assignment);
                collectionHelper.AddChild(expression);


            } else if (checkNext(INTERGER)) {
                //must be declaring an array with size (INTERGER)
                consume(OBRACKET);
                Lexeme arraySize = consume(INTERGER);
                consume(CBRACKET);
                collectionHelper.AddChild(arraySize);
            } else {
                return error("Malformed collection Initialization or Delcaration");
            }
        } else if (check(OARRAYLIST)) {
            consume(OARRAYLIST);
            consume(CARRAYLIST);
            if (check(ASSIGNMENT)) {
                Lexeme assignment = consume(ASSIGNMENT);
                Lexeme expression = expression();
                collectionHelper.AddChild(assignment);
                collectionHelper.AddChild(expression);

            }
        } else if (check(OMATRIX)) {
            consume(OMATRIX);
            consume(CMATRIX);
            Lexeme assignment = consume(ASSIGNMENT);
            if (checkNext(MATRIXSIZE)) {
                Lexeme intergerColumn = consume(INTERGER);
                Lexeme matrixSize = consume(MATRIXSIZE);
                Lexeme intergerRow = consume(INTERGER);
                collectionHelper.AddChild(assignment);
                collectionHelper.AddChild(intergerColumn);
                collectionHelper.AddChild(matrixSize);
                collectionHelper.AddChild(intergerRow);
            } else {
                Lexeme expression = expression();
                collectionHelper.AddChild(expression);
            }
        }

        if (expressionPending()) {
            collectionHelper = statement();
        }
        return collectionHelper;
    }

    private Lexeme number() {

        if (check(DOS)) {
            return consume(DOS);
        } else return consume(INTERGER);
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

    private Lexeme booleanLiteral() {
        Lexeme booleanLiteral = new Lexeme(Types.booleanLiteral);
        if (booleanLiteralPending()) {
            booleanLiteral.AddChild(currentLexeme);
        }
        return new Lexeme(empty);

    }

    private Lexeme conditional() {
        int a = 0;
        if (check(IF)) {
            a++;
            return ifConditional();


        }
        while (check(ELSEIF)) {
            a++;
            return elseIfConditional();

        }
        while (check(ELSE)) {
            a++;
            return elseConditional();

        }
        if (a == 0) {
            return error("Expected conditional keyword (IF, ELSEIF, or ELSE)");
        }
        return error("Expected conditional keyword (IF, ELSEIF, or ELSE)");
    }

    private Lexeme ifConditional() {
        Lexeme ifKeyWord = consume(IF);

        consume(OPAREN);

        Lexeme expression = expression();

        consume(CPAREN);
        Lexeme block = block();
        ifKeyWord.AddChild(expression);
        ifKeyWord.AddChild(block);
        return ifKeyWord;

    }

    private Lexeme elseIfConditional() {
        Lexeme elseIfKeyWord = consume(ELSEIF);
        consume(OPAREN);
        Lexeme expression = expression();
        consume(CPAREN);
        Lexeme block = block();
        elseIfKeyWord.AddChild(expression);
        elseIfKeyWord.AddChild(block);
        return elseIfKeyWord;
    }

    private Lexeme elseConditional() {
        Lexeme elseKeyWord = consume(ELSE);
        consume(OPAREN);
        Lexeme expression = expression();
        consume(CPAREN);
        Lexeme block = block();
        elseKeyWord.AddChild(expression);
        elseKeyWord.AddChild(block);
        return elseKeyWord;
    }

    private Lexeme collection() {
        if (check(OBRACKET)) {
            return array();
        } else if (check(OARRAYLIST)) {
            return linkedList();
        } else if (check(OMATRIX)) {
            return Matrix();
        } else return error("expecting some sort of collection");
    }

    private boolean collectionPending() {
        if (check(OBRACKET)) {
            return true;
        } else if (check(OARRAYLIST)) {
            return true;
        } else return check(OMATRIX);
    }

    private Lexeme array() {
        Lexeme array = new Lexeme(Types.array);
        consume(OBRACKET);
        Lexeme expressionList = expressionList();
        consume(CBRACKET);
        array.AddChild(expressionList);
        return array;

    }

    private Lexeme linkedList() {
        Lexeme linkedList = new Lexeme(Types.linkedList);
        consume(OARRAYLIST);
        Lexeme expressionList = expressionList();
        consume(CARRAYLIST);
        linkedList.AddChild(expressionList);
        return linkedList;
    }

    private Lexeme Matrix() {

        Lexeme matrix = new Lexeme(Types.matrix);
        consume(OMATRIX);
        Lexeme firstRow = expressionList();

        matrix.AddChild(firstRow);
        while (check(CBRACKET)) {
            consume(CBRACKET);
            consume(MATRIX_SEPERATOR);
            consume(OBRACKET);
            Lexeme otherRows = expressionList();

            matrix.AddChild(otherRows);
        }
        consume(CMATRIX);
        return matrix;
    }


    private Lexeme error(String message) {
        C10H15N.syntaxError(message, currentLexeme);
        return new Lexeme(ERROR, currentLexeme.getLineNumber(), message);
    }


}