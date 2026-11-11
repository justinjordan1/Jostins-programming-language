
package src.Evaluation;

import src.Recognizer.Parser;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Lexer;
import src.Evaluation.Environment;
import src.lexicalAnalysis.Types;

import java.io.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static src.lexicalAnalysis.Types.*;

public class Evaluator<Parsing> {

    public Environment environment;
    public Lexeme Result;


    public Lexeme eval(Lexeme programParseTree, Environment environment) {
        this.environment = environment;

        Result = evaluateProgram(programParseTree, environment);
        return Result;

    }

    public Lexeme evaluateProgram(Lexeme programParseTree, Environment env) {
        Lexeme statementList = programParseTree;

        Lexeme lastResult = null;
        for (int i = 0; i < statementList.getChildrenSize(); i++) {
            Lexeme statement = statementList.getChild(i);
            Lexeme result = evaluateStatement(statement, env);
            if (statement.getType() == Types.returnStatement) {
                return result;
            }
            if (result != null) lastResult = result;
        }
        return lastResult != null ? lastResult : statementList;
    }

    public Lexeme evaluateStatement(Lexeme statement, Environment env) {
        if (statement == null) return new Lexeme(null);
        switch (statement.getType()) {
            case statementList:
                for (int i = 0; i < statement.getChildrenSize(); i++) {
                    evaluateStatement(statement.getChild(i), env);
                }
                break;
            case varInitialization:
                return evaluateInitialization(statement, env);
            case binaryExpression:
                return evalBinaryExpression(statement, env);
            case unaryExpression:
                return evalUnaryExpression(statement, env);
            case INTERGER, GEORGE, STRING, DOS:
                return statement;

            case varAssignment:
                return evalVarAssignment(statement, env);
            case functionDefinitionActual:
                return evaluateFunctionDefinition(statement, env);
            case funcCall:

                return evaluateFunctionCall(statement, env);
            case parentheziedExpreission:
                return evaluateParenthesizedExpression(statement, env);
            case primary:
                return evalPrimary(statement, env);
            case returnStatement:
                return evaluateReturnStatement(statement, env);
            case IF:
                return evaluateConditional(statement, env);
            case foorLoopBody:
                return evaluateForLoop(statement, env);
            case collectionInitialization:
                return evaluateCollectionInitialization(statement, env);
            case MatrixAssi:
                return evaluateMatrixAssignment(statement, env);
            case whilePercent:
                return evaluateWhileThisIsBasicallyTrue(statement.getChild(0), env);
            case whileSortOfBody:
                return evaluateWhileThisIsBasicallyTrue(statement, env);
            case indefPreformBody:
                return evaluateIndefinitelyPerform(statement, env);
            case IDENTIFIER:
                return statement;
            case ERROR:
            case empty:
                return null;
            case matrix:
                return evaluateMatrix(statement, env);
            case array:
                return evaluateArray(statement, env);
            case linkedList:
                return evaluateLinkedList(statement, env);

            default:
                throw new RuntimeException("Unknown statement type: " + statement.getType());
        }
        return null;
    }

    public Lexeme evaluateInitialization(Lexeme initialization, Environment environment) {

        Lexeme identList = initialization.getChild(1);
        Lexeme type = initialization.getChild(2);
        Lexeme expression = initialization.getChild(4);

        Lexeme evaluatedExpression = evaluateExpression(expression, environment);
        if (evaluatedExpression.getType() == primary) {
            evaluatedExpression = evalPrimary(evaluatedExpression, environment);
        }
        if (evaluatedExpression.getType() == IDENTIFIER) {
            evaluatedExpression = environment.lookup(evaluatedExpression);
        }

        for (int i = 0; i < identList.getChildrenSize(); i++) {
            Lexeme identifier = identList.getChild(i);
            environment.add(type.getType(), identifier, evaluatedExpression);
        }


        return evaluatedExpression;
    }


    public Lexeme evaluateExpression(Lexeme expression, Environment env) {

        return switch (expression.getType()) {
            case unaryExpression -> evalUnaryExpression(expression, env);
            case binaryExpression -> evalBinaryExpression(expression, env);
            case parentheziedExpreission -> evaluateParenthesizedExpression(expression, env);
            case funcCall -> evaluateFunctionCall(expression, env);
            case functionDefinitionActual -> evaluateFunctionDefinition(expression, env);
            case primary -> evalPrimary(expression, env);
            case IDENTIFIER -> expression;


            default -> throw new RuntimeException("Unsupported expression type: " + expression.getType());
        };
    }

