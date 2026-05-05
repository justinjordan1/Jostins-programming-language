package src.Evaluation;

import src.lexicalAnalysis.Lexeme;

public class ReturnControlException extends RuntimeException {
    private final Lexeme value;

    public ReturnControlException(Lexeme value) {
        super(null, null, false, false);
        this.value = value;
    }

    public Lexeme getValue() {
        return value;
    }
}
