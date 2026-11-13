package src.Web;

import src.C10H15N;
import src.Editor.LatexToSource;
import src.Evaluation.Environment;
import src.Evaluation.Evaluator;
import src.Recognizer.Parser;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Lexer;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class WebMain {
    public static void run(String body, Consumer<String> emit) {
        List<String> lines = new ArrayList<>(Arrays.asList(body.split("\n", -1)));
        if (!lines.isEmpty()) lines.remove(0);

        String source;
        StringBuilder head = new StringBuilder();
        try {
            List<String> kinds = new ArrayList<>();
            List<String> shades = new ArrayList<>();
            List<Boolean> quiet = new ArrayList<>();
            List<String> blocks = new ArrayList<>();
            List<String> values = new ArrayList<>();
            StringBuilder current = null;
            for (String line : lines) {
                if (line.equals("@@values")) {
                    current = null;
                } else if (line.startsWith("@@block ")) {
                    if (current != null) blocks.add(current.toString());
                    String[] header = line.substring(8).trim().split(" ");
                    kinds.add(header[0]);
                    String shade = "";
                    boolean hush = false;
                    for (int i = 1; i < header.length; i++) {
                        if (header[i].equals("novalue")) hush = true;
                        else if (header[i].startsWith("shade=")) shade = header[i].substring(6);
                        else if (!header[i].isEmpty()) shade = header[i];
                    }
                    shades.add(shade);
                    quiet.add(hush);
                    current = new StringBuilder();
                } else if (current == null) {
                    values.add(line);
                } else {
                    if (current.length() > 0) current.append("\n");
                    current.append(line);
                }
            }
            if (current != null) blocks.add(current.toString());

            LatexToSource translator = new LatexToSource();
            source = translator.translate(kinds, shades, quiet, blocks, LatexToSource.parseValues(values));
            head.append("@@vars ").append(String.join(" ", translator.getFreeNames())).append("\n");
            for (Map.Entry<Integer, String> symbol : translator.getSymbols().entrySet()) {
                head.append("@@sym ").append(symbol.getKey()).append(" ").append(symbol.getValue()).append("\n");
            }
        } catch (RuntimeException e) {
            emit.accept("@@error " + e.getMessage() + "\n");
            return;
        }
        head.append("@@source\n").append(source).append("\n@@output\n");
        emit.accept(head.toString());

        Sink sink = new Sink(emit);
        PrintStream original = System.out;
        System.setOut(new PrintStream(sink, true, StandardCharsets.UTF_8));
        C10H15N.embedded = true;
        C10H15N.clearErrors();
        try {
            Lexeme program = new Parser(new Lexer(source).lex()).program();
            new Evaluator().eval(program, new Environment(null, new ArrayList<>()));
            C10H15N.printErrors();
        } catch (RuntimeException e) {
            C10H15N.printErrors();
            System.out.println(e.getMessage());
        } finally {
            System.out.flush();
            System.setOut(original);
        }
        sink.send();
        emit.accept("\n@@asm\n");
    }

    static class Sink extends OutputStream {
        private final Consumer<String> emit;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private long sent = System.currentTimeMillis();

        Sink(Consumer<String> emit) {
            this.emit = emit;
        }

        @Override
        public void write(int b) {
            pending.write(b);
            if (b == '\n' && System.currentTimeMillis() - sent > 60) send();
        }

        void send() {
            if (pending.size() == 0) return;
            String text = new String(pending.toByteArray(), StandardCharsets.UTF_8).replaceAll("\u001B\\[[0-9;]*m", "");
            pending.reset();
            sent = System.currentTimeMillis();
            emit.accept(text);
        }
    }
}
