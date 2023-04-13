package src.Evaluation;

import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

public class NamedValue {
    private final Types type;
    private final Lexeme name;
    private Lexeme value;


    public NamedValue(Types type, Lexeme name) {
        this.type = type;
        this.name = name;
        this.value = new Lexeme(null);
    }

    public Types getType() {
        return type;
    }

    public Lexeme getIdentifier() {
        return name;
    }

    public Lexeme getValue() {
        return value;
    }

    public void setValue(Lexeme newValue) {
        this.value = value;
    }

    public String toString() {
        return name.getStringValue() + ": " + value.toString() + "( " + type + ")";
    }
}
