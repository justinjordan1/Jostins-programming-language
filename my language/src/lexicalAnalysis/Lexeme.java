package src.lexicalAnalysis;

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

    //public Boolean isEqual(Lexeme other) {
    //return this.isEqua}

    public String toString() {
        String str = type + " Line: " + lineNumber + " ";
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
        }
        return str;
    }

    public void addvalue(String str) {
        str += " value:";
    }


    // --------------- Printing Lexemes as Parse Trees ---------------

    public void printAsParseTree() {
        System.out.println(getPrintableTree(this, 0));
        //System.out.println(this.children);

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
}


