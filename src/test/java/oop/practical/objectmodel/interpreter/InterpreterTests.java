package oop.practical.objectmodel.interpreter;

import oop.practical.objectmodel.lisp.Lisp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class InterpreterTests {

    /**
     * Tests included here will be run against the Admin solution as part of the
     * test submission (similar to Practical 1) - this function allows exporting
     * your tests while still allowing whatever test structure you prefer.
     */
    public static Stream<Arguments> getExportedTests() {
        return Stream.of(
            ExpressionTests.testLiteral(),
            ExpressionTests.testFunction(),
            BuiltinTests.testDo(),
            BuiltinTests.testDo(),
            BuiltinTests.testSet(),
            ObjectTests.testObject(),
            ObjectTests.testPrototype()
        ).flatMap(s -> s);
    }

    @Nested
    class ExpressionTests {

        @ParameterizedTest
        @MethodSource
        public void testLiteral(String name, String input, String expected) {
            test(input, expected);
        }

        private static Stream<Arguments> testLiteral() {
            return Stream.of(
                Arguments.of("Integer", """
                    1
                    """, "1"),
                Arguments.of("Decimal", """
                    1.0
                    """, "1.0"),
                Arguments.of("Atom", """
                    :name
                    """, ":name")
            );
        }

        @ParameterizedTest
        @MethodSource
        public void testFunction(String name, String input, String expected) {
            test(input, expected);
        }

        private static Stream<Arguments> testFunction() {
            return Stream.of(
                Arguments.of("Add", """
                    (+ 1 2)
                    """, "3"),
                Arguments.of("Sub Single", """
                    (- 1)
                    """, "-1"),
                Arguments.of("Sub Multiple", """
                    (- 1 2 3)
                    """, "-4"),
                Arguments.of("Mul", """
                    (* 1 2 3 4)
                    """, "24"),
                Arguments.of("Div Single", """
                    (/ 2.0)
                    """, "0.5"),
                Arguments.of("Div Multiple", """
                    (/ 2.0)
                    """, "0.5")
            );
        }

    }

    @Nested
    class BuiltinTests {

        @ParameterizedTest
        @MethodSource
        public void testDo(String name, String input, String expected) {
            test(input, expected);
        }

        private static Stream<Arguments> testDo() {
            return Stream.of(
                Arguments.of("Empty", """
                    (do)
                    """, "null"),
                Arguments.of("Multiple", """
                    (do 1 2 3)
                    """, "3"),
                Arguments.of("Scope Enter", """
                    (do (def x 1) x)
                    """, "1"),
                Arguments.of("Scope Exit", """
                    (do (def x 1))
                    x
                    """, null),
                Arguments.of("Scope Nesting", """
                    (do (def x 1)
                        (do (def y 2)
                            (do (+ x y))))
                    """, "3")
            );
        }

        @ParameterizedTest
        @MethodSource
        public void testDef(String name, String input, String expected) {
            test(input, expected);
        }

        private static Stream<Arguments> testDef() {
            return Stream.of(
                Arguments.of("Variable", """
                    (def x 1)
                    x
                    """, "1"),
                Arguments.of("Function", """
                    (def (f) 1)
                    (f)
                    """, "1"),
                Arguments.of("Function Parameters", """
                    (def (add x y) (+ x y))
                    (add 1 2)
                    """, "3"),
                Arguments.of("Invalid", """
                    (def 1)
                    """, null),
                Arguments.of("Redefined", """
                    (def x 1)
                    (def x 2)
                    """, null)
            );
        }

        @ParameterizedTest
        @MethodSource
        public void testSet(String name, String input, String expected) {
            test(input, expected);
        }

        private static Stream<Arguments> testSet() {
            return Stream.of(
                Arguments.of("Variable", """
                    (def x 1)
                    (set! x 2)
                    """, "2"),
                Arguments.of("Undefined", """
                    (set! x 1)
                    """, null),
                Arguments.of("Invalid", """
                    (def x 1)
                    (set! (x) 2)
                    """, null)
            );
        }

    }

    @Nested
    class ObjectTests {

        @ParameterizedTest
        @MethodSource
        public void testObject(String name, String input, String expected) {
            test(input, expected);
        }

        public static Stream<Arguments> testObject() {
            return Stream.of(
                Arguments.of("Empty", """
                    (object)
                    """, "(object)"),
                Arguments.of("Name", """
                    (object Name)
                    """, "(object Name)"),
                Arguments.of("Field", """
                    (object [field 1])
                    """, "(object [field 1])"),
                Arguments.of("Field Getter", """
                    (def obj (object [field 1]))
                    (.field obj)
                    """, "1"),
                Arguments.of("Field Setter", """
                    (def obj (object [field 1]))
                    (.field= obj 2)
                    (.field obj)
                    """, "2"),
                Arguments.of("Method", """
                    (object [(.method) 1])
                    (.method object)
                    """, "1"),
                Arguments.of("Method Scope", """
                    (object [.field 1] [(.method) field])
                    (.method object)
                    """, "1"),
                Arguments.of("Not Object Instance", """
                    (def obj (object))
                    (.instance? obj Object)
                    """, ":false")
            );
        }

        @ParameterizedTest
        @MethodSource
        public void testPrototype(String name, String input, String expected) {
            test(input, expected);
        }

        public static Stream<Arguments> testPrototype() {
            return Stream.of(
                Arguments.of("Get", """
                    (.prototype (object))
                    """, "null"),
                Arguments.of("Set", """
                    (def obj (object))
                    (.prototype= obj Object)
                    (.prototype obj)
                    """, "(object Object)"),
                Arguments.of("Inherit Method", """
                    (def parent (object [(.method) 1]))
                    (def child (object [prototype parent]))
                    (.method child)
                    """, "1"),
                Arguments.of("Override Method", """
                    (def parent (object [(.method) 1]))
                    (def child (object [prototype parent] [.method 2]))
                    (.method child)
                    """, "2"),
                Arguments.of("Prototype Instance", """
                    (def parent (object))
                    (def child (object [prototype parent]))
                    (.instance? child parent)
                    """, ":true")
            );
        }

    }

    static void test(String input, String expected) {
        var ast = Assertions.assertDoesNotThrow(() -> Lisp.parse("(do " + input + ")"));
        if (expected != null) {
            var result = Assertions.assertDoesNotThrow(() -> new Interpreter().visit(ast));
            Assertions.assertEquals(expected, result.toString());
        } else {
            Assertions.assertThrows(EvaluateException.class, () -> new Interpreter().visit(ast));
        }
    }

}
