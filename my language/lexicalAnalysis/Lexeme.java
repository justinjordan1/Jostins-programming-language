package lexicalAnalysis;

import java.util.ArrayList;

public class Lexeme {
    private Types type;
    private Integer lineNumber;
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

}
