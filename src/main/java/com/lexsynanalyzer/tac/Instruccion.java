package com.lexsynanalyzer.tac;

import java.util.Objects;
import java.util.Set;

/**
 * Una instrucción de tres direcciones: {@code op, arg1, arg2, resultado}.
 *
 * <p>{@code op} es el mnemónico (constantes {@code OP_*}) o, en las instrucciones binarias, el
 * propio operador ({@code +}, {@code <}, ...). Los campos que una instrucción no usa quedan en
 * {@code null}. La sintaxis de texto es la tabla del §3.1 de {@code PLAN_PROYECTO2.md}.
 *
 * <p>Convenciones de campos por instrucción:
 * <ul>
 *   <li>{@code t = a[i]}: a1=arreglo, a2=índice, res=destino.</li>
 *   <li>{@code a[i] = b}: a1=arreglo, a2=índice, res=valor.</li>
 *   <li>{@code t = obj.campo}: a1=objeto, a2=campo, res=destino.</li>
 *   <li>{@code obj.campo = b}: a1=objeto, a2=campo, res=valor.</li>
 *   <li>{@code t = call f, n}: a1=función, a2=número de parámetros, res=destino (o {@code null}).</li>
 * </ul>
 */
public record Instruccion(String op, String a1, String a2, String res) {

    public static final String OP_ASIGNAR = "=";
    public static final String OP_NEGAR = "neg";
    public static final String OP_NO = "not";
    public static final String OP_ETIQUETA = "label";
    public static final String OP_SALTO = "goto";
    public static final String OP_SI = "if";
    public static final String OP_SI_NO = "ifFalse";
    public static final String OP_FUNCION = "function";
    public static final String OP_FIN_FUNCION = "endfunc";
    public static final String OP_PARAM = "param";
    public static final String OP_LLAMAR = "call";
    public static final String OP_RETORNO = "return";
    public static final String OP_NUEVO_ARREGLO = "newarray";
    public static final String OP_LEER_INDICE = "aload";
    public static final String OP_ESCRIBIR_INDICE = "astore";
    public static final String OP_LONGITUD = "len";
    public static final String OP_NUEVO_OBJETO = "new";
    public static final String OP_LEER_CAMPO = "fload";
    public static final String OP_ESCRIBIR_CAMPO = "fstore";
    public static final String OP_TRY = "try";
    public static final String OP_FIN_TRY = "endtry";
    public static final String OP_CATCH = "catch";
    public static final String OP_PRINT = "print";

    /** Operadores binarios válidos; su mnemónico es el propio símbolo. */
    public static final Set<String> OPERADORES_BINARIOS =
            Set.of("+", "-", "*", "/", "%", "<", "<=", ">", ">=", "==", "!=");

    public Instruccion {
        Objects.requireNonNull(op);
    }

    // --- Fábricas: una por forma de instrucción ---

    public static Instruccion asignar(String destino, String origen) {
        return new Instruccion(OP_ASIGNAR, origen, null, destino);
    }

    public static Instruccion binaria(String destino, String izquierda, String operador, String derecha) {
        if (!OPERADORES_BINARIOS.contains(operador)) {
            throw new IllegalArgumentException("Operador binario desconocido: " + operador);
        }
        return new Instruccion(operador, izquierda, derecha, destino);
    }

    public static Instruccion negar(String destino, String operando) {
        return new Instruccion(OP_NEGAR, operando, null, destino);
    }

    public static Instruccion no(String destino, String operando) {
        return new Instruccion(OP_NO, operando, null, destino);
    }

    public static Instruccion etiqueta(String etiqueta) {
        return new Instruccion(OP_ETIQUETA, etiqueta, null, null);
    }

    public static Instruccion salto(String etiqueta) {
        return new Instruccion(OP_SALTO, etiqueta, null, null);
    }

    public static Instruccion saltoSi(String condicion, String etiqueta) {
        return new Instruccion(OP_SI, condicion, etiqueta, null);
    }

    public static Instruccion saltoSiFalso(String condicion, String etiqueta) {
        return new Instruccion(OP_SI_NO, condicion, etiqueta, null);
    }

    public static Instruccion funcion(String nombre, int tamanoFrame) {
        return new Instruccion(OP_FUNCION, nombre, String.valueOf(tamanoFrame), null);
    }

    public static Instruccion finFuncion() {
        return new Instruccion(OP_FIN_FUNCION, null, null, null);
    }

