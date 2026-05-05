package src.lexicalAnalysis;

import src.Evaluation.Environment;

import java.util.ArrayList;

public final class Lexeme {
    private final Types type;
    private Integer lineNumber;
    public ArrayList<Lexeme> children = new ArrayList<Lexeme>();
    ;
    private Integer intValue;
    private Double doubleValue;
    private Boolean booleanValue;
    private String stringValue;
    private Environment definingEnviorment;
    private Environment objectEnvironment;
    private Object[] arrayValue;
    private ArrayList<Object> arrayListValue;
    private Object[][] matrixValue;
    private Object nativeValue;

    public void setEnvironment(Environment env) {
        definingEnviorment = env;
    }

    public Lexeme(Types type) {
        this.type = type;
    }

    public void AddChild(Lexeme newChild) {
        children.add(newChild);
    }

    public void addChildren(ArrayList<Lexeme> children) {
        this.children.addAll(children);
    }

    public Lexeme getChild(int index) {
        return children.get(index);
    }

    public Lexeme(Types type, int lineNumber, String value) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.stringValue = value;
    }

    public Lexeme(Types type, int lineNumber, int value) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.intValue = value;
    }

    public Lexeme(Types type, int lineNumber, double value) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.doubleValue = value;
    }

    public Lexeme(Types type, int lineNumber, boolean value) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.booleanValue = value;
    }

    public Lexeme(Types type, int lineNumber) {
        this.type = type;
        this.lineNumber = lineNumber;
    }


    public Types getType() {
        return type;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getStringValue() {
        return stringValue;
    }

    public int getIntValue() {
        return intValue;
    }

    public double getDosValue() {
        if (doubleValue == null) {
            throw new IllegalStateException("doubleValue is not initialized for this Lexeme.");
        }
        return doubleValue;
    }

    public boolean getBooleanValue() {
        return booleanValue;
    }

    public void setIntValue(int intValue) {
        this.intValue = intValue;
    }

    public void setDoubleValue(double doubleValue) {
        this.doubleValue = doubleValue;
    }

    public void setBooleanValue(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public String toString() {
        String str = type + " Line: " + lineNumber + " value: ";
        if (type == Types.INTERGER) {
            addvalue(str);
            str += intValue;
        } else if (type == Types.DOS) {
            addvalue(str);
            str += doubleValue;
        } else if (type == Types.GEORGE) {
            addvalue(str);
            str += booleanValue;
        } else if (type == Types.STRING) {
            addvalue(str);

            str += "\"" + stringValue + "\"";
        } else if (type == Types.matrix && matrixValue != null) {
            str += matrixToString();
        } else if (type == Types.OBJECT) {
            str += stringValue == null ? "object" : stringValue;
        }
        return str;
    }

    public void addvalue(String str) {
        str += " value:";
    }


    public void printAsParseTree() {
        System.out.println(getPrintableTree(this, 0));

    }


    private static String getPrintableTree(Lexeme root, int level) {

        if (root == null) return "(Empty ParseTree)";

        StringBuilder treeString = new StringBuilder(root.toString());


        StringBuilder spacer = new StringBuilder("\n");

        spacer.append("\t".repeat(level));


        int numChildren = root.children.size();

        if (numChildren > 0) {

            treeString.append(" (with ").append(numChildren).append(numChildren == 1 ? " child):" : " children):");

            for (int i = 0; i < numChildren; i++) {

                Lexeme child = root.getChild(i);

                treeString

                        .append(spacer).append("(").append(i + 1).append(") ")

                        .append(getPrintableTree(child, level + 1));

            }

        }


        return treeString.toString();

    }

    public int getChildrenSize() {
        return children.size();
    }

    public ArrayList<Lexeme> getChildren() {
        return children;
    }

    public Object getValue() {
        return switch (type) {
            case INTERGER -> intValue;
            case DOS -> doubleValue;
            case GEORGE -> booleanValue;
            case STRING -> stringValue;
            case OBJECT -> nativeValue == null ? objectEnvironment : nativeValue;
            case array -> arrayValue;
            case linkedList -> arrayListValue;
            case matrix -> matrixValue;
            default -> throw new IllegalStateException("No value associated with this Lexeme type: " + type);
        };
    }

    public Object getNumberValue() {
        return switch (type) {
            case INTERGER -> intValue;
            case DOS -> doubleValue;
            default -> throw new IllegalStateException("No namber value associated with this Lexeme type: " + type);
        };
    }

    public Lexeme(Types type, int lineNumber, ArrayList<Object> value) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.arrayListValue = value;
    }

    public Lexeme(Types type, int lineNumber, Object[][] value) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.matrixValue = value;
    }

    public Lexeme(Types type, int lineNumber, Object[] value) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.arrayValue = value;
    }

    public Lexeme(Types type, int lineNumber, Environment value) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.objectEnvironment = value;
    }


    public Object[] getArrayValue() {
        return arrayValue;
    }

    public void setArrayValue(Object[] arrayValue) {
        this.arrayValue = arrayValue;
    }

    public void setArrayListValue(ArrayList<Object> arrayListValue) {
        this.arrayListValue = arrayListValue;
    }

    public void setMatrixValue(Object[][] matrixValue) {
        this.matrixValue = matrixValue;
    }

    public Object[][] getMatrixValue() {
        return matrixValue;
    }

    public Environment getObjectEnvironment() {
        return objectEnvironment;
    }

    public void setObjectEnvironment(Environment objectEnvironment) {
        this.objectEnvironment = objectEnvironment;
    }

    public Object getNativeValue() {
        return nativeValue;
    }

    public void setNativeValue(Object nativeValue) {
        this.nativeValue = nativeValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    private String matrixToString() {
        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < matrixValue.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append("[");
            for (int j = 0; j < matrixValue[i].length; j++) {
                if (j > 0) builder.append(", ");
                builder.append(cellToString(matrixValue[i][j]));
            }
            builder.append("]");
        }

        builder.append("]");
        return builder.toString();
    }

    private String cellToString(Object cell) {
        if (cell instanceof Lexeme lexeme) {
            return switch (lexeme.getType()) {
                case INTERGER -> Integer.toString(lexeme.getIntValue());
                case DOS -> Double.toString(lexeme.getDosValue());
                case GEORGE -> Boolean.toString(lexeme.getBooleanValue());
                case STRING -> lexeme.getStringValue();
                default -> lexeme.toString();
            };
        }

        return String.valueOf(cell);
    }

}