    private Lexeme evalUnaryExpression(Lexeme unaryExpression, Environment env) {
        Lexeme operator = null;
        Lexeme operand = null;

        if (isUnaryOperator(unaryExpression.getChild(0))) {
            operator = unaryExpression.getChild(0);
            operand = eval(unaryExpression.getChild(1), env);
            while (operand.getChildrenSize() > 0) {
                operand = operand.getChild(0);
            }
        } else {
            operand = eval(unaryExpression.getChild(0), env);
        }

        if (operator != null) {
            switch (operator.getType()) {
                case MINUS_UNARY:
                case MINUS:
                    Types operandType = operand.getType();
                    return new Lexeme(operandType, operand.getLineNumber(), -operand.getIntValue());
                case NOT:
                    return new Lexeme(Types.GEORGE, operand.getLineNumber(), !operand.getBooleanValue());
                case INVERSE:
                    if (operand.getDosValue() == 0) {
                        throw new RuntimeException("Division by zero error");
                    }
                    return new Lexeme(Types.DOS, operand.getLineNumber(), 1 / operand.getDosValue());
                case PLUS_PLUS:
                    int incrementedValue = operand.getIntValue() + 1;
                    env.update(unaryExpression.getChild(0), new Lexeme(Types.INTERGER, incrementedValue));
                    return new Lexeme(Types.INTERGER, incrementedValue);
                case MINUS_MINUS:
                    int decrementedValue = operand.getIntValue() - 1;
                    env.update(unaryExpression.getChild(0), new Lexeme(Types.INTERGER, decrementedValue));
                    return new Lexeme(Types.INTERGER, decrementedValue);
                default:
                    throw new RuntimeException("Invalid unary operator");
            }
        } else {
            return operand;
        }
    }

    private boolean isUnaryOperator(Lexeme lexeme) {
        return switch (lexeme.getType()) {
            case MINUS_UNARY, NOT, INVERSE, PLUS_PLUS, MINUS_MINUS, MINUS -> true;
            default -> false;
        };
    }

