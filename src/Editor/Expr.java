package src.Editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Expr {
    String op;
    double value;
    String name;
    List<Expr> kids = new ArrayList<>();

    static Expr num(double value) {
        Expr e = new Expr();
        e.op = "num";
        e.value = value;
        return e;
    }

    static Expr var(String name) {
        Expr e = new Expr();
        e.op = "var";
        e.name = name;
        return e;
    }

    static Expr of(String op, Expr... kids) {
        Expr e = new Expr();
        e.op = op;
        for (Expr kid : kids) e.kids.add(kid);
        return e;
    }

    static Expr add(Expr a, Expr b) {
        return of("add", a, b);
    }

    static Expr sub(Expr a, Expr b) {
        return of("add", a, of("mul", num(-1), b));
    }

    static Expr mul(Expr a, Expr b) {
        return of("mul", a, b);
    }

    static Expr div(Expr a, Expr b) {
        return of("mul", a, of("pow", b, num(-1)));
    }

    static Expr pow(Expr a, Expr b) {
        return of("pow", a, b);
    }

    boolean isNum() {
        return op.equals("num");
    }

    boolean isNum(double v) {
        return op.equals("num") && value == v;
    }

    boolean uses(String variable) {
        if (op.equals("var")) return name.equals(variable);
        for (Expr kid : kids) {
            if (kid.uses(variable)) return true;
        }
        return false;
    }

    Expr replace(String variable, Expr with) {
        if (op.equals("var")) return name.equals(variable) ? with : this;
        Expr copy = new Expr();
        copy.op = op;
        copy.value = value;
        copy.name = name;
        for (Expr kid : kids) copy.kids.add(kid.replace(variable, with));
        return copy;
    }

    Expr derivative(String x) {
        if (op.equals("num")) return num(0);
        if (op.equals("var")) return num(name.equals(x) ? 1 : 0);
        if (op.equals("add")) {
            Expr sum = of("add");
            for (Expr kid : kids) sum.kids.add(kid.derivative(x));
            return sum;
        }
        if (op.equals("mul")) {
            Expr sum = of("add");
            for (int i = 0; i < kids.size(); i++) {
                Expr term = of("mul");
                for (int j = 0; j < kids.size(); j++) {
                    term.kids.add(i == j ? kids.get(j).derivative(x) : kids.get(j));
                }
                sum.kids.add(term);
            }
            return sum;
        }
        if (op.equals("pow")) {
            Expr base = kids.get(0), power = kids.get(1);
            if (!power.uses(x)) {
                return of("mul", power, pow(base, add(power, num(-1))), base.derivative(x));
            }
            return mul(this, add(mul(power.derivative(x), of("ln", base)), mul(power, div(base.derivative(x), base))));
        }
        Expr u = kids.get(0);
        Expr du = u.derivative(x);
        if (op.equals("sin")) return mul(of("cos", u), du);
        if (op.equals("cos")) return of("mul", num(-1), of("sin", u), du);
        if (op.equals("tan")) return mul(pow(of("cos", u), num(-2)), du);
        if (op.equals("ln")) return div(du, u);
        if (op.equals("exp")) return mul(of("exp", u), du);
        if (op.equals("atan")) return div(du, add(num(1), pow(u, num(2))));
        throw new RuntimeException("can't differentiate " + op);
    }

    Expr simplify() {
        Expr e = new Expr();
        e.op = op;
        e.value = value;
        e.name = name;
        for (Expr kid : kids) e.kids.add(kid.simplify());

        if (op.equals("add")) return e.simplifySum();
        if (op.equals("mul")) return e.simplifyProduct();
        if (op.equals("pow")) return e.simplifyPower();
        if (e.kids.size() == 1 && e.kids.get(0).isNum()) {
            double v = e.kids.get(0).value;
            double folded;
            if (op.equals("sin")) folded = Math.sin(v);
            else if (op.equals("cos")) folded = Math.cos(v);
            else if (op.equals("tan")) folded = Math.tan(v);
            else if (op.equals("ln")) folded = Math.log(v);
            else if (op.equals("exp")) folded = Math.exp(v);
            else folded = Math.atan(v);
            if (folded == Math.rint(folded)) return num(folded);
        }
        if (op.equals("ln") && e.kids.get(0).op.equals("exp")) return e.kids.get(0).kids.get(0);
        if (op.equals("exp") && e.kids.get(0).op.equals("ln")) return e.kids.get(0).kids.get(0);
        return e;
    }

    private Expr simplifySum() {
        List<Expr> flat = new ArrayList<>();
        for (Expr kid : kids) {
            if (kid.op.equals("add")) flat.addAll(kid.kids);
            else flat.add(kid);
        }
        double constant = 0;
        Map<String, Expr> bases = new LinkedHashMap<>();
        Map<String, Double> counts = new LinkedHashMap<>();
        for (Expr term : flat) {
            if (term.isNum()) {
                constant += term.value;
                continue;
            }
            double coefficient = 1;
            Expr rest = term;
            if (term.op.equals("mul") && term.kids.get(0).isNum()) {
                coefficient = term.kids.get(0).value;
                rest = of("mul");
                rest.kids.addAll(term.kids.subList(1, term.kids.size()));
                if (rest.kids.size() == 1) rest = rest.kids.get(0);
            }
            String key = rest.key();
            bases.putIfAbsent(key, rest);
            counts.put(key, counts.getOrDefault(key, 0.0) + coefficient);
        }
        List<Expr> terms = new ArrayList<>();
        for (String key : bases.keySet()) {
            double count = counts.get(key);
            if (count == 0) continue;
            terms.add(count == 1 ? bases.get(key) : of("mul", num(count), bases.get(key)).simplifyProduct());
        }
        terms.sort((a, b) -> Integer.compare(b.degree(), a.degree()));
        if (constant != 0) terms.add(num(constant));
        if (terms.isEmpty()) return num(0);
        if (terms.size() == 1) return terms.get(0);
        Expr sum = of("add");
        sum.kids.addAll(terms);
        return sum;
    }

    private Expr simplifyProduct() {
        List<Expr> flat = new ArrayList<>();
        for (Expr kid : kids) {
            if (kid.op.equals("mul")) flat.addAll(kid.kids);
            else flat.add(kid);
        }
        double coefficient = 1;
        Map<String, Expr> bases = new LinkedHashMap<>();
        Map<String, Expr> powers = new LinkedHashMap<>();
        for (Expr factor : flat) {
            if (factor.isNum()) {
                coefficient *= factor.value;
                continue;
            }
            Expr base = factor;
            Expr power = num(1);
            if (factor.op.equals("pow")) {
                base = factor.kids.get(0);
                power = factor.kids.get(1);
            }
            String key = base.key();
            bases.putIfAbsent(key, base);
            Expr before = powers.get(key);
            powers.put(key, before == null ? power : of("add", before, power).simplify());
        }
        if (coefficient == 0) return num(0);
        List<Expr> factors = new ArrayList<>();
        for (String key : bases.keySet()) {
            Expr power = powers.get(key);
            if (power.isNum(0)) continue;
            factors.add(power.isNum(1) ? bases.get(key) : pow(bases.get(key), power).simplifyPower());
        }
        factors.sort((a, b) -> a.order().compareTo(b.order()));
        if (factors.isEmpty()) return num(coefficient);
        if (coefficient != 1) factors.add(0, num(coefficient));
        if (factors.size() == 1) return factors.get(0);
        Expr product = of("mul");
        product.kids.addAll(factors);
        return product;
    }

    private Expr simplifyPower() {
        Expr base = kids.get(0), power = kids.get(1);
        if (power.isNum(0)) return num(1);
        if (power.isNum(1)) return base;
        if (base.isNum() && power.isNum()) {
            double folded = Math.pow(base.value, power.value);
            if (folded == Math.rint(folded) || power.value > 0) return num(folded);
        }
        if (base.isNum(1)) return num(1);
        if (base.op.equals("pow") && power.isNum() && base.kids.get(1).isNum()) {
            return pow(base.kids.get(0), num(base.kids.get(1).value * power.value)).simplifyPower();
        }
        if (base.op.equals("mul") && power.isNum()) {
            Expr product = of("mul");
            for (Expr factor : base.kids) product.kids.add(pow(factor, power).simplifyPower());
            return product.simplifyProduct();
        }
        return pow(base, power);
    }

    private int degree() {
        if (op.equals("var")) return 1;
        if (op.equals("pow") && kids.get(1).isNum()) return (int) Math.round(kids.get(0).degree() * kids.get(1).value);
        if (op.equals("mul")) {
            int total = 0;
            for (Expr kid : kids) total += kid.degree();
            return total;
        }
        return 0;
    }

    private String order() {
        if (op.equals("var")) return "0" + name;
        if (op.equals("pow") && kids.get(0).op.equals("var")) return "0" + kids.get(0).name;
        return "1" + key();
    }

    String key() {
        if (op.equals("num")) return format(value);
        if (op.equals("var")) return name;
        List<String> parts = new ArrayList<>();
        for (Expr kid : kids) parts.add(kid.key());
        if (op.equals("add") || op.equals("mul")) Collections.sort(parts);
        return op + "(" + String.join(",", parts) + ")";
    }

    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return Long.toString((long) value);
        String text = Double.toString(Math.round(value * 1e10) / 1e10);
        return text;
    }

    String source() {
        if (op.equals("num")) {
            String text = format(value);
            if (!text.contains(".") && !text.contains("E")) text = text + ".0";
            return value < 0 ? "(0.0 - " + text.substring(1) + ")" : text;
        }
        if (op.equals("var")) return name;
        if (op.equals("add") || op.equals("mul")) {
            List<String> parts = new ArrayList<>();
            for (Expr kid : kids) parts.add(kid.source());
            return "(" + String.join(op.equals("add") ? " + " : " * ", parts) + ")";
        }
        if (op.equals("pow")) return "(" + kids.get(0).source() + " î " + kids.get(1).source() + ")";
        String inside = kids.get(0).source();
        if (op.equals("sin") || op.equals("cos")) return op + "(" + inside + ")";
        if (op.equals("tan")) return "(sin(" + inside + ") / cos(" + inside + "))";
        if (op.equals("ln")) return "mq_ln(" + inside + ")";
        if (op.equals("exp")) return "(2.718281828459045 î " + inside + ")";
        return "mq_atan(" + inside + ")";
    }

    String latex() {
        if (op.equals("num")) return format(value);
        if (op.equals("var")) return latexName(name);
        if (op.equals("add")) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < kids.size(); i++) {
                Expr kid = kids.get(i);
                boolean negative = (kid.isNum() && kid.value < 0) || (kid.op.equals("mul") && kid.kids.get(0).isNum() && kid.kids.get(0).value < 0);
                if (negative) {
                    out.append(i == 0 ? "-" : "-").append(negate(kid).latex());
                } else {
                    if (i > 0) out.append("+");
                    out.append(kid.latex());
                }
            }
            return out.toString();
        }
        if (op.equals("mul")) {
            if (kids.get(0).isNum() && kids.get(0).value < 0) return "-" + negate(this).latex();
            List<Expr> top = new ArrayList<>();
            List<Expr> bottom = new ArrayList<>();
            for (Expr kid : kids) {
                if (kid.op.equals("pow") && kid.kids.get(1).isNum() && kid.kids.get(1).value < 0) {
                    bottom.add(kid.kids.get(1).isNum(-1) ? kid.kids.get(0) : pow(kid.kids.get(0), num(-kid.kids.get(1).value)));
                } else {
                    top.add(kid);
                }
            }
            String upper = productLatex(top);
            if (bottom.isEmpty()) return upper;
            return "\\frac{" + (top.isEmpty() ? "1" : upper) + "}{" + productLatex(bottom) + "}";
        }
        if (op.equals("pow")) {
            Expr base = kids.get(0), power = kids.get(1);
            if (power.isNum(0.5)) return "\\sqrt{" + base.latex() + "}";
            if (power.isNum() && power.value < 0) return "\\frac{1}{" + pow(base, num(-power.value)).latex() + "}";
            String inner = base.op.equals("var") || (base.isNum() && base.value >= 0) ? base.latex() : "\\left(" + base.latex() + "\\right)";
            if (base.op.equals("sin") || base.op.equals("cos") || base.op.equals("tan")) {
                return "\\" + base.op + "^{" + power.latex() + "}\\left(" + base.kids.get(0).latex() + "\\right)";
            }
            return inner + "^{" + power.latex() + "}";
        }
        String inside = "\\left(" + kids.get(0).latex() + "\\right)";
        if (op.equals("exp")) return "e^{" + kids.get(0).latex() + "}";
        if (op.equals("atan")) return "\\arctan" + inside;
        return "\\" + op + inside;
    }

    private static Expr negate(Expr e) {
        if (e.isNum()) return num(-e.value);
        Expr copy = of("mul");
        copy.kids.addAll(e.kids);
        double coefficient = -copy.kids.get(0).value;
        if (coefficient == 1) copy.kids.remove(0);
        else copy.kids.set(0, num(coefficient));
        return copy.kids.size() == 1 ? copy.kids.get(0) : copy;
    }

    private static String productLatex(List<Expr> factors) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < factors.size(); i++) {
            Expr factor = factors.get(i);
            String piece = factor.op.equals("add") ? "\\left(" + factor.latex() + "\\right)" : factor.latex();
            if (i > 0 && (factor.isNum() || (out.length() > 0 && Character.isDigit(out.charAt(out.length() - 1)) && Character.isDigit(piece.charAt(0))))) {
                out.append("\\cdot ");
            }
            out.append(piece);
        }
        return out.toString();
    }

    private static String latexName(String name) {
        int cut = name.indexOf('_');
        if (cut < 0) return name.equals("theta") ? "\\theta" : name;
        return name.substring(0, cut) + "_{" + name.substring(cut + 1) + "}";
    }
}
