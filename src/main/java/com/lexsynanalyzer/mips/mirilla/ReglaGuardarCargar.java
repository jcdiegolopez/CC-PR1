package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;

/**
 * Regla 2: {@code sw $r, M} seguido de {@code lw $r, M} → se elimina el {@code lw}: el registro ya tiene
 * ese valor. Con {@code lw $s, M} → {@code move $s, $r}, que no va a memoria. El {@code sw} no cambia
 * ningún registro, así que {@code M} es la misma dirección en las dos instrucciones.
 */
public final class ReglaGuardarCargar implements Regla {

    @Override
    public String nombre() {
        return "sw/lw";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        int veces = 0;
        for (int k = 0; k < codigo.size(); k++) {
            if (!Ventana.es(codigo, k, "sw")) {
                continue;
            }
            int s = Ventana.siguiente(codigo, k);
            if (!Ventana.es(codigo, s, "lw")) {
                continue;
            }
            InstruccionMips guardar = codigo.get(k);
            InstruccionMips cargar = codigo.get(s);
            String memoria = Ventana.operando(guardar, 1);
            if (!memoria.equals(Ventana.operando(cargar, 1))) {
                continue;
            }
            String origen = Ventana.operando(guardar, 0);
            String destino = Ventana.operando(cargar, 0);
            if (destino.equals(origen)) {
                codigo.remove(s);
            } else {
                codigo.set(s, Ventana.cambiar(cargar, "move", destino, origen));
            }
            veces++;
        }
        return veces;
    }
}
