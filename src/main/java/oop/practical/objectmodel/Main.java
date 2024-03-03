package oop.practical.objectmodel;

import oop.practical.objectmodel.interpreter.EvaluateException;
import oop.practical.objectmodel.interpreter.Interpreter;
import oop.practical.objectmodel.lisp.Lisp;
import oop.practical.objectmodel.lisp.ParseException;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        var interpreter = new Interpreter();
        var scanner = new Scanner(System.in);
        while (true) {
            var input = scanner.nextLine();
            if (input.equals("exit")) {
                break;
            }
            try {
                var ast = Lisp.parse(input);
                var result = interpreter.visit(ast);
                System.out.println(result);
            } catch (ParseException e) {
                System.out.println("Error parsing input: " + e.getMessage());
            } catch (EvaluateException e) {
                System.out.println("Error evaluating expression: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Unexpected exception: " + e.getClass().getName() + ", " + e.getMessage());
            }
        }
    }

}
