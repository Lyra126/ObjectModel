package oop.practical.objectmodel.lisp;

import java.math.BigDecimal;
import java.util.List;

public sealed interface Ast {

    record Number(
        BigDecimal value
    ) implements Ast {}

    record Atom(
        java.lang.String name
    ) implements Ast {}

    record Variable(
        java.lang.String name
    ) implements Ast {}

    record Function(
        java.lang.String name,
        List<Ast> arguments
    ) implements Ast {}

}
