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
        scope = new Scope(scope); // Set the child scope as the current scope

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
        // define the prototype getter method
        var prototypeGetter = new RuntimeValue.Function(".prototype", arguments -> {
            return object.scope().resolve("prototype", true).orElse(new RuntimeValue.Primitive(null));
        });
        object.scope().define(prototypeGetter.name(), prototypeGetter);

        // define the prototype setter method
        var prototypeSetter = new RuntimeValue.Function(".prototype=", arguments -> {

            // ensure that the number of arguments is exactly one
            if (arguments.size() != 1 ) {
                throw new EvaluateException("Setter function must take exactly one argument.");
            }
            // set the prototype of the object
            object.scope().define("prototype", arguments.getFirst());
            // return the new prototype
            return arguments.getFirst();
        });
        object.scope().define(prototypeSetter.name(), prototypeSetter);

        Scope parent = scope;

        var instance = new RuntimeValue.Function(".instance?", arguments -> {
            // ensure that the number of arguments is exactly two
            if (arguments.size() != 2) {
                throw new EvaluateException("Instance function must take exactly two arguments.");
            }

            // get the object and the prototype from the arguments
            var obj = arguments.get(0);
            var prototype = arguments.get(1);

            // get the scope of the object
            var objScope = obj instanceof RuntimeValue.Object ? ((RuntimeValue.Object) obj).scope() : null;

            // check if the prototype exists in the object's prototype chain
            boolean isInstance = false;

            // check if the prototype matches directly
            if (obj instanceof RuntimeValue.Object) {
                if (objScope.resolve(prototype.toString(), false).isPresent()) {
                    isInstance = true;
                } else {
                    // check if the prototype matches any prototype in the chain
                    Scope parentScope = objScope;
                    while (parentScope != parent) {
                        if (parentScope.resolve("prototype", true).filter(p -> p.equals(prototype)).isPresent())
                            isInstance = true;
                        parentScope = parent;
                    }
                }
            }
            // return the result as an Atom
            return isInstance ? new RuntimeValue.Primitive(":true") : new RuntimeValue.Primitive(":false");
        });
        // define the .instance? function in the object's scope
        object.scope().define(instance.name(), instance);

        if(ast.arguments().isEmpty())
            return new RuntimeValue.Object(null, object.scope());
        if (ast.arguments().size() == 1 && ast.arguments().getFirst() instanceof Ast.Variable)
            return new RuntimeValue.Object(((Ast.Variable) ast.arguments().getFirst()).name(), object.scope());


        for (Ast argument : ast.arguments()) {
            if (argument instanceof Ast.Function function && !function.name().isEmpty()) {
                var fieldName = function.name();
                if (function.arguments().size() != 1)
                    throw new EvaluateException("Unexpected number of arguments, expected 1 but received " + function.arguments().size() + ".");

                var fieldValue = visit(function.arguments().getFirst());
                object.scope().define(fieldName, fieldValue);
                var fieldGetter = new RuntimeValue.Function("." + fieldName, arguments -> {
                    // Obtain the receiver's scope dynamically
                    Scope receiverScope = object instanceof RuntimeValue.Object ? ((RuntimeValue.Object) object).scope() : scope;
                    // resolve the field within the receiver's scope
                    return receiverScope.resolve(fieldName, true).orElseThrow(AssertionError::new);
                });
                object.scope().define(fieldGetter.name(), fieldGetter);

                var fieldSetter = new RuntimeValue.Function("." + fieldName + "=", arguments -> {
                    // check if the number of arguments is not exactly one
                    if (arguments.size() != 1 )
                        throw new EvaluateException("Setter function must take exactly one argument.");
                    var newValue = arguments.getFirst();
                    // redefine the field in the object's scope with the new value
                    object.scope().define(fieldName, newValue);
                    return newValue;
                });
                object.scope().define(fieldSetter.name(), fieldSetter);

            } else if (argument instanceof Ast.Function function && function instanceof Ast.Function declaration) {
                // extract method information from the method declaration
                String methodName = ((Ast.Function) declaration.arguments().get(0)).name(); // Method name

                // ensure the method declaration has at least two arguments
                if (declaration.arguments().size() < 2)
                    throw new EvaluateException("Invalid method declaration: " + declaration);

                Ast methodArgsAst = declaration.arguments().get(0);
                Ast methodBodyAst = declaration.arguments().get(1);

                // define the method on the object
                var method = new RuntimeValue.Function( methodName, arguments -> {
                    // when the method is invoked, execute it within the scope of the receiver
                    object.scope().define("this", object);
                    // bind method arguments to their respective values in the method's scope
                    if (methodArgsAst instanceof Ast.Function methodArgs) {
                        if (methodArgs.arguments().size() != arguments.size())
                            throw new EvaluateException("Method '" + methodName + "' expects " +
                                    methodArgs.arguments().size() + " arguments, but received " + arguments.size() + ".");

                        for (int i = 0; i < methodArgs.arguments().size(); i++) {
                            Ast.Variable argVariable = (Ast.Variable) methodArgs.arguments().get(i);
                            object.scope().define(argVariable.name(), arguments.get(i));
                        }
                    } else
                        throw new EvaluateException("Method arguments must be a function declaration: " + methodArgsAst);

                    return visit(methodBodyAst);
                });

                // define the method in the object's scope
                object.scope().define(method.name(), method);
            } else
                throw new EvaluateException("Invalid member definition.");
        }
        return object;
    }



    private RuntimeValue visitFunction(Ast.Function ast) throws EvaluateException {
        var value = scope.resolve(ast.name(), false)
                .orElseThrow(() -> new EvaluateException("Undefined function " + ast.name() + "."));
        if (value instanceof RuntimeValue.Function function) {
            var arguments = new ArrayList<RuntimeValue>();
            for (Ast argument : ast.arguments())
                arguments.add(visit(argument));
            return function.definition().invoke(arguments);
        } else
            throw new EvaluateException("RuntimeValue " + value + " (" + value.getClass() + ") is not an invokable function.");
    }



    private RuntimeValue visitMethod(Ast.Function ast) throws EvaluateException {
        // ensure that the AST represents a method call
        if (ast.name() == null || !ast.name().startsWith("."))
            throw new EvaluateException("Invalid method call.");

        // extract the method name from the AST
        String methodName = ast.name();

        // ensure there is at least one argument
        if (ast.arguments().isEmpty())
            throw new EvaluateException("Method '" + methodName + "' must have at least 1 argument");

        // get the runtime object from the first argument
        Ast runtimeObjectAst = ast.arguments().getFirst();
        RuntimeValue runtimeObject = visit(runtimeObjectAst);
        if (!(runtimeObject instanceof RuntimeValue.Object))
            throw new EvaluateException("Method must be called on an object.");

        // lookup the function object's scope
        Scope functionScope = ((RuntimeValue.Object) runtimeObject).scope();
        // retrieve the specified method from the scope

        var func = functionScope.resolve(methodName, true).orElseThrow(() -> new EvaluateException("Method '" + methodName + "' not found in object."));
        //call method
        if (!(func instanceof RuntimeValue.Function))
            throw new EvaluateException("Make sure it's a runtime value func");

        //handles when arguments are provided, like setters
        if (ast.arguments().size() > 1) {
            List<RuntimeValue> args = new ArrayList<>();
            if(methodName.endsWith("=")){
                args.add(visit(ast.arguments().get(1)));
                return ((RuntimeValue.Function) func).definition().invoke(args);
            }

            for (int i = 0; i < ast.arguments().size(); i++)
                args.add(visit(ast.arguments().get(i)));

            return ((RuntimeValue.Function) func).definition().invoke(args);
        }
        //obtain the receiver's scope dynamically
        Scope receiverScope = runtimeObject instanceof RuntimeValue.Object ? ((RuntimeValue.Object) runtimeObject).scope() : scope;
        Scope currentScope = scope;


        // temporarily set the receiver's scope as the current scope
        try {
            scope = receiverScope;
            // invoke the method definition within the receiver's scope
            // no arguments involved in this method invocation
            return ((RuntimeValue.Function) func).definition().invoke(List.of());
        } finally {
            // restore the original current scope
            scope = currentScope;
        }
    }
}