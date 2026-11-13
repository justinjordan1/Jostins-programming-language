package src.Compiler;

import src.Math.Symbolic;
import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CodeGen {

    enum T {
        INT, DOS, BOOL, STR, MAT, OBJ, FUNC,
        LIST_INT, LIST_DOS, LIST_BOOL, LIST_STR, LIST_MAT, LIST_OBJ, LIST_FUNC,
        VOID, UNKNOWN
    }

    static class Sig {
        final List<T> params = new ArrayList<>();
        T ret;
        Sig[] paramSigs;
        Sig retSig;
    }

    static boolean isList(T type) {
        return type.name().startsWith("LIST_");
    }

    static T elementOf(T list) {
        return T.valueOf(list.name().substring(5));
    }

    static boolean isNumber(T type) {
        return type == T.INT || type == T.DOS;
    }

    static class Var {
        T type;
        String label;
        int offset;
        Fn owner;
        int fieldSlot = -1;
        int heapIndex = -1;
        String name;
        Object sigKey;
        Sig paramOwner;
        int paramIndex;
        String formula;
        Lexeme definition;
        String reg;
    }

    static class Func {
        String label;
        T ret;
        List<T> paramTypes = new ArrayList<>();
        Sig sig;
        Lexeme definition;
        String formula;
    }

    static class ClassInfo {
        String name;
        int id;
        final Set<String> fields = new LinkedHashSet<>();
        final Map<String, Func> methods = new LinkedHashMap<>();
    }

    static class Scope {
        final Scope parent;
        final Fn owner;
        final Map<String, Var> vars = new HashMap<>();
        final Map<String, Func> funcs = new HashMap<>();

        Scope(Scope parent, Fn owner) {
            this.parent = parent;
            this.owner = owner;
        }
    }

    static class Fn {
        final String label;
        final T ret;
        final boolean isMain;
        final String exitLabel;
        final StringBuilder code = new StringBuilder();
        final Deque<String> breaks = new ArrayDeque<>();
        int slots = 2;
        int heapSlots = 0;
        boolean heapFrame;
        boolean hasEnv;
        Fn parent;
        Sig sig;
        ClassInfo cls;
        Var thisVar;
        final Set<Lexeme> wantRegs = new HashSet<>();
        final List<String> regs = new ArrayList<>();
        int ints;
        int floats;

        Fn(String label, T ret, boolean isMain, String exitLabel) {
            this.label = label;
            this.ret = ret;
            this.isMain = isMain;
            this.exitLabel = exitLabel;
        }
    }

    private static final Set<String> CANVAS_METHODS =
            Set.of("setGridSize", "setWindowSize", "setPixel", "exportPPM", "captureFrame", "collapse");

    private final Map<String, T> fieldTypes = new HashMap<>();
    private final Map<String, Integer> fieldSlots = new LinkedHashMap<>();
    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, Func> knownFuncs = new HashMap<>();
    private final Map<String, T> knownGlobals = new HashMap<>();
    private final Map<Lexeme, Sig> defSigs = new HashMap<>();
    private final Map<Object, Sig> varSigs = new HashMap<>();
    private final Map<String, Sig> fieldSigs = new HashMap<>();
    private final Set<String> staticClosures = new LinkedHashSet<>();
    private Sig lastSig;
    private String lastFormula;
    private Lexeme lastDefinition;
    private boolean lenient;
    private boolean inRuntime;
    private boolean changed;

    private final StringBuilder functions = new StringBuilder();
    private final StringBuilder rodata = new StringBuilder();
    private final StringBuilder data = new StringBuilder();
    private final Map<Long, String> doubleLabels = new HashMap<>();
    private final Map<String, String> stringLabels = new HashMap<>();
    private Scope globalScope;
    private Scope scope;
    private Fn cur;
    private int labelCount;
    private int lastLine;

    public String compile(Lexeme runtimeLibrary, Lexeme program) {
        for (int pass = 0; pass < 8; pass++) {
            lenient = true;
            changed = false;
            runPass(runtimeLibrary, program);
            if (!changed) break;
        }
        lenient = false;
        return runPass(runtimeLibrary, program);
    }

    private String runPass(Lexeme runtimeLibrary, Lexeme program) {
        functions.setLength(0);
        rodata.setLength(0);
        data.setLength(0);
        doubleLabels.clear();
        stringLabels.clear();
        staticClosures.clear();
        labelCount = 0;
        lastSig = null;
        lastFormula = null;
        lastDefinition = null;

        cur = new Fn("main", T.VOID, true, newLabel());
        cur.heapFrame = false;
        for (Lexeme statement : program.children) {
            if (statement.getType() == Types.functionDefinitionActual) continue;
            if (containsClosure(statement)) cur.heapFrame = true;
        }
        if (!cur.heapFrame) planRegisters(program.children, List.of());
        globalScope = new Scope(null, cur);
        scope = globalScope;
        inRuntime = true;
        if (runtimeLibrary != null) genList(runtimeLibrary);
        inRuntime = false;
        T lastType = null;
        for (Lexeme statement : program.children) {
            T type = genStmt(statement);
            if (!keepsResult(statement, type)) continue;
            emit("la t0, rtLastResult");
            emit(type == T.DOS ? "fsd fa0, 0(t0)" : "sd a0, 0(t0)");
            lastType = type;
        }
        if (lastType != null) {
            emit("la a0, " + stringConst("Program result: "));
            emit("call printStr");
            emit("la t0, rtLastResult");
            emit(lastType == T.DOS ? "fld fa0, 0(t0)" : "ld a0, 0(t0)");
            genPrint(lastType, program);
            emit("la a0, " + stringConst("\n"));
            emit("call printStr");
        }
        data.append("    .balign 8\nrtLastResult: .quad 0\n");
        String main = finish(cur);

        List<String> nameLabels = new ArrayList<>();
        nameLabels.add(stringConst("object"));
        for (ClassInfo info : classes.values()) nameLabels.add(stringConst(info.name));
        rodata.append("    .balign 8\nclassNames:\n");
        for (String nameLabel : nameLabels) rodata.append("    .quad ").append(nameLabel).append('\n');
        rodata.append("objSize: .quad ").append(8 + 8 * fieldSlots.size()).append('\n');

        StringBuilder out = new StringBuilder();
        out.append("    .text\n    .globl main\n");
        out.append(main);
        out.append(functions);
        out.append("\n    .section .rodata\n");
        out.append(rodata);
        out.append("\n    .data\n");
        out.append(data);
        return out.toString();
    }

    private boolean containsClosure(Lexeme node) {
        if (node == null || node.getType() == Types.classDefinition) return false;
        if (node.getType() == Types.lambda || node.getType() == Types.functionDefinitionActual) return true;
        for (Lexeme child : node.children) {
            if (containsClosure(child)) return true;
        }
        return false;
    }

    private Sig sigOf(Lexeme definition) {
        Sig sig = defSigs.get(definition);
        if (sig == null) {
            sig = new Sig();
            for (Lexeme parameter : definition.getChild(3).children) {
                sig.params.add(typeOf(parameter.getChild(0)));
            }
            sig.ret = typeOf(definition.getChild(2));
            sig.paramSigs = new Sig[sig.params.size()];
            defSigs.put(definition, sig);
        }
        return sig;
    }

    private boolean compatible(Sig a, Sig b, int depth) {
        if (a == b || a == null || b == null || depth > 8) return true;
        if (a.ret != b.ret || !a.params.equals(b.params)) return false;
        if (!compatible(a.retSig, b.retSig, depth + 1)) return false;
        for (int i = 0; i < a.paramSigs.length; i++) {
            if (!compatible(a.paramSigs[i], b.paramSigs[i], depth + 1)) return false;
        }
        return true;
    }

    private Sig merge(Sig current, Sig incoming, Lexeme at) {
        if (incoming == null) return current;
        if (current == null) {
            changed = true;
            return incoming;
        }
        if (!lenient && !compatible(current, incoming, 0)) {
            throw error("this function slot is given two different kinds of function; "
                    + "the compiler needs each one to hold a single kind", at);
        }
        return current;
    }

    private Sig getSig(Var variable) {
        if (variable.paramOwner != null) return variable.paramOwner.paramSigs[variable.paramIndex];
        if (variable.fieldSlot >= 0) return fieldSigs.get(variable.name);
        if (variable.sigKey != null) return varSigs.get(variable.sigKey);
        return null;
    }

    private void learn(Var variable, Sig sig, Lexeme at) {
        if (sig == null) return;
        if (variable.paramOwner != null) {
            Sig[] slots = variable.paramOwner.paramSigs;
            slots[variable.paramIndex] = merge(slots[variable.paramIndex], sig, at);
        } else if (variable.fieldSlot >= 0) {
            fieldSigs.put(variable.name, merge(fieldSigs.get(variable.name), sig, at));
        } else if (variable.sigKey != null) {
            varSigs.put(variable.sigKey, merge(varSigs.get(variable.sigKey), sig, at));
        }
    }

    private void emitClosure(String codeLabel) {
        emit("li a0, 16");
        emit("call rtAlloc");
        emit("la t0, " + codeLabel);
        emit("sd t0, 0(a0)");
        if (cur.heapFrame) {
            emit("ld t0, -32(s0)");
            emit("sd t0, 8(a0)");
        }
    }

    private boolean keepsResult(Lexeme statement, T type) {
        if (type == T.VOID || type == T.UNKNOWN || isList(type)) return false;
        switch (statement.getType()) {
            case varInitialization:
            case varAssignment:
            case fieldAssignment:
            case collectionInitialization:
            case binaryExpression:
            case unaryExpression:
            case primary:
            case parentheziedExpreission:
            case funcCall:
            case methodCall:
            case fieldAccess:
            case matrixGetter:
                return true;
            default:
                return false;
        }
    }

    private void emit(String instruction) {
        cur.code.append("    ").append(instruction).append('\n');
    }

    private void label(String name) {
        cur.code.append(name).append(":\n");
    }

    private String newLabel() {
        return "L" + (labelCount++);
    }

    private static String camel(String name) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upper = out.length() > 0;
                continue;
            }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }

    private static String capitalized(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String fnLabel(String name) {
        return "fn" + capitalized(camel(name));
    }

    private static String globalLabel(String name) {
        return "g" + camel(name);
    }

    private static String classLabel(String name) {
        return "cls" + capitalized(camel(name));
    }

    private static String methodLabel(String owner, String name) {
        return classLabel(owner) + capitalized(camel(name));
    }

    private static String cloLabel(String code) {
        return "clo" + capitalized(code);
    }

    private RuntimeException error(String message, Lexeme at) {
        return new RuntimeException("Compile error (line " + line(at) + "): " + message);
    }

    private int line(Lexeme node) {
        try {
            lastLine = node.getLineNumber();
        } catch (Exception e) {
            for (Lexeme child : node.children) {
                int childLine = line(child);
                if (childLine != 0) break;
            }
        }
        return lastLine;
    }

    private Lexeme strip(Lexeme node) {
        while (node.getChildrenSize() == 1 && (node.getType() == Types.binaryExpression
                || node.getType() == Types.primary || node.getType() == Types.parentheziedExpreission)) {
            node = node.getChild(0);
        }
        return node;
    }

    private String finish(Fn fn) {
        int locals = ((fn.slots + fn.regs.size()) * 8 + 15) & ~15;
        StringBuilder out = new StringBuilder();
        out.append('\n').append(fn.label).append(":\n");
        out.append("    addi sp, sp, -16\n");
        out.append("    sd ra, 8(sp)\n");
        out.append("    sd s0, 0(sp)\n");
        out.append("    addi s0, sp, 16\n");
        if (locals > 0) {
            out.append("    li t0, ").append(locals).append('\n');
            out.append("    sub sp, sp, t0\n");
        }
        for (int k = 0; k < fn.regs.size(); k++) out.append(saveSlot(fn, k, true));
        if (fn.hasEnv) out.append("    sd t6, -24(s0)\n");
        if (fn.heapFrame) {
            out.append("    mv t5, a0\n");
            out.append("    li a0, ").append(8 + 8 * fn.heapSlots).append('\n');
            out.append("    call rtAlloc\n");
            out.append("    sd a0, -32(s0)\n");
            if (fn.hasEnv) {
                out.append("    ld t0, -24(s0)\n");
                out.append("    sd t0, 0(a0)\n");
            }
            out.append("    mv a0, t5\n");
        }
        out.append(tidy(fn.code.toString(), fn.exitLabel));
        out.append(fn.exitLabel).append(":\n");
        for (int k = 0; k < fn.regs.size(); k++) out.append(saveSlot(fn, k, false));
        out.append("    addi sp, s0, -16\n");
        out.append("    ld ra, 8(sp)\n");
        out.append("    ld s0, 0(sp)\n");
        out.append("    addi sp, sp, 16\n");
        out.append("    ret\n");
        return out.toString();
    }

    private String saveSlot(Fn fn, int k, boolean save) {
        String reg = fn.regs.get(k);
        int offset = -(24 + 8 * (fn.slots + k));
        String op = reg.startsWith("f") ? (save ? "fsd " : "fld ") : (save ? "sd " : "ld ");
        if (offset >= -2048) return "    " + op + reg + ", " + offset + "(s0)\n";
        return "    li t0, " + offset + "\n    add t0, t0, s0\n    " + op + reg + ", 0(t0)\n";
    }

    private void planRegisters(List<Lexeme> body, List<Lexeme> parameters) {
        Map<String, Long> weight = new HashMap<>();
        List<Lexeme> doubles = new ArrayList<>(), numbers = new ArrayList<>();
        for (Lexeme parameter : parameters) {
            T type = typeOf(parameter.getChild(0));
            if (type == T.DOS) doubles.add(parameter.getChild(1));
            else if (type == T.INT || type == T.BOOL) numbers.add(parameter.getChild(1));
        }
        for (Lexeme node : body) weigh(node, 0, weight, doubles, numbers);
        java.util.Comparator<Lexeme> hotter = (a, b) -> Long.compare(
                weight.getOrDefault(b.getStringValue(), 0L), weight.getOrDefault(a.getStringValue(), 0L));
        doubles.sort(hotter);
        numbers.sort(hotter);
        cur.wantRegs.addAll(doubles.subList(0, Math.min(12, doubles.size())));
        cur.wantRegs.addAll(numbers.subList(0, Math.min(11, numbers.size())));
    }

    private void weigh(Lexeme node, int depth, Map<String, Long> weight, List<Lexeme> doubles, List<Lexeme> numbers) {
        if (node == null) return;
        Types kind = node.getType();
        if (kind == Types.functionDefinitionActual || kind == Types.lambda || kind == Types.classDefinition) return;
        if (kind == Types.IDENTIFIER) {
            weight.merge(node.getStringValue(), 1L << (3 * Math.min(depth, 6)), Long::sum);
            return;
        }
        if (kind == Types.varInitialization && node.getChildrenSize() > 2) {
            T type;
            try {
                type = typeOf(node.getChild(2));
            } catch (RuntimeException e) {
                type = T.UNKNOWN;
            }
            for (Lexeme identifier : node.getChild(1).children) {
                if (type == T.DOS) doubles.add(identifier);
                else if (type == T.INT || type == T.BOOL) numbers.add(identifier);
            }
        }
        boolean loop = kind == Types.foorLoopBody || kind == Types.forEachBody || kind == Types.whileL
                || kind == Types.whilePercent || kind == Types.whileSortOfBody || kind == Types.indefPreformBody;
        if (node.children == null) return;
        for (Lexeme child : node.children) weigh(child, depth + (loop ? 1 : 0), weight, doubles, numbers);
    }

    private String doubleConst(double value) {
        long bits = Double.doubleToRawLongBits(value);
        String existing = doubleLabels.get(bits);
        if (existing != null) return existing;
        String name = "dbl" + doubleLabels.size();
        doubleLabels.put(bits, name);
        rodata.append("    .balign 8\n").append(name).append(": .quad ").append(bits)
                .append("    # ").append(value).append('\n');
        return name;
    }

    private void loadDouble(String register, double value) {
        emit("la t0, " + doubleConst(value));
        emit("fld " + register + ", 0(t0)");
    }

    private String stringConst(String value) {
        String existing = stringLabels.get(value);
        if (existing != null) return existing;
        String name = "str" + stringLabels.size();
        stringLabels.put(value, name);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        rodata.append("    .balign 8\n").append(name).append(": .quad ").append(bytes.length).append('\n');
        if (bytes.length > 0) {
            rodata.append("    .byte ");
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0) rodata.append(", ");
                rodata.append(bytes[i] & 0xff);
            }
            rodata.append('\n');
        }
        return name;
    }

    private void matrixCell() {
        emit("ld t0, 0(a0)");
        emit("bgeu a1, t0, rtBounds");
        emit("ld t5, 8(a0)");
        emit("bgeu a2, t5, rtBounds");
        emit("mul t1, a1, t5");
        emit("add t1, t1, a2");
        emit("slli t1, t1, 3");
        emit("add t1, t1, a0");
    }

    private static final String[] SPARE_INTS = {"t2", "t3", "t4", "a7", "a6", "a5", "a4", "a3"};
    private static final String[] SPARE_FLOATS = {"ft1", "ft2", "ft3", "ft4", "ft5", "ft6", "ft7", "ft8",
            "ft9", "ft10", "ft11", "fa7", "fa6", "fa5", "fa4", "fa3", "fa2"};

    private String tidy(String code, String exitLabel) {
        List<String> lines = new ArrayList<>(List.of(code.split("\n")));
        List<String> ints = spare(SPARE_INTS, code);
        List<String> floats = spare(SPARE_FLOATS, code);

        String[] rewritten = new String[lines.size()];
        boolean[] dropped = new boolean[lines.size()];
        Deque<int[]> open = new ArrayDeque<>();
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            String next = i + 1 < lines.size() ? lines.get(i + 1).trim() : "";
            if (line.equals("addi sp, sp, -16") && (next.equals("fsd fa0, 0(sp)") || next.equals("sd a0, 0(sp)"))) {
                open.push(new int[]{i, next.startsWith("f") ? 1 : 0, 1, open.size()});
                i++;
                continue;
            }
            if ((line.startsWith("ld ") || line.startsWith("fld ")) && line.endsWith(", 0(sp)") && next.equals("addi sp, sp, 16")) {
                if (open.isEmpty()) continue;
                int[] push = open.pop();
                boolean floating = line.startsWith("f");
                if (push[2] == 1 && push[1] == (floating ? 1 : 0)) {
                    pairs.add(new int[]{push[0], i, push[1], push[3]});
                } else {
                    for (int[] outer : open) outer[2] = 0;
                }
                i++;
                continue;
            }
            if (line.contains("sp") || line.startsWith("call ") || line.startsWith("jalr") || line.equals("ecall")) {
                for (int[] outer : open) outer[2] = 0;
            }
        }
        for (int[] pair : pairs) {
            List<String> pool = pair[2] == 1 ? floats : ints;
            if (pair[3] >= pool.size()) continue;
            String temp = pool.get(pair[3]);
            String target = lines.get(pair[1]).trim().split("[ ,]+")[1];
            rewritten[pair[0]] = pair[2] == 1 ? "fmv.d " + temp + ", fa0" : "mv " + temp + ", a0";
            dropped[pair[0] + 1] = true;
            rewritten[pair[1]] = pair[2] == 1 ? "fmv.d " + target + ", " + temp : "mv " + target + ", " + temp;
            dropped[pair[1] + 1] = true;
        }

        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (dropped[i]) continue;
            String line = rewritten[i] != null ? rewritten[i] : lines.get(i).trim();
            if (line.isEmpty()) continue;
            if (!kept.isEmpty()) {
                String last = kept.get(kept.size() - 1);
                String[] a = last.split("[ ,]+");
                String[] b = line.split("[ ,]+");
                if (a.length == 3 && b.length == 3 && a[0].equals(b[0]) && (a[0].equals("mv") || a[0].equals("fmv.d"))
                        && b[2].equals(a[1]) && isSpare(a[1])) {
                    kept.set(kept.size() - 1, a[0] + " " + b[1] + ", " + a[2]);
                    continue;
                }
                if (a.length == 3 && b.length == 3 && a[0].equals(b[0]) && (a[0].equals("mv") || a[0].equals("fmv.d"))
                        && a[1].equals(b[2]) && a[2].equals(b[1])) {
                    continue;
                }
                if (a.length == 3 && b.length == 3 && a[2].equals(b[2]) && a[1].equals(b[1]) && a[2].endsWith("(s0)")
                        && ((a[0].equals("fsd") && b[0].equals("fld")) || (a[0].equals("sd") && b[0].equals("ld")))) {
                    continue;
                }
            }
            kept.add(line);
        }
        for (int round = 0; round < 4; round++) {
            List<String> before = kept;
            kept = sweep(number(sweep(propagate(kept), exitLabel)), exitLabel);
            if (kept.equals(before)) break;
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < kept.size(); i++) {
            String line = kept.get(i);
            if (line.startsWith("j ") && i + 1 < kept.size() && kept.get(i + 1).equals(line.substring(2) + ":")) continue;
            if (line.equals("j " + exitLabel) && i == kept.size() - 1) continue;
            out.append(line.endsWith(":") ? line : "    " + line).append('\n');
        }
        return out.toString();
    }

    private static final java.util.regex.Pattern REGISTER = java.util.regex.Pattern.compile(
            "zero|ra|sp|gp|tp|t[0-6]|s[0-9]|s1[01]|a[0-7]|f[ts][0-9]|f[ts]1[01]|fa[0-7]");
    private static final Set<String> PURE = Set.of("mv", "fmv.d", "li", "la", "add", "addi", "sub", "mul", "div",
            "divu", "rem", "remu", "and", "andi", "or", "ori", "xor", "xori", "slt", "slti", "sltu", "sltiu", "seqz",
            "snez", "neg", "not", "sll", "slli", "srl", "srli", "sra", "srai", "ld", "fld", "fadd.d", "fsub.d",
            "fmul.d", "fdiv.d", "fsqrt.d", "fneg.d", "fabs.d", "fmin.d", "fmax.d", "feq.d", "flt.d", "fle.d",
            "fcvt.d.l", "fcvt.l.d", "fmv.d.x", "fmv.x.d");
    private static final Set<String> STORES = Set.of("sd", "fsd");
    private static final Set<String> ALL_REGISTERS = new HashSet<>();

    static {
        for (String prefix : new String[]{"t", "s", "a", "ft", "fs", "fa"}) {
            for (int k = 0; k < 12; k++) {
                String name = prefix + k;
                if (REGISTER.matcher(name).matches() && !name.equals("s0")) ALL_REGISTERS.add(name);
            }
        }
        ALL_REGISTERS.add("ra");
    }

    private static String[] operands(String line) {
        int space = line.indexOf(' ');
        if (space < 0) return new String[]{line};
        String[] rest = line.substring(space + 1).split(",\\s*");
        String[] all = new String[rest.length + 1];
        all[0] = line.substring(0, space);
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    private static boolean isRegister(String token) {
        return REGISTER.matcher(token).matches();
    }

    private static String base(String token) {
        int open = token.indexOf('(');
        return open >= 0 && token.endsWith(")") ? token.substring(open + 1, token.length() - 1) : null;
    }

    private static boolean branch(String op) {
        return Set.of("beqz", "bnez", "beq", "bne", "blt", "bge", "bltu", "bgeu", "bgtz", "blez", "bltz", "bgez").contains(op);
    }

    private static String join(String[] parts) {
        StringBuilder line = new StringBuilder(parts[0]);
        for (int k = 1; k < parts.length; k++) line.append(k == 1 ? " " : ", ").append(parts[k]);
        return line.toString();
    }

    private static List<String> propagate(List<String> lines) {
        Map<String, String> copies = new HashMap<>();
        Map<String, String> constants = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String[] parts = operands(line);
            String op = parts[0];
            boolean known = PURE.contains(op) || STORES.contains(op) || branch(op);
            if (line.endsWith(":") || !known) {
                copies.clear();
                constants.clear();
                out.add(line);
                continue;
            }
            int first = PURE.contains(op) ? 2 : 1;
            for (int k = first; k < parts.length; k++) {
                String token = parts[k];
                String inner = base(token);
                if (inner != null && copies.containsKey(inner)) {
                    parts[k] = token.substring(0, token.indexOf('(') + 1) + copies.get(inner) + ")";
                } else if (copies.containsKey(token)) {
                    parts[k] = copies.get(token);
                }
            }
            if (PURE.contains(op)) {
                String target = parts[1];
                copies.remove(target);
                copies.values().removeIf(source -> source.equals(target));
                constants.remove(target);
                if (op.equals("mv") && constants.containsKey(parts[2])) {
                    parts = new String[]{"li", target, constants.get(parts[2])};
                    op = "li";
                }
                if (op.equals("li")) constants.put(target, parts[2]);
                if ((op.equals("mv") || op.equals("fmv.d")) && !parts[2].equals(target)
                        && !target.equals("sp") && !parts[2].equals("sp")) {
                    copies.put(target, parts[2]);
                }
                if ((op.equals("mv") || op.equals("fmv.d")) && parts[2].equals(target)) continue;
            }
            out.add(join(parts));
        }
        return out;
    }

    private static List<String> number(List<String> lines) {
        Map<String, String> value = new HashMap<>();
        int[] fresh = {0};
        int epoch = 0;
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String[] parts = operands(line);
            String op = parts[0];
            if (line.endsWith(":") || !(PURE.contains(op) || STORES.contains(op) || branch(op))) {
                value.clear();
                out.add(line);
                continue;
            }
            if (!PURE.contains(op)) {
                if (STORES.contains(op)) {
                    String where = parts[2];
                    if ("s0".equals(base(where))) {
                        String slot = "@" + where;
                        value.values().removeIf(v -> v.contains(slot));
                    } else {
                        epoch++;
                    }
                }
                out.add(line);
                continue;
            }
            String target = parts[1];
            StringBuilder key = new StringBuilder(op);
            for (int k = 2; k < parts.length; k++) {
                String token = parts[k];
                String inner = base(token);
                key.append(' ');
                if (inner != null) {
                    key.append(inner.equals("s0") ? "@" + token : token.substring(0, token.indexOf('(')) + "(" + valueOf(value, inner, fresh) + ")#" + epoch);
                } else if (isRegister(token)) {
                    key.append(valueOf(value, token, fresh));
                } else {
                    key.append(token);
                }
            }
            String expression = op.equals("mv") || op.equals("fmv.d") ? valueOf(value, parts[2], fresh) : key.toString();
            String holder = null;
            if (!target.equals("sp") && !target.equals("s0")) {
                for (Map.Entry<String, String> entry : value.entrySet()) {
                    if (entry.getValue().equals(expression) && entry.getKey().startsWith("f") == target.startsWith("f")
                            && !entry.getKey().equals("sp") && !entry.getKey().equals("zero")) {
                        holder = entry.getKey();
                        if (holder.equals(target)) break;
                    }
                }
            }
            if (holder != null && holder.equals(target)) continue;
            value.remove(target);
            if (holder != null) {
                out.add((target.startsWith("f") ? "fmv.d " : "mv ") + target + ", " + holder);
            } else {
                out.add(line);
            }
            if (target.equals("sp") || target.equals("s0")) {
                value.clear();
                continue;
            }
            value.put(target, expression.length() > 400 ? "v" + fresh[0]++ : expression);
        }
        return out;
    }

    private static String valueOf(Map<String, String> value, String register, int[] fresh) {
        return value.computeIfAbsent(register, r -> "v" + fresh[0]++);
    }

    private static List<String> sweep(List<String> lines, String exitLabel) {
        Map<String, Set<String>> labels = new HashMap<>();
        for (String line : lines) if (line.endsWith(":")) labels.put(line.substring(0, line.length() - 1), null);
        Set<String> leaving = new HashSet<>(ALL_REGISTERS);
        leaving.removeAll(Set.of("a0", "fa0", "ra"));
        labels.put(exitLabel, leaving);
        labels.put("", leaving);
        for (int round = 0; round < 50; round++) {
            Map<String, Set<String>> before = new HashMap<>(labels);
            sweep(lines, labels, false);
            if (labels.equals(before)) break;
        }
        return sweep(lines, labels, true);
    }

    private static Set<String> deadAt(Map<String, Set<String>> labels, String name, Set<String> dead) {
        if (!labels.containsKey(name)) return new HashSet<>();
        Set<String> known = labels.get(name);
        if (known == null) return dead;
        Set<String> both = new HashSet<>(dead);
        both.retainAll(known);
        return both;
    }

    private static List<String> sweep(List<String> lines, Map<String, Set<String>> labels, boolean apply) {
        Set<String> dead = new HashSet<>(labels.get(""));
        boolean[] drop = new boolean[lines.size()];
        String[] text = lines.toArray(new String[0]);
        int move = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = text[i];
            int pending = move;
            move = -1;
            String[] parts = operands(line);
            String op = parts[0];
            if (line.endsWith(":")) {
                if (!apply) labels.put(line.substring(0, line.length() - 1), new HashSet<>(dead));
                continue;
            }
            if (op.equals("j") && parts.length == 2) {
                Set<String> known = labels.containsKey(parts[1]) ? labels.get(parts[1]) : new HashSet<>();
                dead = known == null ? new HashSet<>(ALL_REGISTERS) : new HashSet<>(known);
                continue;
            }
            if (line.equals("call rtDivZero")) {
                dead = new HashSet<>(ALL_REGISTERS);
                continue;
            }
            if (!(PURE.contains(op) || STORES.contains(op) || branch(op))) {
                dead.clear();
                continue;
            }
            if (branch(op) && !parts[parts.length - 1].equals("rtBounds")) {
                dead = deadAt(labels, parts[parts.length - 1], dead);
            }
            if (STORES.contains(op) || branch(op)) {
                for (int k = 1; k < parts.length; k++) {
                    dead.remove(parts[k]);
                    String inner = base(parts[k]);
                    if (inner != null) dead.remove(inner);
                }
                continue;
            }
            String target = parts[1];
            if (dead.contains(target) && !target.equals("sp") && !target.equals("s0")) {
                drop[i] = apply;
                continue;
            }
            if (apply && pending == i + 1 && operands(text[pending])[2].equals(target)) {
                parts[1] = operands(text[pending])[1];
                text[i] = join(parts);
                drop[pending] = true;
                dead.add(target);
                target = parts[1];
            } else if ((op.equals("mv") || op.equals("fmv.d")) && dead.contains(parts[2])
                    && !parts[2].equals("sp") && !parts[2].equals("s0") && !target.equals("sp")) {
                move = i;
            }
            if (isRegister(target)) dead.add(target);
            for (int k = 2; k < parts.length; k++) {
                dead.remove(parts[k]);
                String inner = base(parts[k]);
                if (inner != null) dead.remove(inner);
            }
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) if (!drop[i]) out.add(text[i]);
        return out;
    }

    private static boolean isSpare(String register) {
        for (String name : SPARE_INTS) if (name.equals(register)) return true;
        for (String name : SPARE_FLOATS) if (name.equals(register)) return true;
        return false;
    }

    private static List<String> spare(String[] pool, String code) {
        List<String> free = new ArrayList<>();
        for (String register : pool) {
            if (!java.util.regex.Pattern.compile("\\b" + register + "\\b").matcher(code).find()) free.add(register);
        }
        return free;
    }

    private void push(T type) {
        emit("addi sp, sp, -16");
        emit(type == T.DOS ? "fsd fa0, 0(sp)" : "sd a0, 0(sp)");
    }

    private void pop(String register) {
        emit((register.startsWith("f") ? "fld " : "ld ") + register + ", 0(sp)");
        emit("addi sp, sp, 16");
    }

    private void popValue(T type) {
        pop(type == T.DOS ? "fa0" : "a0");
    }

    private T typeOf(Lexeme typeNode) {
        switch (typeNode.getType()) {
            case INTERGER:
                return T.INT;
            case DOS:
                return T.DOS;
            case GEORGE:
                return T.BOOL;
            case STRING:
                return T.STR;
            case MATRIX:
                return T.MAT;
            case OBJECT:
                return T.OBJ;
            case FUNCTION:
                return T.FUNC;
            case CHAR:
                throw error("char is not supported by the compiler yet", typeNode);
            default:
                return T.VOID;
        }
    }

    private Var declare(Lexeme identifier, T type) {
        return declareName(identifier.getStringValue(), type, identifier);
    }

    private Var declareName(String name, T type, Lexeme at) {
        if (scope.vars.containsKey(name)) {
            throw error("variable '" + name + "' is already defined", at);
        }
        Var variable = new Var();
        variable.type = type;
        variable.name = name;
        if (cur.isMain && scope == globalScope) {
            variable.label = globalLabel(name);
            variable.sigKey = variable.label;
            data.append("    .balign 8\n").append(variable.label).append(": .quad 0\n");
            if (type != T.UNKNOWN && knownGlobals.get(name) != type) {
                knownGlobals.put(name, type);
                changed = true;
            }
        } else {
            variable.sigKey = at;
            variable.owner = cur;
            boolean number = type == T.INT || type == T.BOOL;
            if (cur.heapFrame) variable.heapIndex = cur.heapSlots++;
            else if (cur.wantRegs.contains(at) && type == T.DOS && cur.floats < 12) {
                variable.reg = "fs" + cur.floats++;
                cur.regs.add(variable.reg);
            } else if (cur.wantRegs.contains(at) && number && cur.ints < 11) {
                variable.reg = "s" + (++cur.ints);
                cur.regs.add(variable.reg);
            } else variable.offset = -(24 + 8 * cur.slots++);
        }
        scope.vars.put(name, variable);
        return variable;
    }

    private int slot(String field) {
        Integer existing = fieldSlots.get(field);
        if (existing != null) return existing;
        fieldSlots.put(field, fieldSlots.size());
        changed = true;
        return fieldSlots.get(field);
    }

    private void recordField(String field, T type) {
        slot(field);
        if (type == T.UNKNOWN || type == T.VOID || fieldTypes.containsKey(field)) return;
        fieldTypes.put(field, type);
        changed = true;
    }

    private Var fieldVar(String field) {
        Var variable = new Var();
        variable.name = field;
        variable.fieldSlot = slot(field);
        variable.type = fieldTypes.getOrDefault(field, T.UNKNOWN);
        return variable;
    }

    private Var tryLookup(Lexeme identifier) {
        String name = identifier.getStringValue();
        Scope s = scope;
        for (; s != null && !s.owner.isMain; s = s.parent) {
            Var variable = s.vars.get(name);
            if (variable != null) return variable;
        }
        if (cur.cls != null && cur.cls.fields.contains(name)) return fieldVar(name);
        for (; s != null; s = s.parent) {
            Var variable = s.vars.get(name);
            if (variable != null) return variable;
        }
        T known = knownGlobals.get(name);
        if (known != null) {
            Var variable = new Var();
            variable.name = name;
            variable.type = known;
            variable.label = globalLabel(name);
            variable.sigKey = variable.label;
            return variable;
        }
        return null;
    }

    private Var lookup(Lexeme identifier) {
        Var variable = tryLookup(identifier);
        if (variable == null && !lenient) {
            throw error("'" + identifier.getStringValue() + "' is undefined", identifier);
        }
        return variable;
    }

    private Func findFunc(String name) {
        for (Scope s = scope; s != null; s = s.parent) {
            Func function = s.funcs.get(name);
            if (function != null) return function;
        }
        return knownFuncs.get(name);
    }

    private boolean sameSignature(Func a, Func b) {
        return a.ret == b.ret && a.paramTypes.equals(b.paramTypes) && a.label.equals(b.label);
    }

    private String address(Var variable) {
        if (variable.fieldSlot >= 0) {
            emit("ld t1, " + address(cur.thisVar));
            return (8 + 8 * variable.fieldSlot) + "(t1)";
        }
        if (variable.label != null) {
            emit("la t0, " + variable.label);
            return "0(t0)";
        }
        if (variable.owner != cur) {
            if (!cur.hasEnv || variable.heapIndex < 0) {
                throw new RuntimeException("Compile error: '" + variable.name
                        + "' belongs to an enclosing function that this code can't reach");
            }
            emit("ld t1, -24(s0)");
            for (Fn fn = cur.parent; fn != variable.owner; fn = fn.parent) {
                if (fn == null || !fn.hasEnv) {
                    throw new RuntimeException("Compile error: '" + variable.name
                            + "' belongs to an enclosing function that this code can't reach");
                }
                emit("ld t1, 0(t1)");
            }
            return (8 + 8 * variable.heapIndex) + "(t1)";
        }
        if (variable.heapIndex >= 0) {
            emit("ld t1, -32(s0)");
            return (8 + 8 * variable.heapIndex) + "(t1)";
        }
        if (variable.offset < -2048) {
            emit("li t0, " + variable.offset);
            emit("add t0, t0, s0");
            return "0(t0)";
        }
        return variable.offset + "(s0)";
    }

    private void load(Var variable) {
        if (variable.reg != null) {
            emit(variable.type == T.DOS ? "fmv.d fa0, " + variable.reg : "mv a0, " + variable.reg);
            return;
        }
        String where = address(variable);
        emit(variable.type == T.DOS ? "fld fa0, " + where : "ld a0, " + where);
    }

    private void store(Var variable) {
        if (variable.reg != null) {
            emit(variable.type == T.DOS ? "fmv.d " + variable.reg + ", fa0" : "mv " + variable.reg + ", a0");
            return;
        }
        String where = address(variable);
        emit(variable.type == T.DOS ? "fsd fa0, " + where : "sd a0, " + where);
    }

    private void convert(T from, T to, Lexeme at) {
        if (from == to || from == T.UNKNOWN || to == T.UNKNOWN) return;
        if ((from == T.INT || from == T.BOOL) && to == T.DOS) {
            emit("fcvt.d.l fa0, a0");
        } else if (from == T.DOS && to == T.INT) {
            loadDouble("ft0", 0.5);
            emit("fadd.d fa0, fa0, ft0");
            emit("fcvt.l.d a0, fa0, rdn");
        } else if (from == T.BOOL && to == T.INT) {
            return;
        } else if (from == T.INT && to == T.BOOL) {
            emit("snez a0, a0");
        } else if (from == T.DOS && to == T.BOOL) {
            emit("fmv.d.x ft0, zero");
            emit("feq.d a0, fa0, ft0");
            emit("xori a0, a0, 1");
        } else {
            throw error("can't use a " + from + " value where " + to + " is expected", at);
        }
    }

    private void toIndex(T type, Lexeme at) {
        if (type == T.DOS) emit("fcvt.l.d a0, fa0, rtz");
        else if (type != T.INT && type != T.UNKNOWN) throw error("matrix index must be a number", at);
    }

    private void checkIntDivisor() {
        String ok = newLabel();
        emit("bnez a1, " + ok);
        emit("call rtDivZero");
        label(ok);
    }

    private void checkDoubleDivisor(String register) {
        String ok = newLabel();
        emit("fmv.d.x ft0, zero");
        emit("feq.d t0, " + register + ", ft0");
        emit("beqz t0, " + ok);
        emit("call rtDivZero");
        label(ok);
    }

    private T genList(Lexeme list) {
        T last = T.VOID;
        for (Lexeme statement : list.children) {
            last = genStmt(statement);
        }
        return last;
    }

    private T genStmt(Lexeme node) {
        if (node == null) return T.VOID;
        switch (node.getType()) {
            case statementList:
                return genList(node);
            case block:
                genBlock(node);
                return T.VOID;
            case varInitialization:
                return genVarInit(node);
            case varAssignment: {
                Var variable = lookup(node.getChild(0));
                T type = genExpr(node.getChild(2));
                if (variable == null) return T.UNKNOWN;
                if (type == T.FUNC) {
                    learn(variable, lastSig, node.getChild(0));
                    variable.formula = lastFormula;
                    variable.definition = lastDefinition;
                }
                if (variable.fieldSlot >= 0 && variable.type == T.UNKNOWN) {
                    recordField(variable.name, type);
                    variable.type = fieldTypes.getOrDefault(variable.name, T.UNKNOWN);
                }
                convert(type, variable.type, node.getChild(0));
                store(variable);
                return variable.type;
            }
            case fieldAssignment:
                return genFieldAssign(node);
            case functionDefinitionActual:
                genFunctionDef(node, null);
                return T.VOID;
            case classDefinition:
                genClassDef(node);
                return T.VOID;
            case returnStatement:
                genReturn(node);
                return T.VOID;
            case IF:
                genIf(node);
                return T.VOID;
            case foorLoopBody:
                genFor(node);
                return T.VOID;
            case forEachBody:
                genForEach(node);
                return T.VOID;
            case whileL:
                genWhile(node.getChild(0).getChild(1), null, node.getChild(1));
                return T.VOID;
            case whilePercent: {
                Lexeme body = node.getChild(0);
                genWhile(body.getChild(1), body.getChild(2), body.getChild(4));
                return T.VOID;
            }
            case whileSortOfBody:
                genWhile(node.getChild(1), node.getChild(2), node.getChild(4));
                return T.VOID;
            case indefPreformBody:
                genWhile(null, null, node.getChild(1));
                return T.VOID;
            case breakstatement:
                if (cur.breaks.isEmpty()) throw error("break outside of a loop", node);
                emit("j " + cur.breaks.peek());
                return T.VOID;
            case collectionInitialization:
                return genCollectionInit(node);
            case MatrixAssi:
                genMatrixAssign(node);
                return T.VOID;
            case ERROR:
            case empty:
                return T.VOID;
            case funcCall:
            case methodCall:
            case fieldAccess:
            case binaryExpression:
            case unaryExpression:
            case primary:
            case parentheziedExpreission:
            case matrixGetter:
            case arrayGetter:
            case arrylistGetter:
                return genExpr(node);
            default:
                throw error("the compiler doesn't support " + node.getType() + " statements yet", node);
        }
    }

    private void genBlock(Lexeme block) {
        scope = new Scope(scope, cur);
        if (block.getChildrenSize() > 0) genList(block.getChild(0));
        scope = scope.parent;
    }

    private T genVarInit(Lexeme node) {
        T declared = typeOf(node.getChild(2));
        if (declared == T.VOID) throw error("variable needs a type", node.getChild(1));
        T type = genExpr(node.getChild(4));
        Sig sig = type == T.FUNC ? lastSig : null;
        String formula = type == T.FUNC ? lastFormula : null;
        Lexeme definition = type == T.FUNC ? lastDefinition : null;
        convert(type, declared, node.getChild(1));
        for (Lexeme identifier : node.getChild(1).children) {
            Var variable = declare(identifier, declared);
            if (declared == T.FUNC) {
                learn(variable, sig, identifier);
                variable.formula = formula;
                variable.definition = definition;
            }
            store(variable);
        }
        if (declared == T.FUNC) {
            lastSig = sig;
            lastFormula = formula;
            lastDefinition = definition;
        }
        return declared;
    }

    private T genFieldAssign(Lexeme node) {
        Lexeme receiver = node.getChild(0);
        Lexeme member = node.getChild(1);
        String field = member.getStringValue();
        T valueType = genExpr(node.getChild(3));
        if (valueType == T.FUNC) fieldSigs.put(field, merge(fieldSigs.get(field), lastSig, member));

        if ("this".equals(receiver.getStringValue()) && cur.cls != null && cur.cls.fields.add(field)) changed = true;
        recordField(field, valueType);
        T fieldType = fieldTypes.getOrDefault(field, T.UNKNOWN);
        convert(valueType, fieldType, member);
        push(fieldType);

        T receiverType = genExpr(receiver);
        if (receiverType != T.OBJ && receiverType != T.UNKNOWN) throw error("field assignment needs an object", receiver);
        emit("mv t1, a0");
        popValue(fieldType);
        emit((fieldType == T.DOS ? "fsd fa0, " : "sd a0, ") + (8 + 8 * slot(field)) + "(t1)");
        return fieldType;
    }

    private void genClassDef(Lexeme node) {
        String name = node.getChild(1).getStringValue();
        ClassInfo info = classes.get(name);
        if (info == null) {
            info = new ClassInfo();
            info.name = name;
            info.id = classes.size() + 1;
            classes.put(name, info);
            changed = true;
        }
        Lexeme body = node.getChild(2);
        List<Lexeme> members = body.getChildrenSize() > 0 ? body.getChild(0).children : new ArrayList<>();

        Fn outerFn = cur;
        Scope outerScope = scope;
        cur = new Fn(classLabel(name) + "New", T.OBJ, false, newLabel());
        cur.cls = info;
        scope = new Scope(outerScope, cur);
        cur.thisVar = declareName("this", T.OBJ, node.getChild(1));
        emit("la t0, objSize");
        emit("ld a0, 0(t0)");
        emit("call rtAlloc");
        emit("li t0, " + info.id);
        emit("sd t0, 0(a0)");
        store(cur.thisVar);

        for (Lexeme member : members) {
            if (member.getType() != Types.varInitialization) continue;
            T declared = typeOf(member.getChild(2));
            if (declared == T.VOID) throw error("field needs a type", member.getChild(1));
            for (Lexeme identifier : member.getChild(1).children) {
                if (info.fields.add(identifier.getStringValue())) changed = true;
                recordField(identifier.getStringValue(), declared);
            }
            convert(genExpr(member.getChild(4)), declared, member.getChild(1));
            for (Lexeme identifier : member.getChild(1).children) {
                Var field = fieldVar(identifier.getStringValue());
                convert(declared, field.type, identifier);
                store(field);
            }
        }
        load(cur.thisVar);
        functions.append(finish(cur));
        cur = outerFn;
        scope = outerScope;

        for (Lexeme member : members) {
            if (member.getType() == Types.functionDefinitionActual) genFunctionDef(member, info);
        }
    }

    private void genFunctionDef(Lexeme node, ClassInfo owner) {
        Lexeme identifier = node.getChild(0);
        String name = identifier.getStringValue();

        if (owner != null) {
            Func function = makeFunc(node, methodLabel(owner.name, name));
            Func previous = owner.methods.get(name);
            if (previous == null || !sameSignature(previous, function)) changed = true;
            owner.methods.put(name, function);
            compileFunction(node, function, owner, false);
            return;
        }

        if (cur.isMain && scope == globalScope) {
            if (scope.funcs.containsKey(name)) throw error("function '" + name + "' is already defined", identifier);
            Func function = makeFunc(node, fnLabel(name));
            scope.funcs.put(name, function);
            Func previous = knownFuncs.get(name);
            if (previous == null || !sameSignature(previous, function)) changed = true;
            knownFuncs.put(name, function);
            compileFunction(node, function, null, false);
            return;
        }

        Func function = makeFunc(node, cur.label + capitalized(camel(name)) + (labelCount++));
        Var holder = declare(identifier, T.FUNC);
        learn(holder, function.sig, identifier);
        holder.formula = function.formula;
        holder.definition = function.definition;
        compileFunction(node, function, null, true);
        emitClosure(function.label);
        store(holder);
    }

    private Func makeFunc(Lexeme node, String label) {
        Func function = new Func();
        function.sig = sigOf(node);
        function.ret = function.sig.ret;
        function.paramTypes = function.sig.params;
        function.label = label;
        function.definition = node;
        try {
            function.formula = Symbolic.formula(Symbolic.shapeOfDefinition(node).expression());
        } catch (RuntimeException ignored) {
        }
        return function;
    }

    private T genLambda(Lexeme node) {
        Lexeme definition = node.getChild(0);
        Func function = makeFunc(definition, cur.label + "Lambda" + (labelCount++));
        compileFunction(definition, function, null, true);
        emitClosure(function.label);
        lastSig = function.sig;
        lastFormula = function.formula;
        lastDefinition = function.definition;
        return T.FUNC;
    }

    private void compileFunction(Lexeme node, Func function, ClassInfo owner, boolean hasEnv) {
        Lexeme identifier = node.getChild(0);
        Lexeme parameters = node.getChild(3);

        Fn outerFn = cur;
        Scope outerScope = scope;
        cur = new Fn(function.label, function.ret, false, newLabel());
        cur.parent = outerFn;
        cur.hasEnv = hasEnv;
        cur.sig = function.sig;
        cur.heapFrame = containsClosure(node.getChild(4));
        cur.cls = owner != null ? owner : hasEnv ? outerFn.cls : null;
        scope = new Scope(outerScope, cur);
        if (!cur.heapFrame) planRegisters(List.of(node.getChild(4)), parameters.children);

        int intRegister = 0, floatRegister = 0;
        if (owner != null) {
            cur.thisVar = declareName("this", T.OBJ, identifier);
            emit("sd a0, " + address(cur.thisVar));
            intRegister = 1;
        } else if (cur.cls != null) {
            cur.thisVar = outerFn.thisVar;
        }
        int parameterIndex = 0;
        for (Lexeme parameter : parameters.children) {
            T type = typeOf(parameter.getChild(0));
            Var variable = declare(parameter.getChild(1), type);
            variable.paramOwner = function.sig;
            variable.paramIndex = parameterIndex++;
            if (type == T.DOS) {
                if (floatRegister > 7) throw error("too many dos parameters", identifier);
                String from = "fa" + (floatRegister++);
                emit(variable.reg != null ? "fmv.d " + variable.reg + ", " + from : "fsd " + from + ", " + address(variable));
            } else {
                if (intRegister > 7) throw error("too many parameters", identifier);
                String from = "a" + (intRegister++);
                emit(variable.reg != null ? "mv " + variable.reg + ", " + from : "sd " + from + ", " + address(variable));
            }
        }

        Lexeme body = node.getChild(4);
        T last = body.getChildrenSize() > 0 ? genList(body.getChild(0)) : T.VOID;
        boolean numeric = last == T.INT || last == T.DOS || last == T.BOOL;
        if (function.ret != T.VOID && last != function.ret && numeric) {
            convert(last, function.ret, identifier);
        }

        functions.append(finish(cur));
        cur = outerFn;
        scope = outerScope;
    }

    private void genReturn(Lexeme node) {
        Lexeme expression = node.getChild(1);
        Lexeme voidNode = strip(expression);
        boolean isVoid = voidNode.getType() == Types.IDENTIFIER && "void".equals(voidNode.getStringValue());
        if (cur.isMain) {
            if (!isVoid) {
                T type = genExpr(expression);
                push(type);
                emit("la a0, " + stringConst("Program result: "));
                emit("call printStr");
                popValue(type);
                genPrint(type, expression);
                emit("la a0, " + stringConst("\n"));
                emit("call printStr");
            }
            emit("li a0, 0");
            emit("j rtExit");
            return;
        }
        if (!isVoid) {
            T type = genExpr(expression);
            if (type == T.FUNC && cur.ret == T.FUNC && cur.sig != null) {
                cur.sig.retSig = merge(cur.sig.retSig, lastSig, expression);
            }
            if (cur.ret != T.VOID) convert(type, cur.ret, expression);
        }
        emit("j " + cur.exitLabel);
    }

    private void genCondition(Lexeme condition, String falseLabel) {
        T type = genExpr(condition);
        convert(type, T.BOOL, condition);
        emit("beqz a0, " + falseLabel);
    }

    private void genIf(Lexeme node) {
        String end = newLabel();
        int count = node.getChildrenSize();
        int i = 0;
        while (i + 1 < count) {
            String next = newLabel();
            genCondition(node.getChild(i), next);
            genStmt(node.getChild(i + 1));
            emit("j " + end);
            label(next);
            i += 2;
        }
        if (i < count) genStmt(node.getChild(i));
        label(end);
    }

    private void genFor(Lexeme node) {
        scope = new Scope(scope, cur);
        Lexeme init = node.getChild(1);
        genVarInit(init);
        Var counter = lookup(init.getChild(1).getChild(0));
        boolean up = node.getChild(3).getType() == Types.LOOPINCREMENTPLUS;

        String start = newLabel(), end = newLabel();
        label(start);
        genCondition(node.getChild(2), end);
        cur.breaks.push(end);
        genStmt(node.getChild(4));
        cur.breaks.pop();

        load(counter);
        if (counter.type == T.DOS) {
            loadDouble("ft0", 1.0);
            emit((up ? "fadd.d" : "fsub.d") + " fa0, fa0, ft0");
        } else {
            emit("addi a0, a0, " + (up ? 1 : -1));
        }
        store(counter);
        emit("j " + start);
        label(end);
        scope = scope.parent;
    }

    private void genForEach(Lexeme node) {
        Lexeme collectionName = node.getChild(3);
        Var collection = lookup(collectionName);
        T collectionType = collection == null ? T.UNKNOWN : collection.type;
        T itemType = collectionType == T.MAT ? T.DOS
                : isList(collectionType) ? elementOf(collectionType) : T.UNKNOWN;
        if (itemType == T.UNKNOWN && !lenient) throw error("foreach needs a collection", collectionName);

        scope = new Scope(scope, cur);
        Var item = declare(node.getChild(1), itemType);
        if (collection != null) item.sigKey = collection.sigKey;
        Var index = declareName(" index", T.INT, collectionName);
        emit("sd zero, " + address(index));

        String start = newLabel(), end = newLabel();
        label(start);
        if (collection != null) {
            load(index);
            emit("mv t3, a0");
            load(collection);
            emit("ld t2, 0(a0)");
            if (collectionType == T.MAT) {
                emit("ld t4, 8(a0)");
                emit("mul t2, t2, t4");
            }
            emit("bge t3, t2, " + end);
            emit("slli t3, t3, 3");
            emit("add t3, t3, a0");
            if (collectionType == T.MAT) emit("fld fa0, 16(t3)");
            else emit(itemType == T.DOS ? "fld fa0, 8(t3)" : "ld a0, 8(t3)");
            store(item);
        }
        cur.breaks.push(end);
        genStmt(node.getChild(4));
        cur.breaks.pop();
        load(index);
        emit("addi a0, a0, 1");
        store(index);
        emit("j " + start);
        label(end);
        scope = scope.parent;
    }

    private void genWhile(Lexeme condition, Lexeme percent, Lexeme block) {
        String start = newLabel(), end = newLabel();
        label(start);
        if (condition != null) {
            T type = genExpr(condition);
            if (percent == null || type == T.BOOL || type == T.UNKNOWN) {
                convert(type, T.BOOL, condition);
            } else {
                double tolerance = (percent.getType() == Types.DOS ? percent.getDosValue() : percent.getIntValue()) / 100.0;
                convert(type, T.DOS, condition);
                loadDouble("ft0", 1.0);
                emit("fsub.d fa0, fa0, ft0");
                emit("fabs.d fa0, fa0");
                loadDouble("ft1", tolerance);
                emit("fle.d a0, fa0, ft1");
            }
            emit("beqz a0, " + end);
        }
        cur.breaks.push(end);
        genStmt(block);
        cur.breaks.pop();
        emit("j " + start);
        label(end);
    }

    private T genCollectionInit(Lexeme node) {
        Types kind = node.getChild(0).getType();
        Lexeme identifier = node.getChild(1);
        Lexeme helper = node.getChild(3);
        boolean isHelper = helper.getType() == Types.collectionHelper;

        if (kind == Types.matrix) {
            if (isHelper && helper.getChildrenSize() == 4) {
                toIndex(genExpr(helper.getChild(3)), identifier);
                push(T.INT);
                toIndex(genExpr(helper.getChild(1)), identifier);
                emit("mv a1, a0");
                pop("a0");
                emit("call matAlloc");
            } else {
                Lexeme expression = isHelper ? helper.getChild(helper.getChildrenSize() - 1) : helper;
                T type = genExpr(expression);
                if (type != T.MAT && type != T.UNKNOWN) throw error("expected a matrix value", identifier);
            }
            store(declare(identifier, T.MAT));
            return T.MAT;
        }

        T elementType = typeOf(node.getChild(2));
        if (elementType == T.VOID) throw error("collection needs an element type", identifier);
        T listType = T.valueOf("LIST_" + elementType.name());
        int count = isHelper ? helper.getChildrenSize() : -1;
        if (count == 2) {
            genListValue(helper.getChild(1), elementType, listType, identifier);
        } else if (count == 1 && helper.getChild(0).getType() == Types.INTERGER) {
            int size = helper.getChild(0).getIntValue();
            emit("li a0, " + (8 + 8 * size));
            emit("call rtAlloc");
            emit("li t0, " + size);
            emit("sd t0, 0(a0)");
        } else if (count == 0) {
            emit("li a0, 8");
            emit("call rtAlloc");
        } else {
            genListValue(isHelper ? helper.getChild(count - 1) : helper, elementType, listType, identifier);
        }
        store(declare(identifier, listType));
        return listType;
    }

    private void genListValue(Lexeme expression, T elementType, T listType, Lexeme at) {
        Lexeme literal = strip(expression);
        if (literal.getType() != Types.array && literal.getType() != Types.linkedList) {
            T type = genExpr(expression);
            if (type != listType && type != T.UNKNOWN) throw error("expected a " + listType + " value", at);
            return;
        }
        List<Lexeme> items = literal.getChild(0).children;
        emit("li a0, " + (8 + 8 * items.size()));
        emit("call rtAlloc");
        emit("li t0, " + items.size());
        emit("sd t0, 0(a0)");
        push(listType);
        Object key = at;
        if (cur.isMain && scope == globalScope) key = globalLabel(at.getStringValue());
        for (int i = 0; i < items.size(); i++) {
            T itemType = genExpr(items.get(i));
            if (itemType == T.FUNC && elementType == T.FUNC) {
                varSigs.put(key, merge(varSigs.get(key), lastSig, items.get(i)));
            }
            convert(itemType, elementType, items.get(i));
            emit("ld t1, 0(sp)");
            emit("li t2, " + (8 + 8 * i));
            emit("add t1, t1, t2");
            emit(elementType == T.DOS ? "fsd fa0, 0(t1)" : "sd a0, 0(t1)");
        }
        pop("a0");
    }

    private T genListGet(Lexeme node) {
        Var list = lookup(node.getChild(1));
        if (list == null) return T.UNKNOWN;
        if (!isList(list.type)) throw error("'" + node.getChild(1).getStringValue() + "' is not a list", node.getChild(1));
        T elementType = elementOf(list.type);
        String ok = newLabel();
        load(list);
        emit("li t1, " + node.getChild(2).getIntValue());
        emit("ld t2, 0(a0)");
        emit("bltu t1, t2, " + ok);
        emit("call rtBounds");
        label(ok);
        emit("slli t1, t1, 3");
        emit("add t1, t1, a0");
        emit(elementType == T.DOS ? "fld fa0, 8(t1)" : "ld a0, 8(t1)");
        if (elementType == T.FUNC) lastSig = getSig(list);
        return elementType;
    }

    private void genIndexes(Lexeme row, Lexeme column) {
        toIndex(genExpr(row), row);
        push(T.INT);
        toIndex(genExpr(column), column);
        push(T.INT);
    }

    private void genMatrixAssign(Lexeme node) {
        if (node.getChildrenSize() != 5) {
            throw error("matrix assignment needs exactly [[row, column]]", node.getChild(0));
        }
        Var matrix = lookup(node.getChild(0));
        if (matrix != null && matrix.type != T.MAT && matrix.type != T.UNKNOWN) {
            throw error("'" + node.getChild(0).getStringValue() + "' is not a matrix", node.getChild(0));
        }
        genIndexes(node.getChild(1), node.getChild(2));
        convert(genExpr(node.getChild(4)), T.DOS, node.getChild(0));
        pop("a2");
        pop("a1");
        if (matrix == null) return;
        load(matrix);
        matrixCell();
        emit("fsd fa0, 16(t1)");
    }

    private T genExpr(Lexeme node) {
        switch (node.getType()) {
            case primary:
            case parentheziedExpreission:
                return genExpr(node.getChild(0));
            case binaryExpression:
                if (node.getChildrenSize() == 1) return genExpr(node.getChild(0));
                return climb(node.children, new int[]{0}, 0);
            case unaryExpression:
                return genUnary(node);
            case INTERGER:
                emit("li a0, " + node.getIntValue());
                return T.INT;
            case DOS:
                loadDouble("fa0", node.getDosValue());
                return T.DOS;
            case GEORGE:
                emit("li a0, " + (node.getBooleanValue() ? 1 : 0));
                return T.BOOL;
            case STRING:
                emit("la a0, " + stringConst(node.getStringValue()));
                return T.STR;
            case IDENTIFIER: {
                Var variable = tryLookup(node);
                if (variable == null) {
                    Func function = findFunc(node.getStringValue());
                    if (function != null && function.sig != null) {
                        if (staticClosures.add(function.label)) {
                            data.append("    .balign 8\n").append(cloLabel(function.label))
                                    .append(": .quad ").append(function.label).append(", 0\n");
                        }
                        emit("la a0, " + cloLabel(function.label));
                        lastSig = function.sig;
                        lastFormula = function.formula;
                        lastDefinition = function.definition;
                        return T.FUNC;
                    }
                    if (lenient) return T.UNKNOWN;
                    throw error("'" + node.getStringValue() + "' is undefined", node);
                }
                load(variable);
                if (variable.type == T.FUNC) {
                    lastSig = getSig(variable);
                    lastFormula = variable.formula;
                    lastDefinition = variable.definition;
                }
                return variable.type;
            }
            case funcCall:
                return genCall(node);
            case methodCall:
                return genMethodCall(node);
            case fieldAccess: {
                T receiverType = genExpr(node.getChild(0));
                if (receiverType != T.OBJ && receiverType != T.UNKNOWN) {
                    throw error("field access needs an object", node.getChild(0));
                }
                String field = node.getChild(1).getStringValue();
                T fieldType = fieldTypes.get(field);
                if (fieldType == null) {
                    if (lenient) return T.UNKNOWN;
                    throw error("no class ever gives '" + field + "' a value", node.getChild(1));
                }
                emit((fieldType == T.DOS ? "fld fa0, " : "ld a0, ") + (8 + 8 * slot(field)) + "(a0)");
                if (fieldType == T.FUNC) lastSig = fieldSigs.get(field);
                return fieldType;
            }
            case lambda:
                return genLambda(node);
            case matrixGetter: {
                if (node.getChildrenSize() != 4) throw error("GET needs [[row, column]]", node.getChild(1));
                Var matrix = lookup(node.getChild(1));
                if (matrix != null && matrix.type != T.MAT && matrix.type != T.UNKNOWN) {
                    throw error("'" + node.getChild(1).getStringValue() + "' is not a matrix", node.getChild(1));
                }
                genIndexes(node.getChild(2), node.getChild(3));
                pop("a2");
                pop("a1");
                if (matrix == null) return T.DOS;
                load(matrix);
                matrixCell();
                emit("fld fa0, 16(t1)");
                return T.DOS;
            }
            case arrayGetter:
            case arrylistGetter:
                return genListGet(node);
            case matrix:
                return genMatrixLiteral(node);
            case array:
            case linkedList:
                throw error("list literals only work in a collection declaration", node);
            default:
                throw error("the compiler doesn't support " + node.getType() + " in expressions yet", node);
        }
    }

    private int precedence(Types operator) {
        switch (operator) {
            case PIPE:
                return 0;
            case OR:
                return 1;
            case AND:
                return 2;
            case EQUALS:
            case NOT_EQUAL:
                return 3;
            case LESS:
            case GREATER:
            case LESS_EQ:
            case GREATER_EQ:
                return 4;
            case PLUS:
            case MINUS:
                return 5;
            case MULTIPLY:
            case DIVIDE:
            case MOD:
            case DOT_PRODUCT:
                return 6;
            case EXPONENTIATE:
                return 7;
            default:
                return -1;
        }
    }

    private T climb(List<Lexeme> items, int[] position, int minimum) {
        T left = genExpr(items.get(position[0]++));
        while (position[0] < items.size()) {
            Lexeme operator = items.get(position[0]);
            int level = precedence(operator.getType());
            if (level < 0) throw error("expected a binary operator", operator);
            if (level < minimum) break;
            position[0]++;
            if (position[0] >= items.size()) throw error("operator is missing its right side", operator);
            push(left);
            T right = climb(items, position, level + 1);
            left = applyBinary(left, operator, right);
        }
        return left;
    }

    private void toDoubles(T left, T right) {
        emit(right == T.DOS ? "fmv.d fa1, fa0" : "fcvt.d.l fa1, a0");
        if (left == T.DOS) {
            pop("fa0");
        } else {
            pop("a0");
            emit("fcvt.d.l fa0, a0");
        }
    }

    private void toInts() {
        emit("mv a1, a0");
        pop("a0");
    }

    private T genEquality(T left, T right, boolean negate) {
        if (isNumber(left) && isNumber(right)) {
            if (left == T.DOS || right == T.DOS) {
                toDoubles(left, right);
                emit("feq.d a0, fa0, fa1");
            } else {
                toInts();
                emit("xor a0, a0, a1");
                emit("seqz a0, a0");
            }
        } else if (left == T.STR && right == T.STR) {
            toInts();
            emit("call rtStrEq");
        } else if (left == T.MAT && right == T.MAT) {
            toInts();
            emit("call fnRtMatEq");
        } else if (left == right) {
            toInts();
            emit("xor a0, a0, a1");
            emit("seqz a0, a0");
        } else {
            pop(left == T.DOS ? "fa1" : "a1");
            emit("li a0, 0");
        }
        if (negate) emit("xori a0, a0, 1");
        return T.BOOL;
    }

    private T applyBinary(T left, Lexeme operator, T right) {
        Types op = operator.getType();
        boolean comparison = op == Types.EQUALS || op == Types.NOT_EQUAL || op == Types.LESS || op == Types.GREATER
                || op == Types.LESS_EQ || op == Types.GREATER_EQ || op == Types.AND || op == Types.OR;
        if (left == T.UNKNOWN || right == T.UNKNOWN) {
            pop("a1");
            return comparison ? T.BOOL : T.UNKNOWN;
        }
        RuntimeException mismatch = error("can't apply " + op + " to " + left + " and " + right, operator);
        if (left == T.VOID || right == T.VOID) throw mismatch;

        if (op == Types.PIPE) {
            Sig signature = lastSig;
            if (right != T.FUNC || signature == null) {
                popValue(left);
                if (lenient) return T.UNKNOWN;
                throw error("the right side of |> needs a known one-argument function", operator);
            }
            if (signature.params.size() != 1) {
                popValue(left);
                throw error("the right side of |> needs exactly one parameter", operator);
            }
            emit("mv t5, a0");
            popValue(left);
            convert(left, signature.params.get(0), operator);
            emit("ld t6, 8(t5)");
            emit("ld t5, 0(t5)");
            emit("jalr t5");
            lastSig = signature.retSig;
            lastFormula = null;
            lastDefinition = null;
            return signature.ret;
        }

        if (op == Types.EQUALS || op == Types.NOT_EQUAL) return genEquality(left, right, op == Types.NOT_EQUAL);

        if (left == T.MAT || right == T.MAT) {
            if (left == T.MAT && right == T.MAT) {
                String routine;
                if (op == Types.PLUS) routine = "matAdd";
                else if (op == Types.MINUS) routine = "matSub";
                else if (op == Types.MULTIPLY) routine = "matMul";
                else if (op == Types.DOT_PRODUCT) routine = "fnRtMatDot";
                else throw mismatch;
                toInts();
                emit("call " + routine);
                return T.MAT;
            }
            int code = -1;
            if (op == Types.PLUS) code = 0;
            else if (op == Types.MINUS) code = 1;
            else if (op == Types.MULTIPLY) code = 2;
            else if (op == Types.DIVIDE) code = 3;
            if (left == T.MAT && isNumber(right) && code >= 0) {
                if (right == T.INT) emit("fcvt.d.l fa0, a0");
                pop("a0");
                emit("li a1, " + code);
                emit("li a2, 0");
                emit("call fnRtMatScale");
                return T.MAT;
            }
            if (right == T.MAT && isNumber(left) && code >= 0 && code != 3) {
                emit("mv t0, a0");
                if (left == T.DOS) {
                    pop("fa0");
                } else {
                    pop("a0");
                    emit("fcvt.d.l fa0, a0");
                }
                emit("mv a0, t0");
                emit("li a1, " + code);
                emit("li a2, 1");
                emit("call fnRtMatScale");
                return T.MAT;
            }
            throw mismatch;
        }

        if (op == Types.AND || op == Types.OR) {
            convert(right, T.BOOL, operator);
            emit("mv a1, a0");
            popValue(left);
            convert(left, T.BOOL, operator);
            emit((op == Types.AND ? "and" : "or") + " a0, a0, a1");
            return T.BOOL;
        }

        if (left == T.STR || right == T.STR) {
            throw error("joining strings with + isn't supported by the compiler yet", operator);
        }
        if (!isNumber(left) || !isNumber(right)) throw mismatch;
        boolean bothInt = left == T.INT && right == T.INT;

        switch (op) {
            case EXPONENTIATE:
                if (bothInt) {
                    toInts();
                    emit("call powInt");
                    return T.INT;
                }
                toDoubles(left, right);
                emit("call fnRtPow");
                return T.DOS;
            case MOD:
                if (!bothInt) throw mismatch;
                toInts();
                checkIntDivisor();
                emit("rem a0, a0, a1");
                return T.INT;
            case DIVIDE:
                if (bothInt) {
                    toInts();
                    checkIntDivisor();
                    emit("div a0, a0, a1");
                    return T.INT;
                }
                toDoubles(left, right);
                checkDoubleDivisor("fa1");
                emit("fdiv.d fa0, fa0, fa1");
                return T.DOS;
            default:
                break;
        }

        boolean arithmetic = op == Types.PLUS || op == Types.MINUS || op == Types.MULTIPLY;
        if (!bothInt) {
            toDoubles(left, right);
            if (op == Types.PLUS) emit("fadd.d fa0, fa0, fa1");
            else if (op == Types.MINUS) emit("fsub.d fa0, fa0, fa1");
            else if (op == Types.MULTIPLY) emit("fmul.d fa0, fa0, fa1");
            else if (op == Types.LESS) emit("flt.d a0, fa0, fa1");
            else if (op == Types.GREATER) emit("flt.d a0, fa1, fa0");
            else if (op == Types.LESS_EQ) emit("fle.d a0, fa0, fa1");
            else if (op == Types.GREATER_EQ) emit("fle.d a0, fa1, fa0");
            else throw mismatch;
            return arithmetic ? T.DOS : T.BOOL;
        }

        toInts();
        if (op == Types.PLUS) {
            emit("add a0, a0, a1");
        } else if (op == Types.MINUS) {
            emit("sub a0, a0, a1");
        } else if (op == Types.MULTIPLY) {
            emit("mul a0, a0, a1");
        } else if (op == Types.LESS) {
            emit("slt a0, a0, a1");
        } else if (op == Types.GREATER) {
            emit("slt a0, a1, a0");
        } else if (op == Types.LESS_EQ) {
            emit("slt a0, a1, a0");
            emit("xori a0, a0, 1");
        } else if (op == Types.GREATER_EQ) {
            emit("slt a0, a0, a1");
            emit("xori a0, a0, 1");
        } else {
            throw mismatch;
        }
        return arithmetic ? T.INT : T.BOOL;
    }

    private T genUnary(Lexeme node) {
        if (node.getChildrenSize() == 1) return genExpr(node.getChild(0));
        Lexeme operator = node.getChild(0);
        T type = genExpr(node.getChild(1));
        switch (operator.getType()) {
            case MINUS:
            case MINUS_UNARY:
                if (type == T.INT) emit("neg a0, a0");
                else if (type == T.DOS) emit("fneg.d fa0, fa0");
                else if (type != T.UNKNOWN) throw error("can't negate a " + type, operator);
                return type;
            case NOT:
                convert(type, T.BOOL, operator);
                emit("xori a0, a0, 1");
                return T.BOOL;
            case INVERSE:
                if (type == T.MAT) {
                    emit("call fnRtMatInverse");
                    return T.MAT;
                }
                convert(type, T.DOS, operator);
                checkDoubleDivisor("fa0");
                loadDouble("ft0", 1.0);
                emit("fdiv.d fa0, ft0, fa0");
                return T.DOS;
            default:
                throw error("the compiler doesn't support the " + operator.getType() + " operator yet", operator);
        }
    }

    private void genPrint(T type, Lexeme at) {
        if (type == T.INT) {
            emit("call printInt");
        } else if (type == T.DOS) {
            emit("call printDos");
        } else if (type == T.BOOL) {
            emit("call printBool");
        } else if (type == T.STR) {
            emit("call printStr");
        } else if (type == T.MAT) {
            emit("call matPrint");
        } else if (type == T.FUNC) {
            emit("la a0, " + stringConst("function"));
            emit("call printStr");
        } else if (type == T.OBJ) {
            emit("ld t0, 0(a0)");
            emit("slli t0, t0, 3");
            emit("la t1, classNames");
            emit("add t1, t1, t0");
            emit("ld a0, 0(t1)");
            emit("call printStr");
        } else if (type != T.UNKNOWN) {
            throw error("the compiler can't print a " + type + " yet", at);
        }
    }

    private void marshal(List<Lexeme> arguments, List<T> wanted, int firstIntRegister, Lexeme at) {
        marshal(arguments, wanted, null, firstIntRegister, at);
    }

    private void marshal(List<Lexeme> arguments, List<T> wanted, Sig sig, int firstIntRegister, Lexeme at) {
        if (arguments.size() != wanted.size()) {
            String name = at.getType() == Types.IDENTIFIER ? "'" + at.getStringValue() + "'" : "this function";
            throw error(name + " expects " + wanted.size() + " arguments but got " + arguments.size(), at);
        }
        String[] registers = new String[arguments.size()];
        int intRegister = firstIntRegister, floatRegister = 0;
        for (int i = 0; i < arguments.size(); i++) {
            T want = wanted.get(i);
            T got = genExpr(arguments.get(i));
            if (got == T.FUNC && want == T.FUNC && sig != null) {
                sig.paramSigs[i] = merge(sig.paramSigs[i], lastSig, arguments.get(i));
            }
            convert(got, want, arguments.get(i));
            push(want);
            registers[i] = want == T.DOS ? "fa" + (floatRegister++) : "a" + (intRegister++);
        }
        for (int i = arguments.size() - 1; i >= 0; i--) {
            pop(registers[i]);
        }
    }

    private T genCall(Lexeme node) {
        Lexeme identifier = node.getChild(0);
        List<Lexeme> arguments = node.getChild(1).children;
        if (identifier.getType() != Types.IDENTIFIER) {
            T calleeType = genExpr(identifier);
            return callValue(calleeType, lastSig, arguments, node);
        }
        String name = identifier.getStringValue();

        switch (name) {
            case "print":
                for (Lexeme argument : arguments) genPrint(genExpr(argument), argument);
                return T.VOID;
            case "random":
                if (arguments.isEmpty()) {
                    emit("call fnRtRandom01");
                } else {
                    marshal(arguments, List.of(T.DOS, T.DOS), 0, identifier);
                    emit("call fnRtRandom");
                }
                return T.DOS;
            case "sin":
            case "cos":
                if (arguments.size() != 1) throw error("'" + name + "' expects one argument", identifier);
                T trigType = genExpr(arguments.get(0));
                if (trigType == T.MAT) {
                    emit("call " + fnLabel("rt_mat_" + name));
                    return T.MAT;
                }
                convert(trigType, T.DOS, arguments.get(0));
                emit("call " + fnLabel("rt_" + name));
                return T.DOS;
            case "eye":
                marshal(arguments, List.of(T.INT), 0, identifier);
                emit("call fnRtEye");
                return T.MAT;
            case "tabulate":
                return genTabulate(arguments, identifier);
            case "det":
                marshal(arguments, List.of(T.MAT), 0, identifier);
                emit("call fnRtMatDet");
                return T.DOS;
            case "diff":
                return genSymbolic(arguments, identifier, true);
            case "simplify":
                return genSymbolic(arguments, identifier, false);
            case "formula":
                return genFormula(arguments, identifier);
            case "solve":
                return genSolve(arguments, identifier);
            case "rt_fsqrt":
                marshal(arguments, List.of(T.DOS), 0, identifier);
                emit("fsqrt.d fa0, fa0");
                return T.DOS;
            case "rt_rows":
            case "rt_cols":
                marshal(arguments, List.of(T.MAT), 0, identifier);
                emit("ld a0, " + (name.equals("rt_rows") ? 0 : 8) + "(a0)");
                return T.INT;
            case "compose":
                if (findFunc(name) == null) return genCompose(arguments, identifier);
                break;
            case "Canvas":
                throw error("Canvas isn't supported by the compiler; run this one on the interpreter", identifier);
            default:
                break;
        }

        Var holder = tryLookup(identifier);
        if (holder != null && holder.type == T.FUNC) {
            load(holder);
            return callValue(T.FUNC, getSig(holder), arguments, identifier);
        }

        Func function = findFunc(name);
        if (function != null) {
            marshal(arguments, function.paramTypes, function.sig, 0, identifier);
            emit("call " + function.label);
            if (function.ret == T.FUNC) lastSig = function.sig == null ? null : function.sig.retSig;
            return function.ret;
        }

        ClassInfo info = classes.get(name);
        if (info != null) {
            emit("call " + classLabel(name) + "New");
            Func init = info.methods.get("init");
            if (init == null) {
                if (lenient) for (Lexeme argument : arguments) genExpr(argument);
                return T.OBJ;
            }
            push(T.OBJ);
            marshal(arguments, init.paramTypes, init.sig, 1, identifier);
            emit("ld a0, 0(sp)");
            emit("call " + init.label);
            pop("a0");
            return T.OBJ;
        }

        if (lenient) {
            for (Lexeme argument : arguments) genExpr(argument);
            return T.UNKNOWN;
        }
        throw error("function '" + name + "' is undefined", identifier);
    }

    private T genSymbolic(List<Lexeme> arguments, Lexeme at, boolean derivative) {
        if (arguments.size() != 1) throw error((derivative ? "diff" : "simplify") + " expects one function", at);
        Lexeme definition = definitionOf(arguments.get(0));
        if (definition == null) {
            throw error((derivative ? "diff" : "simplify")
                    + " needs a statically visible function or lambda", arguments.get(0));
        }
        try {
            Symbolic.FunctionShape shape = Symbolic.shapeOfDefinition(definition);
            Lexeme expression = derivative ? Symbolic.derivative(shape) : Symbolic.simplify(shape.expression());
            Lexeme generated = Symbolic.derivedDefinition(
                    derivative ? "diff" : "simplify", shape, expression, shape.returnType().getType());
            Func function = makeFunc(generated, cur.label + (derivative ? "Diff" : "Simplify")
                    + (labelCount++));
            compileFunction(generated, function, null, true);
            emitClosure(function.label);
            lastSig = function.sig;
            lastFormula = Symbolic.formula(expression);
            lastDefinition = generated;
            return T.FUNC;
        } catch (RuntimeException e) {
            throw error(e.getMessage() == null ? "could not transform this function" : e.getMessage(), at);
        }
    }

    private T genFormula(List<Lexeme> arguments, Lexeme at) {
        if (arguments.size() != 1) throw error("formula expects one function", at);
        T function = genExpr(arguments.get(0));
        if (function != T.FUNC) throw error("formula expects a function", arguments.get(0));
        String formula = lastFormula;
        if (formula == null && lastDefinition != null) {
            try {
                formula = Symbolic.formula(Symbolic.shapeOfDefinition(lastDefinition).expression());
            } catch (RuntimeException ignored) {
            }
        }
        if (formula == null) {
            throw error("formula needs a one-parameter function with one return expression", arguments.get(0));
        }
        emit("la a0, " + stringConst(formula));
        return T.STR;
    }

    private T genSolve(List<Lexeme> arguments, Lexeme at) {
        Func helper = findFunc("rt_solve");
        if (helper == null) throw error("the runtime library is missing rt_solve", at);
        marshal(arguments, helper.paramTypes, helper.sig, 0, at);
        emit("call " + helper.label);
        lastFormula = null;
        lastDefinition = null;
        return helper.ret;
    }

    private Lexeme definitionOf(Lexeme expression) {
        Lexeme node = strip(expression);
        if (node.getType() == Types.lambda) return node.getChild(0);
        if (node.getType() != Types.IDENTIFIER) return null;
        Var variable = tryLookup(node);
        if (variable != null) return variable.definition;
        Func function = findFunc(node.getStringValue());
        return function == null ? null : function.definition;
    }

    private T genTabulate(List<Lexeme> arguments, Lexeme at) {
        Func helper = findFunc("rt_tabulate");
        if (helper == null) throw error("the runtime library is missing rt_tabulate", at);
        marshal(arguments, helper.paramTypes, helper.sig, 0, at);
        emit("call " + helper.label);
        lastFormula = null;
        lastDefinition = null;
        return helper.ret;
    }

    private T genCompose(List<Lexeme> arguments, Lexeme at) {
        if (arguments.size() != 2) throw error("compose expects two functions", at);

        T firstType = genExpr(arguments.get(0));
        Sig first = lastSig;
        if (firstType != T.FUNC || first == null) {
            if (lenient) return T.UNKNOWN;
            throw error("compose needs a known function as its first argument", arguments.get(0));
        }
        push(T.FUNC);
        T secondType = genExpr(arguments.get(1));
        Sig second = lastSig;
        if (secondType != T.FUNC || second == null) {
            popValue(T.FUNC);
            if (lenient) return T.UNKNOWN;
            throw error("compose needs a known function as its second argument", arguments.get(1));
        }
        if (first.params.size() != 1 || second.params.size() != 1) {
            popValue(T.FUNC);
            throw error("compose currently needs one-argument functions", at);
        }
        if (first.params.get(0) != second.ret
                && !(isNumber(first.params.get(0)) && isNumber(second.ret))) {
            popValue(T.FUNC);
            throw error("the second function's result cannot be passed to the first", at);
        }

        String codeLabel = emitComposedFunction(first, second, at);
        push(T.FUNC);
        emit("li a0, 24");
        emit("call rtAlloc");
        pop("a1");
        pop("a2");
        emit("sd zero, 0(a0)");
        emit("sd a2, 8(a0)");
        emit("sd a1, 16(a0)");
        push(T.FUNC);
        emit("li a0, 16");
        emit("call rtAlloc");
        pop("t0");
        emit("la t1, " + codeLabel);
        emit("sd t1, 0(a0)");
        emit("sd t0, 8(a0)");

        Sig combined = new Sig();
        combined.params.addAll(second.params);
        combined.ret = first.ret;
        combined.paramSigs = second.paramSigs.clone();
        combined.retSig = first.retSig;
        lastSig = combined;
        lastFormula = null;
        lastDefinition = null;
        return T.FUNC;
    }

    private String emitComposedFunction(Sig first, Sig second, Lexeme at) {
        Fn outerFn = cur;
        Scope outerScope = scope;
        String codeLabel = cur.label + "Compose" + (labelCount++);
        Fn adapter = new Fn(codeLabel, first.ret, false, newLabel());
        adapter.parent = outerFn;
        adapter.hasEnv = true;
        adapter.sig = new Sig();
        adapter.sig.params.addAll(second.params);
        adapter.sig.ret = first.ret;
        adapter.sig.paramSigs = second.paramSigs.clone();
        adapter.sig.retSig = first.retSig;
        cur = adapter;
        scope = new Scope(outerScope, cur);

        T inputType = second.params.get(0);
        Var input = declareName(" compose input", inputType, at);
        emit((inputType == T.DOS ? "fsd fa0, " : "sd a0, ") + address(input));

        emit("ld t4, -24(s0)");
        emit("ld t5, 16(t4)");
        load(input);
        emit("ld t6, 8(t5)");
        emit("ld t5, 0(t5)");
        emit("jalr t5");
        convert(second.ret, first.params.get(0), at);
        push(first.params.get(0));
        emit("ld t4, -24(s0)");
        emit("ld t5, 8(t4)");
        popValue(first.params.get(0));
        emit("ld t6, 8(t5)");
        emit("ld t5, 0(t5)");
        emit("jalr t5");
        emit("j " + cur.exitLabel);

        functions.append(finish(cur));
        cur = outerFn;
        scope = outerScope;
        return codeLabel;
    }

    private T callValue(T calleeType, Sig sig, List<Lexeme> arguments, Lexeme at) {
        if (calleeType == T.FUNC && sig == null && inRuntime) {
            for (Lexeme argument : arguments) genExpr(argument);
            emit("call rtNoMethod");
            return T.UNKNOWN;
        }
        if (calleeType != T.FUNC || sig == null) {
            if (lenient) {
                for (Lexeme argument : arguments) genExpr(argument);
                return T.UNKNOWN;
            }
            if (calleeType != T.FUNC) throw error("tried to call something that is not a function", at);
            throw error("the compiler can't tell what kind of function is called here; "
                    + "it needs to see a function being stored or passed into this spot", at);
        }
        push(T.FUNC);
        marshal(arguments, sig.params, sig, 0, at);
        pop("t5");
        emit("ld t6, 8(t5)");
        emit("ld t5, 0(t5)");
        emit("jalr t5");
        lastSig = sig.retSig;
        return sig.ret;
    }

    private T genMethodCall(Lexeme node) {
        Lexeme method = node.getChild(1);
        String name = method.getStringValue();
        List<Lexeme> arguments = node.getChild(2).children;

        T receiverType = genExpr(node.getChild(0));
        if (receiverType != T.OBJ && receiverType != T.UNKNOWN) {
            throw error("method calls need an object", node.getChild(0));
        }

        List<ClassInfo> owners = new ArrayList<>();
        for (ClassInfo info : classes.values()) {
            if (info.methods.containsKey(name)) owners.add(info);
        }
        if (owners.isEmpty() && fieldTypes.get(name) == T.FUNC) {
            emit("ld a0, " + (8 + 8 * slot(name)) + "(a0)");
            return callValue(T.FUNC, fieldSigs.get(name), arguments, method);
        }
        if (owners.isEmpty()) {
            if (CANVAS_METHODS.contains(name)) {
                throw error("Canvas isn't supported by the compiler; run this one on the interpreter", method);
            }
            if (lenient) {
                for (Lexeme argument : arguments) genExpr(argument);
                return T.UNKNOWN;
            }
            throw error("no class has a method named '" + name + "'", method);
        }

        Func first = owners.get(0).methods.get(name);
        for (ClassInfo info : owners) {
            Func other = info.methods.get(name);
            if (other.ret != first.ret || !other.paramTypes.equals(first.paramTypes)) {
                if (lenient) continue;
                throw error("classes that share the method name '" + name
                        + "' need the same parameter and return types", method);
            }
        }

        push(T.OBJ);
        marshal(arguments, first.paramTypes, first.sig, 1, method);
        pop("a0");
        if (first.ret == T.FUNC) lastSig = first.sig == null ? null : first.sig.retSig;
        if (owners.size() == 1) {
            emit("call " + first.label);
            return first.ret;
        }

        String end = newLabel();
        List<String> targets = new ArrayList<>();
        emit("ld t0, 0(a0)");
        for (ClassInfo info : owners) {
            String target = newLabel();
            targets.add(target);
            emit("li t1, " + info.id);
            emit("beq t0, t1, " + target);
        }
        emit("call rtNoMethod");
        for (int i = 0; i < owners.size(); i++) {
            label(targets.get(i));
            emit("call " + owners.get(i).methods.get(name).label);
            emit("j " + end);
        }
        label(end);
        return first.ret;
    }

    private T genMatrixLiteral(Lexeme node) {
        int rows = node.getChildrenSize();
        int columns = node.getChild(0).getChildrenSize();
        emit("li a0, " + rows);
        emit("li a1, " + columns);
        emit("call matAlloc");
        push(T.MAT);
        for (int i = 0; i < rows; i++) {
            Lexeme row = node.getChild(i);
            if (row.getChildrenSize() != columns) throw error("matrix rows must all be the same length", row);
            for (int j = 0; j < columns; j++) {
                Lexeme cell = row.getChild(j);
                convert(genExpr(cell), T.DOS, cell);
                emit("ld a0, 0(sp)");
                emit("li a1, " + i);
                emit("li a2, " + j);
                emit("call matSet");
            }
        }
        pop("a0");
        return T.MAT;
    }
}
