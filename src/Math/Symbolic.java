package src.Math;

import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.util.ArrayList;
import java.util.List;

import static src.lexicalAnalysis.Types.*;

public final class Symbolic {
    private Symbolic() {
    }

    public static class FunctionShape {
        private String parameter;
        private Lexeme parameterType;
        private Lexeme returnType;
        private Lexeme expression;
        private int line;

        public FunctionShape(String parameter, Lexeme parameterType, Lexeme returnType, Lexeme expression, int line) {
            this.parameter = parameter;
            this.parameterType = parameterType;
            this.returnType = returnType;
            this.expression = expression;
            this.line = line;
        }

        public String parameter() {
            return parameter;
        }

        public Lexeme parameterType() {
            return parameterType;
        }

        public Lexeme returnType() {
            return returnType;
        }

        public Lexeme expression() {
            return expression;
        }

        public int line() {
            return line;
        }
    }

    public static FunctionShape functionShape(Lexeme closure) {
        if (closure == null || closure.getType() != FUNCTION || closure.getChildrenSize() != 1) {
            throw new IllegalArgumentException("symbolic operations need a language function");
        }
        return shapeOfDefinition(closure.getChild(0));
    }

    public static FunctionShape shapeOfDefinition(Lexeme definition) {
        if (definition == null || definition.getType() != functionDefinitionActual) {
            throw new IllegalArgumentException("symbolic operations need a language function definition");
        }
        Lexeme parameters = definition.getChild(3);
        Lexeme block = definition.getChild(4);
        if (parameters.getChildrenSize() != 1 || block.getChildrenSize() != 1) {
            throw new IllegalArgumentException("symbolic operations need one parameter and one return expression");
        }
        Lexeme statements = block.getChild(0);
        if (statements.getChildrenSize() != 1 || statements.getChild(0).getType() != returnStatement) {
            throw new IllegalArgumentException("symbolic operations need a function body with one return expression");
        }
        Lexeme parameter = parameters.getChild(0);
        return new FunctionShape(
                parameter.getChild(1).getStringValue(),
                parameter.getChild(0),
                definition.getChild(2),
                canonical(statements.getChild(0).getChild(1)),
                lineOf(definition));
    }

    public static Lexeme derivative(FunctionShape shape) {
        return simplify(differentiate(shape.expression(), shape.parameter(), shape.line()));
    }

    public static Lexeme simplify(Lexeme expression) {
        Lexeme node = canonical(expression);
        if (node.getType() == unaryExpression) {
            Lexeme operator = node.getChild(0);
            Lexeme operand = simplify(node.getChild(1));
            if (operator.getType() == MINUS && isNumber(operand)) return number(-numberValue(operand), lineOf(node));
            if (operator.getType() == MINUS && operand.getType() == unaryExpression
                    && operand.getChild(0).getType() == MINUS) return operand.getChild(1);
            return unary(operator.getType(), operand, lineOf(node));
        }
        if (node.getType() == funcCall) {
            String name = node.getChild(0).getStringValue();
            Lexeme argument = simplify(node.getChild(1).getChild(0));
            if (isNumber(argument) && (name.equals("sin") || name.equals("cos"))) {
                return number(name.equals("sin") ? Math.sin(numberValue(argument)) : Math.cos(numberValue(argument)), lineOf(node));
            }
            return call(name, argument, lineOf(node));
        }
        if (node.getType() != binaryExpression) return node;

        Lexeme left = simplify(node.getChild(0));
        Lexeme operator = node.getChild(1);
        Lexeme right = simplify(node.getChild(2));
        Types op = operator.getType();

        if (isNumber(left) && isNumber(right)) {
            if (op == PLUS) return number(numberValue(left) + numberValue(right), lineOf(node));
            else if (op == MINUS) return number(numberValue(left) - numberValue(right), lineOf(node));
            else if (op == MULTIPLY) return number(numberValue(left) * numberValue(right), lineOf(node));
            else if (op == DIVIDE) return number(numberValue(left) / numberValue(right), lineOf(node));
            else if (op == EXPONENTIATE) return number(Math.pow(numberValue(left), numberValue(right)), lineOf(node));
            else return binary(left, op, right, lineOf(node));
        }

        if (op == PLUS) {
            if (isZero(left)) return right;
            if (isZero(right)) return left;
            return binary(left, PLUS, right, lineOf(node));
        } else if (op == MINUS) {
            if (isZero(right)) return left;
            if (same(left, right)) return integer(0, lineOf(node));
            return binary(left, MINUS, right, lineOf(node));
        } else if (op == MULTIPLY) {
            if (isZero(left) || isZero(right)) return integer(0, lineOf(node));
            if (isOne(left)) return right;
            if (isOne(right)) return left;
            return binary(left, MULTIPLY, right, lineOf(node));
        } else if (op == DIVIDE) {
            if (isZero(left)) return integer(0, lineOf(node));
            if (isOne(right)) return left;
            return binary(left, DIVIDE, right, lineOf(node));
        } else if (op == EXPONENTIATE) {
            if (isZero(right)) return integer(1, lineOf(node));
            if (isOne(right)) return left;
            return binary(left, EXPONENTIATE, right, lineOf(node));
        }
        return binary(left, op, right, lineOf(node));
    }

