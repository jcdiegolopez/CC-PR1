package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;

/**
 * Regla 3: accesos a memoria que no cambian nada, con las dos instrucciones seguidas.
 * <ul>
 *   <li>{@code lw $r, M} + {@code lw $r, M} → se elimina el segundo.</li>
 *   <li>{@code lw $r, M} + {@code sw $r, M} → se elimina el {@code sw}: guardaría lo que ya está.</li>
 *   <li>{@code sw $a, M} + {@code sw $b, M} → se elimina el primero: el segundo lo pisa.</li>
 * </ul>
 * En las dos primeras el registro base de {@code M} no puede ser {@code $r}, porque el {@code lw} lo
 * cambia y {@code M} pasaría a ser otra dirección ({@code lw $t0, 0($t0)} dos veces sigue una lista).
 */
public final class ReglaAccesoRedundante implements Regla {

    @Override
    public String nombre() {
        return "acceso-redundante";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        int veces = 0;
        for (int k = 0; k < codigo.size(); k++) {
            boolean cargar = Ventana.es(codigo, k, "lw");
            if (!cargar && !Ventana.es(codigo, k, "sw")) {
                continue;
            }
            int s = Ventana.siguiente(codigo, k);
            if (s < 0 || !codigo.get(s).esInstruccion()) {
                continue;
            }
            InstruccionMips primera = codigo.get(k);
            InstruccionMips segunda = codigo.get(s);
            String memoria = Ventana.operando(primera, 1);
            String registro = Ventana.operando(primera, 0);
            boolean mismaMemoria = segunda.operandos().size() == 2 && memoria.equals(Ventana.operando(segunda, 1));
            if (!mismaMemoria) {
                continue;
            }
            if (cargar && registro.equals(Ventana.operando(segunda, 0)) && !registro.equals(Ventana.base(memoria))
                    && ("lw".equals(segunda.op()) || "sw".equals(segunda.op()))) {
                codigo.remove(s);
                veces++;
            } else if (!cargar && "sw".equals(segunda.op())) {
                codigo.remove(k);
                k--;
                veces++;
            }
        }
        return veces;
    }
}
