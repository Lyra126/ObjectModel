package oop.practical.objectmodel.lisp;

record Token(
    Type type,
    String value
) {

    enum Type {
        NUMBER,
        IDENTIFIER,
        OPERATOR,
    }

}
