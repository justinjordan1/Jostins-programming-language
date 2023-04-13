package testInput;

import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;
import src.Evaluation.Environment;
import src.Evaluation.NamedValue;

import java.util.ArrayList;

//test cases generated partially with chat GPT
public class EnviormentTester {
    public static void main(String[] args) {
        // Create a Lexeme object representing an integer with value 42
        Lexeme intLexeme = new Lexeme(Types.INTERGER, 0, 42);

        // Create a NamedValue object representing a variable named "x" with type INTERGER and value 42
        NamedValue namedValue = new NamedValue(Types.INTERGER, new Lexeme(Types.IDENTIFIER, 0, "x"));
        namedValue.setValue(intLexeme);

        // Create an ArrayList of NamedValue objects and add namedValue to it
        ArrayList<NamedValue> entries = new ArrayList<>();
        entries.add(namedValue);

        // Create an Environment object representing a local scope with entries ArrayList and no parent environment
        Environment env = new Environment(null, entries);

        // Verify that lookup works by retrieving the value of variable "x"
        Lexeme x = env.lookup(new Lexeme(Types.IDENTIFIER, 0, "x"));
        System.out.println(x.getIntValue()); // Output: 42

        // Verify that add and update work by adding a new variable "y" with value 10 and updating "x" to have value 5
        env.add(Types.INTERGER, new Lexeme(Types.IDENTIFIER, 0, "y"), new Lexeme(Types.INTERGER, 0, 10));
        env.update(new Lexeme(Types.IDENTIFIER, 0, "x"), new Lexeme(Types.INTERGER, 0, 5));

        // Verify that lookup works for both "x" and "y"
        Lexeme y = env.lookup(new Lexeme(Types.IDENTIFIER, 0, "y"));
        System.out.println(y.getIntValue()); // Output: 10

        x = env.lookup(new Lexeme(Types.IDENTIFIER, 0, "x"));
        System.out.println(x.getIntValue()); // Output: 5

        // Verify that error is thrown when trying to redeclare a variable
        try {
            env.add(Types.INTERGER, new Lexeme(Types.IDENTIFIER, 0, "y"), new Lexeme(Types.INTERGER, 0, 20));
        } catch (RuntimeException e) {
            System.out.println(e.getMessage()); // Output: A variable with the name *y' is already defined and cannot be re-declared.
        }

        // Verify that error is thrown when trying to update a variable with a value of the wrong type
        try {
            env.update(new Lexeme(Types.IDENTIFIER, 0, "x"), new Lexeme(Types.STRING, 0, "hello"));
        } catch (RuntimeException e) {
            System.out.println(e.getMessage()); // Output: Variable *x' has been declared as type INTERGER and cannot be assigned a value of type STRING
        }

        // Verify that error is thrown when trying to lookup an undefined variable
        try {
            env.lookup(new Lexeme(Types.IDENTIFIER, 0, "z"));
        } catch (RuntimeException e) {
            System.out.println(e.getMessage()); // Output: 'z is undefined.
        }
    }
}