    public static String formula(Lexeme expression) {
        return formula(canonical(expression), 0);
    }

    public static Lexeme derivedDefinition(String name, FunctionShape shape, Lexeme expression, Types returnType) {
        int line = shape.line();
        Lexeme definition = new Lexeme(functionDefinitionActual);
        definition.AddChild(new Lexeme(IDENTIFIER, line, name));
        definition.AddChild(new Lexeme(FUNC_DEFINITION, line));
        definition.AddChild(new Lexeme(returnType, line));

        Lexeme parameters = new Lexeme(parameterList);
        Lexeme parameter = new Lexeme(Types.parameter);
        parameter.AddChild(shape.parameterType());
        parameter.AddChild(new Lexeme(IDENTIFIER, line, shape.parameter()));
        parameters.AddChild(parameter);
        definition.AddChild(parameters);

        Lexeme statementList = new Lexeme(Types.statementList);
        Lexeme returned = new Lexeme(returnStatement);
        returned.AddChild(new Lexeme(RETURN, line));
        returned.AddChild(expression);
        statementList.AddChild(returned);
        Lexeme block = new Lexeme(Types.block);
        block.AddChild(statementList);
        definition.AddChild(block);
        return definition;
    }

    private static Lexeme differentiate(Lexeme expression, String parameter, int line) {
        Lexeme node = canonical(expression);
        if (isNumber(node)) return integer(0, line);
        if (node.getType() == IDENTIFIER) return integer(node.getStringValue().equals(parameter) ? 1 : 0, line);
        if (node.getType() == unaryExpression) {
            Types op = node.getChild(0).getType();
            Lexeme inner = node.getChild(1);
            Lexeme dInner = differentiate(inner, parameter, line);
            if (op == MINUS || op == MINUS_UNARY) return unary(MINUS, dInner, line);
            if (op == INVERSE) {
                return binary(unary(MINUS, dInner, line), DIVIDE,
                        binary(inner, MULTIPLY, inner, line), line);
            }
            throw unsupported(node);
        }
        if (node.getType() == funcCall) {
            String name = node.getChild(0).getStringValue();
            Lexeme inner = node.getChild(1).getChild(0);
            Lexeme dInner = differentiate(inner, parameter, line);
            if (name.equals("sin")) return binary(call("cos", inner, line), MULTIPLY, dInner, line);
            if (name.equals("cos")) return binary(unary(MINUS, call("sin", inner, line), line), MULTIPLY, dInner, line);
            throw unsupported(node);
        }
        if (node.getType() != binaryExpression) throw unsupported(node);

        Lexeme left = node.getChild(0);
        Lexeme right = node.getChild(2);
        Lexeme dLeft = differentiate(left, parameter, line);
        Lexeme dRight = differentiate(right, parameter, line);
        Types op = node.getChild(1).getType();
        if (op == PLUS) {
            return binary(dLeft, PLUS, dRight, line);
        } else if (op == MINUS) {
            return binary(dLeft, MINUS, dRight, line);
        } else if (op == MULTIPLY) {
            return binary(binary(dLeft, MULTIPLY, right, line), PLUS,
                    binary(left, MULTIPLY, dRight, line), line);
        } else if (op == DIVIDE) {
            return binary(binary(binary(dLeft, MULTIPLY, right, line), MINUS,
                    binary(left, MULTIPLY, dRight, line), line), DIVIDE,
                    binary(right, MULTIPLY, right, line), line);
        } else if (op == EXPONENTIATE) {
            if (!isNumber(right)) throw unsupported(node);
            double exponent = numberValue(right);
            return binary(binary(number(exponent, line), MULTIPLY,
                    binary(left, EXPONENTIATE, number(exponent - 1, line), line), line), MULTIPLY, dLeft, line);
        }
        throw unsupported(node);
    }

