package src.Compiler;

import src.C10H15N;
import src.Recognizer.Parser;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Lexer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class Compile {

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage: Compile [path to .mexplainerth file] [output .s file]");
            System.exit(64);
        }
        String source = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);

        ArrayList<Lexeme> lexemes = new Lexer(source).lex();
        Lexeme program = new Parser(lexemes).program();
        if (C10H15N.hasSyntaxErrors()) C10H15N.printErrors();

        try {
            String assembly = new CodeGen().compile(program);
            Files.write(Paths.get(args[1]), assembly.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
