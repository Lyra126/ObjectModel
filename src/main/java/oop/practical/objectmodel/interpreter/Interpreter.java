package oop.practical.objectmodel.interpreter;

import oop.practical.objectmodel.lisp.Ast;

import java.util.ArrayList;

public final class Interpreter {

    private Scope scope;

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
        for (Ast expression : ast.arguments()) {
            result = visit(expression); //TODO: Scope?
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
            var definition = new RuntimeValue.Function(function.name(), arguments -> {
                // To be implemented in lecture Wednesday 2/21
                return visit(body); //TODO: Arguments/Scope?
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
     */
    private RuntimeValue visitBuiltinObject(Ast.Function ast) {
        throw new UnsupportedOperationException("TODO");
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

    private RuntimeValue visitMethod(Ast.Function ast) {
        throw new UnsupportedOperationException("TODO");
    }

}
