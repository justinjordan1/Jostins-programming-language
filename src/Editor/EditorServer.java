package src.Editor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import src.C10H15N;
import src.Compiler.CodeGen;
import src.Recognizer.Parser;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Lexer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EditorServer {
    private static Path root;
    private static int port = 8123;

    public static void main(String[] args) throws IOException {
        root = Paths.get("").toAbsolutePath();

        if (args.length == 2 && args[0].equals("--translate")) {
            List<String> lines = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
            System.out.print(translate(lines).source);
            return;
        }
        if (args.length == 1) port = Integer.parseInt(args[0]);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", EditorServer::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("editor running at http://localhost:" + port);
    }

    static class Result {
        String source;
        String vars;
        String symbols = "";
    }

    private static Result translate(List<String> lines) {
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
        Result result = new Result();
        result.source = translator.translate(kinds, shades, quiet, blocks, LatexToSource.parseValues(values));
        result.vars = String.join(" ", translator.getFreeNames());
        StringBuilder symbols = new StringBuilder();
        for (Map.Entry<Integer, String> symbol : translator.getSymbols().entrySet()) {
            symbols.append("@@sym ").append(symbol.getKey()).append(" ").append(symbol.getValue()).append("\n");
        }
        result.symbols = symbols.toString();
        return result;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (exchange.getRequestMethod().equals("POST") && path.equals("/run")) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(Arrays.asList(body.split("\n", -1)));
            String target = lines.isEmpty() ? "interp" : lines.remove(0).trim();

            Result result;
            try {
                result = translate(lines);
            } catch (RuntimeException e) {
                send(exchange, 200, "text/plain; charset=utf-8", ("@@error " + e.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            try {
                String head = "@@vars " + result.vars + "\n" + result.symbols + "@@source\n" + result.source + "\n@@output\n";
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.flush();
                String assembly = stream(result.source, target, out);
                if (assembly == null) assembly = assembly();
                out.write(("\n@@asm\n" + assembly).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                stopCurrent();
            }
            try {
                out.close();
            } catch (IOException e) {
            }
            return;
        }

        if (exchange.getRequestMethod().equals("POST") && path.equals("/gif")) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            send(exchange, 200, "image/gif", gif(body.split("\n")));
            return;
        }

        if (path.startsWith("/assets/") && path.endsWith(".obj")) {
            Path model = root.resolve(path.substring(1)).normalize();
            if (model.startsWith(root.resolve("assets")) && Files.isRegularFile(model)) {
                send(exchange, 200, "text/plain; charset=utf-8", Files.readAllBytes(model));
                return;
            }
        }

        if (path.equals("/")) path = "/index.html";
        Path file = root.resolve("editor").resolve(path.substring(1)).normalize();
        if (!file.startsWith(root.resolve("editor")) || !Files.isRegularFile(file)) {
            send(exchange, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String type = "text/plain";
        if (file.toString().endsWith(".html")) type = "text/html; charset=utf-8";
        if (file.toString().endsWith(".js")) type = "text/javascript";
        if (file.toString().endsWith(".css")) type = "text/css";
        send(exchange, 200, type, Files.readAllBytes(file));
    }

    private static final Object runLock = new Object();
    private static Process current;

    private static void stopCurrent() {
        Process old;
        synchronized (EditorServer.class) {
            old = current;
        }
        if (old != null) {
            old.descendants().forEach(ProcessHandle::destroyForcibly);
            old.destroyForcibly();
        }
    }

    private static String stream(String source, String target, OutputStream out) throws IOException {
        stopCurrent();
        synchronized (runLock) {
            Path build = root.resolve("editor").resolve("build").resolve(String.valueOf(port));
            Files.createDirectories(build);
            Path program = build.resolve("editor.mexplainerth");
            Files.write(program, source.getBytes(StandardCharsets.UTF_8));

            String assembly = null;
            List<String> command = new ArrayList<>();
            if (target.equals("riscv")) {
                assembly = compile(source);
                Path assemblyFile = build.resolve("editor.s");
                if (assembly != null) Files.write(assemblyFile, assembly.getBytes(StandardCharsets.UTF_8));
                command.add(root.resolve("riscv").resolve("run.sh").toString());
                command.add(assembly != null ? assemblyFile.toString() : program.toString());
            } else {
                command.add("java");
                command.add("-Djava.awt.headless=true");
                command.add("-cp");
                command.add(root.resolve("out").toString());
                command.add("src.C10H15N");
                command.add(program.toString());
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(root.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            synchronized (EditorServer.class) {
                current = process;
            }
            long started = System.currentTimeMillis();
            long flushed = started;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.replaceAll("\u001B\\[[0-9;]*m", "");
                    if (line.startsWith("Program result: ")) continue;
                    if (line.startsWith("Running ") && line.endsWith("...")) continue;
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    long now = System.currentTimeMillis();
                    if (now - flushed > 40) {
                        out.flush();
                        flushed = now;
                    }
                    if (now - started > 600000) {
                        out.write("the program took longer than 10 minutes and was stopped\n".getBytes(StandardCharsets.UTF_8));
                        stopCurrent();
                        break;
                    }
                }
                out.flush();
            } finally {
                synchronized (EditorServer.class) {
                    if (current == process) current = null;
                }
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            if (assembly == null && !target.equals("riscv")) assembly = compile(source);
            return assembly;
        }
    }

    private static String compile(String source) {
        try {
            Lexeme runtimeLibrary = new Parser(new Lexer(new String(Files.readAllBytes(root.resolve("riscv").resolve("runtime.mexplainerth")), StandardCharsets.UTF_8)).lex()).program();
            Lexeme program = new Parser(new Lexer(source).lex()).program();
            if (C10H15N.hasSyntaxErrors()) return null;
            return new CodeGen().compile(runtimeLibrary, program);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            clearErrors();
        }
    }

    private static void clearErrors() {
        try {
            for (String name : new String[]{"syntaxErrorMessages", "runtimeErrorMessages"}) {
                Field field = C10H15N.class.getDeclaredField(name);
                field.setAccessible(true);
                ((Collection<?>) field.get(null)).clear();
            }
        } catch (ReflectiveOperationException e) {
        }
    }

    private static byte[] gif(String[] frames) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageOutputStream output = ImageIO.createImageOutputStream(bytes);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        writer.setOutput(output);
        writer.prepareWriteSequence(null);
        for (String frame : frames) {
            if (!frame.startsWith("data:image/png;base64,")) continue;
            byte[] png = Base64.getDecoder().decode(frame.substring(22));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            BufferedImage flat = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            flat.getGraphics().drawImage(image, 0, 0, null);

            ImageWriteParam param = writer.getDefaultWriteParam();
            IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(flat), param);
            String format = meta.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
            IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
            control.setAttribute("disposalMethod", "none");
            control.setAttribute("userInputFlag", "FALSE");
            control.setAttribute("transparentColorFlag", "FALSE");
            control.setAttribute("delayTime", "4");
            control.setAttribute("transparentColorIndex", "0");
            IIOMetadataNode extensions = new IIOMetadataNode("ApplicationExtensions");
            IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
            loop.setAttribute("applicationID", "NETSCAPE");
            loop.setAttribute("authenticationCode", "2.0");
            loop.setUserObject(new byte[]{1, 0, 0});
            extensions.appendChild(loop);
            root.appendChild(control);
            root.appendChild(extensions);
            meta.setFromTree(format, root);
            writer.writeToSequence(new IIOImage(flat, null, meta), param);
        }
        writer.endWriteSequence();
        output.close();
        return bytes.toByteArray();
    }

    private static synchronized String assembly() {
        try {
            Path build = root.resolve("editor").resolve("build").resolve(String.valueOf(port));
            Path program = build.resolve("editor.mexplainerth");
            Path output = build.resolve("editor.s");
            Files.deleteIfExists(output);

            ProcessBuilder builder = new ProcessBuilder("java", "-cp", root.resolve("out").toString(), "src.Compiler.Compile",
                    root.resolve("riscv").resolve("runtime.mexplainerth").toString(), program.toString(), output.toString());
            builder.directory(root.toFile());
            builder.redirectErrorStream(true);
            File log = build.resolve("compile.txt").toFile();
            builder.redirectOutput(log);
            Process process = builder.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "the compiler took too long\n";
            }
            if (!Files.exists(output)) {
                return new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8).replaceAll("\u001B\\[[0-9;]*m", "");
            }
            return new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            return "couldn't compile: " + e.getMessage() + "\n";
        }
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }
}
