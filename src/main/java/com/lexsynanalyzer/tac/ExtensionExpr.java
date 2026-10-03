package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.parser.LexSynAnalyzerParser.CallExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.PrimaryAtomContext;

/**
 * Puntos donde {@link ExprTAC} delega construcciones de otros dueños: llamadas a función (B) y
 * {@code new}/{@code this}/llamadas a método (C). Cada método devuelve el operando con el resultado.
 */
public interface ExtensionExpr {

    ExtensionExpr NINGUNA = new ExtensionExpr() { };

    /** {@code new Clase(...)} o {@code this}. */
    default String atomo(PrimaryAtomContext atomo) {
        throw pendiente("new / this");
    }

    /** {@code f(args)} donde {@code funcion} es el operando ya resuelto de {@code f}. */
    default String llamada(String funcion, CallExprContext llamada) {
        throw pendiente("llamada a función");
    }

    /** {@code objeto.metodo(args)}. */
    default String metodo(String objeto, String metodo, CallExprContext llamada) {
        throw pendiente("llamada a método");
    }

    private static UnsupportedOperationException pendiente(String construccion) {
        return new UnsupportedOperationException("Aún no implementado en el generador TAC: " + construccion);
    }
}
