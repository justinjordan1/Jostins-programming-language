package src.Evaluation;

import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.util.ArrayList;

public class EnviormentTester {

    public static void main(String[] args) {
        Environment env = new Environment(null, new ArrayList<>());
        System.out.println("Test 1: Environment creation: " + (env != null ? "Passed" : "Failed"));

        Lexeme id = new Lexeme(Types.IDENTIFIER, 1, "testVar");
        env.add(Types.INTERGER, id);
        Lexeme value = env.lookup(id);
        System.out.println("Test 2: Adding a variable: " + (value != null ? "Passed" : "Failed"));

        Lexeme newValue = new Lexeme(Types.INTERGER, 2, 42);
        env.update(id, newValue);
        Lexeme updatedValue = env.lookup(id);
        System.out.println("Test 3: Updating a variable's value: " +
                (updatedValue != null && updatedValue.getIntValue() == 42 ? "Passed" : "Failed"));
        System.out.println(env);
        Lexeme typeElevateTest = env.typeElevate(newValue, Types.DOS);
        System.out.println(typeElevateTest);
        /// Test 5: looking up a value
        System.out.println(env.lookup(id));
    }
}

