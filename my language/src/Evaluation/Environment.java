package src.Evaluation;

import src.lexicalAnalysis.Lexeme;
import src.lexicalAnalysis.Types;

import java.util.ArrayList;

import static java.lang.Integer.parseInt;
import static src.C10H15N.runtimeError;

public class Environment {
    private final Environment parent;
    private final ArrayList<NamedValue> entries;

    public Environment(Environment parent, ArrayList<NamedValue> entries) {
        this.parent = parent;
        this.entries = new ArrayList<>();
//
    }

    public Environment(Environment env) {
        this.entries = env.entries;
        this.parent = env.parent;


    }

    public Lexeme typeElevate(Lexeme value, Types targetType) {
        switch (targetType) {
            case INTERGER:
                if (value.getType() == Types.DOS) {
                    return new Lexeme(Types.INTERGER, value.getLineNumber(), (int) Math.round(value.getDosValue()));
                } else if (value.getType() == Types.GEORGE) {
                    return new Lexeme(Types.INTERGER, value.getLineNumber(), value.getBooleanValue() ? 1 : 0);
                } else if (value.getType() == Types.STRING) {
                    try {
                        return new Lexeme(Types.INTERGER, value.getLineNumber(), Integer.parseInt(value.getStringValue()));
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Cannot cast \"" + value.getStringValue() + "\" to inteRger");
                    }
                }
                break;
            case DOS:
                if (value.getType() == Types.INTERGER) {
                    return new Lexeme(Types.DOS, value.getLineNumber(), (double) value.getIntValue());
                } else if (value.getType() == Types.GEORGE) {
                    return new Lexeme(Types.DOS, value.getLineNumber(), value.getBooleanValue() ? 1.0 : 0.0);
                } else if (value.getType() == Types.STRING) {
                    try {
                        return new Lexeme(Types.DOS, value.getLineNumber(), Double.parseDouble(value.getStringValue()));
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Cannot cast \"" + value.getStringValue() + "\" to float");
                    }
                }
                break;
            case GEORGE:
                if (value.getType() == Types.INTERGER) {
                    int intValue = value.getIntValue();
                    if (intValue == 0) {
                        return new Lexeme(Types.GEORGE, value.getLineNumber(), false);
                    } else if (intValue == 1) {
                        return new Lexeme(Types.GEORGE, value.getLineNumber(), true);
                    } else {
                        throw new RuntimeException("Cannot cast " + intValue + " to boolean");
                    }
                } else if (value.getType() == Types.DOS) {
                    double floatValue = value.getDosValue();
                    if (floatValue == 0.0) {
                        return new Lexeme(Types.GEORGE, value.getLineNumber(), false);
                    } else if (floatValue == 1.0) {
                        return new Lexeme(Types.GEORGE, value.getLineNumber(), true);
                    } else {
                        throw new RuntimeException("Cannot cast " + floatValue + " to boolean");
                    }
                } else if (value.getType() == Types.STRING) {
                    return new Lexeme(Types.GEORGE, value.getLineNumber(), !value.getStringValue().isEmpty());
                }
                break;
        }
        // If the value cannot be elevated to the target type, return null
        System.out.println("casting error");
        return null;
    }

    public void update(Lexeme identifier, Lexeme newValue) {
        // Ensure this identifier is defined in this or some parent environment lookup (identifier);
        // Search this environment and update if found locally
        for (NamedValue namedValue : entries) {
            System.out.println((namedValue.getIdentifier().getStringValue().equals(identifier.getStringValue())));
            if (namedValue.getIdentifier().equals(identifier)) {

                Types declaredType = namedValue.getType();
                Types providedType = newValue.getType();
                if (providedType != declaredType)
                    newValue = typeElevate(newValue, declaredType);
                if (newValue == null)
                    error("Variable *" + identifier.getStringValue() +
                                    "' has been declared as type " + declaredType +
                                    " and cannot be assigned a value of type " + providedType,
                            identifier.getLineNumber());
                namedValue.setValue(newValue);
                // Whether a value of a valid type was provided or not,
                // quit looking once we find a match.
                return;
            } else if (namedValue.getIdentifier().getStringValue().equals(identifier.getStringValue())) {
                
                Types declaredType = namedValue.getType();
                Types providedType = newValue.getType();
                if (providedType != declaredType)
                    newValue = typeElevate(newValue, declaredType);
                if (newValue == null)
                    error("Variable *" + identifier.getStringValue() +
                                    "' has been declared as type " + declaredType +
                                    " and cannot be assigned a value of type " + providedType,
                            identifier.getLineNumber());
                namedValue.setValue(newValue);
                // Whether a value of a valid type was provided or not,
                // quit looking once we find a match.
                return;
            }
        }
        // If no local match is found, try the update in the parent environment

        parent.update(identifier, newValue);
    }


    public void add(Types type, Lexeme identifier) {
//        if (entries == null) {
//            entries = new ArrayList<>();
//        }
        NamedValue namedValue = new NamedValue(type, identifier);
        entries.add(namedValue);
    }

    public void funcAdd(Lexeme identifier, Lexeme value) {
//        if (entries == null) {
//            entries = new ArrayList<>();
//        }

        NamedValue namedValue = new NamedValue(identifier.getType(), identifier);
        namedValue.setValue(value);
        entries.add(namedValue);
    }


    public void add(Types type, Lexeme identifier, Lexeme value) {
        if (softLookup(identifier) != null) {
            error("A variable with the name " + identifier.getStringValue() +
                            "' is already defined and cannot be re-declared.",
                    identifier.getLineNumber());
        } else {
            entries.add(new NamedValue(type, identifier));
            if (value != null) update(identifier, value);
        }
    }

    public Lexeme lookup(Lexeme identifier) {
        Lexeme value = softLookup(identifier);
        //System.out.println(value + "lookupTesting");
        if (value == null) {
            if (parent != null) return parent.lookup(identifier);

            else error("'" + identifier.getStringValue() +
                    " is undefined.", identifier.getLineNumber());
        }
        return value;
    }

    private Lexeme softLookup(Lexeme identifier) {


        for (NamedValue namedValue : entries) {
            try {
                if (namedValue.getIdentifier().getStringValue().equals(identifier.getStringValue())) {
                    return namedValue.getValue();
                }
            } catch (Exception e) {
                if (namedValue.getIdentifier().equals(identifier)) {
                    return namedValue.getValue();
                }
            }

        }
        return null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Environment" + " " + this.hashCode() + ":\n");
        System.out.println(entries);
        if (entries != null) {
            for (NamedValue namedValue : entries) {
                sb.append(namedValue.getIdentifier().getStringValue());
                sb.append(": ");
                sb.append(namedValue.getType().toString());
                sb.append(" = ");
                sb.append(namedValue.getValue().toString());
                sb.append("\n");
            }
        } else {
            sb.append("Entries is null");
        }
        return sb.toString();
    }

    public void error(String message) {
        runtimeError(message, 0);
    }

    public void error(String message, int lineNumber) {
        runtimeError(message, lineNumber);
    }
}