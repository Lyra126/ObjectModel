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

    static RuntimeValue sub(List<RuntimeValue> raw) {
        throw new UnsupportedOperationException("TODO");
    }

    static RuntimeValue mul(List<RuntimeValue> raw) {
        throw new UnsupportedOperationException("TODO");
    }

    static RuntimeValue div(List<RuntimeValue> raw) {
        throw new UnsupportedOperationException("TODO");
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
