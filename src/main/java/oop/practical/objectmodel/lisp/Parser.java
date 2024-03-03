package oop.practical.objectmodel.lisp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class Parser {

    private final TokenStream tokens;

    Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    Ast parse() throws ParseException {
        if (match(Token.Type.NUMBER)) {
            return new Ast.Number(new BigDecimal(tokens.get(-1).value()));
        } else if (match(Token.Type.IDENTIFIER)) {
            var identifier = tokens.get(-1).value();
            return identifier.length() > 1 && identifier.charAt(0) == ':'
                ? new Ast.Atom(identifier.substring(1))
                : new Ast.Variable(identifier);
        } else if (match(List.of("(", "["))) {
            var open = tokens.get(-1).value();
            var name = match(Token.Type.IDENTIFIER) ? tokens.get(-1).value() : "";
            var arguments = new ArrayList<Ast>();
            while (!match(open.equals("(") ? ")" : "]")) {
                arguments.add(parse());
            }
            return new Ast.Function(name, arguments);
        } else if (tokens.has(0)) {
            throw new ParseException("Expected an expression at token " + tokens.index + ", received " + tokens.get(0) + ".");
        } else {
            throw new ParseException("Unexpected end of input at token " + tokens.index + ".");
        }
    }

    private boolean peek(Object... objects) {
        for (var i = 0; i < objects.length; i++) {
            if (!tokens.has(i) || !test(objects[i], tokens.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean match(Object... objects) {
        var peek = peek(objects);
        if (peek) {
            tokens.advance(objects.length);
        }
        return peek;
    }

    private static boolean test(Object object, Token token) {
        return switch (object) {
            case Token.Type type -> token.type() == type;
            case String value -> token.value().equals(value);
            case List<?> options -> options.stream().anyMatch(o -> test(o, token));
            default -> throw new AssertionError(object);
        };
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        public Token get(int offset) {
            if (!has(offset)) {
                throw new IllegalArgumentException("Broken lexer invariant.");
            }
            return tokens.get(index + offset);
        }

        public void advance(int tokens) {
            index += tokens;
        }

    }

}