    public static Instruccion param(String argumento) {
        return new Instruccion(OP_PARAM, argumento, null, null);
    }

    /** Llamada cuyo resultado se guarda en {@code destino}. */
    public static Instruccion llamar(String destino, String funcion, int numParametros) {
        return new Instruccion(OP_LLAMAR, funcion, String.valueOf(numParametros), destino);
    }

    /** Llamada sin resultado (sentencia). */
    public static Instruccion llamar(String funcion, int numParametros) {
        return llamar(null, funcion, numParametros);
    }

    public static Instruccion retorno(String valor) {
        return new Instruccion(OP_RETORNO, valor, null, null);
    }

    public static Instruccion retorno() {
        return retorno(null);
    }

    public static Instruccion nuevoArreglo(String destino, String tamano) {
        return new Instruccion(OP_NUEVO_ARREGLO, tamano, null, destino);
    }

    public static Instruccion leerIndice(String destino, String arreglo, String indice) {
        return new Instruccion(OP_LEER_INDICE, arreglo, indice, destino);
    }

    public static Instruccion escribirIndice(String arreglo, String indice, String valor) {
        return new Instruccion(OP_ESCRIBIR_INDICE, arreglo, indice, valor);
    }

    public static Instruccion longitud(String destino, String arreglo) {
        return new Instruccion(OP_LONGITUD, arreglo, null, destino);
    }

    public static Instruccion nuevoObjeto(String destino, String clase, int tamano) {
        return new Instruccion(OP_NUEVO_OBJETO, clase, String.valueOf(tamano), destino);
    }

    public static Instruccion leerCampo(String destino, String objeto, String campo) {
        return new Instruccion(OP_LEER_CAMPO, objeto, campo, destino);
    }

    public static Instruccion escribirCampo(String objeto, String campo, String valor) {
        return new Instruccion(OP_ESCRIBIR_CAMPO, objeto, campo, valor);
    }

    public static Instruccion intentar(String etiquetaCatch) {
        return new Instruccion(OP_TRY, etiquetaCatch, null, null);
    }

    public static Instruccion finIntentar() {
        return new Instruccion(OP_FIN_TRY, null, null, null);
    }

    public static Instruccion capturar(String variable) {
        return new Instruccion(OP_CATCH, variable, null, null);
    }

    public static Instruccion imprimir(String valor) {
        return new Instruccion(OP_PRINT, valor, null, null);
    }

    public boolean esEtiqueta() {
        return OP_ETIQUETA.equals(op);
    }

    @Override
    public String toString() {
        return switch (op) {
            case OP_ASIGNAR -> res + " = " + a1;
            case OP_NEGAR -> res + " = -" + a1;
            case OP_NO -> res + " = !" + a1;
            case OP_ETIQUETA -> a1 + ":";
            case OP_SALTO -> "goto " + a1;
            case OP_SI -> "if " + a1 + " goto " + a2;
            case OP_SI_NO -> "ifFalse " + a1 + " goto " + a2;
            case OP_FUNCION -> "function " + a1 + ", " + a2;
            case OP_FIN_FUNCION -> "endfunc";
            case OP_PARAM -> "param " + a1;
            case OP_LLAMAR -> (res == null ? "" : res + " = ") + "call " + a1 + ", " + a2;
            case OP_RETORNO -> a1 == null ? "return" : "return " + a1;
            case OP_NUEVO_ARREGLO -> res + " = newarray " + a1;
            case OP_LEER_INDICE -> res + " = " + a1 + "[" + a2 + "]";
            case OP_ESCRIBIR_INDICE -> a1 + "[" + a2 + "] = " + res;
            case OP_LONGITUD -> res + " = len " + a1;
            case OP_NUEVO_OBJETO -> res + " = new " + a1 + ", " + a2;
            case OP_LEER_CAMPO -> res + " = " + a1 + "." + a2;
            case OP_ESCRIBIR_CAMPO -> a1 + "." + a2 + " = " + res;
            case OP_TRY -> "try " + a1;
            case OP_FIN_TRY -> "endtry";
            case OP_CATCH -> "catch " + a1;
            case OP_PRINT -> "print " + a1;
            default -> {
                if (OPERADORES_BINARIOS.contains(op)) {
                    yield res + " = " + a1 + " " + op + " " + a2;
                }
                throw new IllegalStateException("Instrucción desconocida: " + op);
            }
        };
    }
}
