package src.Evaluation;

import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import static src.lexicalAnalysis.Types.*;

public class Evaluator<Parsing> {

    public Environment environment;
    public Lexeme Result;
    private final Random random = new Random();

    public Lexeme eval(Lexeme programParseTree, Environment environment) {
        this.environment = environment;

        try {
            Result = evaluateProgram(programParseTree, environment);
        } catch (ReturnControlException returnControl) {
            Result = returnControl.getValue();
        }
        return Result;
    }

    public Lexeme evaluateProgram(Lexeme programParseTree, Environment env) {
        Lexeme lastResult = null;

        for (int i = 0; i < programParseTree.getChildrenSize(); i++) {
            Lexeme statement = programParseTree.getChild(i);
            Lexeme result = evaluateStatement(statement, env);

            if (statement.getType() == Types.returnStatement) {
                return result;
            }

            if (result != null) {
                lastResult = result;
            }
        }

        return lastResult != null ? lastResult : programParseTree;
    }

    public Lexeme evaluateStatement(Lexeme statement, Environment env) {
        if (statement == null) return new Lexeme(null);

        switch (statement.getType()) {
            case statementList:
                Lexeme lastResult = null;
                for (int i = 0; i < statement.getChildrenSize(); i++) {
                    Lexeme child = statement.getChild(i);
                    Lexeme result = evaluateStatement(child, env);
                    if (child.getType() == Types.returnStatement) {
                        return result;
                    }
                    if (result != null) {
                        lastResult = result;
                    }
                }
                return lastResult;
            case varInitialization:
                return evaluateInitialization(statement, env);
            case binaryExpression:
                return evalBinaryExpression(statement, env);
            case unaryExpression:
                return evalUnaryExpression(statement, env);
            case INTERGER, GEORGE, STRING, DOS, OBJECT:
                return statement;
            case varAssignment:
                return evalVarAssignment(statement, env);
            case functionDefinitionActual:
                return evaluateFunctionDefinition(statement, env);
            case funcCall:
                return evaluateFunctionCall(statement, env);
            case methodCall:
                return evaluateMethodCall(statement, env);
            case fieldAccess:
                return evaluateFieldAccess(statement, env);
            case fieldAssignment:
                return evaluateFieldAssignment(statement, env);
            case classDefinition:
                return evaluateClassDefinition(statement, env);
            case parentheziedExpreission:
                return evaluateParenthesizedExpression(statement, env);
            case primary:
                return evalPrimary(statement, env);
            case returnStatement:
                return evaluateReturnStatement(statement, env);
            case IF:
                return evaluateConditional(statement, env);
            case block:
                return evaluateBlock(statement, env);
            case whileL:
                return evaluateWhileLoop(statement, env);
            case foorLoopBody:
                return evaluateForLoop(statement, env);
            case whilePercent:
                return evaluateWhileThisIsBasicallyTrue(statement.getChild(0), env);
            case whileSortOfBody:
                return evaluateWhileThisIsBasicallyTrue(statement, env);
            case indefPreformBody:
                return evaluateIndefinitelyPerform(statement, env);
            case collectionInitialization:
                return evaluateCollectionInitialization(statement, env);
            case matrixGetter:
                return evaluateMatrixGetter(statement, env);
            case MatrixAssi:
                return evaluateMatrixAssignment(statement, env);
            case matrix:
                return statement.getMatrixValue() == null ? evaluateMatrix(statement, env) : statement;
            case array:
                return evaluateArray(statement, env);
            case linkedList:
                return evaluateLinkedList(statement, env);
            case IDENTIFIER:
                return statement;
            case ERROR:
            case empty:
                return null;
            case breakstatement:
            case BREAK:
                return statement;
            default:
                throw new RuntimeException("Unknown statement type: " + statement.getType());
        }
    }

    public Lexeme evaluateInitialization(Lexeme initialization, Environment environment) {
        Lexeme identList = initialization.getChild(1);
        Lexeme type = initialization.getChild(2);
        Lexeme expression = initialization.getChild(4);

        Lexeme evaluatedExpression = resolveValue(evaluateExpression(expression, environment), environment);

        for (int i = 0; i < identList.getChildrenSize(); i++) {
            Lexeme identifier = identList.getChild(i);
            environment.add(type.getType(), identifier, evaluatedExpression);
        }

        return evaluatedExpression;
    }

    public Lexeme evaluateExpression(Lexeme expression, Environment env) {
        if (expression == null) return null;

        return switch (expression.getType()) {
            case unaryExpression -> evalUnaryExpression(expression, env);
            case binaryExpression -> evalBinaryExpression(expression, env);
            case parentheziedExpreission -> evaluateParenthesizedExpression(expression, env);
            case funcCall -> evaluateFunctionCall(expression, env);
            case functionDefinitionActual -> evaluateFunctionDefinition(expression, env);
            case primary -> evalPrimary(expression, env);
            case matrix -> expression.getMatrixValue() == null ? evaluateMatrix(expression, env) : expression;
            case array -> evaluateArray(expression, env);
            case linkedList -> evaluateLinkedList(expression, env);
            case fieldAccess -> evaluateFieldAccess(expression, env);
            case methodCall -> evaluateMethodCall(expression, env);
            case matrixGetter -> evaluateMatrixGetter(expression, env);
            case INTERGER, GEORGE, STRING, DOS, OBJECT, IDENTIFIER -> expression;
            default -> throw new RuntimeException("Unsupported expression type: " + expression.getType());
        };
    }

    private Lexeme evalUnaryExpression(Lexeme unaryExpression, Environment env) {
        Lexeme operator = null;
        Lexeme operand;

        if (isUnaryOperator(unaryExpression.getChild(0))) {
            operator = unaryExpression.getChild(0);
            operand = resolveValue(evaluateStatement(unaryExpression.getChild(1), env), env);
        } else {
            operand = resolveValue(evaluateStatement(unaryExpression.getChild(0), env), env);
        }

        if (operator == null) {
            return operand;
        }

        return switch (operator.getType()) {
            case MINUS_UNARY, MINUS -> negate(operand);
            case NOT -> new Lexeme(Types.GEORGE, lineOf(operand), !asBoolean(operand));
            case INVERSE -> invert(operand);
            case PLUS_PLUS -> increment(unaryExpression.getChild(1), operand, env, 1);
            case MINUS_MINUS -> increment(unaryExpression.getChild(1), operand, env, -1);
            default -> throw new RuntimeException("Invalid unary operator");
        };
    }

