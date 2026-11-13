package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;

/**
 * Regla 8: {@code mul $d, $s, 2^k} → {@code sll $d, $s, k}. Un corrimiento cuesta un ciclo y no usa
 * {@code HI}/{@code LO}; los 32 bits bajos son iguales aunque haya desborde. La potencia de dos puede
 * ser un inmediato o un registro cargado con {@code li} justo antes, en cualquiera de los dos lados.
 */
public final class ReglaReduccionFuerza implements Regla {

    @Override
    public String nombre() {
        return "reduccion-de-fuerza";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        int veces = 0;
        for (int k = 0; k < codigo.size(); k++) {
            if (!Ventana.es(codigo, k, "mul") || codigo.get(k).operandos().size() != 3) {
                continue;
            }
            InstruccionMips ins = codigo.get(k);
            String izquierdo = Ventana.operando(ins, 1);
            String derecho = Ventana.operando(ins, 2);
            int corrimiento = exponente(Ventana.constante(codigo, k, derecho));
            String base = izquierdo;
            if (corrimiento < 0) {
                corrimiento = exponente(Ventana.constante(codigo, k, izquierdo));
                base = derecho;
            }
            if (corrimiento > 0 && base.startsWith("$")) {
                codigo.set(k, Ventana.cambiar(ins, "sll", Ventana.operando(ins, 0), base, String.valueOf(corrimiento)));
                veces++;
            }
        }
        return veces;
    }

    /** {@code k} si {@code valor = 2^k} con {@code k >= 1}; {@code -1} si no. */
    static int exponente(Integer valor) {
        if (valor == null || valor < 2 || Integer.bitCount(valor) != 1) {
            return -1;
        }
        return Integer.numberOfTrailingZeros(valor);
    }
}
