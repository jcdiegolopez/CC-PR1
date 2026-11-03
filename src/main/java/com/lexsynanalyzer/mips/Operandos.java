package com.lexsynanalyzer.mips;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Clasifica los operandos del TAC. Un operando es un literal ({@code 5}, {@code "hola"},
 * {@code true}, {@code false}, {@code null}) o un nombre (variable o temporal) que vive en memoria.
 *
 * <p>En MIPS los booleanos son {@code 1}/{@code 0} y {@code null} es la dirección {@code 0}.
 */
public final class Operandos {

    private static final Pattern ENTERO = Pattern.compile("-?\\d+");

    private Operandos() {
    }

    public static boolean esCadena(String operando) {
        return operando.length() >= 2 && operando.startsWith("\"") && operando.endsWith("\"");
    }

    /** Contenido de un literal string, sin las comillas. */
    public static String contenidoCadena(String operando) {
        if (!esCadena(operando)) {
            throw new IllegalArgumentException("No es un literal string: " + operando);
        }
        return operando.substring(1, operando.length() - 1);
    }

    /** Enteros, booleanos y {@code null}: valores que caben en un {@code li}. */
    public static boolean esInmediato(String operando) {
        return ENTERO.matcher(operando).matches()
                || operando.equals("true") || operando.equals("false") || operando.equals("null");
    }

    /**
     * Valor numérico de un inmediato, como texto para {@code li}. Los enteros de Compiscript son de
     * 32 bits con desborde (como {@code addu}/{@code mul}), así que un literal que no cabe se reduce a
     * 32 bits: {@code 2147483648} es {@code -2147483648}, y {@code -2147483648} sale bien.
     */
    public static String valorInmediato(String operando) {
        return switch (operando) {
            case "true" -> "1";
            case "false", "null" -> "0";
            default -> {
                if (!ENTERO.matcher(operando).matches()) {
                    throw new IllegalArgumentException("No es un inmediato: " + operando);
                }
                yield String.valueOf(new BigInteger(operando).intValue());
            }
        };
    }

    public static boolean esLiteral(String operando) {
        return esInmediato(operando) || esCadena(operando);
    }
}