    private boolean isUnaryOperator(Lexeme lexeme) {
        return switch (lexeme.getType()) {
            case MINUS_UNARY, NOT, INVERSE, PLUS_PLUS, MINUS_MINUS, MINUS -> true;
            default -> false;
        };
    }

    private Lexeme evalBinaryExpression(Lexeme binaryExpression, Environment env) {
        if (binaryExpression.getChildrenSize() == 1) {
            return resolveValue(evaluateStatement(binaryExpression.getChild(0), env), env);
        }

        ArrayList<Lexeme> operands = new ArrayList<>();
        ArrayList<Lexeme> operators = new ArrayList<>();

        for (int i = 0; i < binaryExpression.getChildrenSize(); i += 2) {
            operands.add(resolveValue(evaluateStatement(binaryExpression.getChild(i), env), env));
            if (i + 1 < binaryExpression.getChildrenSize()) {
                operators.add(binaryExpression.getChild(i + 1));
            }
        }

        reduceOperations(operands, operators, EXPONENTIATE);
        reduceOperations(operands, operators, MULTIPLY, DIVIDE, MOD, DOT_PRODUCT);
        reduceOperations(operands, operators, PLUS, MINUS);
        reduceOperations(operands, operators, GREATER, GREATER_EQ, LESS, LESS_EQ);
        reduceOperations(operands, operators, EQUALS, NOT_EQUAL);
        reduceOperations(operands, operators, AND);
        reduceOperations(operands, operators, OR);

        if (operands.size() != 1) {
            throw new RuntimeException("Malformed binary expression");
        }

        return operands.get(0);
    }

    private void reduceOperations(ArrayList<Lexeme> operands, ArrayList<Lexeme> operators, Types... matchingTypes) {
        int i = 0;
        while (i < operators.size()) {
            if (matches(operators.get(i).getType(), matchingTypes)) {
                Lexeme result = applyBinaryOperation(operands.get(i), operators.get(i), operands.get(i + 1));
                operands.set(i, result);
                operands.remove(i + 1);
                operators.remove(i);
            } else {
                i++;
            }
        }
    }

    private boolean matches(Types type, Types... matchingTypes) {
        for (Types matchingType : matchingTypes) {
            if (type == matchingType) return true;
        }
        return false;
    }

    private Lexeme applyBinaryOperation(Lexeme leftOperand, Lexeme operator, Lexeme rightOperand) {
        Types op = operator.getType();
        int line = lineOf(leftOperand);

        if (leftOperand.getType() == matrix || rightOperand.getType() == matrix) {
            return applyMatrixOperation(leftOperand, operator, rightOperand);
        }

        return switch (op) {
            case PLUS -> {
                if (leftOperand.getType() == STRING || rightOperand.getType() == STRING) {
                    yield new Lexeme(Types.STRING, line, stringValue(leftOperand) + stringValue(rightOperand));
                }
                yield numericResult(line, asDouble(leftOperand) + asDouble(rightOperand), bothInt(leftOperand, rightOperand));
            }
            case MINUS -> numericResult(line, asDouble(leftOperand) - asDouble(rightOperand), bothInt(leftOperand, rightOperand));
            case MULTIPLY -> numericResult(line, asDouble(leftOperand) * asDouble(rightOperand), bothInt(leftOperand, rightOperand));
            case DIVIDE -> divideNumbers(leftOperand, rightOperand);
            case EXPONENTIATE -> exponentiate(leftOperand, rightOperand);
            case MOD -> {
                if (rightOperand.getIntValue() == 0) throw new RuntimeException("Division by zero in MOD operation");
                yield new Lexeme(Types.INTERGER, line, leftOperand.getIntValue() % rightOperand.getIntValue());
            }
            case GREATER -> new Lexeme(Types.GEORGE, line, asDouble(leftOperand) > asDouble(rightOperand));
            case GREATER_EQ -> new Lexeme(Types.GEORGE, line, asDouble(leftOperand) >= asDouble(rightOperand));
            case LESS -> new Lexeme(Types.GEORGE, line, asDouble(leftOperand) < asDouble(rightOperand));
            case LESS_EQ -> new Lexeme(Types.GEORGE, line, asDouble(leftOperand) <= asDouble(rightOperand));
            case EQUALS -> new Lexeme(Types.GEORGE, line, valuesEqual(leftOperand, rightOperand));
            case NOT_EQUAL -> new Lexeme(Types.GEORGE, line, !valuesEqual(leftOperand, rightOperand));
            case AND -> new Lexeme(Types.GEORGE, line, asBoolean(leftOperand) && asBoolean(rightOperand));
            case OR -> new Lexeme(Types.GEORGE, line, asBoolean(leftOperand) || asBoolean(rightOperand));
            default -> throw new RuntimeException("Invalid binary operator");
        };
    }

