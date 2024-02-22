package oop.practical.objectmodel.lisp;

import java.util.ArrayList;
import java.util.List;

final class Lexer {

    private final CharStream chars;

    Lexer(String input) {
        chars = new CharStream(input);
    }

    List<Token> lex() {
        var tokens = new ArrayList<Token>();
        while (chars.has(0)) {
            while (match("[ \n\r\r]")) {}
            chars.emit(Token.Type.OPERATOR);
            if (peek("[0-9]") || peek("[+\\-]", "[0-9]")) {
                tokens.add(lexNumber());
            } else if (peek("[A-Za-z0-9_+\\-*/<>=.:!?]")) {
                tokens.add(lexIdentifier());
            } else if (chars.has(0)) {
                tokens.add(lexOperator());
            }
        }
        return tokens;
    }

    private Token lexNumber() {
        match("[+\\-]");
        while (match("[0-9]")) {}
        if (match('.', "[0-9]")) {
            while (match("[0-9]")) {}
        }
        if (match("e")) {
            match("[+\\-]");
            while (match("[0-9]")) {}
        }
        return chars.emit(Token.Type.NUMBER);
    }

    private Token lexIdentifier() {
        while (match("[A-Za-z0-9_+\\-*/<>=.:!?]")) {}
        return chars.emit(Token.Type.IDENTIFIER);
    }

    private Token lexOperator() {
        chars.advance(1);
        return chars.emit(Token.Type.OPERATOR);
    }

    private boolean peek(Object... objects) {
        for (var i = 0; i < objects.length; i++) {
            if (!chars.has(i) || !test(objects[i], chars.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean match(Object... objects) {
        var peek = peek(objects);
        if (peek) {
            chars.advance(objects.length);
        }
        return peek;
    }

    private static boolean test(Object object, char character) {
        return switch (object) {
            case Character c -> character == c;
            case String regex -> Character.toString(character).matches(regex);
            case List<?> options -> options.stream().anyMatch(o -> test(o, character));
            default -> throw new AssertionError(object);
        };
    }

    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        private CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + length + offset < input.length();
        }

        public char get(int offset) {
            if (!has(offset)) {
                throw new IllegalArgumentException("Broken lexer invariant.");
            }
            return input.charAt(index + length + offset);
        }

        public void advance(int chars) {
            length += chars;
        }

        public Token emit(Token.Type type) {
            var token = new Token(type, input.substring(index, index + length));
            index += length;
            length = 0;
            return token;
        }

    }

}
