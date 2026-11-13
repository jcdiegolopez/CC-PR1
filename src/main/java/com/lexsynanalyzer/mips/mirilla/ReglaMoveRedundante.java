package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;

/** Regla 1: {@code move $x, $x} no hace nada y se elimina. */
public final class ReglaMoveRedundante implements Regla {

    @Override
    public String nombre() {
        return "move-redundante";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        int veces = 0;
        for (int k = codigo.size() - 1; k >= 0; k--) {
            InstruccionMips ins = codigo.get(k);
            if (Ventana.es(codigo, k, "move") && ins.operandos().get(0).equals(ins.operandos().get(1))) {
                codigo.remove(k);
                veces++;
            }
        }
        return veces;
    }
}