    private Lexeme applyMatrixOperation(Lexeme leftOperand, Lexeme operator, Lexeme rightOperand) {
        Types op = operator.getType();
        int line = lineOf(leftOperand);

        if (leftOperand.getType() == matrix && rightOperand.getType() == matrix) {
            return switch (op) {
                case PLUS -> new Lexeme(Types.matrix, line, matrixElementWise(leftOperand.getMatrixValue(), rightOperand.getMatrixValue(), PLUS, line));
                case MINUS -> new Lexeme(Types.matrix, line, matrixElementWise(leftOperand.getMatrixValue(), rightOperand.getMatrixValue(), MINUS, line));
                case MULTIPLY -> new Lexeme(Types.matrix, line, matrixMultiplication(leftOperand.getMatrixValue(), rightOperand.getMatrixValue(), line));
                case DOT_PRODUCT -> new Lexeme(Types.matrix, line, matrixDotProduct(leftOperand.getMatrixValue(), rightOperand.getMatrixValue(), line));
                case EQUALS -> new Lexeme(Types.GEORGE, line, matricesEqual(leftOperand.getMatrixValue(), rightOperand.getMatrixValue()));
                case NOT_EQUAL -> new Lexeme(Types.GEORGE, line, !matricesEqual(leftOperand.getMatrixValue(), rightOperand.getMatrixValue()));
                default -> throw new RuntimeException("Unsupported matrix operation: " + op);
            };
        }

        if (leftOperand.getType() == matrix && isNumeric(rightOperand)) {
            return switch (op) {
                case PLUS, MINUS, MULTIPLY, DIVIDE -> new Lexeme(Types.matrix, line,
                        matrixScalar(leftOperand.getMatrixValue(), rightOperand, op, line, false));
                default -> throw new RuntimeException("Unsupported matrix/scalar operation: " + op);
            };
        }

        if (isNumeric(leftOperand) && rightOperand.getType() == matrix) {
            return switch (op) {
                case PLUS, MINUS, MULTIPLY -> new Lexeme(Types.matrix, line,
                        matrixScalar(rightOperand.getMatrixValue(), leftOperand, op, line, true));
                default -> throw new RuntimeException("Unsupported scalar/matrix operation: " + op);
            };
        }

        throw new RuntimeException("Unsupported matrix operation: " + leftOperand.getType() + " " + op + " " + rightOperand.getType());
    }

    private Object[][] matrixDotProduct(Object[][] leftMatrix, Object[][] rightMatrix, int line) {
        if (isVector(leftMatrix) && isVector(rightMatrix) && cellCount(leftMatrix) == cellCount(rightMatrix)) {
            Object[][] result = new Object[1][1];
            double sum = 0;
            boolean allInt = true;
            ArrayList<Lexeme> leftCells = flattenMatrix(leftMatrix, line);
            ArrayList<Lexeme> rightCells = flattenMatrix(rightMatrix, line);

            for (int i = 0; i < leftCells.size(); i++) {
                Lexeme left = leftCells.get(i);
                Lexeme right = rightCells.get(i);
                if (!bothInt(left, right)) allInt = false;
                sum += asDouble(left) * asDouble(right);
            }

            result[0][0] = numericResult(line, sum, allInt);
            return result;
        }

        return matrixMultiplication(leftMatrix, rightMatrix, line);
    }

    private Object[][] matrixMultiplication(Object[][] leftMatrix, Object[][] rightMatrix, int line) {
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
                    Lexeme left = matrixCell(leftMatrix[i][k], line);
                    Lexeme right = matrixCell(rightMatrix[k][j], line);
                    if (!bothInt(left, right)) allInt = false;
                    sum += asDouble(left) * asDouble(right);
                }

