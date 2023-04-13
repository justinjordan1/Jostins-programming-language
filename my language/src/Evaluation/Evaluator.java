package src.Evaluation;

import src.Recognizer.Parser;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Lexer;
import src.
import src.lexicalAnalysis.Types;

import java.util.ArrayList;

public class Evaluator<Parsing> {
    private final Environment globalEnvironment;

    public Evaluator() {
        globalEnvironment = new Environment(null, new ArrayList<>());
    }

    public void evaluate(List<Lexeme> statements) {
        for (Lexeme statement : statements) {
            evaluateStatement(globalEnvironment, statement);
        }
    }

    private void evaluateStatement(Environment environment, Lexeme statement) {
        switch (statement.getType()) {
            case ASSIGNMENT:
                Lexeme identifier = statement.getChild(0);
                Lexeme expression = statement.getChild(1);
                Lexeme evaluatedExpression = evaluateExpression(environment, expression);
                environment.update(identifier, evaluatedExpression);
                break;
            case VAR:
                Types varType = statement.getChild(0).getType();
                Lexeme varIdentifier = statement.getChild(1);
                Lexeme varValue = statement.getChild(2) != null ? evaluateExpression(environment, statement.getChild(2)) : null;
                environment.add(varType, varIdentifier, varValue);
                break;
            case WHILE:
                Lexeme condition = statement.getChild(0);
                Lexeme whileBody = statement.getChild(1);
                while (evaluateExpression(environment, condition).getBooleanValue()) {
                    evaluateStatement(environment, whileBody);
                }
                break;
            case IF:
                Lexeme ifCondition = statement.getChild(0);
                Lexeme ifBody = statement.getChild(1);
                Lexeme elseBody = statement.getChild(2);
                if (evaluateExpression(environment, ifCondition).getBooleanValue()) {
                    evaluateStatement(environment, ifBody);
                } else if (elseBody != null) {
                    evaluateStatement(environment, elseBody);
                }
                break;
            case FUNC_DEFINITION::

            default:
                throw new RuntimeException("Unsupported statement type: " + statement.getType());
        }
    }

    private Lexeme evaluateExpression(Environment environment, Lexeme expression) {
        switch (expression.getType()) {
            case PLUS:
                Lexeme leftOperand = evaluateExpression(environment, expression.getChild(0));
                Lexeme rightOperand = evaluateExpression(environment, expression.getChild(1));
                return new Lexeme(Types.INTERGER, leftOperand.getIntValue() + rightOperand.getIntValue());
            case IDENTIFIER:
                return environment.lookup(expression);
            // Other expression types can be added here
            default:
                throw new RuntimeException("Unsupported expression type: " + expression.getType());
        }
    }
    public Lexeme evaluateFunctionDefinition(Lexeme funcDefActual, Environment env) {
        // Extract the function name and type from the funcDefActual Lexeme
        Lexeme functionName = funcDefActual.getChild(0); // Assuming the first child is the function name
        Types functionType = funcDefActual.getChild(2).getType(); // Assuming the third child contains the function return type

        // Create a NamedValue for the function and store the entire funcDefActual Lexeme as its value
        NamedValue function = new NamedValue(functionType, functionName);
        function.setValue(funcDefActual);

        // Add the function to the environment
        env.addVariable(functionName.getStringValue(), function);

        return functionName;
    }

    public Lexeme evaluateFunctionCall(Lexeme functionCall, Environment env) {
        // Extract the function name and arguments from the functionCall Lexeme
        String functionName = functionCall.getChild(0).getStringValue(); // Assuming the first child is the function name
        List<Lexeme> arguments = functionCall.getChild(1).children; // Assuming the second child contains the list of arguments

        // Retrieve the function from the environment
        NamedValue function = env.getVariable(functionName);

        if (function == null || function.getType() != Types.FUNC_DEFINITION) {
            throw new RuntimeException("Undefined function: " + functionName);
        }

        // Get the function definition from the value of the NamedValue
        Lexeme funcDefActual = function.getValue();

        // Extract the parameter list and body from the funcDefActual Lexeme
        Lexeme parameterList = funcDefActual.getChild(3); // Assuming the parameterList is the fourth child
        Lexeme body = funcDefActual.getChild(4); // Assuming the body is the fifth child

        // Create a new environment for the function call, using the current environment as the parent
        Environment functionEnv = new Environment(env);

        // Evaluate the arguments and bind them to the function's parameter names in the new environment
        for (int i = 0; i < arguments.size(); i++) {
            Lexeme evaluatedArgument = evaluateExpression(arguments.get(i), functionEnv);
            String paramName = parameterList.getChild(i).getStringValue();
            functionEnv.addVariable(paramName, evaluatedArgument);
        }

        // Evaluate the function's body in the new environment
        Lexeme result = evaluate(body, functionEnv);

        // Check the return type, if necessary (according to your language rules)
        // ...

        return result;
    }





}
