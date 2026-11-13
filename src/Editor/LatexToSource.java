package src.Editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LatexToSource {

    static class Val {
        String scalar;
        List<String> parts;
        String tupleCall;
        int tupleSize;
        boolean matrix;
        boolean color;
        boolean symbolic;
        Expr tree;
        List<String> rowItems;

        Val(String scalar) {
            this.scalar = scalar;
        }

        Val(List<String> parts) {
            this.parts = parts;
        }

        boolean isTuple() {
            return parts != null || tupleCall != null;
        }
    }

    static class FunctionInfo {
        List<String> params = new ArrayList<>();
        Expr body;
        boolean tuple;
        int tupleSize;
    }

    private final Map<String, FunctionInfo> functions = new HashMap<>();
    private final Set<String> freeNames = new LinkedHashSet<>();
    private Set<String> rowNames;
    private Set<String> boundNames = new LinkedHashSet<>();
    private final Set<String> codeNames = new LinkedHashSet<>();
    private final Set<String> mathNames = new LinkedHashSet<>();
    private final Set<String> matrixNames = new LinkedHashSet<>();
    private final Set<String> forced = new LinkedHashSet<>();

    private List<String> tokens;
    private int position;

    private final StringBuilder definitions = new StringBuilder();
    private final StringBuilder plots = definitions;
    private final StringBuilder hoisted = new StringBuilder();
    private List<String> shades;
    private List<Boolean> quiet;
    private boolean rowQuiet;
    private final Map<Integer, String> symbols = new LinkedHashMap<>();
    private int plotCount = 0;

    public String translate(List<String> kinds, List<String> shadeNames, List<Boolean> quietRows, List<String> blocks, Map<String, Double> values) {
        shades = shadeNames;
        quiet = quietRows;
        for (int i = 0; i < blocks.size(); i++) {
            if (kinds.get(i).equals("code")) learnCode(blocks.get(i));
            else learnMath(blocks.get(i));
        }
        for (String name : values.keySet()) {
            if (name.startsWith("!")) forced.add(name.substring(1));
        }
        freeNames.addAll(forced);

        for (int i = 0; i < blocks.size(); i++) {
            String block = blocks.get(i);
            shading(i);
            if (kinds.get(i).equals("code")) {
                definitions.append("mq_pen = ").append(plotCount++).append(";\n");
                definitions.append("mq_colored = 0;\n");
                definitions.append(block).append("\n\n");
                continue;
            }
            if (block.trim().isEmpty()) {
                plotCount++;
                continue;
            }
            try {
                rowQuiet = i < quiet.size() && quiet.get(i);
                translateRow(block.trim());
            } catch (RuntimeException e) {
                throw new RuntimeException("block " + (i + 1) + ": " + e.getMessage());
            }
        }

        StringBuilder out = new StringBuilder();
        out.append(PRELUDE);
        for (String name : freeNames) {
            Double value = values.get(name);
            if (value == null) value = values.get("!" + name);
            if (value == null) value = 1.0;
            out.append("var ").append(name).append(" dos = ").append(number(value)).append(";\n");
        }
        out.append("var view_xmin dos = ").append(number(values.getOrDefault("view_xmin", -10.0))).append(";\n");
        out.append("var view_xmax dos = ").append(number(values.getOrDefault("view_xmax", 10.0))).append(";\n");
        out.append("var view_ymin dos = ").append(number(values.getOrDefault("view_ymin", -10.0))).append(";\n");
        out.append("var view_ymax dos = ").append(number(values.getOrDefault("view_ymax", 10.0))).append(";\n\n");
        out.append(hoisted);
        out.append(definitions);
        return out.toString();
    }

    private void shading(int block) {
        String shade = block < shades.size() ? shades.get(block) : "";
        if (shade.isEmpty()) {
            definitions.append("mq_shading = 0;\n");
        } else {
            definitions.append("mq_shade = ").append(shade).append(";\nmq_shading = 1;\n");
        }
    }

    private void learnCode(String code) {
        Matcher function = Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*[a-z]+\\s*\\(([^)]*)\\)").matcher(code);
        while (function.find()) {
            FunctionInfo info = new FunctionInfo();
            String params = function.group(2).trim();
            if (!params.isEmpty()) {
                for (String param : params.split(",")) {
                    String[] words = param.trim().split("\\s+");
                    info.params.add(words[words.length - 1]);
                }
            }
            functions.put(function.group(1), info);
        }
        Matcher variable = Pattern.compile("(?m)\\b(?:var|collection)\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)*)").matcher(code);
        while (variable.find()) {
            for (String name : variable.group(1).split(",")) codeNames.add(name.trim());
        }
    }

    private void learnMath(String block) {
        String row = block.split("\t", -1)[0].trim();
        Matcher name = Pattern.compile("^([A-Za-z])(?:_\\{([^}]*)\\}|_([A-Za-z0-9]))?=(.*)$").matcher(row);
        if (!name.matches()) return;
        String letter = name.group(1);
        if (letter.equals("x") || letter.equals("y") || letter.equals("t")) {
            if (name.group(2) == null && name.group(3) == null) return;
        }
        String sub = name.group(2) != null ? name.group(2).replace("\\", "") : name.group(3);
        String full = sub == null ? letter : letter + "_" + sub;
        mathNames.add(full);
        if (name.group(4).startsWith("\\left[")) matrixNames.add(full);
    }

    public Map<Integer, String> getSymbols() {
        return symbols;
    }

    private void showValue(int id, String expression) {
        if (rowQuiet) return;
        definitions.append("print(\"val ").append(id).append(" \", ").append(expression).append(", \"\\n\");\n\n");
    }

    private void showSymbol(int id, Val value) {
        if (rowQuiet || value.tree == null) return;
        symbols.put(id, value.tree.simplify().latex());
    }

    public Set<String> getFreeNames() {
        return freeNames;
    }

    private void translateRow(String whole) {
        String[] pieces = whole.split("\t", -1);
        String row = pieces[0];
        rowNames = new LinkedHashSet<>();
        boundNames = new LinkedHashSet<>();
        String tMin = "0.0";
        String tMax = "6.283185307179586";
        if (pieces.length == 3) {
            if (!pieces[1].trim().isEmpty()) tMin = rangeEnd(pieces[1]);
            if (!pieces[2].trim().isEmpty()) tMax = rangeEnd(pieces[2]);
        }
        tokens = tokenize(row);
        position = 0;
        int id = plotCount++;
        definitions.append("mq_pen = ").append(id).append(";\n");

        if (looksLikeDefinition()) {
            String name = readName();
            FunctionInfo info = new FunctionInfo();
            expect("(");
            info.params.add(readName());
            while (peek(",")) {
                advance();
                info.params.add(readName());
            }
            expect(")");
            expect("=");

            boundNames = new LinkedHashSet<>(info.params);
            functions.put(name, info);
            Val body = expression();
            String keep = null;
            if (peek("\\{")) keep = restriction();
            if (position < tokens.size()) throw new RuntimeException("didn't understand '" + tokens.get(position) + "'");
            addFree();

            info.tuple = body.isTuple();
            info.tupleSize = tupleSize(body);
            info.body = body.tree;
            if (body.symbolic) showSymbol(id, body);
            writeFunction(name, info, body, keep);

            if (!info.tuple && info.params.size() == 1 && info.params.get(0).equals("x")) {
                writeGraph(id, name + "(x)", null);
            }
            return;
        }

        if (tokens.size() > 2 && tokens.get(0).equals("r") && tokens.get(1).equals("=")) {
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).equals("\\theta")) tokens.set(i, "t");
            }
            position = 2;
            boundNames = new LinkedHashSet<>();
            boundNames.add("t");
            Val radius = expression();
            String keep = null;
            if (peek("\\{")) keep = restriction();
            if (position < tokens.size()) throw new RuntimeException("didn't understand '" + tokens.get(position) + "'");
            addFree();
            String r = scalarOf(radius);
            List<String> parts = new ArrayList<>();
            parts.add("(" + r + " * cos(t))");
            parts.add("(" + r + " * sin(t))");
            writeParametric(id, new Val(parts), keep, true, tMin, tMax);
            return;
        }

        if (looksLikeVariable()) {
            String name = readName();
            expect("=");
            boundNames = new LinkedHashSet<>();
            boundNames.add("x");
            boundNames.add("y");
            Val value = expression();
            if (position < tokens.size()) throw new RuntimeException("didn't understand '" + tokens.get(position) + "'");
            addFree();
            if (value.isTuple()) throw new RuntimeException("a name can hold a number, a matrix or a color, not a point");
            boolean moves = rowNames.contains("x") || rowNames.contains("y");
            if (moves && !value.color) throw new RuntimeException("only colors can change with x and y here; use " + name + "(x)=... for a function");
            if (moves) {
                FunctionInfo shader = new FunctionInfo();
                shader.params.add("x");
                shader.params.add("y");
                functions.put(name, shader);
                hoisted.append(name).append(" : object(dos x, dos y) {\n    return ").append(value.scalar).append(";\n}\n\n");
                definitions.append("print(\"shader ").append(name).append("\\n\");\n\n");
            } else if (value.color) {
                definitions.append("var ").append(name).append(" object = ").append(value.scalar).append(";\n");
                definitions.append("swatch(\"").append(name).append("\", ").append(name).append(");\n\n");
            } else if (value.matrix) {
                matrixNames.add(name);
                definitions.append("collection ").append(name).append(" matrix[[]] = ").append(value.scalar).append(";\n");
                showValue(id, name);
            } else {
                definitions.append("var ").append(name).append(" dos = ").append(value.scalar).append(";\n");
                showValue(id, name);
            }
            return;
        }

        boolean isY = tokens.size() > 1 && tokens.get(0).equals("y") && tokens.get(1).equals("=");
        if (isY) position = 2;

        boundNames = new LinkedHashSet<>();
        boundNames.add("x");
        boundNames.add("y");
        boundNames.add("t");
        Val value = expression();
        String relation = null;
        if (!isY) {
            if (peek("=")) relation = "eq";
            else if (peek("<")) relation = "lt";
            else if (peek(">")) relation = "gt";
            else if (peek("\\le")) relation = "le";
            else if (peek("\\ge")) relation = "ge";
        }
        if (relation != null) {
            advance();
            Val right = expression();
            if (position < tokens.size()) throw new RuntimeException("didn't understand '" + tokens.get(position) + "'");
            addFree();
            writeGrid(id, relation, "(" + scalarOf(value) + " - " + scalarOf(right) + ")");
            return;
        }
        String keep = null;
        if (peek("\\{")) keep = restriction();
        if (position < tokens.size()) throw new RuntimeException("didn't understand '" + tokens.get(position) + "'");
        addFree();
        if (rowNames.contains("y")) throw new RuntimeException("y belongs in an equation or inequality, like x^2+y^2=1 or y<\\sin(x)");

        if (!value.isTuple() && !value.matrix && !value.color && rowNames.contains("x") && (value.symbolic || !isY)) {
            showSymbol(id, value);
        }

        if (!isY && keep == null && !value.isTuple() && !rowNames.contains("x") && !rowNames.contains("t")) {
            if (rowQuiet) return;
            if (value.color) {
                definitions.append("print(\"val ").append(id).append(" \");\n");
                definitions.append("mq_describe(").append(value.scalar).append(");\n");
                definitions.append("print(\"\\n\");\n\n");
            } else {
                showValue(id, value.scalar);
            }
            return;
        }
        if (value.isTuple()) {
            writeParametric(id, value, keep, rowNames.contains("t"), tMin, tMax);
        } else {
            writeGraph(id, value.scalar, keep);
        }
    }

    private boolean looksLikeVariable() {
        if (tokens.isEmpty() || !isNameToken(tokens.get(0))) return false;
        int saved = position;
        try {
            String name = readName();
            if (name.equals("x") || name.equals("y") || name.equals("t")) return false;
            return peek("=");
        } catch (RuntimeException e) {
            return false;
        } finally {
            position = saved;
        }
    }

    private boolean looksLikeDefinition() {
        int i = 0;
        if (i >= tokens.size() || !isNameToken(tokens.get(i))) return false;
        String first = tokens.get(i);
        if (first.equals("x") || first.equals("y") || first.equals("t")) return false;
        i++;
        if (i < tokens.size() && tokens.get(i).equals("_")) {
            i += 2;
            if (i - 1 < tokens.size() && tokens.get(i - 1).equals("{")) {
                while (i < tokens.size() && !tokens.get(i).equals("}")) i++;
                i++;
            }
        }
        if (i >= tokens.size() || !tokens.get(i).equals("(")) return false;
        int depth = 0;
        for (; i < tokens.size(); i++) {
            if (tokens.get(i).equals("(")) depth++;
            if (tokens.get(i).equals(")")) {
                depth--;
                if (depth == 0) return i + 1 < tokens.size() && tokens.get(i + 1).equals("=");
            }
        }
        return false;
    }

    private void addFree() {
        for (String name : rowNames) {
            if (boundNames.contains(name) || functions.containsKey(name) || codeNames.contains(name)) continue;
            if (mathNames.contains(name)) continue;
            freeNames.add(name);
        }
    }

    private int tupleSize(Val value) {
        if (value.parts != null) return value.parts.size();
        if (value.tupleCall != null) return value.tupleSize;
        return 0;
    }

    private void writeFunction(String name, FunctionInfo info, Val body, String keep) {
        hoisted.append(name).append(" : ").append(info.tuple ? "matrix" : "dos").append("(");
        for (int i = 0; i < info.params.size(); i++) {
            if (i > 0) hoisted.append(", ");
            hoisted.append("dos ").append(info.params.get(i));
        }
        hoisted.append(") {\n");

        if (info.tuple) {
            List<String> parts = new ArrayList<>(partsOf(body, "mq_body"));
            if (body.tupleCall == null) {
                List<String> texts = new ArrayList<>(parts);
                if (keep != null) texts.add(keep);
                share(texts, info.params);
                for (int i = 0; i < parts.size(); i++) parts.set(i, texts.get(i));
                if (keep != null) keep = texts.get(parts.size());
            }
            if (body.tupleCall != null) {
                hoisted.append("    collection mq_body matrix[[]] = ").append(body.tupleCall).append(";\n");
            }
            hoisted.append("    var mq_keep dos = 1.0;\n");
            if (keep != null) {
                hoisted.append("    if (").append(keep).append(") {\n    } else {\n        mq_keep = 0.0;\n    }\n");
            }
            if (body.tupleCall != null) {
                hoisted.append("    if (get mq_body[[0,").append(body.tupleSize).append("]] < 0.5) {\n        mq_keep = 0.0;\n    }\n");
            }
            hoisted.append("    return [[");
            for (String part : parts) {
                hoisted.append(part).append(", ");
            }
            hoisted.append("mq_keep]];\n}\n\n");
        } else {
            if (keep != null) {
                hoisted.append("    if (").append(keep).append(") {\n        return ").append(body.scalar)
                        .append(";\n    }\n    return mq_nan();\n}\n\n");
            } else {
                List<String> texts = new ArrayList<>(List.of(body.scalar));
                share(texts, info.params);
                hoisted.append("    return ").append(texts.get(0)).append(";\n}\n\n");
            }
        }
    }

    private void share(List<String> texts, List<String> params) {
        for (int round = 0; round < 64; round++) {
            String best = null;
            for (String text : texts) {
                for (int start = 0; start < text.length(); start++) {
                    if (text.charAt(start) != '(') continue;
                    if (start > 0 && (Character.isLetterOrDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '_')) continue;
                    int end = closing(text, start);
                    if (end < 0) continue;
                    String piece = text.substring(start, end + 1);
                    if (piece.length() < 12 || (best != null && piece.length() >= best.length())) continue;
                    if (!sharable(piece, params) || occurrences(texts, piece) < 2) continue;
                    best = piece;
                }
            }
            if (best == null) return;
            String name = "mq_s" + (shared++);
            hoisted.append("    var ").append(name).append(" dos = ").append(best).append(";\n");
            java.util.regex.Pattern spot = java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(best));
            for (int i = 0; i < texts.size(); i++) texts.set(i, spot.matcher(texts.get(i)).replaceAll(java.util.regex.Matcher.quoteReplacement(name)));
        }
    }

    private int shared;

    private int closing(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) return i;
            if (c == ',' && depth == 1) return -1;
        }
        return -1;
    }

    private boolean sharable(String piece, List<String> params) {
        if (piece.contains("[") || piece.contains("\"") || piece.contains(" get ")) return false;
        java.util.regex.Matcher word = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z_0-9]*").matcher(piece);
        while (word.find()) {
            String name = word.group();
            if (params.contains(name)) continue;
            if (matrixNames.contains(name) || codeNames.contains(name)) return false;
            FunctionInfo function = functions.get(name);
            if (function != null && function.tuple) return false;
        }
        return true;
    }

    private int occurrences(List<String> texts, String piece) {
        java.util.regex.Pattern spot = java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(piece));
        int count = 0;
        for (String text : texts) {
            java.util.regex.Matcher found = spot.matcher(text);
            while (found.find()) count++;
        }
        return count;
    }

    private List<String> partsOf(Val value, String holder) {
        if (value.parts != null) return value.parts;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < value.tupleSize; i++) {
            parts.add("(get " + holder + "[[0," + i + "]])");
        }
        return parts;
    }

    private void writeGraph(int id, String expression, String keep) {
        plots.append("for (var mq_step interger = 0; mq_step <= 800)¬+ {\n");
        plots.append("    var x dos = view_xmin + (view_xmax - view_xmin) * mq_step / 800.0;\n");
        plots.append("    var mq_y dos = ").append(expression).append(";\n");
        String okay = "(mq_y == mq_y)";
        if (keep != null) okay = "(" + okay + " & " + keep + ")";
        plots.append("    if ").append(okay).append(" {\n");
        plots.append("        plot(x, mq_y);\n");
        plots.append("    } else {\n        penUp();\n    }\n}\n\n");
    }

    private void writeGrid(int id, String mode, String difference) {
        plots.append("print(\"grid ").append(id).append(" ").append(mode).append(" 160 100 \", view_xmin, \" \", view_xmax, \" \", view_ymin, \" \", view_ymax, \"\\n\");\n");
        plots.append("for (var mq_row interger = 0; mq_row <= 100)¬+ {\n");
        plots.append("    var y dos = view_ymin + (view_ymax - view_ymin) * mq_row / 100.0;\n");
        plots.append("    print(\"gv\");\n");
        plots.append("    for (var mq_column interger = 0; mq_column <= 160)¬+ {\n");
        plots.append("        var x dos = view_xmin + (view_xmax - view_xmin) * mq_column / 160.0;\n");
        plots.append("        var mq_f dos = ").append(difference).append(";\n");
        plots.append("        if (mq_f == mq_f) {\n            print(\" \", mq_f);\n        } else {\n            print(\" n\");\n        }\n");
        plots.append("    }\n    print(\"\\n\");\n}\n\n");
    }

    private String rangeEnd(String latex) {
        tokens = tokenize(latex);
        position = 0;
        Val end = expression();
        if (position < tokens.size()) throw new RuntimeException("didn't understand the t range");
        return scalarOf(end);
    }

    private void writeParametric(int id, Val value, String keep, boolean usesT, String tMin, String tMax) {
        int steps = usesT ? 20000 : 0;
        plots.append("var mq_tmin_").append(id).append(" dos = ").append(tMin).append(";\n");
        plots.append("var mq_tmax_").append(id).append(" dos = ").append(tMax).append(";\n");
        plots.append("for (var mq_step interger = 0; mq_step <= ").append(steps).append(")¬+ {\n");
        plots.append("    var t dos = mq_tmin_").append(id).append(" + (mq_tmax_").append(id).append(" - mq_tmin_").append(id)
                .append(") * mq_step / ").append(steps == 0 ? "1.0" : steps + ".0").append(";\n");
        String okay;
        List<String> parts;
        if (value.tupleCall != null) {
            plots.append("    collection mq_p matrix[[]] = ").append(value.tupleCall).append(";\n");
            parts = partsOf(value, "mq_p");
            okay = "((get mq_p[[0," + value.tupleSize + "]]) > 0.5)";
        } else {
            parts = value.parts;
            okay = "(1.0 > 0.5)";
        }
        if (parts.size() < 2) throw new RuntimeException("a point needs two coordinates");
        plots.append("    var mq_px dos = ").append(parts.get(0)).append(";\n");
        plots.append("    var mq_py dos = ").append(parts.get(1)).append(";\n");
        okay = "(" + okay + " & ((mq_px == mq_px) & (mq_py == mq_py)))";
        if (keep != null) okay = "(" + okay + " & " + keep + ")";
        plots.append("    if ").append(okay).append(" {\n");
        plots.append("        plot(mq_px, mq_py);\n");
        plots.append("    } else {\n        penUp();\n    }\n}\n\n");
    }

    private String restriction() {
        expect("\\{");
        Val left = expression();
        String op;
        if (peek(">") || peek("<")) {
            op = advance();
        } else if (peek("\\ge")) {
            advance();
            op = ">=";
        } else if (peek("\\le")) {
            advance();
            op = "<=";
        } else {
            throw new RuntimeException("restrictions need a < or >");
        }
        Val right = expression();
        expect("\\}");
        return "(" + scalarOf(left) + " " + op + " " + scalarOf(right) + ")";
    }

    private Val expression() {
        Val left = term();
        while (peek("+") || peek("-")) {
            String op = advance();
            Val right = term();
            left = combine(left, op, right);
        }
        return left;
    }

    private Val term() {
        Val left = factor();
        while (true) {
            if (peek("\\cdot") || peek("\\times") || peek("*")) {
                advance();
                left = combine(left, "*", factor());
            } else if (peek("/")) {
                advance();
                left = combine(left, "/", factor());
            } else if (startsAtom()) {
                left = combine(left, "*", factor());
            } else {
                break;
            }
        }
        return left;
    }

    private Val factor() {
        if (peek("-")) {
            advance();
            Val inside = factor();
            Val zero = new Val("0.0");
            zero.tree = Expr.num(0);
            return combine(zero, "-", inside);
        }
        if (peek("+")) {
            advance();
            return factor();
        }
        Val base = atom();
        if (peek("^")) {
            advance();
            Val power = group();
            if (base.matrix) {
                if (!power.scalar.replace(" ", "").equals("(0.0-1.0)")) {
                    throw new RuntimeException("matrices can only be raised to -1");
                }
                Val inverse = new Val("(í " + base.scalar + ")");
                inverse.matrix = true;
                return inverse;
            }
            Val raised = new Val("(" + scalarOf(base) + " î " + scalarOf(power) + ")");
            if (base.tree != null && power.tree != null) raised.tree = Expr.pow(base.tree, power.tree);
            raised.symbolic = base.symbolic || power.symbolic;
            base = raised;
        }
        return base;
    }

    private Val group() {
        if (peek("{")) {
            advance();
            Val inside = expression();
            expect("}");
            return inside;
        }
        String token = advance();
        if (isNumberToken(token)) return numberVal(Double.parseDouble(token));
        if (isNameToken(token)) {
            rowNames.add(token);
            Val named = new Val(token);
            named.tree = Expr.var(token);
            return named;
        }
        throw new RuntimeException("expected something after ^ but found '" + token + "'");
    }

    private boolean startsAtom() {
        if (position >= tokens.size()) return false;
        String token = tokens.get(position);
        if (isNumberToken(token) || isNameToken(token) || token.equals("(") || token.equals("[")) return true;
        return token.equals("\\frac") || token.equals("\\sqrt") || token.equals("\\pi")
                || isFunctionCommand(token);
    }

    private boolean isFunctionCommand(String token) {
        return token.equals("\\sin") || token.equals("\\cos") || token.equals("\\tan")
                || token.equals("\\arctan") || token.equals("\\ln") || token.equals("\\exp")
                || token.equals("\\det") || token.equals("\\rgb") || token.equals("\\hsv");
    }

    private Val atom() {
        if (position >= tokens.size()) throw new RuntimeException("the expression ends too early");
        String token = advance();

        if (isNumberToken(token)) return numberVal(Double.parseDouble(token));

        if (token.equals("\\pi")) return numberVal(Math.PI);

        if (token.equals("(")) {
            List<String> parts = new ArrayList<>();
            Val first = expression();
            if (!peek(",")) {
                expect(")");
                if (first.isTuple()) return first;
                Val inside = new Val("(" + first.scalar + ")");
                inside.tree = first.tree;
                inside.matrix = first.matrix;
                inside.symbolic = first.symbolic;
                return inside;
            }
            parts.add(scalarOf(first));
            while (peek(",")) {
                advance();
                parts.add(scalarOf(expression()));
            }
            expect(")");
            return new Val(parts);
        }

        if (token.equals("[")) {
            List<Val> items = new ArrayList<>();
            items.add(expression());
            while (peek(",")) {
                advance();
                items.add(expression());
            }
            expect("]");
            boolean rows = true;
            for (Val item : items) {
                if (item.rowItems == null) rows = false;
            }
            StringBuilder literal = new StringBuilder("[[");
            Val result;
            if (rows) {
                int width = items.get(0).rowItems.size();
                for (int r = 0; r < items.size(); r++) {
                    if (items.get(r).rowItems.size() != width) throw new RuntimeException("matrix rows need the same length");
                    if (r > 0) literal.append("]\\[");
                    literal.append(String.join(", ", items.get(r).rowItems));
                }
                literal.append("]]");
                result = new Val(literal.toString());
            } else {
                List<String> cells = new ArrayList<>();
                for (Val item : items) cells.add("(" + scalarOf(item) + ")");
                literal.append(String.join(", ", cells)).append("]]");
                result = new Val(literal.toString());
                result.rowItems = cells;
            }
            result.matrix = true;
            return result;
        }

        if (token.equals("\\frac") && derivativeAhead()) {
            position += 7;
            Val inside = factor();
            if (inside.tree == null) throw new RuntimeException("d/dx works on numbers and functions of x");
            return fromTree(inside.tree.derivative("x").simplify());
        }

        if (token.equals("\\frac")) {
            Val top = group();
            Val bottom = group();
            return combine(top, "/", bottom);
        }

        if (token.equals("\\sqrt")) {
            Val inside = group();
            Val root = new Val("(" + scalarOf(inside) + " î 0.5)");
            if (inside.tree != null) root.tree = Expr.pow(inside.tree, Expr.num(0.5));
            root.symbolic = inside.symbolic;
            return root;
        }

        if (isFunctionCommand(token)) {
            List<Val> arguments = new ArrayList<>();
            if (peek("(")) {
                advance();
                arguments.add(expression());
                while (peek(",")) {
                    advance();
                    arguments.add(expression());
                }
                expect(")");
            } else {
                arguments.add(factor());
            }
            String name = token.substring(1);
            if (name.equals("rgb") || name.equals("hsv")) {
                if (arguments.size() != 3) throw new RuntimeException(name + " takes three inputs");
                Val color = new Val(name + "(" + scalarOf(arguments.get(0)) + ", " + scalarOf(arguments.get(1))
                        + ", " + scalarOf(arguments.get(2)) + ")");
                color.color = true;
                return color;
            }
            if (name.equals("det")) {
                if (arguments.size() != 1 || !arguments.get(0).matrix) throw new RuntimeException("det takes one matrix");
                return new Val("det(" + arguments.get(0).scalar + ")");
            }
            if (name.equals("arctan") && arguments.size() == 2) {
                return new Val("mq_atan2(" + scalarOf(arguments.get(0)) + ", " + scalarOf(arguments.get(1)) + ")");
            }
            if (arguments.size() != 1) throw new RuntimeException(name + " takes one input");
            String inside = scalarOf(arguments.get(0));
            Val result;
            String op = name;
            if (name.equals("sin") || name.equals("cos")) result = new Val(name + "(" + inside + ")");
            else if (name.equals("tan")) result = new Val("(sin(" + inside + ") / cos(" + inside + "))");
            else if (name.equals("arctan")) {
                result = new Val("mq_atan(" + inside + ")");
                op = "atan";
            } else if (name.equals("ln")) result = new Val("mq_ln(" + inside + ")");
            else result = new Val("(2.718281828459045 î " + inside + ")");
            if (arguments.get(0).tree != null) result.tree = Expr.of(op, arguments.get(0).tree);
            result.symbolic = arguments.get(0).symbolic;
            return result;
        }

        if (isNameToken(token)) {
            position--;
            String name = readName();
            FunctionInfo info = functions.get(name);
            if (info != null && peek("'")) {
                advance();
                if (info.body == null || info.params.size() != 1) throw new RuntimeException(name + "' needs a one-input function made of math rows");
                Expr slope = info.body.derivative(info.params.get(0)).simplify();
                Expr argument = Expr.var("x");
                if (peek("(")) {
                    advance();
                    Val inside = expression();
                    expect(")");
                    if (inside.tree == null) throw new RuntimeException("can't take " + name + "' of that");
                    argument = inside.tree;
                } else {
                    rowNames.add("x");
                }
                return fromTree(slope.replace(info.params.get(0), argument).simplify());
            }
            if (info != null && !peek("(") && info.params.size() >= 1 && info.params.size() <= 2) {
                rowNames.add("x");
                String call = info.params.size() == 1 ? name + "(x)" : name + "(x, y)";
                if (info.params.size() == 2) rowNames.add("y");
                if (info.tuple) throw new RuntimeException(name + " gives a point, so it needs its inputs written out");
                return new Val(call);
            }
            if (info != null && peek("(")) {
                advance();
                List<String> arguments = new ArrayList<>();
                List<Expr> argumentTrees = new ArrayList<>();
                Val firstArgument = expression();
                arguments.add(scalarOf(firstArgument));
                argumentTrees.add(firstArgument.tree);
                while (peek(",")) {
                    advance();
                    Val more = expression();
                    arguments.add(scalarOf(more));
                    argumentTrees.add(more.tree);
                }
                expect(")");
                if (arguments.size() != info.params.size()) {
                    throw new RuntimeException(name + " takes " + info.params.size() + " inputs");
                }
                String call = name + "(" + String.join(", ", arguments) + ")";
                if (!info.tuple && info.body != null) {
                    Val called = new Val(call);
                    called.tree = inline(info, argumentTrees);
                    return called;
                }
                if (info.tuple) {
                    Val result = new Val((String) null);
                    result.tupleCall = call;
                    result.tupleSize = info.tupleSize;
                    return result;
                }
                return new Val(call);
            }
            rowNames.add(name);
            Val named = new Val(name);
            named.matrix = matrixNames.contains(name);
            if (!named.matrix) named.tree = Expr.var(name);
            return named;
        }

        throw new RuntimeException("didn't understand '" + token + "'");
    }

    private Val combine(Val left, String op, Val right) {
        if (left.tupleCall != null || right.tupleCall != null) {
            throw new RuntimeException("can't do math on a point-valued function call here");
        }
        if (left.color || right.color) throw new RuntimeException("can't do math on a color");
        if (!left.isTuple() && !right.isTuple()) {
            Val result = new Val("(" + left.scalar + " " + op + " " + right.scalar + ")");
            result.matrix = left.matrix || right.matrix;
            result.symbolic = left.symbolic || right.symbolic;
            if (!result.matrix && left.tree != null && right.tree != null) {
                if (op.equals("+")) result.tree = Expr.add(left.tree, right.tree);
                else if (op.equals("-")) result.tree = Expr.sub(left.tree, right.tree);
                else if (op.equals("*")) result.tree = Expr.mul(left.tree, right.tree);
                else result.tree = Expr.div(left.tree, right.tree);
            }
            return result;
        }
        List<String> parts = new ArrayList<>();
        if (left.isTuple() && right.isTuple()) {
            if (!op.equals("+") && !op.equals("-")) throw new RuntimeException("points can only be added or subtracted");
            if (left.parts.size() != right.parts.size()) throw new RuntimeException("points have different sizes");
            for (int i = 0; i < left.parts.size(); i++) {
                parts.add("(" + left.parts.get(i) + " " + op + " " + right.parts.get(i) + ")");
            }
        } else if (left.isTuple()) {
            if (!op.equals("*") && !op.equals("/")) throw new RuntimeException("can't add a number to a point");
            for (String part : left.parts) {
                parts.add("(" + part + " " + op + " " + right.scalar + ")");
            }
        } else {
            if (!op.equals("*")) throw new RuntimeException("can only multiply a number by a point");
            for (String part : right.parts) {
                parts.add("(" + left.scalar + " * " + part + ")");
            }
        }
        return new Val(parts);
    }

    private Val numberVal(double value) {
        Val result = new Val(number(value));
        result.tree = Expr.num(value);
        return result;
    }

    private Val fromTree(Expr tree) {
        Val result = new Val(tree.source());
        result.tree = tree;
        result.symbolic = true;
        if (tree.uses("x")) rowNames.add("x");
        return result;
    }

    private boolean derivativeAhead() {
        String[] shape = {"{", "d", "}", "{", "d", "x", "}"};
        if (position + shape.length > tokens.size()) return false;
        for (int i = 0; i < shape.length; i++) {
            if (!tokens.get(position + i).equals(shape[i])) return false;
        }
        return true;
    }

    private Expr inline(FunctionInfo info, List<Expr> arguments) {
        Expr body = info.body;
        for (int i = 0; i < info.params.size(); i++) {
            if (arguments.get(i) == null) return null;
            body = body.replace(info.params.get(i), Expr.var("@" + i));
        }
        for (int i = 0; i < info.params.size(); i++) {
            body = body.replace("@" + i, arguments.get(i));
        }
        return body;
    }

    private String scalarOf(Val value) {
        if (value.isTuple()) throw new RuntimeException("expected a number here, not a point");
        if (value.matrix) throw new RuntimeException("expected a number here, not a matrix");
        return value.scalar;
    }

    private String readName() {
        String letter = advance();
        if (!isNameToken(letter)) throw new RuntimeException("expected a name but found '" + letter + "'");
        if (peek("_")) {
            advance();
            StringBuilder sub = new StringBuilder();
            if (peek("{")) {
                advance();
                while (!peek("}")) {
                    String piece = advance();
                    if (piece.startsWith("\\")) piece = piece.substring(1);
                    sub.append(piece);
                }
                advance();
            } else {
                sub.append(advance());
            }
            return letter + "_" + sub;
        }
        return letter;
    }

    private boolean isNumberToken(String token) {
        char c = token.charAt(0);
        return (c >= '0' && c <= '9') || (c == '.' && token.length() > 1);
    }

    private boolean isNameToken(String token) {
        if (token.startsWith("\\")) {
            return GREEK.contains(token.substring(1));
        }
        char c = token.charAt(0);
        return token.length() == 1 && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
    }

    private boolean peek(String expected) {
        return position < tokens.size() && tokens.get(position).equals(expected);
    }

    private String advance() {
        if (position >= tokens.size()) throw new RuntimeException("the expression ends too early");
        return tokens.get(position++);
    }

    private void expect(String expected) {
        if (!peek(expected)) {
            String found = position < tokens.size() ? tokens.get(position) : "the end";
            throw new RuntimeException("expected '" + expected + "' but found '" + found + "'");
        }
        position++;
    }

    private List<String> tokenize(String latex) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < latex.length()) {
            char c = latex.charAt(i);
            if (c == ' ') {
                i++;
            } else if (c == '\\') {
                int start = i;
                i++;
                if (i < latex.length() && !Character.isLetter(latex.charAt(i))) {
                    String symbol = latex.substring(start, i + 1);
                    i++;
                    if (!symbol.equals("\\ ") && !symbol.equals("\\,") && !symbol.equals("\\;")) out.add(symbol);
                    continue;
                }
                while (i < latex.length() && Character.isLetter(latex.charAt(i))) i++;
                String command = latex.substring(start, i);
                if (command.equals("\\left") || command.equals("\\right")) continue;
                if (command.equals("\\operatorname")) {
                    int close = latex.indexOf('}', i);
                    command = "\\" + latex.substring(i + 1, close);
                    i = close + 1;
                }
                out.add(command);
            } else if ((c >= '0' && c <= '9') || c == '.') {
                int start = i;
                while (i < latex.length() && ((latex.charAt(i) >= '0' && latex.charAt(i) <= '9') || latex.charAt(i) == '.')) i++;
                out.add(latex.substring(start, i));
            } else {
                out.add(String.valueOf(c));
                i++;
            }
        }
        return out;
    }

    private String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return ((long) value) + ".0";
        return Double.toString(value);
    }

    private static final Set<String> GREEK = new LinkedHashSet<>(List.of(
            "alpha", "beta", "gamma", "delta", "epsilon", "theta", "lambda", "mu", "phi", "psi", "omega", "rho", "sigma", "tau"));

    private static final String PRELUDE = """
            var mq_pen interger = 0;
            var mq_colored interger = 0;
            var mq_red dos = 0.0;
            var mq_green dos = 0.0;
            var mq_blue dos = 0.0;
            var mq_shading interger = 0;

            class Color {
                var red dos = 0.0;
                var green dos = 0.0;
                var blue dos = 0.0;

                init : interger(dos r, dos g, dos b) {
                    this.red = r;
                    this.green = g;
                    this.blue = b;
                    return 0;
                }
            }

            rgb : object(dos r, dos g, dos b) {
                return Color(r, g, b);
            }

            hsv : object(dos h, dos s, dos v) {
                var turn dos = h;
                while (turn >= 360.0) {
                    turn = turn - 360.0;
                }
                while (turn < 0.0) {
                    turn = turn + 360.0;
                }
                var sector dos = turn / 60.0;
                var k interger = 0;
                while (k + 1 <= sector) {
                    k = k + 1;
                }
                var f dos = sector - k;
                var p dos = 255.0 * v * (1.0 - s);
                var q dos = 255.0 * v * (1.0 - s * f);
                var w dos = 255.0 * v * (1.0 - s * (1.0 - f));
                var full dos = 255.0 * v;
                if (k == 0) {
                    return Color(full, w, p);
                }
                if (k == 1) {
                    return Color(q, full, p);
                }
                if (k == 2) {
                    return Color(p, full, w);
                }
                if (k == 3) {
                    return Color(p, q, full);
                }
                if (k == 4) {
                    return Color(w, p, full);
                }
                return Color(full, p, q);
            }

            swatch : interger(string name, object c) {
                print("color ", name, " ", c.red, " ", c.green, " ", c.blue, "\\n");
                return 0;
            }

            mq_describe : interger(object c) {
                print("rgb(", c.red, ", ", c.green, ", ", c.blue, ")");
                return 0;
            }

            var mq_shade function = : object(dos x, dos y) {
                return Color(0.0, 0.0, 0.0);
            };

            penColor : interger(object c) {
                mq_red = c.red;
                mq_green = c.green;
                mq_blue = c.blue;
                mq_colored = 1;
                return 0;
            }

            plot : interger(dos x, dos y) {
                if (mq_shading == 1) {
                    penColor(mq_shade(x, y));
                }
                if (mq_colored == 1) {
                    print("pt ", mq_pen, " ", x, " ", y, " ", mq_red, " ", mq_green, " ", mq_blue, "\\n");
                } else {
                    print("pt ", mq_pen, " ", x, " ", y, "\\n");
                }
                return 0;
            }

            image : interger(interger wide, interger tall, dos left, dos right, dos bottom, dos top) {
                print("img ", mq_pen, " ", wide, " ", tall, " ", left, " ", right, " ", bottom, " ", top, "\\n");
                return 0;
            }

            pixel : interger(interger i, interger j, dos r, dos g, dos b) {
                print("px ", mq_pen, " ", i, " ", j, " ", r, " ", g, " ", b, "\\n");
                return 0;
            }

            penUp : interger() {
                print("gap ", mq_pen, "\\n");
                return 0;
            }

            mq_nan : dos() {
                return (0.0 - 1.0) î 0.5;
            }

            mq_atan : dos(dos z) {
                var flip dos = 1.0;
                var value dos = z;
                if (value < 0.0) {
                    value = 0.0 - value;
                    flip = 0.0 - 1.0;
                }
                var outside interger = 0;
                if (value > 1.0) {
                    value = 1.0 / value;
                    outside = 1;
                }
                var halved dos = value / (1.0 + ((1.0 + value * value) î 0.5));
                halved = halved / (1.0 + ((1.0 + halved * halved) î 0.5));
                var square dos = halved * halved;
                var term dos = halved;
                var sum dos = 0.0;
                var n interger = 0;
                while (n < 12) {
                    sum = sum + term / (2 * n + 1);
                    term = (0.0 - term) * square;
                    n = n + 1;
                }
                sum = sum * 4.0;
                if (outside == 1) {
                    sum = 1.5707963267948966 - sum;
                }
                return sum * flip;
            }

            mq_atan2 : dos(dos y, dos x) {
                if (x > 0.0) {
                    return mq_atan(y / x);
                }
                if (x < 0.0) {
                    if (y < 0.0) {
                        return mq_atan(y / x) - 3.141592653589793;
                    }
                    return mq_atan(y / x) + 3.141592653589793;
                }
                if (y > 0.0) {
                    return 1.5707963267948966;
                }
                if (y < 0.0) {
                    return 0.0 - 1.5707963267948966;
                }
                return 0.0;
            }

            mq_ln : dos(dos x) {
                if (x <= 0.0) {
                    return mq_nan();
                }
                var k interger = 0;
                var m dos = x;
                while (m > 1.5) {
                    m = m / 2.0;
                    k = k + 1;
                }
                while (m < 0.75) {
                    m = m * 2.0;
                    k = k - 1;
                }
                var s dos = (m - 1.0) / (m + 1.0);
                var term dos = s;
                var sum dos = 0.0;
                var n interger = 0;
                while (n < 20) {
                    sum = sum + term / (2 * n + 1);
                    term = term * s * s;
                    n = n + 1;
                }
                return 2.0 * sum + k * 0.6931471805599453;
            }

            """;

    public static Map<String, Double> parseValues(List<String> lines) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String line : lines) {
            int equals = line.indexOf('=');
            if (equals < 0) continue;
            try {
                values.put(line.substring(0, equals).trim(), Double.parseDouble(line.substring(equals + 1).trim()));
            } catch (NumberFormatException e) {

            }
        }
        return values;
    }
}