                resultMatrix[i][j] = numericResult(line, sum, allInt);
            }
        }

        return resultMatrix;
    }

    private Object[][] matrixElementWise(Object[][] leftMatrix, Object[][] rightMatrix, Types op, int line) {
        checkSameMatrixShape(leftMatrix, rightMatrix, op);

        Object[][] resultMatrix = new Object[leftMatrix.length][leftMatrix[0].length];

        for (int i = 0; i < leftMatrix.length; i++) {
            for (int j = 0; j < leftMatrix[i].length; j++) {
                Lexeme left = matrixCell(leftMatrix[i][j], line);
                Lexeme right = matrixCell(rightMatrix[i][j], line);
                double result = op == PLUS ? asDouble(left) + asDouble(right) : asDouble(left) - asDouble(right);
                resultMatrix[i][j] = numericResult(line, result, bothInt(left, right));
            }
        }

        return resultMatrix;
    }

    private Object[][] matrixScalar(Object[][] matrix, Lexeme scalar, Types op, int line, boolean scalarFirst) {
        Object[][] resultMatrix = new Object[matrix.length][matrix[0].length];

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                Lexeme cell = matrixCell(matrix[i][j], line);
                double left = scalarFirst ? asDouble(scalar) : asDouble(cell);
                double right = scalarFirst ? asDouble(cell) : asDouble(scalar);
                boolean resultIsInt = bothInt(cell, scalar);

                resultMatrix[i][j] = switch (op) {
                    case PLUS -> numericResult(line, left + right, resultIsInt);
                    case MINUS -> numericResult(line, left - right, resultIsInt);
                    case MULTIPLY -> numericResult(line, left * right, resultIsInt);
                    case DIVIDE -> {
                        if (right == 0) throw new RuntimeException("Division by zero error");
                        yield numericResult(line, left / right, false);
                    }
                    default -> throw new RuntimeException("Unsupported matrix/scalar operation: " + op);
                };
            }
        }

        return resultMatrix;
    }

    private void checkSameMatrixShape(Object[][] leftMatrix, Object[][] rightMatrix, Types op) {
        if (leftMatrix.length != rightMatrix.length || leftMatrix[0].length != rightMatrix[0].length) {
            throw new RuntimeException("Matrix dimensions do not match for " + op + ": " +
                    leftMatrix.length + "x" + leftMatrix[0].length + " and " +
                    rightMatrix.length + "x" + rightMatrix[0].length);
        }
    }

    private Lexeme evalPrimary(Lexeme primary, Environment env) {
        if (primary == null) return null;
        return evaluateStatement(primary.getChild(0), env);
    }

    private Lexeme evaluateParenthesizedExpression(Lexeme parenExpr, Environment env) {
        return evaluateExpression(parenExpr.getChild(0), env);
    }

    public Lexeme evalVarAssignment(Lexeme varAssignment, Environment environment) {
        Lexeme identifier = varAssignment.getChild(0);
        Lexeme expression = varAssignment.getChild(2);
        Lexeme evaluatedExpression = resolveValue(evaluateStatement(expression, environment), environment);

        environment.update(identifier, evaluatedExpression);

        return evaluatedExpression;
    }

    public Lexeme evaluateFunctionDefinition(Lexeme funcDefActual, Environment env) {
        funcDefActual.setEnvironment(env);

        Lexeme functionName = funcDefActual.getChild(0);
        env.funcAdd(functionName, funcDefActual);

        return functionName;
    }

    public Lexeme evaluateClassDefinition(Lexeme classDefinition, Environment env) {
        classDefinition.setEnvironment(env);
        env.funcAdd(classDefinition.getChild(1), classDefinition);
        return classDefinition.getChild(1);
    }

    public Lexeme evaluateFunctionCall(Lexeme functionCall, Environment env) {
        Lexeme functionName = functionCall.getChild(0);

        if ("print".equals(functionName.getStringValue())) {
            Lexeme evaluatedArgList = evalArgList(functionCall.getChild(1), env);
            for (int i = 0; i < evaluatedArgList.getChildrenSize(); i++) {
                System.out.print(stringify(evaluatedArgList.getChild(i)));
            }
            return null;
        }

        if ("random".equals(functionName.getStringValue())) {
            return randomNumber(functionCall, env);
        }

        if ("Canvas".equals(functionName.getStringValue())) {
            return createCanvas(functionCall, env);
        }

        if ("sin".equals(functionName.getStringValue())) {
            Lexeme evaluatedArgList = evalArgList(functionCall.getChild(1), env);
            double val = evaluatedArgList.getChild(0).getDosValue();
            return new Lexeme(Types.DOS, functionName.getLineNumber(), Math.sin(val));
        }

        if ("cos".equals(functionName.getStringValue())) {
            Lexeme evaluatedArgList = evalArgList(functionCall.getChild(1), env);
            double val = evaluatedArgList.getChild(0).getDosValue();
            return new Lexeme(Types.DOS, functionName.getLineNumber(), Math.cos(val));
        }

        Lexeme funcDefTree = env.lookup(functionName);
        if (funcDefTree.getType() == Types.classDefinition) {
            return createObject(funcDefTree, functionCall.getChild(1), env);
        }

        Lexeme paramList = funcDefTree.getChild(3);
        Lexeme evaluatedArgList = evalArgList(functionCall.getChild(1), env);

        if (paramList.getChildrenSize() != evaluatedArgList.getChildrenSize()) {
            throw new IllegalArgumentException("Mismatch between the number of arguments and parameters in the function call: "
                    + functionName.getStringValue() + ". Expected " + paramList.getChildrenSize() + " arguments but received "
                    + evaluatedArgList.getChildrenSize() + " arguments.");
        }

        Environment callEnv = new Environment(env, new ArrayList<>());

        for (int i = 0; i < paramList.getChildrenSize(); i++) {
            Lexeme param = paramList.getChild(i);
            callEnv.funcAdd(param.getChild(1), evaluatedArgList.getChild(i));
        }

        return eval(funcDefTree.getChild(4), callEnv);
    }

    private Lexeme randomNumber(Lexeme functionCall, Environment env) {
        Lexeme evaluatedArgList = evalArgList(functionCall.getChild(1), env);
        int line = lineOf(functionCall);

        if (evaluatedArgList.getChildrenSize() == 0) {
            return new Lexeme(Types.DOS, line, random.nextDouble());
        }

        if (evaluatedArgList.getChildrenSize() == 2) {
            double min = asDouble(evaluatedArgList.getChild(0));
            double max = asDouble(evaluatedArgList.getChild(1));
            return new Lexeme(Types.DOS, line, min + (max - min) * random.nextDouble());
        }

        throw new IllegalArgumentException("random expected 0 or 2 arguments but received " + evaluatedArgList.getChildrenSize());
    }

    private Lexeme createCanvas(Lexeme functionCall, Environment env) {
        Lexeme evaluatedArgList = evalArgList(functionCall.getChild(1), env);
        int line = lineOf(functionCall);
        int width = evaluatedArgList.getChildrenSize() > 0 ? evaluatedArgList.getChild(0).getIntValue() : 200;
        int height = evaluatedArgList.getChildrenSize() > 1 ? evaluatedArgList.getChild(1).getIntValue() : 200;
        int windowWidth = evaluatedArgList.getChildrenSize() > 2 ? evaluatedArgList.getChild(2).getIntValue() : width;
        int windowHeight = evaluatedArgList.getChildrenSize() > 3 ? evaluatedArgList.getChild(3).getIntValue() : height;

        Environment objectEnv = new Environment(env, new ArrayList<>());
        PixelCanvas canvas = new PixelCanvas(width, height, windowWidth, windowHeight);
        Lexeme object = new Lexeme(Types.OBJECT, line, objectEnv);
        object.setStringValue("Canvas");
        object.setNativeValue(canvas);
        objectEnv.add(Types.OBJECT, new Lexeme(Types.IDENTIFIER, line, "this"), object);
        objectEnv.add(Types.INTERGER, new Lexeme(Types.IDENTIFIER, line, "width"), new Lexeme(Types.INTERGER, line, width));
        objectEnv.add(Types.INTERGER, new Lexeme(Types.IDENTIFIER, line, "height"), new Lexeme(Types.INTERGER, line, height));
        objectEnv.add(Types.INTERGER, new Lexeme(Types.IDENTIFIER, line, "windowWidth"), new Lexeme(Types.INTERGER, line, windowWidth));
        objectEnv.add(Types.INTERGER, new Lexeme(Types.IDENTIFIER, line, "windowHeight"), new Lexeme(Types.INTERGER, line, windowHeight));
        return object;
    }

    private Lexeme createObject(Lexeme classDefinition, Lexeme argList, Environment env) {
        int line = lineOf(classDefinition.getChild(1));
        Environment classEnv = new Environment(env, new ArrayList<>());
        Lexeme object = new Lexeme(Types.OBJECT, line, classEnv);
        object.setStringValue(classDefinition.getChild(1).getStringValue());
        classEnv.add(Types.OBJECT, new Lexeme(Types.IDENTIFIER, line, "this"), object);

        Lexeme block = classDefinition.getChild(2);
        if (block.getChildrenSize() > 0) {
            Lexeme statements = block.getChild(0);
            for (int i = 0; i < statements.getChildrenSize(); i++) {
                Lexeme statement = statements.getChild(i);
                if (statement.getType() == Types.varInitialization || statement.getType() == Types.functionDefinitionActual) {
                    evaluateStatement(statement, classEnv);
                }
            }
        }

        Lexeme initName = new Lexeme(Types.IDENTIFIER, line, "init");
        if (classEnv.hasLocal(initName)) {
            evaluateMethodCall(object, initName, argList, env);
        }

        return object;
    }

    public Lexeme evaluateMethodCall(Lexeme methodCall, Environment env) {
        Lexeme receiver = resolveValue(evaluateStatement(methodCall.getChild(0), env), env);
        return evaluateMethodCall(receiver, methodCall.getChild(1), methodCall.getChild(2), env);
    }

    private Lexeme evaluateMethodCall(Lexeme receiver, Lexeme methodName, Lexeme argList, Environment env) {
        if (receiver.getType() != Types.OBJECT) {
            throw new RuntimeException("Method calls require an object");
        }

        if (receiver.getNativeValue() instanceof PixelCanvas canvas) {
            return evaluateCanvasMethod(receiver, canvas, methodName, argList, env);
        }

        Environment objectEnv = receiver.getObjectEnvironment();
        Lexeme method = objectEnv.lookup(methodName);
        Lexeme paramList = method.getChild(3);
        Lexeme evaluatedArgList = evalArgList(argList, env);

        if (paramList.getChildrenSize() != evaluatedArgList.getChildrenSize()) {
            throw new IllegalArgumentException("Mismatch between the number of arguments and parameters in the method call: "
                    + methodName.getStringValue() + ". Expected " + paramList.getChildrenSize() + " arguments but received "
                    + evaluatedArgList.getChildrenSize() + " arguments.");
        }

        Environment callEnv = new Environment(objectEnv, new ArrayList<>());
        callEnv.funcAdd(new Lexeme(Types.IDENTIFIER, methodName.getLineNumber(), "this"), receiver);

        for (int i = 0; i < paramList.getChildrenSize(); i++) {
            Lexeme param = paramList.getChild(i);
            callEnv.funcAdd(param.getChild(1), evaluatedArgList.getChild(i));
        }

        return eval(method.getChild(4), callEnv);
    }

    private Lexeme evaluateCanvasMethod(Lexeme receiver, PixelCanvas canvas, Lexeme methodName, Lexeme argList, Environment env) {
        Lexeme evaluatedArgList = evalArgList(argList, env);
        int line = methodName.getLineNumber();

        switch (methodName.getStringValue()) {
            case "setGridSize":
                requireArgCount(methodName, evaluatedArgList, 2);
                int width = evaluatedArgList.getChild(0).getIntValue();
                int height = evaluatedArgList.getChild(1).getIntValue();
                canvas.setGridSize(width, height);
                receiver.getObjectEnvironment().setLocal(new Lexeme(Types.IDENTIFIER, line, "width"), new Lexeme(Types.INTERGER, line, width));
                receiver.getObjectEnvironment().setLocal(new Lexeme(Types.IDENTIFIER, line, "height"), new Lexeme(Types.INTERGER, line, height));
                return new Lexeme(Types.INTERGER, line, 0);
            case "setWindowSize":
                requireArgCount(methodName, evaluatedArgList, 2);
                int windowWidth = evaluatedArgList.getChild(0).getIntValue();
                int windowHeight = evaluatedArgList.getChild(1).getIntValue();
                canvas.setWindowSize(windowWidth, windowHeight);
                receiver.getObjectEnvironment().setLocal(new Lexeme(Types.IDENTIFIER, line, "windowWidth"), new Lexeme(Types.INTERGER, line, windowWidth));
                receiver.getObjectEnvironment().setLocal(new Lexeme(Types.IDENTIFIER, line, "windowHeight"), new Lexeme(Types.INTERGER, line, windowHeight));
                return new Lexeme(Types.INTERGER, line, 0);
            case "setPixel":
                requireArgCount(methodName, evaluatedArgList, 5);
                canvas.setPixel(
                        evaluatedArgList.getChild(0).getIntValue(),
                        evaluatedArgList.getChild(1).getIntValue(),
                        evaluatedArgList.getChild(2).getIntValue(),
                        evaluatedArgList.getChild(3).getIntValue(),
                        evaluatedArgList.getChild(4).getIntValue());
                return new Lexeme(Types.INTERGER, line, 0);
            case "exportPPM":
                requireArgCount(methodName, evaluatedArgList, 1);
                String filename = evaluatedArgList.getChild(0).getStringValue();
                canvas.exportPPM(filename);
                return new Lexeme(Types.INTERGER, line, 0);
            case "captureFrame":
                requireArgCount(methodName, evaluatedArgList, 0);
                canvas.captureFrame();
                return new Lexeme(Types.INTERGER, line, 0);
            case "collapse":
                requireArgCount(methodName, evaluatedArgList, 2);
                String outName = evaluatedArgList.getChild(0).getStringValue();
                int scaleFactor = evaluatedArgList.getChild(1).getIntValue();
                canvas.collapse(outName, scaleFactor);
                return new Lexeme(Types.INTERGER, line, 0);
            default:
                throw new RuntimeException("Canvas has no method named " + methodName.getStringValue());
        }
    }

    private void requireArgCount(Lexeme name, Lexeme args, int expected) {
        if (args.getChildrenSize() != expected) {
            throw new IllegalArgumentException(name.getStringValue() + " expected " + expected + " arguments but received " + args.getChildrenSize());
        }
    }

    public Lexeme evaluateFieldAccess(Lexeme fieldAccess, Environment env) {
        Lexeme receiver = resolveValue(evaluateStatement(fieldAccess.getChild(0), env), env);
        if (receiver.getType() != Types.OBJECT) {
            throw new RuntimeException("Field access requires an object");
        }

        return receiver.getObjectEnvironment().lookup(fieldAccess.getChild(1));
    }

    public Lexeme evaluateFieldAssignment(Lexeme fieldAssignment, Environment env) {
        Lexeme receiver = resolveValue(evaluateStatement(fieldAssignment.getChild(0), env), env);
        if (receiver.getType() != Types.OBJECT) {
            throw new RuntimeException("Field assignment requires an object");
        }

        Lexeme value = resolveValue(evaluateStatement(fieldAssignment.getChild(3), env), env);
        receiver.getObjectEnvironment().setLocal(fieldAssignment.getChild(1), value);
        return value;
    }

    private Lexeme evalArgList(Lexeme argList, Environment env) {
        Lexeme evaluatedArgList = new Lexeme(argList.getType());

        for (int i = 0; i < argList.getChildrenSize(); i++) {
            Lexeme evaluatedArg = resolveValue(evaluateExpression(argList.getChild(i), env), env);
            evaluatedArgList.AddChild(evaluatedArg);
        }

        return evaluatedArgList;
    }

    public Lexeme evaluateReturnStatement(Lexeme returnStatement, Environment env) {
        throw new ReturnControlException(resolveValue(evaluateExpression(returnStatement.getChild(1), env), env));
    }

    public Lexeme evaluateConditional(Lexeme conditional, Environment env) {
        Lexeme condition = resolveValue(evaluateExpression(conditional.getChild(0), env), env);

        if (asBoolean(condition)) {
            return evaluateBlock(conditional.getChild(1), env);
        }

        int index = 2;
        while (index + 1 < conditional.getChildrenSize()) {
            condition = resolveValue(evaluateExpression(conditional.getChild(index), env), env);
            if (asBoolean(condition)) {
                return evaluateBlock(conditional.getChild(index + 1), env);
            }
            index += 2;
        }

        if (index < conditional.getChildrenSize()) {
            return evaluateBlock(conditional.getChild(index), env);
        }

        return null;
    }

    public Lexeme evaluateBlock(Lexeme block, Environment env) {
        if (block.getChildrenSize() == 0) {
            return null;
        }

        Environment blockEnv = new Environment(env);
        return evaluateStatement(block.getChild(0), blockEnv);
    }

    public Lexeme evaluateWhileLoop(Lexeme whileLoop, Environment env) {
        Lexeme whileBody = whileLoop.getChild(0);
        Lexeme condition = whileBody.getChild(1);
        Lexeme block = whileLoop.getChild(1);

        while (asBoolean(resolveValue(evaluateExpression(condition, env), env))) {
            Lexeme result = evaluateBlock(block, env);
            if (isBreak(result)) break;
        }

        return null;
    }

    public Lexeme evaluateForLoop(Lexeme forLoop, Environment env) {
        Environment loopEnv = new Environment(env);
        evaluateInitialization(forLoop.getChild(1), loopEnv);
        Lexeme incrementDirection = forLoop.getChild(3);

        while (asBoolean(resolveValue(evaluateStatement(forLoop.getChild(2), loopEnv), loopEnv))) {
            Lexeme result = evaluateBlock(forLoop.getChild(4), loopEnv);

            if (isBreak(result)) {
                break;
            }

            Lexeme loopVariable = forLoop.getChild(1).getChild(1).getChild(0);
            Lexeme currentValue = loopEnv.lookup(loopVariable);
            int nextValue = currentValue.getIntValue() + (incrementDirection.getType() == Types.LOOPINCREMENTPLUS ? 1 : -1);
            loopEnv.update(loopVariable, new Lexeme(Types.INTERGER, currentValue.getLineNumber(), nextValue));
        }

        return null;
    }

    public Lexeme evaluateIndefinitelyPerform(Lexeme indefinitelyPerformLoop, Environment env) {
        Lexeme block = indefinitelyPerformLoop.getChild(1);

        while (true) {
            Lexeme result = evaluateBlock(block, env);
            if (isBreak(result)) break;
        }

        return null;
    }

    public Lexeme evaluateWhileThisIsBasicallyTrue(Lexeme whileThisIsBasicallyTrueLoop, Environment env) {
        Lexeme condition = whileThisIsBasicallyTrueLoop.getChild(1);
        Lexeme percentage = whileThisIsBasicallyTrueLoop.getChild(2);
        Lexeme block = whileThisIsBasicallyTrueLoop.getChild(4);
        double percentError = percentage.getIntValue() / 100.0;

        while (conditionPasses(resolveValue(evaluateExpression(condition, env), env), percentError)) {
            Lexeme result = evaluateBlock(block, env);
            if (isBreak(result)) break;
        }

        return null;
    }

    private boolean conditionPasses(Lexeme conditionResult, double percentError) {
        if (conditionResult.getType() == Types.GEORGE) {
            return conditionResult.getBooleanValue();
        }

        return Math.abs(asDouble(conditionResult) - 1) <= percentError;
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
            Lexeme value = resolveValue(evaluateExpression(expression, env), env);
            array.add(value.getValue());
        }

        return new Lexeme(Types.array, lineOf(arrayNode), array);
    }

    private Lexeme evaluateLinkedList(Lexeme linkedListNode, Environment env) {
        Lexeme expressionList = linkedListNode.getChild(0);
        ArrayList<Object> linkedList = new ArrayList<>();

        for (Lexeme expression : expressionList.getChildren()) {
            Lexeme value = resolveValue(evaluateExpression(expression, env), env);
            linkedList.add(value.getValue());
        }

        return new Lexeme(Types.linkedList, lineOf(linkedListNode), linkedList);
    }

    private Lexeme evaluateMatrix(Lexeme matrixNode, Environment env) {
        int numRows = matrixNode.getChildrenSize();
        if (numRows == 0) {
            return new Lexeme(Types.matrix, lineOf(matrixNode), new Object[0][0]);
        }

        int numColumns = matrixNode.getChild(0).getChildrenSize();
        Object[][] matrix = new Object[numRows][numColumns];

        for (int i = 0; i < numRows; i++) {
            Lexeme rowNode = matrixNode.getChild(i);
            if (rowNode.getChildrenSize() != numColumns) {
                throw new RuntimeException("Matrix rows must have the same length");
            }

            for (int j = 0; j < numColumns; j++) {
                Lexeme value = resolveValue(evaluateExpression(rowNode.getChild(j), env), env);
                matrix[i][j] = value;
            }
        }

        return new Lexeme(Types.matrix, lineOf(matrixNode), matrix);
    }

    private Lexeme evaluateCollectionInitialization(Lexeme collectionInitialization, Environment env) {
        Lexeme collectionType = collectionInitialization.getChild(0);
        Lexeme identifier = collectionInitialization.getChild(1);
        Lexeme dataType = collectionInitialization.getChild(2);
        Lexeme collectionHelper = collectionInitialization.getChild(3);

        return switch (collectionType.getType()) {
            case array -> evaluateArrayHelper(collectionHelper, identifier, dataType, env);
            case linkedList -> evaluateLinkedListHelper(collectionHelper, identifier, dataType, env);
            case matrix -> evaluateMatrixHelper(collectionHelper, identifier, dataType, env);
            default -> throw new RuntimeException("Unsupported collection type: " + collectionType.getType());
        };
    }

    private Lexeme evaluateArrayHelper(Lexeme arrayHelper, Lexeme identifier, Lexeme dataType, Environment env) {
        if (arrayHelper.getChildrenSize() == 1) {
            int size = arrayHelper.getChild(0).getIntValue();
            ArrayList<Object> array = new ArrayList<>(Collections.nCopies(size, null));
            Lexeme value = new Lexeme(Types.array, identifier.getLineNumber(), array);
            env.add(Types.array, identifier, value);
            return value;
        }

        Lexeme expression = arrayHelper.getChild(1);
        Lexeme value = resolveValue(evaluateExpression(expression, env), env);
        env.add(Types.array, identifier, value);
        return value;
    }

    private Lexeme evaluateLinkedListHelper(Lexeme linkedListHelper, Lexeme identifier, Lexeme dataType, Environment env) {
        Lexeme value;

        if (linkedListHelper.getChildrenSize() == 2) {
            value = resolveValue(evaluateExpression(linkedListHelper.getChild(1), env), env);
        } else {
            value = new Lexeme(Types.linkedList, identifier.getLineNumber(), new ArrayList<>());
        }

        env.add(Types.linkedList, identifier, value);
        return value;
    }

    private Lexeme evaluateMatrixHelper(Lexeme matrixHelper, Lexeme identifier, Lexeme dataType, Environment env) {
        Lexeme value;

        if (matrixHelper.getChildrenSize() == 4) {
            int numColumns = matrixIndex(matrixHelper.getChild(1), env);
            int numRows = matrixIndex(matrixHelper.getChild(3), env);
            value = new Lexeme(Types.matrix, identifier.getLineNumber(), new Object[numRows][numColumns]);
        } else if (matrixHelper.getChildrenSize() == 1) {
            value = resolveValue(evaluateExpression(matrixHelper.getChild(0), env), env);
            if (value.getType() != Types.matrix) {
                throw new RuntimeException("Matrix collection must be initialized with a matrix value");
            }
        } else {
            throw new RuntimeException("Malformed matrix collection initialization");
        }

        env.add(Types.matrix, identifier, value);
        return value;
    }

    private Lexeme evaluateMatrixAssignment(Lexeme matrixAssignment, Environment env) {
        Lexeme identifier = matrixAssignment.getChild(0);
        Object[][] editableMatrix = env.lookup(identifier).getMatrixValue();
        int row = matrixIndex(matrixAssignment.getChild(1), env);
        int column = matrixIndex(matrixAssignment.getChild(2), env);

        if (row < 0 || row >= editableMatrix.length || column < 0 || column >= editableMatrix[row].length) {
            throw new RuntimeException("Matrix index out of bounds: " + row + "," + column);
        }

        Lexeme value = resolveValue(evaluateStatement(matrixAssignment.getChild(4), env), env);
        editableMatrix[row][column] = value;
        env.lookup(identifier).setMatrixValue(editableMatrix);

        return new Lexeme(Types.matrix, identifier.getLineNumber(), editableMatrix);
    }

    private Lexeme evaluateMatrixGetter(Lexeme matrixGetter, Environment env) {
        Lexeme identifier = matrixGetter.getChild(1);
        Object[][] matrix = env.lookup(identifier).getMatrixValue();
        int row = matrixIndex(matrixGetter.getChild(2), env);
        int column = matrixIndex(matrixGetter.getChild(3), env);

        if (row < 0 || row >= matrix.length || column < 0 || column >= matrix[row].length) {
            throw new RuntimeException("Matrix index out of bounds: " + row + "," + column);
        }

        return matrixCell(matrix[row][column], identifier.getLineNumber());
    }

    private int matrixIndex(Lexeme indexExpression, Environment env) {
        Lexeme value = resolveValue(evaluateExpression(indexExpression, env), env);

        if (value.getType() == Types.INTERGER) {
            return value.getIntValue();
        }

        if (value.getType() == Types.DOS) {
            return (int) value.getDosValue();
        }

        throw new RuntimeException("Matrix index must be numeric");
    }

    private Lexeme resolveValue(Lexeme value, Environment env) {
        if (value == null) return null;

        while (value.getType() != null
                && value.getType() != Types.matrix
                && value.getType() != Types.array
                && value.getType() != Types.linkedList
                && value.getType() != Types.OBJECT
                && value.getType() != Types.IDENTIFIER
                && value.getChildrenSize() == 1) {
            value = value.getChild(0);
        }

        if (value.getType() == Types.IDENTIFIER) {
            return env.lookup(value);
        }

        return value;
    }

    private boolean isBreak(Lexeme value) {
        return value != null && (value.getType() == Types.breakstatement || value.getType() == Types.BREAK);
    }

    private boolean isNumeric(Lexeme value) {
        return value != null && (value.getType() == Types.INTERGER || value.getType() == Types.DOS);
    }

    private boolean bothInt(Lexeme left, Lexeme right) {
        return left.getType() == Types.INTERGER && right.getType() == Types.INTERGER;
    }

    private double asDouble(Lexeme value) {
        if (value.getType() == Types.INTERGER) return value.getIntValue();
        if (value.getType() == Types.DOS) return value.getDosValue();
        throw new RuntimeException("Expected a number but found " + value.getType());
    }

    private boolean asBoolean(Lexeme value) {
        if (value.getType() == Types.GEORGE) return value.getBooleanValue();
        if (value.getType() == Types.INTERGER) return value.getIntValue() != 0;
        if (value.getType() == Types.DOS) return value.getDosValue() != 0.0;
        throw new RuntimeException("Expected a boolean but found " + value.getType());
    }

    private Lexeme numericResult(int line, double value, boolean intResult) {
        if (intResult) {
            return new Lexeme(Types.INTERGER, line, (int) value);
        }

        return new Lexeme(Types.DOS, line, value);
    }

    private Lexeme divideNumbers(Lexeme leftOperand, Lexeme rightOperand) {
        int line = lineOf(leftOperand);

        if (asDouble(rightOperand) == 0) {
            throw new RuntimeException("Division by zero error");
        }

        if (bothInt(leftOperand, rightOperand)) {
            return new Lexeme(Types.INTERGER, line, leftOperand.getIntValue() / rightOperand.getIntValue());
        }

        return new Lexeme(Types.DOS, line, asDouble(leftOperand) / asDouble(rightOperand));
    }

    private Lexeme exponentiate(Lexeme leftOperand, Lexeme rightOperand) {
        double resultValue = Math.pow(asDouble(leftOperand), asDouble(rightOperand));
        return numericResult(lineOf(leftOperand), resultValue, bothInt(leftOperand, rightOperand));
    }

    private Lexeme negate(Lexeme value) {
        if (value.getType() == Types.INTERGER) {
            return new Lexeme(Types.INTERGER, lineOf(value), -value.getIntValue());
        }

        if (value.getType() == Types.DOS) {
            return new Lexeme(Types.DOS, lineOf(value), -value.getDosValue());
        }

        throw new RuntimeException("Cannot negate " + value.getType());
    }

    private Lexeme invert(Lexeme value) {
        double number = asDouble(value);
        if (number == 0) {
            throw new RuntimeException("Division by zero error");
        }
        return new Lexeme(Types.DOS, lineOf(value), 1 / number);
    }

    private Lexeme increment(Lexeme target, Lexeme operand, Environment env, int amount) {
        if (operand.getType() != Types.INTERGER) {
            throw new RuntimeException("Increment and decrement only support integers");
        }

        Lexeme value = new Lexeme(Types.INTERGER, lineOf(operand), operand.getIntValue() + amount);
        env.update(target, value);
        return value;
    }

    private boolean valuesEqual(Lexeme leftOperand, Lexeme rightOperand) {
        if (isNumeric(leftOperand) && isNumeric(rightOperand)) {
            return asDouble(leftOperand) == asDouble(rightOperand);
        }

        if (leftOperand.getType() != rightOperand.getType()) {
            return false;
        }

        return switch (leftOperand.getType()) {
            case GEORGE -> leftOperand.getBooleanValue() == rightOperand.getBooleanValue();
            case STRING -> leftOperand.getStringValue().equals(rightOperand.getStringValue());
            case matrix -> matricesEqual(leftOperand.getMatrixValue(), rightOperand.getMatrixValue());
            case OBJECT -> leftOperand == rightOperand;
            default -> leftOperand == rightOperand;
        };
    }

    private boolean matricesEqual(Object[][] leftMatrix, Object[][] rightMatrix) {
        if (leftMatrix.length != rightMatrix.length || leftMatrix[0].length != rightMatrix[0].length) {
            return false;
        }

        for (int i = 0; i < leftMatrix.length; i++) {
            for (int j = 0; j < leftMatrix[i].length; j++) {
                if (!valuesEqual(matrixCell(leftMatrix[i][j], 0), matrixCell(rightMatrix[i][j], 0))) {
                    return false;
                }
            }
        }

        return true;
    }

    private Lexeme matrixCell(Object cell, int line) {
        if (cell == null) {
            throw new RuntimeException("Matrix cell is empty");
        }

        if (cell instanceof Lexeme lexeme) {
            return lexeme;
        }

        if (cell instanceof Integer integer) {
            return new Lexeme(Types.INTERGER, line, integer);
        }

        if (cell instanceof Double doubleValue) {
            return new Lexeme(Types.DOS, line, doubleValue);
        }

        if (cell instanceof Number number) {
            return new Lexeme(Types.DOS, line, number.doubleValue());
        }

        throw new RuntimeException("Matrix cell is not numeric: " + cell);
    }

    private boolean isVector(Object[][] matrix) {
        return matrix.length == 1 || matrix[0].length == 1;
    }

    private int cellCount(Object[][] matrix) {
        return matrix.length * matrix[0].length;
    }

    private ArrayList<Lexeme> flattenMatrix(Object[][] matrix, int line) {
        ArrayList<Lexeme> cells = new ArrayList<>();

        for (Object[] row : matrix) {
            for (Object cell : row) {
                cells.add(matrixCell(cell, line));
            }
        }

        return cells;
    }

    private String stringify(Lexeme value) {
        if (value == null) return "";

        return switch (value.getType()) {
            case STRING -> value.getStringValue();
            case INTERGER -> Integer.toString(value.getIntValue());
            case DOS -> Double.toString(value.getDosValue());
            case GEORGE -> Boolean.toString(value.getBooleanValue());
            case matrix -> matrixToString(value.getMatrixValue());
            case OBJECT -> value.getStringValue() == null ? "object" : value.getStringValue();
            default -> value.toString();
        };
    }

    private String stringValue(Lexeme value) {
        return value.getType() == Types.STRING ? value.getStringValue() : stringify(value);
    }

    private String matrixToString(Object[][] matrix) {
        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < matrix.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append("[");
            for (int j = 0; j < matrix[i].length; j++) {
                if (j > 0) builder.append(", ");
                builder.append(stringify(matrixCell(matrix[i][j], 0)));
            }
            builder.append("]");
        }

        builder.append("]");
        return builder.toString();
    }

    private int lineOf(Lexeme lexeme) {
        try {
            return lexeme.getLineNumber();
        } catch (Exception e) {
            return 0;
        }
    }
}
