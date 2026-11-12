package src.Compiler;

import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CodeGen {

    enum T {INT, DOS, BOOL, STR, MAT, OBJ, LIST_INT, LIST_DOS, LIST_BOOL, LIST_STR, LIST_MAT, LIST_OBJ, VOID, UNKNOWN}

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
        String name;
    }

    static class Func {
        String label;
        T ret;
        List<T> paramTypes = new ArrayList<>();
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
        int slots = 0;
        ClassInfo cls;
        Var thisVar;

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
    private boolean lenient;
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
        labelCount = 0;

        cur = new Fn("main", T.VOID, true, newLabel());
        globalScope = new Scope(null, cur);
        scope = globalScope;
        if (runtimeLibrary != null) genList(runtimeLibrary);
        T lastType = null;
        for (Lexeme statement : program.children) {
            T type = genStmt(statement);
            if (!keepsResult(statement, type)) continue;
            emit("la t0, rt_last_result");
            emit(type == T.DOS ? "fsd fa0, 0(t0)" : "sd a0, 0(t0)");
            lastType = type;
        }
        if (lastType != null) {
            emit("la a0, " + stringConst("Program result: "));
            emit("call print_str");
            emit("la t0, rt_last_result");
            emit(lastType == T.DOS ? "fld fa0, 0(t0)" : "ld a0, 0(t0)");
            genPrint(lastType, program);
            emit("la a0, " + stringConst("\n"));
            emit("call print_str");
        }
        data.append("    .balign 8\nrt_last_result: .quad 0\n");
        String main = finish(cur);

        List<String> nameLabels = new ArrayList<>();
        nameLabels.add(stringConst("object"));
        for (ClassInfo info : classes.values()) nameLabels.add(stringConst(info.name));
        rodata.append("    .balign 8\nclass_names:\n");
        for (String nameLabel : nameLabels) rodata.append("    .quad ").append(nameLabel).append('\n');
        rodata.append("obj_size: .quad ").append(8 + 8 * fieldSlots.size()).append('\n');

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

    private boolean keepsResult(Lexeme statement, T type) {
        if (type == T.VOID || type == T.UNKNOWN || isList(type)) return false;
        return switch (statement.getType()) {
            case varInitialization, varAssignment, fieldAssignment, collectionInitialization, binaryExpression,
                 unaryExpression, primary, parentheziedExpreission, funcCall, methodCall, fieldAccess,
                 matrixGetter -> true;
            default -> false;
        };
    }

    // ---------- plumbing ----------

    private void emit(String instruction) {
        cur.code.append("    ").append(instruction).append('\n');
    }

    private void label(String name) {
        cur.code.append(name).append(":\n");
    }

    private String newLabel() {
        return "L" + (labelCount++);
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
        int locals = (fn.slots * 8 + 15) & ~15;
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
        out.append(fn.code);
        out.append(fn.exitLabel).append(":\n");
        out.append("    addi sp, s0, -16\n");
        out.append("    ld ra, 8(sp)\n");
        out.append("    ld s0, 0(sp)\n");
        out.append("    addi sp, sp, 16\n");
        out.append("    ret\n");
        return out.toString();
    }

    private String doubleConst(double value) {
        long bits = Double.doubleToRawLongBits(value);
        String existing = doubleLabels.get(bits);
        if (existing != null) return existing;
        String name = "dbl_" + doubleLabels.size();
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
        String name = "str_" + stringLabels.size();
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

    // ---------- names ----------

    private T typeOf(Lexeme typeNode) {
        return switch (typeNode.getType()) {
            case INTERGER -> T.INT;
            case DOS -> T.DOS;
            case GEORGE -> T.BOOL;
            case STRING -> T.STR;
            case MATRIX -> T.MAT;
            case OBJECT -> T.OBJ;
            case CHAR -> throw error("char is not supported by the compiler yet", typeNode);
            default -> T.VOID;
        };
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
            variable.label = "g_" + name;
            data.append("    .balign 8\n").append(variable.label).append(": .quad 0\n");
            if (type != T.UNKNOWN && knownGlobals.get(name) != type) {
                knownGlobals.put(name, type);
                changed = true;
            }
        } else {
            variable.offset = -(24 + 8 * cur.slots++);
            variable.owner = cur;
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

    private Var lookup(Lexeme identifier) {
        String name = identifier.getStringValue();
        Scope s = scope;
        for (; s != null && s.owner == cur; s = s.parent) {
            Var variable = s.vars.get(name);
            if (variable != null) return variable;
        }
        if (cur.cls != null && cur.cls.fields.contains(name)) return fieldVar(name);
        for (; s != null; s = s.parent) {
            Var variable = s.vars.get(name);
            if (variable == null) continue;
            if (variable.label == null) {
                throw error("'" + name + "' is a local of an enclosing function and can't be used here", identifier);
            }
            return variable;
        }
        T known = knownGlobals.get(name);
        if (known != null) {
            Var variable = new Var();
            variable.name = name;
            variable.type = known;
            variable.label = "g_" + name;
            return variable;
        }
        if (lenient) return null;
        throw error("'" + name + "' is undefined", identifier);
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
            emit("ld t1, " + cur.thisVar.offset + "(s0)");
            return (8 + 8 * variable.fieldSlot) + "(t1)";
        }
        if (variable.label != null) {
            emit("la t0, " + variable.label);
            return "0(t0)";
        }
        if (variable.offset < -2048) {
            emit("li t0, " + variable.offset);
            emit("add t0, t0, s0");
            return "0(t0)";
        }
        return variable.offset + "(s0)";
    }

    private void load(Var variable) {
        String where = address(variable);
        emit(variable.type == T.DOS ? "fld fa0, " + where : "ld a0, " + where);
    }

    private void store(Var variable) {
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
        emit("call rt_div_zero");
        label(ok);
    }

    private void checkDoubleDivisor(String register) {
        String ok = newLabel();
        emit("fmv.d.x ft0, zero");
        emit("feq.d t0, " + register + ", ft0");
        emit("beqz t0, " + ok);
        emit("call rt_div_zero");
        label(ok);
    }

    // ---------- statements ----------

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
        convert(type, declared, node.getChild(1));
        for (Lexeme identifier : node.getChild(1).children) {
            store(declare(identifier, declared));
        }
        return declared;
    }

    private T genFieldAssign(Lexeme node) {
        Lexeme receiver = node.getChild(0);
        Lexeme member = node.getChild(1);
        String field = member.getStringValue();
        T valueType = genExpr(node.getChild(3));

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
        cur = new Fn("cls_" + name + "__new", T.OBJ, false, newLabel());
        cur.cls = info;
        scope = new Scope(outerScope, cur);
        cur.thisVar = declareName("this", T.OBJ, node.getChild(1));
        emit("la t0, obj_size");
        emit("ld a0, 0(t0)");
        emit("call rt_alloc");
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
        Lexeme parameters = node.getChild(3);

        Func function = new Func();
        function.ret = typeOf(node.getChild(2));
        for (Lexeme parameter : parameters.children) {
            function.paramTypes.add(typeOf(parameter.getChild(0)));
        }

        if (owner != null) {
            function.label = "cls_" + owner.name + "__" + name;
            Func previous = owner.methods.get(name);
            if (previous == null || !sameSignature(previous, function)) changed = true;
            owner.methods.put(name, function);
        } else {
            if (scope.funcs.containsKey(name)) throw error("function '" + name + "' is already defined", identifier);
            function.label = (cur.isMain ? "fn_" : cur.label + "__") + name;
            scope.funcs.put(name, function);
            if (cur.isMain && scope == globalScope) {
                Func previous = knownFuncs.get(name);
                if (previous == null || !sameSignature(previous, function)) changed = true;
                knownFuncs.put(name, function);
            }
        }

        Fn outerFn = cur;
        Scope outerScope = scope;
        cur = new Fn(function.label, function.ret, false, newLabel());
        cur.cls = owner;
        scope = new Scope(outerScope, cur);

        int intRegister = 0, floatRegister = 0;
        if (owner != null) {
            cur.thisVar = declareName("this", T.OBJ, identifier);
            emit("sd a0, " + address(cur.thisVar));
            intRegister = 1;
        }
        for (Lexeme parameter : parameters.children) {
            T type = typeOf(parameter.getChild(0));
            Var variable = declare(parameter.getChild(1), type);
            if (type == T.DOS) {
                if (floatRegister > 7) throw error("too many dos parameters", identifier);
                emit("fsd fa" + (floatRegister++) + ", " + address(variable));
            } else {
                if (intRegister > 7) throw error("too many parameters", identifier);
                emit("sd a" + (intRegister++) + ", " + address(variable));
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

    private boolean isVoidKeyword(Lexeme expression) {
        Lexeme node = strip(expression);
        return node.getType() == Types.IDENTIFIER && "void".equals(node.getStringValue());
    }

    private void genReturn(Lexeme node) {
        Lexeme expression = node.getChild(1);
        boolean isVoid = isVoidKeyword(expression);
        if (cur.isMain) {
            if (!isVoid) {
                T type = genExpr(expression);
                push(type);
                emit("la a0, " + stringConst("Program result: "));
                emit("call print_str");
                popValue(type);
                genPrint(type, expression);
                emit("la a0, " + stringConst("\n"));
                emit("call print_str");
            }
            emit("li a0, 0");
            emit("j rt_exit");
            return;
        }
        if (!isVoid) {
            T type = genExpr(expression);
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
                emit("call mat_alloc");
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
            emit("call rt_alloc");
            emit("li t0, " + size);
            emit("sd t0, 0(a0)");
        } else if (count == 0) {
            emit("li a0, 8");
            emit("call rt_alloc");
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
        emit("call rt_alloc");
        emit("li t0, " + items.size());
        emit("sd t0, 0(a0)");
        push(listType);
        for (int i = 0; i < items.size(); i++) {
            convert(genExpr(items.get(i)), elementType, items.get(i));
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
        emit("call rt_bounds");
        label(ok);
        emit("slli t1, t1, 3");
        emit("add t1, t1, a0");
        emit(elementType == T.DOS ? "fld fa0, 8(t1)" : "ld a0, 8(t1)");
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
        emit("call mat_set");
    }

    // ---------- expressions ----------

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
                Var variable = lookup(node);
                if (variable == null) return T.UNKNOWN;
                load(variable);
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
                return fieldType;
            }
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
                emit("call mat_get");
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
        return switch (operator) {
            case OR -> 1;
            case AND -> 2;
            case EQUALS, NOT_EQUAL -> 3;
            case LESS, GREATER, LESS_EQ, GREATER_EQ -> 4;
            case PLUS, MINUS -> 5;
            case MULTIPLY, DIVIDE, MOD, DOT_PRODUCT -> 6;
            case EXPONENTIATE -> 7;
            default -> -1;
        };
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
            emit("call rt_str_eq");
        } else if (left == T.MAT && right == T.MAT) {
            toInts();
            emit("call fn_rt_mat_eq");
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

    private int scaleCode(Types op) {
        return switch (op) {
            case PLUS -> 0;
            case MINUS -> 1;
            case MULTIPLY -> 2;
            case DIVIDE -> 3;
            default -> -1;
        };
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

        if (op == Types.EQUALS || op == Types.NOT_EQUAL) return genEquality(left, right, op == Types.NOT_EQUAL);

        if (left == T.MAT || right == T.MAT) {
            if (left == T.MAT && right == T.MAT) {
                String routine = switch (op) {
                    case PLUS -> "mat_add";
                    case MINUS -> "mat_sub";
                    case MULTIPLY -> "mat_mul";
                    case DOT_PRODUCT -> "fn_rt_mat_dot";
                    default -> throw mismatch;
                };
                toInts();
                emit("call " + routine);
                return T.MAT;
            }
            int code = scaleCode(op);
            if (left == T.MAT && isNumber(right) && code >= 0) {
                if (right == T.INT) emit("fcvt.d.l fa0, a0");
                pop("a0");
                emit("li a1, " + code);
                emit("li a2, 0");
                emit("call fn_rt_mat_scale");
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
                emit("call fn_rt_mat_scale");
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
                    emit("call pow_int");
                    return T.INT;
                }
                toDoubles(left, right);
                emit("call fn_rt_pow");
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
            switch (op) {
                case PLUS -> emit("fadd.d fa0, fa0, fa1");
                case MINUS -> emit("fsub.d fa0, fa0, fa1");
                case MULTIPLY -> emit("fmul.d fa0, fa0, fa1");
                case LESS -> emit("flt.d a0, fa0, fa1");
                case GREATER -> emit("flt.d a0, fa1, fa0");
                case LESS_EQ -> emit("fle.d a0, fa0, fa1");
                case GREATER_EQ -> emit("fle.d a0, fa1, fa0");
                default -> throw mismatch;
            }
            return arithmetic ? T.DOS : T.BOOL;
        }

        toInts();
        switch (op) {
            case PLUS -> emit("add a0, a0, a1");
            case MINUS -> emit("sub a0, a0, a1");
            case MULTIPLY -> emit("mul a0, a0, a1");
            case LESS -> emit("slt a0, a0, a1");
            case GREATER -> emit("slt a0, a1, a0");
            case LESS_EQ -> {
                emit("slt a0, a1, a0");
                emit("xori a0, a0, 1");
            }
            case GREATER_EQ -> {
                emit("slt a0, a0, a1");
                emit("xori a0, a0, 1");
            }
            default -> throw mismatch;
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
                if (type == T.MAT) throw error("matrix inverse isn't supported by the compiler yet", operator);
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
        switch (type) {
            case INT -> emit("call print_int");
            case DOS -> emit("call print_dos");
            case BOOL -> emit("call print_bool");
            case STR -> emit("call print_str");
            case MAT -> emit("call mat_print");
            case OBJ -> {
                emit("ld t0, 0(a0)");
                emit("slli t0, t0, 3");
                emit("la t1, class_names");
                emit("add t1, t1, t0");
                emit("ld a0, 0(t1)");
                emit("call print_str");
            }
            case UNKNOWN -> {
            }
            default -> throw error("the compiler can't print a " + type + " yet", at);
        }
    }

    private void marshal(List<Lexeme> arguments, List<T> wanted, int firstIntRegister, Lexeme at) {
        if (arguments.size() != wanted.size()) {
            throw error("'" + at.getStringValue() + "' expects " + wanted.size()
                    + " arguments but got " + arguments.size(), at);
        }
        String[] registers = new String[arguments.size()];
        int intRegister = firstIntRegister, floatRegister = 0;
        for (int i = 0; i < arguments.size(); i++) {
            T want = wanted.get(i);
            convert(genExpr(arguments.get(i)), want, arguments.get(i));
            push(want);
            registers[i] = want == T.DOS ? "fa" + (floatRegister++) : "a" + (intRegister++);
        }
        for (int i = arguments.size() - 1; i >= 0; i--) {
            pop(registers[i]);
        }
    }

    private T genCall(Lexeme node) {
        Lexeme identifier = node.getChild(0);
        String name = identifier.getStringValue();
        List<Lexeme> arguments = node.getChild(1).children;

        switch (name) {
            case "print":
                for (Lexeme argument : arguments) genPrint(genExpr(argument), argument);
                return T.VOID;
            case "random":
                if (arguments.isEmpty()) {
                    emit("call fn_rt_random01");
                } else {
                    marshal(arguments, List.of(T.DOS, T.DOS), 0, identifier);
                    emit("call fn_rt_random");
                }
                return T.DOS;
            case "sin":
            case "cos":
                marshal(arguments, List.of(T.DOS), 0, identifier);
                emit("call fn_rt_" + name);
                return T.DOS;
            case "rt_fsqrt":
                marshal(arguments, List.of(T.DOS), 0, identifier);
                emit("fsqrt.d fa0, fa0");
                return T.DOS;
            case "rt_rows":
            case "rt_cols":
                marshal(arguments, List.of(T.MAT), 0, identifier);
                emit("ld a0, " + (name.equals("rt_rows") ? 0 : 8) + "(a0)");
                return T.INT;
            case "Canvas":
                throw error("Canvas isn't supported by the compiler; run this one on the interpreter", identifier);
            default:
                break;
        }

        Func function = findFunc(name);
        if (function != null) {
            marshal(arguments, function.paramTypes, 0, identifier);
            emit("call " + function.label);
            return function.ret;
        }

        ClassInfo info = classes.get(name);
        if (info != null) {
            emit("call cls_" + name + "__new");
            Func init = info.methods.get("init");
            if (init == null) {
                if (lenient) for (Lexeme argument : arguments) genExpr(argument);
                return T.OBJ;
            }
            push(T.OBJ);
            marshal(arguments, init.paramTypes, 1, identifier);
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
        marshal(arguments, first.paramTypes, 1, method);
        pop("a0");
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
        emit("call rt_no_method");
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
        emit("call mat_alloc");
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
                emit("call mat_set");
            }
        }
        pop("a0");
        return T.MAT;
    }
}
