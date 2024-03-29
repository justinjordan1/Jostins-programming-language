package src;

import src.Evaluation.EnviormentTester;
import src.Evaluation.Environment;
import src.Evaluation.Evaluator;
import src.Evaluation.NamedValue;
import src.Recognizer.Parser;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Lexer;
//import testInput.EnviormentTester;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class C10H15N {
    private static final ArrayList<String> syntaxErrorMessages = new ArrayList<>();
    private static final ArrayList<String> runtimeErrorMessages = new ArrayList<>();


    public static void main(String[] args) throws IOException {
        try {
            if (args.length == 1) runFile(args[0]);
            else {
                System.out.println("Usage: C10H15N [path to .mexplainerth file]");
                System.exit(64);
            }
        } catch (IOException exception) {
            throw new IOException(exception.toString());
        }
        // EnviormentTester.main(args);
    }

    private static String getSourceCodeFromFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, Charset.defaultCharset());
    }

    private static void runFile(String path) throws IOException {
        System.out.println("Running" + path + "...");
        String source = getSourceCodeFromFile(path);
        // Lexing
        Lexer lexer = new Lexer(source);
        ArrayList<Lexeme> lexemes = lexer.lex();
// Parsing
        Parser parser = new Parser(lexemes);
        Lexeme programParseTree = parser.program();
// Environments


        Environment globalEnvironment = new Environment(null, new ArrayList<>());

        //Evaluation
//        programParseTree.printAsParseTree();
        Evaluator evaluator = new Evaluator();
        Lexeme programResult = evaluator.eval(programParseTree, globalEnvironment);

        printErrors();
        System.out.println("Program result: " + programResult);

    }

    public static void syntaxError(String message, int lineNumber) {
        syntaxErrorMessages.add("Syntax error (line " + lineNumber + "): " + message);
    }

    public static void syntaxError(String message, Lexeme lexeme) {
        syntaxErrorMessages.add("Syntax error at " + lexeme + " : " + message);
    }

    public static void runtimeError(String message, Lexeme lexeme) {
        runtimeErrorMessages.add("Runtime error at " + lexeme + ". " + message);
        printErrors();
        System.exit(65);
    }


    public static void runtimeError(String message, int lineNumber) {
        runtimeErrorMessages.add("Runtime error at line " + lineNumber + ". " + message);
        printErrors();
        System.exit(65);
    }

    private static void printErrors() {
        final String ANSI_YELLOW = "\u001B[33m";
        final String ANSI_RED_BACKGROUND = "\u001B[41m";
        final String ANSI_RESET = "\u001B[Om";

        for (String syntaxErrorMessage : syntaxErrorMessages)
            System.out.println(ANSI_YELLOW + syntaxErrorMessage + ANSI_RESET);

        for (String runtimeErrorMessage : runtimeErrorMessages)
            System.out.println(ANSI_RED_BACKGROUND + runtimeErrorMessage + ANSI_RESET);
    }


}
