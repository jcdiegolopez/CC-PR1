package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;
import java.util.Set;

/**
 * Regla 7: operaciones cuyo resultado es uno de sus operandos.
 * <ul>
 *   <li>{@code x + 0}, {@code 0 + x}, {@code x - 0}, {@code x | 0}, {@code x ^ 0}, {@code x << 0}, {@code x * 1}
 *   → {@code move $d, x}, o nada si {@code $d} ya es {@code x}.</li>
 *   <li>{@code x * 0} → {@code li $d, 0}.</li>
 * </ul>
 * La constante puede ser un inmediato, {@code $zero} o un registro cargado con {@code li} justo antes;
 * ese {@code li} se deja, porque el registro puede usarse después.
 */
public final class ReglaIdentidadAlgebraica implements Regla {

    /** Operaciones con neutro 0 en los dos lados. */
    private static final Set<String> NEUTRO_CERO = Set.of("addu", "add", "addiu", "addi", "or", "ori", "xor", "xori");
    /** Operaciones con neutro 0 solo a la derecha. */
    private static final Set<String> NEUTRO_CERO_DERECHA = Set.of("subu", "sub", "sll", "srl", "sra");

    @Override
    public String nombre() {
        return "identidad-algebraica";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        int veces = 0;
        for (int k = codigo.size() - 1; k >= 0; k--) {
            InstruccionMips ins = codigo.get(k);
            if (!ins.esInstruccion() || ins.operandos().size() != 3) {
                continue;
            }
            String destino = Ventana.operando(ins, 0);
            String izquierdo = Ventana.operando(ins, 1);
            String derecho = Ventana.operando(ins, 2);
            Integer cIzq = Ventana.constante(codigo, k, izquierdo);
            Integer cDer = Ventana.constante(codigo, k, derecho);
            String queda = null;
            if (NEUTRO_CERO.contains(ins.op()) || NEUTRO_CERO_DERECHA.contains(ins.op())) {
                if (esValor(cDer, 0)) {
                    queda = izquierdo;
                } else if (NEUTRO_CERO.contains(ins.op()) && esValor(cIzq, 0)) {
                    queda = derecho;
                }
            } else if ("mul".equals(ins.op())) {
                if (esValor(cDer, 0) || esValor(cIzq, 0)) {
                    codigo.set(k, Ventana.cambiar(ins, "li", destino, "0"));
                    veces++;
                    continue;
                }
                if (esValor(cDer, 1)) {
                    queda = izquierdo;
                } else if (esValor(cIzq, 1)) {
                    queda = derecho;
                }
            }
            if (queda == null || !queda.startsWith("$")) {
                continue;
            }
            if (queda.equals(destino)) {
                codigo.remove(k);
            } else {
                codigo.set(k, Ventana.cambiar(ins, "move", destino, queda));
            }
            veces++;
        }
        return veces;
    }

    private static boolean esValor(Integer constante, int valor) {
        return constante != null && constante == valor;
    }
}
