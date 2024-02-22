package oop.practical.objectmodel.interpreter;

import java.util.List;
import java.util.stream.Collectors;

public sealed interface RuntimeValue {

    record Primitive(
        java.lang.Object value
    ) implements RuntimeValue {

        @Override
        public String toString() {
            return String.valueOf(value);
        }

    }

    record Function(
        String name,
        Definition definition
    ) implements RuntimeValue {

        @FunctionalInterface
        interface Definition {
            RuntimeValue invoke(List<RuntimeValue> arguments) throws EvaluateException;
        }

        @Override
        public String toString() {
            return "(function " + name + " " + definition + ")";
        }

    }

    record Object(
        String name,
        Scope scope
    ) implements RuntimeValue {

        @Override
        public String toString() {
            var name = this.name != null ? " " + this.name : "";
            var fields = scope.collect(true).entrySet().stream()
                .filter(e -> !e.getKey().startsWith(".")) //filter out methods
                .map(e -> " [" + e.getKey() + " " + e.getValue() + "]")
                .collect(Collectors.joining(""));
            return "(object" + name + fields + ")";
        }

    }

}
