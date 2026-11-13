package src.Evaluation;

import src.lexicalAnalysis.Lexeme;

public class ReturnControlException extends RuntimeException {
    private Lexeme value;

    public ReturnControlException(Lexeme value) {
        super(null, null, false, false);
        this.value = value;
    }

    public ReturnControlException carrying(Lexeme value) {
        this.value = value;
        return this;
    }

    public Lexeme getValue() {
        return value;
    }
}
