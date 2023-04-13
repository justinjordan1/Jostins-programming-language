package src.lexicalAnalysis;

public enum Types {
    //DATTA TYPES
    GEORGE, INTERGER, STRING, MATRIX, CHAR, DOS, TRUE, FALSE,


    // INITIRALIZATION
    ASSIGNMENT, VAR, MATRIXSIZE, IDENTIFIER, COLLECTION,

    //LOOPING
    WHILE, INDEFINITELYPERFORM, WHILETHISISBASICALLYTRUE, FOR, FOREACH, LOOPINCREMENTPLUS, LOOPINCREMENTMINUS,
    PERCENTERROR,

    //operators
    PLUS, PLUS_PLUS, MINUS, MINUS_MINUS, INVERSE, MULTIPLY, DIVIDE,
    EXPONENTIATE, AND, OR, NOT, MINUS_UNARY, DOT_PRODUCT,

    //conditionals
    IF, ELSEIF, ELSE,
    //
    GREATER_EQ, LESS_EQ, GREATER, LESS, EQUALS, NOT_EQUAL,
    //

    //Tokens
    OBRACE, CBRACE, OBRACKET, CBRACKET, OPAREN, CPAREN, OMATRIX, CMATRIX, OARRAYLIST, CARRAYLIST, SEMI_COLON, COMMA, PERIOD, FUNC_DEFINITION, RETURN, BREAK,
    FOREACH_DELTA, GET, MATRIX_SEPERATOR,
    //special
    END_OF_FILE, ERROR, empty,
    //lexemes that dont occur naturallty
    varAssignment, arrayAssi, listAssi, MatrixAssi, whileL, whileTrue, whilePercent, matrixGetter, arrylistGetter, arrayGetter, expressionList, varInitialization,
    varIdentifierList, functionDefinitionActual, parameterList, parameter, whileBody, foorLoopBody, forEachBody, indefPreformBody, whileSortOfBody, statementList, block,
    returnStatement, parentheziedExpreission, unaryExpression, binaryExpression, funcCall, primary, collectionInitialization, collectionHelper, booleanLiteral, breakstatement,
    array, linkedList, matrix

}