    private static Lexeme canonical(Lexeme expression) {
        Lexeme node = expression;
        while ((node.getType() == primary || node.getType() == parentheziedExpreission
                || node.getType() == unaryExpression || node.getType() == binaryExpression)
                && node.getChildrenSize() == 1) {
            node = node.getChild(0);
        }
        if (node.getType() != binaryExpression || node.getChildrenSize() <= 3) return node;

        List<Lexeme> operands = new ArrayList<>();
        List<Lexeme> operators = new ArrayList<>();
        for (int i = 0; i < node.getChildrenSize(); i++) {
            if (i % 2 == 0) operands.add(canonical(node.getChild(i)));
            else operators.add(node.getChild(i));
        }
        List<Lexeme> values = new ArrayList<>();
        List<Lexeme> ops = new ArrayList<>();
        values.add(operands.get(0));
        for (int i = 0; i < operators.size(); i++) {
            Lexeme op = operators.get(i);
            while (!ops.isEmpty() && precedence(ops.get(ops.size() - 1).getType()) >= precedence(op.getType())) {
                reduce(values, ops.remove(ops.size() - 1));
            }
            ops.add(op);
            values.add(operands.get(i + 1));
        }
        while (!ops.isEmpty()) reduce(values, ops.remove(ops.size() - 1));
        return values.get(0);
    }

    private static void reduce(List<Lexeme> values, Lexeme operator) {
        Lexeme right = values.remove(values.size() - 1);
        Lexeme left = values.remove(values.size() - 1);
        values.add(binary(left, operator.getType(), right, lineOf(operator)));
    }

    private static int precedence(Types type) {
        if (type == PLUS || type == MINUS) return 1;
        else if (type == MULTIPLY || type == DIVIDE) return 2;
        else if (type == EXPONENTIATE) return 3;
        return -1;
    }

    private static Lexeme binary(Lexeme left, Types type, Lexeme right, int line) {
        Lexeme expression = new Lexeme(binaryExpression);
        expression.AddChild(left);
        expression.AddChild(new Lexeme(type, line));
        expression.AddChild(right);
        return expression;
    }

    private static Lexeme unary(Types type, Lexeme operand, int line) {
        Lexeme expression = new Lexeme(unaryExpression);
        expression.AddChild(new Lexeme(type, line));
        expression.AddChild(operand);
        return expression;
    }

    private static Lexeme call(String name, Lexeme argument, int line) {
        Lexeme call = new Lexeme(funcCall);
        call.AddChild(new Lexeme(IDENTIFIER, line, name));
        Lexeme arguments = new Lexeme(expressionList);
        arguments.AddChild(argument);
        call.AddChild(arguments);
        return call;
    }

    private static Lexeme integer(int value, int line) {
        return new Lexeme(INTERGER, line, value);
    }

    private static Lexeme number(double value, int line) {
        if (value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return integer((int) value, line);
        }
        return new Lexeme(DOS, line, value);
    }

    private static boolean isNumber(Lexeme node) {
        return node.getType() == INTERGER || node.getType() == DOS;
    }

    private static double numberValue(Lexeme node) {
        return node.getType() == INTERGER ? node.getIntValue() : node.getDosValue();
    }

    private static boolean isZero(Lexeme node) {
        return isNumber(node) && numberValue(node) == 0.0;
    }

    private static boolean isOne(Lexeme node) {
        return isNumber(node) && numberValue(node) == 1.0;
    }

    private static boolean same(Lexeme left, Lexeme right) {
        return formula(left).equals(formula(right));
    }

    private static String formula(Lexeme node, int outerPrecedence) {
        if (node.getType() == IDENTIFIER) return node.getStringValue();
        if (node.getType() == INTERGER) return Integer.toString(node.getIntValue());
        if (node.getType() == DOS) return Double.toString(node.getDosValue());
        if (node.getType() == unaryExpression) return "-" + formula(node.getChild(1), 4);
        if (node.getType() == funcCall) return node.getChild(0).getStringValue() + "(" + formula(node.getChild(1).getChild(0), 0) + ")";
        if (node.getType() != binaryExpression) return node.toString();
        Types op = node.getChild(1).getType();
        int precedence = precedence(op);
        String symbol;
        if (op == PLUS) symbol = "+";
        else if (op == MINUS) symbol = "-";
        else if (op == MULTIPLY) symbol = "*";
        else if (op == DIVIDE) symbol = "/";
        else if (op == EXPONENTIATE) symbol = "î";
        else symbol = op.toString();
        String text = formula(node.getChild(0), precedence) + " " + symbol + " "
                + formula(node.getChild(2), precedence + (op == EXPONENTIATE ? -1 : 1));
        return precedence < outerPrecedence ? "(" + text + ")" : text;
    }

    private static RuntimeException unsupported(Lexeme node) {
        return new IllegalArgumentException("diff supports numbers, the parameter, +, -, *, /, constant powers, sin, and cos (line "
                + lineOf(node) + ")");
    }

    private static int lineOf(Lexeme node) {
        try {
            return node.getLineNumber();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