    private Lexeme evalBinaryExpression(Lexeme binaryExpression, Environment env) {

        if (binaryExpression.getChildrenSize() == 1) {
            return evaluateStatement(binaryExpression.getChild(0), env);
        }
        Lexeme leftPrimary = binaryExpression.getChild(0);
        Lexeme leftOperand = evalPrimary(leftPrimary, env);
        Lexeme operator = binaryExpression.getChild(1);
        Lexeme rightPrimary = binaryExpression.getChild(2);
        Lexeme rightOperand = evalPrimary(rightPrimary, env);
        Lexeme result = null;


        if (leftOperand.getType() == Types.IDENTIFIER) {
            leftOperand = env.lookup(leftOperand);


        }
        if (rightOperand.getType() == Types.IDENTIFIER) {
            rightOperand = env.lookup(rightOperand);

        }
        switch (operator.getType()) {
            case PLUS:

                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.INTERGER, leftOperand.getLineNumber(), leftOperand.getIntValue() + rightOperand.getIntValue());
                } else if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.DOS) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getIntValue() + rightOperand.getDosValue());
                } else if (leftOperand.getType() == Types.DOS && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getDosValue() + rightOperand.getIntValue());
                } else if (leftOperand.getType() == Types.DOS && rightOperand.getType() == Types.DOS) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getDosValue() + rightOperand.getDosValue());
                } else {
                    throw new RuntimeException("Unsupported types for PLUS operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case MINUS:
                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.INTERGER, leftOperand.getLineNumber(), leftOperand.getIntValue() - rightOperand.getIntValue());
                } else if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.DOS) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getIntValue() - rightOperand.getDosValue());
                } else if (leftOperand.getType() == Types.DOS && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getDosValue() - rightOperand.getIntValue());
                } else if (leftOperand.getType() == Types.DOS && rightOperand.getType() == Types.DOS) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getDosValue() - rightOperand.getDosValue());
                } else {
                    throw new RuntimeException("Unsupported types for PLUS operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case MULTIPLY:
                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.INTERGER, leftOperand.getLineNumber(), leftOperand.getIntValue() * rightOperand.getIntValue());
                } else if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.DOS) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getIntValue() * rightOperand.getDosValue());
                } else if (leftOperand.getType() == Types.DOS && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getDosValue() * rightOperand.getIntValue());
                } else if (leftOperand.getType() == Types.DOS && rightOperand.getType() == Types.DOS) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getDosValue() * rightOperand.getDosValue());
                } else if (leftOperand.getType() == matrix && rightOperand.getType() == matrix) {
                    Object[][] leftMatrix = leftOperand.getMatrixValue();
                    Object[][] rightMatrix = rightOperand.getMatrixValue();
                    result = new Lexeme(Types.matrix, leftOperand.getLineNumber(), matrixMultiplication(leftMatrix, rightMatrix));

                } else {
                    throw new RuntimeException("Unsupported types for MULTIPLY operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case DOT_PRODUCT:

                if (leftOperand.getType() == matrix && rightOperand.getType() == matrix) {
                    Object[][] leftMatrix = leftOperand.getMatrixValue();
                    Object[][] rightMatrix = rightOperand.getMatrixValue();
                    result = new Lexeme(Types.matrix, leftOperand.getLineNumber(), matrixDotProduct(leftMatrix, rightMatrix));
                } else {
                    throw new RuntimeException("Unsupported types for DOT_PRODUCT operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case DIVIDE:
                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.INTERGER, leftOperand.getLineNumber(), leftOperand.getIntValue() / rightOperand.getIntValue());
                } else if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.DOS) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getIntValue() / rightOperand.getDosValue());
                } else if (leftOperand.getType() == Types.DOS && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getDosValue() / rightOperand.getIntValue());
                } else if (leftOperand.getType() == Types.DOS && rightOperand.getType() == Types.DOS) {
                    result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), leftOperand.getDosValue() / rightOperand.getDosValue());
                } else {
                    throw new RuntimeException("Unsupported types for DIVIDE operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case GREATER:
                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), leftOperand.getIntValue() > rightOperand.getIntValue());
                } else {
                    throw new RuntimeException("Unsupported types for GREATER_THAN operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;

            case LESS:
                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), leftOperand.getIntValue() < rightOperand.getIntValue());
                } else {
                    throw new RuntimeException("Unsupported types for LESS_THAN operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;

            case GREATER_EQ:
                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), leftOperand.getIntValue() >= rightOperand.getIntValue());
                } else {
                    throw new RuntimeException("Unsupported types for GREATER_THAN_EQUAL operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;

            case LESS_EQ:
                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), leftOperand.getIntValue() <= rightOperand.getIntValue());
                } else {
                    throw new RuntimeException("Unsupported types for LESS_THAN_EQUAL operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case EQUALS:
                if (leftOperand.getType() == rightOperand.getType()) {
                    boolean isEqual;
                    if (leftOperand.getType() == Types.INTERGER) {
                        isEqual = leftOperand.getIntValue() == rightOperand.getIntValue();
                    } else if (leftOperand.getType() == Types.DOS) {
                        isEqual = leftOperand.getDosValue() == rightOperand.getDosValue();
                    } else if (leftOperand.getType() == Types.GEORGE) {
                        isEqual = leftOperand.getBooleanValue() == rightOperand.getBooleanValue();
                    } else {
                        throw new RuntimeException("Unsupported types for EQUALS operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                    }
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), isEqual);
                } else {
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), false);
                }
                break;

            case NOT_EQUAL:
                if (leftOperand.getType() == rightOperand.getType()) {
                    boolean isNotEqual;
                    if (leftOperand.getType() == Types.INTERGER) {
                        isNotEqual = leftOperand.getIntValue() != rightOperand.getIntValue();
                    } else if (leftOperand.getType() == Types.DOS) {
                        isNotEqual = leftOperand.getDosValue() != rightOperand.getDosValue();
                    } else if (leftOperand.getType() == Types.GEORGE) {
                        isNotEqual = leftOperand.getBooleanValue() != rightOperand.getBooleanValue();
                    } else {
                        throw new RuntimeException("Unsupported types for NOT_EQUAL operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                    }
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), isNotEqual);
                } else {
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), true);
                }
                break;
            case EXPONENTIATE:

                if ((leftOperand.getType() == Types.INTERGER || leftOperand.getType() == Types.DOS) &&
                        (rightOperand.getType() == Types.INTERGER || rightOperand.getType() == Types.DOS)) {

                    double leftValue = leftOperand.getType() == Types.INTERGER ? leftOperand.getIntValue() : leftOperand.getDosValue();
                    double rightValue = rightOperand.getType() == Types.INTERGER ? rightOperand.getIntValue() : rightOperand.getDosValue();
                    double resultValue = Math.pow(leftValue, rightValue);

                    if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                        result = new Lexeme(Types.INTERGER, leftOperand.getLineNumber(), (int) resultValue);
                    } else {
                        result = new Lexeme(Types.DOS, leftOperand.getLineNumber(), resultValue);
                    }
                } else {
                    throw new RuntimeException("Unsupported types for EXPONENTIATE operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case MOD:
                if (leftOperand.getType() == Types.INTERGER && rightOperand.getType() == Types.INTERGER) {
                    int leftValue = leftOperand.getIntValue();
                    int rightValue = rightOperand.getIntValue();

                    if (rightValue == 0) {
                        throw new RuntimeException("Division by zero in MOD operation");
                    }

                    int resultValue = leftValue % rightValue;
                    result = new Lexeme(Types.INTERGER, leftOperand.getLineNumber(), resultValue);
                } else {
                    throw new RuntimeException("Unsupported types for MOD operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case AND:
                if (leftOperand.getType() == Types.GEORGE && rightOperand.getType() == Types.GEORGE) {
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), leftOperand.getBooleanValue() && rightOperand.getBooleanValue());
                } else {
                    throw new RuntimeException("Unsupported types for AND operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;
            case OR:
                if (leftOperand.getType() == Types.GEORGE && rightOperand.getType() == Types.GEORGE) {
                    result = new Lexeme(Types.GEORGE, leftOperand.getLineNumber(), leftOperand.getBooleanValue() || rightOperand.getBooleanValue());
                } else {
                    throw new RuntimeException("Unsupported types for OR operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
                break;

            default:
                throw new RuntimeException("Invalid binary operator");
        }
        return result;
    }

    private static String stringifyForPrint(Lexeme value) {
        if (value == null) return "null";
        return switch (value.getType()) {
            case STRING -> value.getStringValue();
            case INTERGER -> Integer.toString(value.getIntValue());
            case DOS -> Double.toString(value.getDosValue());
            case GEORGE -> Boolean.toString(value.getBooleanValue());
            case matrix -> Arrays.deepToString(value.getMatrixValue());
            default -> value.toString();
        };
    }

    private static double cellAsDouble(Object cell) {
        if (cell instanceof Lexeme l) {
            if (l.getType() == DOS) return l.getDosValue();
            return l.getIntValue();
        }
        if (cell instanceof Integer i) return i;
        if (cell instanceof Double d) return d;
        if (cell instanceof Number n) return n.doubleValue();
        throw new RuntimeException("Matrix cell is not numeric: " + cell);
    }

    private static boolean cellIsInt(Object cell) {
        if (cell instanceof Lexeme l) return l.getType() == Types.INTERGER;
        return cell instanceof Integer;
    }

    private Object[][] matrixDotProduct(Object[][] leftMatrix, Object[][] rightMatrix) {
        int rowsLeft = leftMatrix.length;
        int colsLeft = leftMatrix[0].length;
        int rowsRight = rightMatrix.length;
        int colsRight = rightMatrix[0].length;

        if (colsLeft != rowsRight) {
            throw new RuntimeException("Matrix dimensions do not match for dot product: " +
                    rowsLeft + "x" + colsLeft + " and " + rowsRight + "x" + colsRight);
        }
        Object[][] resultMatrix = new Object[rowsLeft][colsRight];

        for (int i = 0; i < rowsLeft; i++) {
            for (int j = 0; j < colsRight; j++) {
                double sum = 0;
                boolean allInt = true;
                for (int k = 0; k < colsLeft; k++) {
                    Object lc = leftMatrix[i][k];
                    Object rc = rightMatrix[k][j];
                    if (!cellIsInt(lc) || !cellIsInt(rc)) allInt = false;
                    sum += cellAsDouble(lc) * cellAsDouble(rc);
                }
                resultMatrix[i][j] = allInt ? (int) sum : sum;
            }
        }
        return resultMatrix;
    }


    private Object[][] matrixMultiplication(Object[][] leftMatrix, Object[][] rightMatrix) {

        int leftRows = leftMatrix.length;
        int leftColumns = leftMatrix[0].length;
        int rightRows = rightMatrix.length;
        int rightColumns = rightMatrix[0].length;

        if (leftColumns != rightRows) {
            throw new RuntimeException("Matrix dimensions do not match for multiplication: " +
                    leftRows + "x" + leftColumns + " and " + rightRows + "x" + rightColumns);
        }

        Object[][] resultMatrix = new Object[leftRows][rightColumns];

        for (int i = 0; i < leftRows; i++) {
            for (int j = 0; j < rightColumns; j++) {
                double sum = 0;
                boolean allInt = true;
                for (int k = 0; k < leftColumns; k++) {
                    Object lc = leftMatrix[i][k];
                    Object rc = rightMatrix[k][j];
                    if (!cellIsInt(lc) || !cellIsInt(rc)) allInt = false;
                    sum += cellAsDouble(lc) * cellAsDouble(rc);
                }
                resultMatrix[i][j] = allInt ? (int) sum : sum;
            }
        }
        return resultMatrix;
    }


    private Lexeme evalPrimary(Lexeme primary, Environment env) {

        if (primary == null) return null;
        Lexeme innerNode = primary.getChild(0);
        if (innerNode.getType() == Types.INTERGER || innerNode.getType() == DOS) {
            return innerNode;
        } else {
            return evaluateStatement(innerNode, env);
        }
    }

    private Lexeme evaluateParenthesizedExpression(Lexeme parenExpr, Environment env) {
        return evaluateExpression(parenExpr.getChild(0), env);
    }

    public Lexeme evalVarAssignment(Lexeme varAssignment, Environment environment) {
        Lexeme identifier = varAssignment.getChild(0);

        Lexeme expression = varAssignment.getChild(2);

        Lexeme evaluatedExpression = evaluateStatement(expression, environment);
        while (evaluatedExpression != null
                && evaluatedExpression.getType() != Types.matrix
                && evaluatedExpression.getType() != Types.array
                && evaluatedExpression.getType() != Types.linkedList
                && evaluatedExpression.getChildrenSize() > 0) {
            evaluatedExpression = evaluatedExpression.getChild(0);
        }
        if (evaluatedExpression != null && evaluatedExpression.getType() == IDENTIFIER) {
            evaluatedExpression = environment.lookup(evaluatedExpression);
        }


        if (environment.lookup(identifier) != null) {

            environment.update(identifier, evaluatedExpression);
        } else {
            throw new RuntimeException("Variable not found: " + identifier.getStringValue());
        }


        return evaluatedExpression;
    }

    public Lexeme evaluateFunctionDefinition(Lexeme funcDefActual, Environment env) {
        funcDefActual.setEnvironment(env);

        Lexeme functionName = funcDefActual.getChild(0);

        env.funcAdd(functionName, funcDefActual);

        return functionName;
    }

    public Lexeme evaluateFunctionCall(Lexeme functionCall, Environment env) {
        Lexeme functionName = functionCall.getChild(0);

        if ("print".equals(functionName.getStringValue())) {
            Lexeme argList = functionCall.getChild(1);
            Lexeme evaluatedArgList = evalArgList(argList, env);
            for (int i = 0; i < evaluatedArgList.getChildrenSize(); i++) {
                System.out.print(stringifyForPrint(evaluatedArgList.getChild(i)));
            }
            return null;
        }

        Lexeme funcDefTree = env.lookup(functionName);


        Lexeme paramList = funcDefTree.getChild(3);
        Lexeme argList = functionCall.getChild(1);
        Lexeme evaluatedArgList = evalArgList(argList, env);


        if (paramList.getChildrenSize() != evaluatedArgList.getChildrenSize()) {
            throw new IllegalArgumentException("Mismatch between the number of arguments and parameters in the function call: "
                    + functionName.getChild(0) + ". Expected " + paramList.getChildrenSize() + " arguments but received "
                    + evaluatedArgList.getChildrenSize() + " arguments.");
        }

        Environment callEnv = new Environment(env, new ArrayList<>());

        for (int i = 0; i < paramList.getChildrenSize(); i++) {

            Lexeme param = paramList.getChild(i);


            callEnv.funcAdd(param.getChild(1), evaluatedArgList.getChild(i));


        }
        Lexeme funcBody = funcDefTree.getChild(4);
        Lexeme result = eval(funcBody, callEnv);

        if (result != null && result.getType() == Types.returnStatement) {
            return evaluateReturnStatement(result, callEnv);
        } else {
            return result;
        }

    }


    private Lexeme evalArgList(Lexeme argList, Environment env) {
        Lexeme evaluatedArgList = new Lexeme(argList.getType());
        for (int i = 0; i < argList.getChildrenSize(); i++) {
            Lexeme arg = argList.getChild(i);
            Lexeme evaluatedArg = eval(arg, env);
            while (evaluatedArg.getChildrenSize() > 0) {

                evaluatedArg = evaluatedArg.getChild(0);
            }

            evaluatedArgList.AddChild(evaluatedArg);
        }
        return evaluatedArgList;
    }

    public Lexeme evaluateReturnStatement(Lexeme returnStatement, Environment env) {
        Lexeme returnValue = returnStatement.getChild(1);
        Lexeme result = evaluateExpression(returnValue, env);
        while (result != null
                && result.getType() != Types.matrix
                && result.getType() != Types.array
                && result.getType() != Types.linkedList
                && result.getChildrenSize() > 0) {
            result = result.getChild(0);
        }
        if (result != null && result.getType() == IDENTIFIER) {
            result = env.lookup(result);
        }
        return result;
    }

    public Lexeme evaluateConditional(Lexeme conditional, Environment env) {
        Lexeme result = null;
        Lexeme condition = null;

        condition = evaluateExpression(conditional.getChild(0), env);

        if (condition.getBooleanValue()) {
            result = evaluateBlock(conditional.getChild(1), env);
        } else {
            boolean ifElseEvaluated = false;
            int ifElseChildIndex = 2;
            while (!ifElseEvaluated && ifElseChildIndex < conditional.children.size() - 1) {
                condition = evaluateExpression(conditional.getChild(ifElseChildIndex), env);
                if (condition.getBooleanValue()) {
                    result = evaluateBlock(conditional.getChild(ifElseChildIndex + 1), env);
                    ifElseEvaluated = true;
                }
                ifElseChildIndex += 2;
            }

            if (!ifElseEvaluated && ifElseChildIndex == conditional.children.size() - 1) {
                result = evaluateBlock(conditional.getChild(ifElseChildIndex), env);
            }
        }

        return result;

    }

    public Lexeme evaluateBlock(Lexeme block, Environment env) {
        Lexeme result = null;
        Environment blockEnv = new Environment(env, new ArrayList<>());

        result = evaluateStatement(block.getChild(0), blockEnv);

        return result;
    }

    public Lexeme evaluateForLoop(Lexeme forLoop, Environment env) {
        Environment loopEnv = new Environment(env, new ArrayList<>());
        evaluateInitialization(forLoop.getChild(1), loopEnv);
        Lexeme incrementDirection = forLoop.getChild(3);
        while (evaluateStatement(forLoop.getChild(2), loopEnv).getBooleanValue()) {

            Lexeme forblock = forLoop.getChild(4);

            Lexeme result = evaluateBlock(forblock, loopEnv);

            if (result != null && result.getType() == Types.BREAK) {
                break;
            }

            Lexeme loopVariable = forLoop.getChild(1).getChild(1).getChild(0);

            Lexeme currentValue = loopEnv.lookup(loopVariable);

            if (incrementDirection.getType() == Types.LOOPINCREMENTPLUS) {
                Lexeme incrementedValue = new Lexeme(Types.INTERGER, currentValue.getLineNumber(), currentValue.getIntValue() + 1);
                loopEnv.update(loopVariable, incrementedValue);
            } else {
                Lexeme decrementedValue = new Lexeme(Types.INTERGER, currentValue.getLineNumber(), currentValue.getIntValue() - 1);
                loopEnv.update(loopVariable, decrementedValue);
            }
        }


        return null;
    }


//    public Lexeme evaluateForEach(Lexeme forEachLoop, Environment env) {
//        Environment loopEnv = new Environment(env);
//
//        Lexeme loopVariable = forEachLoop.getChild(1);
//        Lexeme collectionIdentifier = forEachLoop.getChild(3);
//        Lexeme block = forEachLoop.getChild(4);
//
//        NamedValue collectionNamedValue = env.lookup(collectionIdentifier);
//        Lexeme collection = collectionNamedValue.getValue();
//
//        if (collection.getType() != Types.array && collection.getType() != Types.LINKED_LIST) {
//            throw new RuntimeException("forEach loop can only iterate over arrays or linked lists.");
//        }
//
//        for (Lexeme element : collection.getChildren()) {
//            loopEnv.addVariable(loopVariable, element);
//
//            Lexeme result = evaluateBlock(block, loopEnv);
//
//            if (result != null && result.getType() == Types.BREAK) {
//                break;
//            }
//        }
//
//        return null;
//    }

    public Lexeme evaluateIndefinitelyPerform(Lexeme indefinitelyPerformLoop, Environment env) {
        Lexeme block = indefinitelyPerformLoop.getChild(1);

        while (true) {
            Lexeme result = evaluateBlock(block, env);

            if (result != null && result.getType() == Types.BREAK) {
                break;
            }
        }

        return null;
    }

    public Lexeme evaluateWhileThisIsBasicallyTrue(Lexeme whileThisIsBasicallyTrueLoop, Environment env) {
        Lexeme condition = whileThisIsBasicallyTrueLoop.getChild(1);
        Lexeme percentage = whileThisIsBasicallyTrueLoop.getChild(2);
        Lexeme block = whileThisIsBasicallyTrueLoop.getChild(4);

        double percentError = percentage.getIntValue() / 100.0;

        while (true) {
            Lexeme conditionResult = evaluateExpression(condition, env);
            double conditionValue;
            if (conditionResult.getType() == Types.GEORGE) {
                conditionValue = conditionResult.getBooleanValue() ? 1.0 : 0.0;
            } else {
                conditionValue = ((Number) conditionResult.getNumberValue()).doubleValue();
            }

            if (Math.abs(conditionValue - 1) <= percentError) {
                Lexeme result = evaluateBlock(block, env);

                if (result != null && result.getType() == Types.BREAK) {
                    break;
                }
            } else {
                break;
            }
        }

        return null;
    }

    private Lexeme evaluateCollection(Lexeme collection, Environment env) {
        return switch (collection.getType()) {
            case array -> evaluateArray(collection, env);
            case linkedList -> evaluateLinkedList(collection, env);
            case matrix -> evaluateMatrix(collection, env);
            default -> throw new RuntimeException("Unsupported collection type: " + collection.getType());
        };
    }

    private Lexeme evaluateArray(Lexeme arrayNode, Environment env) {
        Lexeme expressionList = arrayNode.getChild(0);
        ArrayList<Object> array = new ArrayList<>();

        for (Lexeme expression : expressionList.getChildren()) {
            Lexeme value = evaluateExpression(expression, env);
            array.add(value.getValue());
        }

        return new Lexeme(Types.array, arrayNode.getLineNumber(), array);
    }

    private Lexeme evaluateLinkedList(Lexeme linkedListNode, Environment env) {
        Lexeme expressionList = linkedListNode.getChild(0);
        ArrayList<Object> linkedList = new ArrayList<>();

        for (Lexeme expression : expressionList.getChildren()) {
            Lexeme value = evaluateExpression(expression, env);
            linkedList.add(value.getValue());
        }

        return new Lexeme(Types.linkedList, linkedListNode.getLineNumber(), linkedList);
    }

    private Lexeme evaluateMatrix(Lexeme matrixNode, Environment env) {

        int numRows = matrixNode.children.size();
        int numColumns = matrixNode.getChild(0).children.size();
        Object[][] matrix = new Object[numRows][numColumns];

        for (int i = 0; i < numRows; i++) {
            Lexeme rowNode = matrixNode.getChild(i);
            for (int j = 0; j < numColumns; j++) {
                Lexeme expression = rowNode.getChild(j);
                Lexeme value = evaluateExpression(expression, env);
                while (value != null && value.getChildrenSize() == 1) {
                    value = value.getChild(0);
                }
                if (value != null && value.getType() == IDENTIFIER) {
                    value = env.lookup(value);
                }
                matrix[i][j] = value;
            }
        }
        int line = 0;
        try { line = matrixNode.getLineNumber(); } catch (Exception ignored) {}
        return new Lexeme(Types.matrix, line, matrix);
    }

    private Lexeme evaluateCollectionInitialization(Lexeme collectionInitialization, Environment env) {

        Lexeme collectionType = collectionInitialization.getChild(0);
        Lexeme identifier = collectionInitialization.getChild(1);
        Lexeme dataType = collectionInitialization.getChild(2);
        Lexeme collectionHelper = collectionInitialization.getChild(3);

        switch (collectionType.getType()) {
            case array -> evaluateArrayHelper(collectionHelper, identifier, dataType, env);
            case linkedList -> evaluateLinkedListHelper(collectionHelper, identifier, dataType, env);
            case matrix -> evaluateMatrixHelper(collectionHelper, identifier, dataType, env);
            default -> throw new RuntimeException("Unsupported collection type: " + collectionType.getType());

        }


        return null;
    }

    private void evaluateArrayHelper(Lexeme arrayHelper, Lexeme identifier, Lexeme dataType, Environment env) {
        if (arrayHelper.getChildrenSize() == 1) {
            int size = arrayHelper.getChild(0).getIntValue();
            ArrayList<Object> array = new ArrayList<>(Collections.nCopies(size, null));
            env.add(Types.array, identifier, new Lexeme(Types.array, identifier.getLineNumber(), array));
        } else {

//            Lexeme assignment = arrayHelper.getChild(0);
            Lexeme expression = arrayHelper.getChild(1);
            Object value = evaluateExpression(expression, env);
            env.add(Types.array, identifier, new Lexeme(Types.array, identifier.getLineNumber(), (Object[]) value));

        }
    }

    private void evaluateLinkedListHelper(Lexeme linkedListHelper, Lexeme identifier, Lexeme dataType, Environment env) {
        if (linkedListHelper.getChildrenSize() == 2) {
            Lexeme assignment = linkedListHelper.getChild(0);
            Lexeme expression = linkedListHelper.getChild(1);
            Object value = evaluateExpression(expression, env);
            env.add(Types.linkedList, identifier, new Lexeme(Types.linkedList, identifier.getLineNumber(), (ArrayList<Object>) value));
        } else {
            ArrayList<Object> linkedList = new ArrayList<>();
            env.add(Types.linkedList, identifier, new Lexeme(Types.linkedList, identifier.getLineNumber(), linkedList));
        }
    }

    private void evaluateMatrixHelper(Lexeme matrixHelper, Lexeme identifier, Lexeme dataType, Environment env) {

        if (matrixHelper.getChildrenSize() == 4) {
            int numColumns = matrixHelper.getChild(1).getIntValue();
            int numRows = matrixHelper.getChild(3).getIntValue();

            Object[][] matrixToADD = new Object[numRows][numColumns];
            env.add(Types.matrix, identifier, new Lexeme(Types.matrix, identifier.getLineNumber(), matrixToADD));
            return;
        }

        Lexeme expression = matrixHelper.getChild(matrixHelper.getChildrenSize() - 1);
        Lexeme evaluated = evaluateStatement(expression, env);
        while (evaluated != null && evaluated.getType() != Types.matrix && evaluated.getChildrenSize() > 0) {
            evaluated = evaluated.getChild(0);
        }
        if (evaluated != null && evaluated.getType() == IDENTIFIER) {
            evaluated = env.lookup(evaluated);
        }

        if (evaluated != null && evaluated.getType() == Types.matrix) {
            env.add(Types.matrix, identifier, new Lexeme(Types.matrix, identifier.getLineNumber(), evaluated.getMatrixValue()));
            return;
        }

        Lexeme matrixLiteral = findMatrixLiteral(expression);
        if (matrixLiteral != null) {
            int numRows = matrixLiteral.getChildrenSize();
            int numCols = numRows > 0 ? matrixLiteral.getChild(0).getChildrenSize() : 0;
            Object[][] value = new Object[numRows][numCols];
            for (int i = 0; i < numRows; i++) {
                Lexeme rowList = matrixLiteral.getChild(i);
                for (int j = 0; j < rowList.getChildrenSize(); j++) {
                    Lexeme cell = evaluateStatement(rowList.getChild(j), env);
                    while (cell != null && cell.getChildrenSize() == 1) {
                        cell = cell.getChild(0);
                    }
                    if (cell != null && cell.getType() == IDENTIFIER) {
                        cell = env.lookup(cell);
                    }
                    value[i][j] = cell;
                }
            }
            env.add(Types.matrix, identifier, new Lexeme(Types.matrix, identifier.getLineNumber(), value));
            return;
        }

        throw new RuntimeException("Could not initialize matrix " + identifier.getStringValue() + " from expression");
    }

    private Lexeme findMatrixLiteral(Lexeme node) {
        if (node == null) return null;
        if (node.getType() == Types.matrix) return node;
        for (int i = 0; i < node.getChildrenSize(); i++) {
            Lexeme found = findMatrixLiteral(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }


    private void evaluateArrayAssignment(Lexeme arrayAssignment, Environment env) {
        Lexeme identifier = arrayAssignment.getChild(0);
        int size = arrayAssignment.getChild(1).getIntValue();
        ArrayList<Object> array = new ArrayList<>(Collections.nCopies(size, null));
        env.add(Types.array, identifier, new Lexeme(Types.array, identifier.getLineNumber(), array));
    }

    private void evaluateLinkedListAssignment(Lexeme linkedListAssignment, Environment env) {
        Lexeme identifier = linkedListAssignment.getChild(0);
        Lexeme operation = linkedListAssignment.getChild(1);

        if (operation.getType() == Types.empty) {
            int size = linkedListAssignment.getChild(2).getIntValue();
            ArrayList<Object> linkedList = new ArrayList<>(Collections.nCopies(size, null));
            env.add(Types.linkedList, identifier, new Lexeme(Types.linkedList, identifier.getLineNumber(), linkedList));
        } else if (operation.getType() == Types.PLUS) {
            Lexeme sizeNode = linkedListAssignment.getChild(2);
            int size = sizeNode.getType() == Types.empty ? 0 : sizeNode.getIntValue();
            ArrayList<Object> linkedList = new ArrayList<>(Collections.nCopies(size, null));
            env.add(Types.linkedList, identifier, new Lexeme(Types.linkedList, identifier.getLineNumber(), linkedList));
        } else {
            throw new RuntimeException("Unsupported operation for linked list assignment: " + operation.getType());
        }
    }

    private Lexeme evaluateMatrixAssignment(Lexeme matrixAssignment, Environment env) {
        Lexeme identifier = matrixAssignment.getChild(0);
        Object[][] editableMatrix = env.lookup(identifier).getMatrixValue();

        Lexeme step1;
        try {
            step1 = eval(matrixAssignment.getChild(4), env);
            while (step1.getChildrenSize() > 0) {
                step1 = step1.getChild(0);
            }
        } catch (Exception ImABadProgrammer) {
            step1 = evalBinaryExpression(matrixAssignment.getChild(4), env);
            try {
                while (step1.getChildrenSize() > 0) {
                    step1 = step1.getChild(0);
                }
            } catch (Exception e) {
            }
        }

        int row = matrixAssignment.getChild(1).getIntValue();
        int col = matrixAssignment.getChild(2).getIntValue();
        editableMatrix[row][col] = step1;

        env.lookup(identifier).setMatrixValue(editableMatrix);

        return new Lexeme(Types.matrix, identifier.getLineNumber(), editableMatrix);
    }

//

}
