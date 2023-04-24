
package src.Evaluation;

import src.Recognizer.Parser;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Lexer;
import src.Evaluation.Environment;
import src.lexicalAnalysis.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

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

        for (int i = 0; i < statementList.getChildrenSize(); i++) {
            Lexeme statement = statementList.getChild(i);
            evaluateStatement(statement, env);
        }

        return statementList; // You may return a more meaningful value if required.
    }

    public Lexeme evaluateStatement(Lexeme statement, Environment env) {
        if (statement == null) return new Lexeme(null);
        switch (statement.getType()) {
            case statementList:
                for (int i = 0; i < statement.getChildrenSize(); i++) {
                    evaluateStatement(statement.getChild(i), env);
                }
                break; // Add this break statement
            case varInitialization:
                return evaluateInitialization(statement, env);
            case binaryExpression:
                return evalBinaryExpression(statement, env);
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


//

            default:
                throw new RuntimeException("Unknown statement type: " + statement.getType());
        }
        return null;
    }

    public Lexeme evaluateInitialization(Lexeme initialization, Environment environment) {

        // Assuming the structure of the initialization Lexeme is: [VAR, IDENTIFIER_LIST, DATA_TYPE, ASSIGNMENT, EXPRESSION]
        Lexeme identList = initialization.getChild(1);
        Lexeme type = initialization.getChild(2);
        Lexeme expression = initialization.getChild(4);

        Lexeme evaluatedExpression = evaluateExpression(expression, environment);
        if (evaluatedExpression.getType() == primary) {
            evaluatedExpression = evalPrimary(evaluatedExpression, environment);
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
        } else {
            operand = eval(unaryExpression.getChild(0), env);
        }

        if (operator != null) {
            switch (operator.getType()) {
                case MINUS_UNARY:
                    return new Lexeme(Types.INTERGER, operand.getLineNumber(), -operand.getIntValue());
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
            case MINUS_UNARY, NOT, INVERSE, PLUS_PLUS, MINUS_MINUS -> true;
            default -> false;
        };
    }

    private Lexeme evalBinaryExpression(Lexeme binaryExpression, Environment env) {
        if (binaryExpression.getChildrenSize() == 1) {
            return eval(binaryExpression.getChild(0), env);
        }
        Lexeme leftPrimary = binaryExpression.getChild(0);
        Lexeme leftOperand = evalPrimary(leftPrimary, env);
        Lexeme operator = binaryExpression.getChild(1);
        Lexeme rightPrimary = binaryExpression.getChild(2);
        Lexeme rightOperand = evalPrimary(rightPrimary, env);
        Lexeme result = null;


        // Look up the values of the identifiers in the environment
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
                    throw new RuntimeException("Unsupported types for PLUS operation: " + leftOperand.getType() + ", " + rightOperand.getType());
                }
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

    private Lexeme evalPrimary(Lexeme primary, Environment env) {

        if (primary == null) return null;
        Lexeme innerNode = primary.getChild(0);
        if (innerNode.getType() == Types.INTERGER) {
            return innerNode;
        } else {
            return eval(innerNode, env);
        }
    }

    private Lexeme evaluateParenthesizedExpression(Lexeme parenExpr, Environment env) {
        // Implement the evaluation logic for parenthesized expressions.
        // You can just evaluate the child expression as it's already in the correct order.
        return evaluateExpression(parenExpr.getChild(0), env);
    }

    public Lexeme evalVarAssignment(Lexeme varAssignment, Environment environment) {
        // Assuming the structure of the varAssignment Lexeme is: [IDENTIFIER, ASSIGNMENT, EXPRESSION]
        Lexeme identifier = varAssignment.getChild(0);
        System.out.println(identifier.getStringValue());
        Lexeme expression = varAssignment.getChild(2);

        Lexeme evaluatedExpression = eval(expression, environment);
        while (evaluatedExpression.getChildrenSize() > 0) {
            evaluatedExpression = evaluatedExpression.getChild(0);
        }


        // Updating the variable in the environment
        if (environment.lookup(identifier) != null) {

            environment.update(identifier, evaluatedExpression);
        } else {
            throw new RuntimeException("Variable not found: " + identifier.getStringValue());
        }

        System.out.println(environment);
        return evaluatedExpression;
    }

    public Lexeme evaluateFunctionDefinition(Lexeme funcDefActual, Environment env) {
        funcDefActual.setEnvironment(env);

        Lexeme functionName = funcDefActual.getChild(0);

        env.funcAdd(functionName, funcDefActual);

        return functionName;
    }

    public Lexeme evaluateFunctionCall(Lexeme functionCall, Environment env) {
        // Extract the function name and arguments from the functionCall Lexeme

        Lexeme functionName = functionCall.getChild(0);// Assuming the first child is the function name

        Lexeme funcDefTree = env.lookup(functionName);
        funcDefTree.printAsParseTree();
        functionCall.printAsParseTree();

        Lexeme paramList = funcDefTree.getChild(3);
        Lexeme argList = functionCall.getChild(1);
        Lexeme evaluatedArgList = evalArgList(argList, env);


        // Verify that the number of arguments matches the number of parameters
        if (paramList.getChildrenSize() != evaluatedArgList.getChildrenSize()) {
            throw new IllegalArgumentException("Mismatch between the number of arguments and parameters in the function call: "
                    + functionName.getChild(0) + ". Expected " + paramList.getChildrenSize() + " arguments but received "
                    + evaluatedArgList.getChildrenSize() + " arguments.");
        }

        Environment callEnv = new Environment(env, new ArrayList<>());

        for (int i = 0; i < paramList.getChildrenSize(); i++) {

            Lexeme param = paramList.getChild(i);
            param.printAsParseTree();
            argList.getChild(i).printAsParseTree();
            callEnv.funcAdd(param.getChild(1), evaluatedArgList.getChild(i));


        }

        Lexeme funcBody = funcDefTree.getChild(4);
        Lexeme result = eval(funcBody, callEnv);

        if (result != null && result.getType() == Types.returnStatement) {
            // Evaluate the return statement and return its value
            return evaluateReturnStatement(result, callEnv);
        } else {
            // If there's no return statement, return null
            return null;
        }

    }


    private Lexeme evalArgList(Lexeme argList, Environment env) {
        Lexeme evaluatedArgList = new Lexeme(argList.getType());
        for (int i = 0; i < argList.getChildrenSize(); i++) {
            Lexeme arg = argList.getChild(i);
            Lexeme evaluatedArg = eval(arg, env);
            while (evaluatedArg.getChildrenSize() > 0) {
                System.out.println("hi");
                evaluatedArg = evaluatedArg.getChild(0);
            }
            evaluatedArg.printAsParseTree();
            evaluatedArgList.AddChild(evaluatedArg);
        }
        return evaluatedArgList;
    }

    public Lexeme evaluateReturnStatement(Lexeme returnStatement, Environment env) {
        Lexeme returnValue = returnStatement.getChild(1);
        return evaluateExpression(returnValue, env);
    }

    public Lexeme evaluateConditional(Lexeme conditional, Environment env) {
        Lexeme result = null;
        Lexeme condition = null;

        // Evaluate the condition in the "if" statement
        condition = evaluateExpression(conditional.getChild(0), env);

        if (condition.getBooleanValue()) {
            // Execute the "if" block
            result = evaluateBlock(conditional.getChild(1), env);
        } else {
            // Evaluate "ifelse" statements, if any
            boolean ifElseEvaluated = false;
            int ifElseChildIndex = 2;
            while (!ifElseEvaluated && ifElseChildIndex < conditional.children.size() - 1) {
                condition = evaluateExpression(conditional.getChild(ifElseChildIndex), env);
                if (condition.getBooleanValue()) {
                    // Execute the "ifelse" block
                    result = evaluateBlock(conditional.getChild(ifElseChildIndex + 1), env);
                    ifElseEvaluated = true;
                }
                ifElseChildIndex += 2;
            }

            // If no "ifelse" condition matched, execute the "else" block (if exists)
            if (!ifElseEvaluated && ifElseChildIndex == conditional.children.size() - 1) {
                result = evaluateBlock(conditional.getChild(ifElseChildIndex), env);
            }
        }
        System.out.println(env);
        return result;

    }

    public Lexeme evaluateBlock(Lexeme block, Environment env) {
        Lexeme result = null;
        Environment blockEnv = new Environment(env);

        result = eval(block.getChild(0), blockEnv);

        return result;
    }

    public Lexeme evaluateForLoop(Lexeme forLoop, Environment env) {
        Environment loopEnv = new Environment(env);
        evaluateInitialization(forLoop.getChild(1), loopEnv);
        Lexeme incrementDirection = forLoop.getChild(3);
        while (evaluateStatement(forLoop.getChild(2), loopEnv).getBooleanValue()) {
            System.out.println("areWeInHere");
            Lexeme forblock = forLoop.getChild(4);
            forblock.printAsParseTree();
            Lexeme result = evaluateBlock(forblock, loopEnv);

            if (result != null && result.getType() == Types.BREAK) {
                break;
            }

            Lexeme loopVariable = forLoop.getChild(1).getChild(1).getChild(0);
            System.out.println("loop-variable equals" + loopVariable);
            Lexeme currentValue = loopEnv.lookup(loopVariable);

            if (incrementDirection.getType() == Types.LOOPINCREMENTPLUS) {
                Lexeme incrementedValue = new Lexeme(Types.INTERGER, currentValue.getLineNumber(), currentValue.getIntValue() + 1);
                loopEnv.update(loopVariable, incrementedValue);
            } else {
                Lexeme decrementedValue = new Lexeme(Types.INTERGER, currentValue.getLineNumber(), currentValue.getIntValue() - 1);
                loopEnv.update(loopVariable, decrementedValue);
            }
        }

        System.out.println(loopEnv);
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
//        // Ensure the collection is an array or a linkedList
//        if (collection.getType() != Types.array && collection.getType() != Types.LINKED_LIST) {
//            throw new RuntimeException("forEach loop can only iterate over arrays or linked lists.");
//        }
//
//        // Iterate through the collection
//        for (Lexeme element : collection.getChildren()) {
//            // Add the loop variable to the loop environment with the current element as its value
//            loopEnv.addVariable(loopVariable, element);
//
//            // Evaluate the block for the current element
//            Lexeme result = evaluateBlock(block, loopEnv);
//
//            // Handle break statements
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

            // Handle break statements
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
            double conditionValue = (double) conditionResult.getNumberValue();

            // Check if the condition value is within the acceptable range
            if (Math.abs(conditionValue - 1) <= percentError) {
                Lexeme result = evaluateBlock(block, env);

                // Handle break statements
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
                matrix[i][j] = value.getValue();
            }
        }

        return new Lexeme(Types.matrix, matrixNode.getLineNumber(), matrix);
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
        if (collectionType.getType() == matrix) {
            System.out.println(Arrays.deepToString(env.lookup(identifier).getMatrixValue()));
        }
        System.out.println(env);
        return null;
    }

    private void evaluateArrayHelper(Lexeme arrayHelper, Lexeme identifier, Lexeme dataType, Environment env) {
        if (arrayHelper.getChildrenSize() == 1) {
            int size = arrayHelper.getChild(0).getIntValue();
            ArrayList<Object> array = new ArrayList<>(Collections.nCopies(size, null));
            env.add(Types.array, identifier, new Lexeme(Types.array, identifier.getLineNumber(), array));
        } else {
            Lexeme assignment = arrayHelper.getChild(0);
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
        Lexeme assignment = matrixHelper.getChild(0);
        if (matrixHelper.getChildrenSize() == 4) {
            System.out.println("evaluatingMatrixHelper");
            int numColumns = matrixHelper.getChild(1).getIntValue();
            Lexeme matrixSize = matrixHelper.getChild(2);
            int numRows = matrixHelper.getChild(3).getIntValue();

            Object[][] matrix = new Object[numRows][numColumns];
            for (int i = 0; i < numRows; i++) {
                for (int j = 0; j < numColumns; j++) {

                    matrix[i][j] = null;
                }
            }

            env.add(Types.matrix, identifier, new Lexeme(Types.matrix, identifier.getLineNumber(), matrix));
        } else {
            Lexeme expression = matrixHelper.getChild(1);
            Object value = evaluateExpression(expression, env);
            env.add(Types.matrix, identifier, new Lexeme(Types.matrix, identifier.getLineNumber(), (Object[][]) value));
        }
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
        try {
            editableMatrix[matrixAssignment.getChild(1).getIntValue()][matrixAssignment.getChild(2).getIntValue()] = eval(matrixAssignment.getChild(6), env);
        } catch (Exception d) {
            throw new RuntimeException("index out of bounds for the matrix dawg");
        }

        env.lookup(identifier).setMatrixValue(editableMatrix);
        System.out.println(Arrays.deepToString(env.lookup(identifier).getMatrixValue()));
        return new Lexeme(Types.matrix, identifier.getLineNumber(), editableMatrix);
    }

//

}
