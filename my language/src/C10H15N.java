package src;

import src.lexicalAnalysis.Lexeme;

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
            else { System.out.println("Usage: src.C10H15N [path to .mexplainerth file]"); System.exit(64);
            }
        } catch (IOException exception) {
            throw new IOException(exception.toString());
        }
    }

    private static String getSourceCodeFromFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, Charset.defaultCharset());
    }

    private static void runFile(String path) throws IOException {
        System.out.println("Running" + path + "...");
        String source = getSourceCodeFromFile(path);
// Lexing Lexer lexer = new Lexer(source); ArrayList<Lexeme> lexemes = lexer.lex(); System.out.println(lexemes);
    }

    public static void syntaxError(String message, int lineNumber) {
        syntaxErrorMessages.add("Ayo, somethin' wrong with your syntax, line (" + lineNumber + "):" + message);
    }
    public static void syntaxError(Lexeme lexeme, int lineNumber) {
        syntaxErrorMessages.add("Ayo, somethin' wrong with your syntax" + lexeme +  );
    }
    public static void syntaxError(String message, int lineNumber) {
        syntaxErrorMessages.add("Ayo, somethin' wrong with your syntax line (" + lineNumber + "):" + message);
    }



}
