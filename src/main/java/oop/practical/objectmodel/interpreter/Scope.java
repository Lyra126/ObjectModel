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
        // To be implemented in lecture Friday 2/23
        return Optional.ofNullable(variables.get(name)); //TODO: parent?
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
