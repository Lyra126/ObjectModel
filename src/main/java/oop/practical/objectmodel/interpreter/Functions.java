package oop.practical.objectmodel.interpreter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class Functions {

    static RuntimeValue add(List<RuntimeValue> raw) throws EvaluateException {
        var arguments = parse(raw);
        var result = BigDecimal.ZERO;
        for (var number : arguments) {
            result = result.add(number);
        }
        return new RuntimeValue.Primitive(result);
    }

    static RuntimeValue sub(List<RuntimeValue> raw) throws EvaluateException {
        var arguments = parse(raw);

        if(arguments.isEmpty())
            throw new EvaluateException("No arguments");

        //if list only has one argument, subtract against 0
        if(arguments.size() == 1)
            return new RuntimeValue.Primitive(BigDecimal.ZERO.subtract(arguments.getFirst()));

        //if list has multiple arguments
        var result = arguments.getFirst();
        for (int i = 1; i < arguments.size(); i++)
            if (arguments.get(i) != null)
                result = result.subtract(arguments.get(i));

        return new RuntimeValue.Primitive(result);
    }

    static RuntimeValue mul(List<RuntimeValue> raw) throws EvaluateException{
        var arguments = parse(raw);
        if(arguments.isEmpty())
            return new RuntimeValue.Primitive(BigDecimal.ONE);

        //if list has 1 item, return it
        if(arguments.size() == 1)
            return new RuntimeValue.Primitive(BigDecimal.ONE.multiply(arguments.getFirst()));

        //if item has multiple items, multiply all
        var result = BigDecimal.ONE;
        for (var number : arguments) {
            result = result.multiply(number);
        }
        return new RuntimeValue.Primitive(result);
    }

    static RuntimeValue div(List<RuntimeValue> raw) throws EvaluateException{
        var arguments = parse(raw);
        // if list is empty throw exception
        if (arguments.isEmpty())
            throw new EvaluateException("Unable to divide nothing");

        // if 1 argument, return it
        if (arguments.size() == 1) {
            if (arguments.getFirst().equals(BigDecimal.ZERO))
                throw new EvaluateException("Division by zero");
            return new RuntimeValue.Primitive(BigDecimal.ONE.divide(arguments.getFirst(), arguments.getFirst().scale(), RoundingMode.HALF_EVEN));
        }

        var result = arguments.getFirst();  // Start with the first argument

        for (int i = 1; i < arguments.size(); i++) {
            if (arguments.get(i).equals(BigDecimal.ZERO))
                throw new EvaluateException("Division by Zero");
            result = result.divide(arguments.get(i), result.scale(), RoundingMode.HALF_EVEN);
        }

        return new RuntimeValue.Primitive(result);
    }

    private static List<BigDecimal> parse(List<RuntimeValue> raw) throws EvaluateException {
        var numbers = new ArrayList<BigDecimal>();
        for (RuntimeValue argument : raw) {
            if (argument instanceof RuntimeValue.Primitive primitive && primitive.value() instanceof BigDecimal number) {
                numbers.add(number);
            } else {
                throw new EvaluateException("Invalid argument " + argument + " (" + argument.getClass() + "), expected a number.");
            }
        }
        return numbers;
    }

}
