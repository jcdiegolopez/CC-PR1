package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Consultas comunes de las reglas sobre la lista de instrucciones. */
final class Ventana {

    private Ventana() {
    }

    /**
     * Índice de la primera línea después de {@code desde} que no es un comentario suelto, o {@code -1}.
     * Si es una etiqueta se devuelve igual: quien llama decide si la ventana termina ahí.
     */
    static int siguiente(List<InstruccionMips> codigo, int desde) {
        for (int k = desde + 1; k < codigo.size(); k++) {
            if (codigo.get(k).tipo() != InstruccionMips.Tipo.COMENTARIO) {
                return k;
            }
        }
        return -1;
    }

    /** Como {@link #siguiente} pero hacia atrás. */
    static int anterior(List<InstruccionMips> codigo, int desde) {
        for (int k = desde - 1; k >= 0; k--) {
            if (codigo.get(k).tipo() != InstruccionMips.Tipo.COMENTARIO) {
                return k;
            }
        }
        return -1;
    }

    /** Primera instrucción real a partir de {@code desde} (inclusive), saltando etiquetas y comentarios. */
    static int primeraInstruccion(List<InstruccionMips> codigo, int desde) {
        for (int k = desde; k < codigo.size(); k++) {
            if (codigo.get(k).esInstruccion()) {
                return k;
            }
        }
        return -1;
    }

    /** {@code true} si {@code k} es un índice válido de una instrucción con mnemónico {@code op}. */
    static boolean es(List<InstruccionMips> codigo, int k, String op) {
        return k >= 0 && codigo.get(k).esInstruccion() && op.equals(codigo.get(k).op());
    }

    static String operando(InstruccionMips ins, int i) {
        return ins.operandos().get(i);
    }

    /** Registro base de un operando de memoria {@code k($r)}; {@code null} si es una etiqueta ({@code g_x}). */
    static String base(String memoria) {
        int abre = memoria.indexOf('(');
        return abre < 0 ? null : memoria.substring(abre + 1, memoria.length() - 1);
    }

    /** Valor de un literal entero ({@code 4}, {@code -1}, {@code 0x10}) o de {@code $zero}; {@code null} si no es constante. */
    static Integer literal(String operando) {
        if ("$zero".equals(operando) || "$0".equals(operando)) {
            return 0;
        }
        if (operando.isEmpty() || operando.startsWith("$")) {
            return null;
        }
        try {
            return Integer.decode(operando);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Valor constante de {@code operando} en la instrucción {@code k}: un literal, {@code $zero}, o un
     * registro cargado con {@code li} justo antes (sin etiquetas en medio).
     */
    static Integer constante(List<InstruccionMips> codigo, int k, String operando) {
        Integer valor = literal(operando);
        if (valor != null) {
            return valor;
        }
        int previa = anterior(codigo, k);
        if (es(codigo, previa, "li") && operando(codigo.get(previa), 0).equals(operando)) {
            return literal(operando(codigo.get(previa), 1));
        }
        return null;
    }

    /** La misma línea con otro mnemónico y operandos; conserva el comentario. */
    static InstruccionMips cambiar(InstruccionMips original, String op, String... operandos) {
        return new InstruccionMips(InstruccionMips.Tipo.INSTRUCCION, op, List.of(operandos), original.comentario());
    }

    /** La misma instrucción saltando a {@code destino}. */
    static InstruccionMips conDestino(InstruccionMips salto, String destino) {
        List<String> operandos = new ArrayList<>(salto.operandos());
        operandos.set(operandos.size() - 1, destino);
        return new InstruccionMips(InstruccionMips.Tipo.INSTRUCCION, salto.op(), operandos, salto.comentario());
    }

    /** Posición de cada etiqueta definida en el código. */
    static Map<String, Integer> etiquetas(List<InstruccionMips> codigo) {
        Map<String, Integer> posiciones = new HashMap<>();
        for (int k = 0; k < codigo.size(); k++) {
            if (codigo.get(k).esEtiqueta()) {
                posiciones.put(codigo.get(k).op(), k);
            }
        }
        return posiciones;
    }

    /** {@code j} o {@code b}: saltos incondicionales a una etiqueta (no {@code jr}). */
    static boolean esSaltoAEtiqueta(InstruccionMips ins) {
        return ins.esInstruccion() && ("j".equals(ins.op()) || "b".equals(ins.op()));
    }
}
