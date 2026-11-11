package src.Compiler;

import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeGen {

    enum T {INT, DOS, BOOL, STR, MAT, VOID}

    static class Var {
        T type;
        String label;
        int offset;
        Fn owner;
    }

    static class Func {
        String label;
        T ret;
        List<T> paramTypes = new ArrayList<>();
    }

    static class Scope {
        final Scope parent;
        final Map<String, Var> vars = new HashMap<>();
        final Map<String, Func> funcs = new HashMap<>();

        Scope(Scope parent) {
            this.parent = parent;
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

        Fn(String label, T ret, boolean isMain, String exitLabel) {
            this.label = label;
            this.ret = ret;
            this.isMain = isMain;
            this.exitLabel = exitLabel;
        }
    }

    private final StringBuilder functions = new StringBuilder();
    private final StringBuilder rodata = new StringBuilder();
    private final StringBuilder data = new StringBuilder();
    private final Map<Long, String> doubleLabels = new HashMap<>();
    private final Map<String, String> stringLabels = new HashMap<>();
    private final Scope globalScope = new Scope(null);
    private Scope scope = globalScope;
    private Fn cur;
    private int labelCount = 0;
    private int lastLine = 0;

    public String compile(Lexeme program) {
        cur = new Fn("main", T.VOID, true, newLabel());
        genList(program);
        String main = finish(cur);

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

    // ---------- names ----------

    private T typeOf(Lexeme typeNode) {
        return switch (typeNode.getType()) {
            case INTERGER -> T.INT;
            case DOS -> T.DOS;
            case GEORGE -> T.BOOL;
            case STRING -> T.STR;
            case MATRIX -> T.MAT;
            case CHAR -> throw error("char is not supported by the compiler yet", typeNode);
            default -> T.VOID;
        };
    }

    private Var declare(Lexeme identifier, T type) {
        String name = identifier.getStringValue();
        if (scope.vars.containsKey(name)) {
            throw error("variable '" + name + "' is already defined", identifier);
        }
        Var variable = new Var();
        variable.type = type;
        if (cur.isMain && scope == globalScope) {
            variable.label = "g_" + name;
            data.append("    .balign 8\n").append(variable.label).append(": .quad 0\n");
        } else {
            variable.offset = -(24 + 8 * cur.slots++);
            variable.owner = cur;
        }
        scope.vars.put(name, variable);
        return variable;
    }

    private Var lookup(Lexeme identifier) {
        String name = identifier.getStringValue();
        for (Scope s = scope; s != null; s = s.parent) {
            Var variable = s.vars.get(name);
            if (variable == null) continue;
            if (variable.label == null && variable.owner != cur) {
                throw error("'" + name + "' is a local of an enclosing function and can't be used here", identifier);
            }
            return variable;
        }
        throw error("'" + name + "' is undefined", identifier);
    }

    private Func lookupFunc(Lexeme identifier) {
        String name = identifier.getStringValue();
        for (Scope s = scope; s != null; s = s.parent) {
            Func function = s.funcs.get(name);
            if (function != null) return function;
        }
        throw error("function '" + name + "' is undefined", identifier);
    }

    private String address(Var variable) {
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
        if (from == to) return;
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
                convert(type, variable.type, node.getChild(0));
                store(variable);
                return variable.type;
            }
            case functionDefinitionActual:
                genFunctionDef(node);
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
                genCollectionInit(node);
                return T.VOID;
            case MatrixAssi:
                genMatrixAssign(node);
                return T.VOID;
            case ERROR:
            case empty:
                return T.VOID;
            case funcCall:
            case binaryExpression:
            case unaryExpression:
            case primary:
            case parentheziedExpreission:
            case matrixGetter:
                return genExpr(node);
            default:
                throw error("the compiler doesn't support " + node.getType() + " statements yet", node);
        }
    }

    private void genBlock(Lexeme block) {
        scope = new Scope(scope);
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

    private void genFunctionDef(Lexeme node) {
        Lexeme identifier = node.getChild(0);
        String name = identifier.getStringValue();
        if (scope.funcs.containsKey(name)) throw error("function '" + name + "' is already defined", identifier);

        Func function = new Func();
        function.ret = typeOf(node.getChild(2));
        function.label = (cur.isMain ? "fn_" : cur.label + "__") + name;
        Lexeme parameters = node.getChild(3);
        for (Lexeme parameter : parameters.children) {
            function.paramTypes.add(typeOf(parameter.getChild(0)));
        }
        scope.funcs.put(name, function);

        Fn outerFn = cur;
        Scope outerScope = scope;
        cur = new Fn(function.label, function.ret, false, newLabel());
        scope = new Scope(outerScope);

        int intRegister = 0, floatRegister = 0;
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
        Lexeme node = expression;
        while (node.getChildrenSize() == 1) node = node.getChild(0);
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
                pop(type == T.DOS ? "fa0" : "a0");
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
        scope = new Scope(scope);
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

    private void genWhile(Lexeme condition, Lexeme percent, Lexeme block) {
        String start = newLabel(), end = newLabel();
        label(start);
        if (condition != null) {
            T type = genExpr(condition);
            if (percent == null || type == T.BOOL) {
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

    private void genCollectionInit(Lexeme node) {
        if (node.getChild(0).getType() != Types.matrix) {
            throw error("only matrix collections are supported by the compiler so far", node.getChild(1));
        }
        Lexeme identifier = node.getChild(1);
        Lexeme helper = node.getChild(3);
        if (helper.getType() == Types.collectionHelper && helper.getChildrenSize() == 4) {
            emit("li a0, " + helper.getChild(3).getIntValue());
            emit("li a1, " + helper.getChild(1).getIntValue());
            emit("call mat_alloc");
        } else {
            Lexeme expression = helper.getType() == Types.collectionHelper
                    ? helper.getChild(helper.getChildrenSize() - 1) : helper;
            T type = genExpr(expression);
            if (type != T.MAT) throw error("expected a matrix value", identifier);
        }
        store(declare(identifier, T.MAT));
    }

    private void genMatrixAssign(Lexeme node) {
        if (node.getChildrenSize() != 5) {
            throw error("matrix assignment needs exactly [[row, column]]", node.getChild(0));
        }
        Var matrix = lookup(node.getChild(0));
        if (matrix.type != T.MAT) throw error("'" + node.getChild(0).getStringValue() + "' is not a matrix", node.getChild(0));
        T type = genExpr(node.getChild(4));
        convert(type, T.DOS, node.getChild(0));
        load(matrix);
        emit("li a1, " + node.getChild(1).getIntValue());
        emit("li a2, " + node.getChild(2).getIntValue());
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
                load(variable);
                return variable.type;
            }
            case funcCall:
                return genCall(node);
            case matrixGetter: {
                Var matrix = lookup(node.getChild(1));
                if (matrix.type != T.MAT || node.getChildrenSize() != 4) {
                    throw error("GET needs a matrix and [[row, column]]", node.getChild(1));
                }
                load(matrix);
                emit("li a1, " + node.getChild(2).getIntValue());
                emit("li a2, " + node.getChild(3).getIntValue());
                emit("call mat_get");
                return T.DOS;
            }
            case matrix:
                return genMatrixLiteral(node);
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

    private T applyBinary(T left, Lexeme operator, T right) {
        Types op = operator.getType();
        RuntimeException mismatch = error("can't apply " + op + " to " + left + " and " + right, operator);

        if (left == T.MAT || right == T.MAT) {
            if (left != T.MAT || right != T.MAT) throw mismatch;
            String routine = switch (op) {
                case PLUS -> "mat_add";
                case MINUS -> "mat_sub";
                case MULTIPLY, DOT_PRODUCT -> "mat_mul";
                default -> throw mismatch;
            };
            emit("mv a1, a0");
            pop("a0");
            emit("call " + routine);
            return T.MAT;
        }
        if (left == T.STR || right == T.STR || left == T.VOID || right == T.VOID) throw mismatch;

        if (op == Types.AND || op == Types.OR) {
            if (left != T.BOOL || right != T.BOOL) throw mismatch;
            emit("mv a1, a0");
            pop("a0");
            emit((op == Types.AND ? "and" : "or") + " a0, a0, a1");
            return T.BOOL;
        }
        if (op == Types.EXPONENTIATE) {
            if (right == T.DOS) throw error("the compiler only supports whole-number exponents", operator);
            if (left == T.DOS) {
                pop("fa0");
                emit("call pow_dos_int");
                return T.DOS;
            }
            emit("mv a1, a0");
            pop("a0");
            emit("call pow_int");
            return T.INT;
        }
        if (op == Types.MOD) {
            if (left == T.DOS || right == T.DOS) throw mismatch;
            emit("mv a1, a0");
            pop("a0");
            emit("rem a0, a0, a1");
            return T.INT;
        }
        if (op == Types.DOT_PRODUCT) throw mismatch;

        boolean arithmetic = op == Types.PLUS || op == Types.MINUS || op == Types.MULTIPLY || op == Types.DIVIDE;
        if (arithmetic && (left == T.BOOL || right == T.BOOL)) throw mismatch;

        if (left == T.DOS || right == T.DOS) {
            emit(right == T.DOS ? "fmv.d fa1, fa0" : "fcvt.d.l fa1, a0");
            if (left == T.DOS) {
                pop("fa0");
            } else {
                pop("a0");
                emit("fcvt.d.l fa0, a0");
            }
            switch (op) {
                case PLUS -> emit("fadd.d fa0, fa0, fa1");
                case MINUS -> emit("fsub.d fa0, fa0, fa1");
                case MULTIPLY -> emit("fmul.d fa0, fa0, fa1");
                case DIVIDE -> emit("fdiv.d fa0, fa0, fa1");
                case LESS -> emit("flt.d a0, fa0, fa1");
                case GREATER -> emit("flt.d a0, fa1, fa0");
                case LESS_EQ -> emit("fle.d a0, fa0, fa1");
                case GREATER_EQ -> emit("fle.d a0, fa1, fa0");
                case EQUALS -> emit("feq.d a0, fa0, fa1");
                case NOT_EQUAL -> {
                    emit("feq.d a0, fa0, fa1");
                    emit("xori a0, a0, 1");
                }
                default -> throw mismatch;
            }
            return arithmetic ? T.DOS : T.BOOL;
        }

        emit("mv a1, a0");
        pop("a0");
        switch (op) {
            case PLUS -> emit("add a0, a0, a1");
            case MINUS -> emit("sub a0, a0, a1");
            case MULTIPLY -> emit("mul a0, a0, a1");
            case DIVIDE -> emit("div a0, a0, a1");
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
            case EQUALS -> {
                emit("xor a0, a0, a1");
                emit("seqz a0, a0");
            }
            case NOT_EQUAL -> {
                emit("xor a0, a0, a1");
                emit("snez a0, a0");
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
                else throw error("can't negate a " + type, operator);
                return type;
            case NOT:
                convert(type, T.BOOL, operator);
                emit("xori a0, a0, 1");
                return T.BOOL;
            case INVERSE:
                if (type == T.MAT) throw error("matrix inverse isn't supported by the compiler yet", operator);
                convert(type, T.DOS, operator);
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
            default -> throw error("can't print a value with no type", at);
        }
    }

    private T genCall(Lexeme node) {
        Lexeme identifier = node.getChild(0);
        List<Lexeme> arguments = node.getChild(1).children;

        if ("print".equals(identifier.getStringValue())) {
            for (Lexeme argument : arguments) {
                genPrint(genExpr(argument), argument);
            }
            return T.VOID;
        }

        Func function = lookupFunc(identifier);
        if (arguments.size() != function.paramTypes.size()) {
            throw error("'" + identifier.getStringValue() + "' expects " + function.paramTypes.size()
                    + " arguments but got " + arguments.size(), identifier);
        }
        String[] registers = new String[arguments.size()];
        int intRegister = 0, floatRegister = 0;
        for (int i = 0; i < arguments.size(); i++) {
            T wanted = function.paramTypes.get(i);
            convert(genExpr(arguments.get(i)), wanted, arguments.get(i));
            push(wanted);
            registers[i] = wanted == T.DOS ? "fa" + (floatRegister++) : "a" + (intRegister++);
        }
        for (int i = arguments.size() - 1; i >= 0; i--) {
            pop(registers[i]);
        }
        emit("call " + function.label);
        return function.ret;
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
