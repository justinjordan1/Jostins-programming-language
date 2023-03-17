package src.lexicalAnalysis;

public final class Lexeme {
    private final Types type;
    private final Integer lineNumber;
    private Integer intValue;
    private Double doubleValue;
    private Boolean booleanValue;
    private String stringValue;

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

    public double getDoubleValue() {
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
}
