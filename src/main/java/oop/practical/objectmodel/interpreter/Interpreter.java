package oop.practical.objectmodel.interpreter;

import oop.practical.objectmodel.lisp.Ast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class Interpreter {

    private Scope scope;
    private Ast fieldAst;

    public Interpreter() {
        scope = new Scope(null);
        scope.define("null", new RuntimeValue.Primitive(null));
        scope.define("+", new RuntimeValue.Function("+", Functions::add));
        scope.define("-", new RuntimeValue.Function("-", Functions::sub));
        scope.define("*", new RuntimeValue.Function("*", Functions::mul));
        scope.define("/", new RuntimeValue.Function("/", Functions::div));
        scope.define("Object", new RuntimeValue.Object("Object", new Scope(scope)));
    }

    public Interpreter(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    public RuntimeValue visit(Ast ast) throws EvaluateException {
        return switch (ast) {
            case Ast.Number number -> visit(number);
            case Ast.Atom atom -> visit(atom);
            case Ast.Variable variable -> visit(variable);
            case Ast.Function function -> visit(function);
        };
    }

    public RuntimeValue visit(Ast.Number ast) {
        return new RuntimeValue.Primitive(ast.value());
    }

    public RuntimeValue visit(Ast.Atom ast) {
        //Our language doesn't have strings so this is sufficient, but the
        //better practice would be to define a proper type.
        return new RuntimeValue.Primitive(":" + ast.name());
    }

    public RuntimeValue visit(Ast.Variable ast) throws EvaluateException {
        return scope.resolve(ast.name(), false)
            .orElseThrow(() -> new EvaluateException("Undefined variable " + ast.name() + "."));
    }

    public RuntimeValue visit(Ast.Function ast) throws EvaluateException {
        return switch (ast.name()) {
            case "do" -> visitBuiltinDo(ast);
            case "def" -> visitBuiltinDef(ast);
            case "set!" -> visitBuiltinSet(ast);
            case "object" -> visitBuiltinObject(ast);
            default -> ast.name().startsWith(".") ? visitMethod(ast) : visitFunction(ast);
        };
    }

    /**
     *  - Do: (do [expressions])
     */
    private RuntimeValue visitBuiltinDo(Ast.Function ast) throws EvaluateException {
        RuntimeValue result = new RuntimeValue.Primitive(null);
        Scope parent = scope; // Save the parent scope
        Scope childScope = new Scope(scope); // Create a child scope for the do block
        scope = childScope; // Set the child scope as the current scope

        try {
            for (Ast expression : ast.arguments()) {
                result = visit(expression); // Visit each expression in the do block
            }
        } finally {
            scope = parent; // Restore the parent scope
        }

        return result;
    }

    /**
     *  - Variable: (def <name> <value>)
     *  - Function: (def (<name> [parameters]) <body>)
     */
    private RuntimeValue visitBuiltinDef(Ast.Function ast) throws EvaluateException {
        if (ast.arguments().size() != 2) {
            throw new EvaluateException("Builtin function def requires exactly 2 arguments.");
        }
        if (ast.arguments().getFirst() instanceof Ast.Variable variable) {
            if (scope.resolve(variable.name(), true).isPresent()) {
                throw new EvaluateException("Redefined identifier " + variable.name() + ".");
            }
            var value = visit(ast.arguments().getLast());
            scope.define(variable.name(), value);
            return value;
        } else if (ast.arguments().getFirst() instanceof Ast.Function function) {
            if (scope.resolve(function.name(), true).isPresent()) {
                throw new EvaluateException("Redefined identifier " + function.name() + ".");
            }
            var parameters = new ArrayList<String>();
            for (Ast parameter : function.arguments()) {
                if (!(parameter instanceof Ast.Variable)) {
                    throw new EvaluateException("Invalid function parameter form for builtin function def.");
                }
                parameters.add(((Ast.Variable) parameter).name());
            }
            var body = ast.arguments().getLast();
            var parent = scope;
            var definition = new RuntimeValue.Function(function.name(), arguments -> {
                // Implemented in M3L5.5 recording
                var child = new Scope(parent);
                if (arguments.size() != parameters.size()) {
                    throw new EvaluateException("Expected " + parameters.size() + " arguments, received " + arguments.size() + ".");
                }
                for (int i = 0; i < arguments.size(); i++) {
                    child.define(parameters.get(i), arguments.get(i));
                }
                var current = scope;
                try {
                    scope = child;
                    return visit(body);
                } finally {
                    scope = current;
                }
            });
            scope.define(function.name(), definition);
            return definition;
        } else {
            throw new EvaluateException("Invalid variable/function form for builtin function def.");
        }
    }

    /**
     * Variable: (set! <name> <value>)
     */
    private RuntimeValue visitBuiltinSet(Ast.Function ast) throws EvaluateException {
        if (ast.arguments().size() != 2) {
            throw new EvaluateException("Builtin function set! requires exactly 2 arguments.");
        } else if (ast.arguments().getFirst() instanceof Ast.Variable variableAst) {
            if (scope.resolve(variableAst.name(), false).isEmpty()) {
                throw new EvaluateException("Undefined variable " + ast.name() + ".");
            }
            var value = visit(ast.arguments().getLast());
            scope.define(variableAst.name(), value);
            return value;
        } else {
            throw new EvaluateException("Invalid variable form for builtin function set!.");
        }
    }

    /**
     *  - Field: (object [<name> <value>])
     *  - Method: (object [(<.name> [arguments]) <body>])
     *     - Note: Since all functions require a name (Ast.Variable) and the
     *       first element of this list is an Ast.Function, the parser returns
     *       an Ast.Function with an empty name (""). In other words:
     *       new Ast.Function("", new Ast.Function(".name", arguments), body);
     */
    private RuntimeValue visitBuiltinObject(Ast.Function ast) throws EvaluateException {
        String name =ast.name();
        var object = new RuntimeValue.Object(null, new Scope(scope));

        //all objects must define the .prototype/.prototype= methods even if they don't have a prototype (this ensures the prototype can be set or unset)
        // Define the prototype getter method
        var prototypeGetter = new RuntimeValue.Function(".prototype", arguments -> {
            // Return the prototype of the object
            var prototype = object.scope().resolve("prototype", true);

            // If a prototype is found, return it; otherwise, return null
            return prototype.orElse(new RuntimeValue.Primitive(null));
        });
        object.scope().define(prototypeGetter.name(), prototypeGetter);

        // Define the prototype setter method
        var prototypeSetter = new RuntimeValue.Function(".prototype=", arguments -> {
            // Ensure that the number of arguments is exactly one
            if (arguments.size() != 1) {
                throw new EvaluateException("Setter function must take exactly one argument.");
            }

            // Set the prototype of the object
            object.scope().define("prototype", arguments.get(0));

            // Return the new prototype
            return arguments.get(0);
        });
        object.scope().define(prototypeSetter.name(), prototypeSetter);

        if(ast.arguments().isEmpty() && ast.name().equals("object"))
            return new RuntimeValue.Object(null, object.scope());
        if (ast.arguments().size() == 1 && ast.arguments().get(0) instanceof Ast.Variable)
            return new RuntimeValue.Object(((Ast.Variable) ast.arguments().get(0)).name(), object.scope());

        for (Ast argument : ast.arguments()) {
            if (argument instanceof Ast.Function function && !function.name().isEmpty()) {
                System.out.println("test1");
                var fieldName = function.name();
                if (function.arguments().size() != 1) {
                    throw new EvaluateException("Unexpected number of arguments, expected 1 but received " + function.arguments().size() + ".");
                }
                var fieldValue = visit(function.arguments().getFirst());
                object.scope().define(fieldName, fieldValue);
                var fieldGetter = new RuntimeValue.Function("." + fieldName, arguments -> {
                    //TODO: object.scope() is INCORRECT with inheritance - this should be the receiver's scope!
                    //Remember, with inheritance 'this' is not always the parent; it can be the child instance.
                    return object.scope().resolve(fieldName, true).orElseThrow(AssertionError::new);
                });
                object.scope().define(fieldGetter.name(), fieldGetter);

                var fieldSetter = new RuntimeValue.Function("." + fieldName + "=", arguments -> {
                    // Check if the number of arguments is not exactly one
                    if (arguments.size() != 1) {
                        throw new EvaluateException("Setter function must take exactly one argument.");
                    }

                    // Get the new value from the arguments list
                    var newValue = arguments.get(0);

                    // Redefine the field in the object's scope with the new value
                    object.scope().resolve(fieldName, true).orElseThrow(AssertionError::new);

                    // Return the new value
                    return newValue;
                });

                // Define the field setter in the object's scope
                object.scope().define(fieldSetter.name(), fieldSetter);

            }  else if (argument instanceof Ast.Function function && function instanceof Ast.Function declaration) {
                System.out.println("test2");
                // Extract method information from the method declaration
                String methodName = declaration.name(); // Method name

                // Ensure the method declaration has at least two arguments
                if (declaration.arguments().size() < 2 || !(declaration.arguments().get(0) instanceof Ast.Function)) {
                    throw new EvaluateException("Invalid method declaration.");
                }

                Ast.Function methodArgs = (Ast.Function) declaration.arguments().get(0); // Method arguments
                Ast methodBody = declaration.arguments().get(1); // Method body

                // Define the method on the object
                RuntimeValue method = new RuntimeValue.Function(methodName, args -> {
                    // When the method is invoked, execute it within the scope of the receiver
                    Scope receiverScope = object.scope();

                    // Define 'this' as a variable in the method's scope
                    receiverScope.define("this", object);

                    // Bind method arguments to their respective values in the method's scope
                    if (methodArgs.arguments().size() != args.size()) {
                        throw new EvaluateException("Method '" + methodName + "' expects " + methodArgs.arguments().size() + " arguments, but received " + args.size() + ".");
                    }

                    for (int i = 0; i < methodArgs.arguments().size(); i++) {
                        Ast.Variable argVariable = (Ast.Variable) methodArgs.arguments().get(i);
                        receiverScope.define(argVariable.name(), args.get(i));
                    }

                    // Execute the method body
                    return visit(methodBody);
                });

                // Define the method in the object's scope
                object.scope().define("." + methodName, method);
            } else {
                throw new EvaluateException("Invalid member definition.");
            }
        }

        System.out.println("test3");
        return object;
    }



    private RuntimeValue visitFunction(Ast.Function ast) throws EvaluateException {
        var value = scope.resolve(ast.name(), false)
            .orElseThrow(() -> new EvaluateException("Undefined function " + ast.name() + "."));
        if (value instanceof RuntimeValue.Function function) {
            var arguments = new ArrayList<RuntimeValue>();
            for (Ast argument : ast.arguments()) {
                arguments.add(visit(argument));
            }
            return function.definition().invoke(arguments);
        } else {
            throw new EvaluateException("RuntimeValue " + value + " (" + value.getClass() + ") is not an invokable function.");
        }
    }



    private RuntimeValue visitMethod(Ast.Function ast) throws EvaluateException {
        // Ensure that the AST represents a method call
        if (ast.name() == null || !ast.name().startsWith(".")) {
            throw new EvaluateException("Invalid method call.");
        }

        // Extract the method name from the AST
        String methodName = ast.name().substring(1);

        // Ensure there is at least one argument
        if (ast.arguments().isEmpty()) {
            throw new EvaluateException("Method call must have at least one argument.");
        }

        // Get the runtime object from the first argument
        Ast runtimeObjectAst = ast.arguments().getFirst();
        RuntimeValue runtimeObject = visit(runtimeObjectAst);
        if (!(runtimeObject instanceof RuntimeValue.Object)) {
            throw new EvaluateException("Method must be called on an object.");
        }

        // Lookup the function object's scope
        Scope functionScope = ((RuntimeValue.Object) runtimeObject).scope();

        // Retrieve the specified method from the scope
        return functionScope.resolve(methodName, true).orElseThrow(() -> new EvaluateException("Method '" + methodName + "' not found in object."));
    }

}
