package oop.practical.objectmodel.interpreter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Scope {

    private final Scope parent;
    private final Map<String, RuntimeValue> variables = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public void define(String name, RuntimeValue object) {
        variables.put(name, object);
    }

    public Optional<RuntimeValue> resolve(String name, boolean current) {
        // Implemented in M3L5.5 recording
        if (variables.containsKey(name)) {
            return Optional.of(variables.get(name));
        } else if (parent != null && !current) {
            return parent.resolve(name, current);
        } else {
            return Optional.empty();
        }
    }

    public Map<String, RuntimeValue> collect(boolean current) {
        if (current || parent == null) {
            return new LinkedHashMap<>(variables);
        } else {
            var map = parent.collect(false);
            map.putAll(variables);
            return map;
        }
    }

}
