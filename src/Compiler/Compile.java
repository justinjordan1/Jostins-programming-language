package src.Compiler;

import src.C10H15N;
import src.Recognizer.Parser;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Lexer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Compile {

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: Compile [runtime .mexplainerth] [program .mexplainerth] [output .s file]");
            System.exit(64);
        }

        Lexeme runtimeLibrary = parse(args[0]);
        if (C10H15N.hasSyntaxErrors()) {
            C10H15N.printErrors();
            System.err.println("The runtime library " + args[0] + " has syntax errors.");
            System.exit(1);
        }
        Lexeme program = parse(args[1]);
        if (C10H15N.hasSyntaxErrors()) C10H15N.printErrors();

        try {
            String assembly = new CodeGen().compile(runtimeLibrary, program);
            Files.write(Paths.get(args[2]), assembly.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static Lexeme parse(String path) throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        return new Parser(new Lexer(source).lex()).program();
    }
}
