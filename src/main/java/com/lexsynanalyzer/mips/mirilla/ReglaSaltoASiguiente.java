package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;

/**
 * Regla 4: un salto (incondicional o condicional) a una etiqueta que está justo después, sin ninguna
 * instrucción en medio, llega al mismo lugar que no saltar. Se elimina.
 */
public final class ReglaSaltoASiguiente implements Regla {

    @Override
    public String nombre() {
        return "salto-a-siguiente";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        int veces = 0;
        for (int k = codigo.size() - 1; k >= 0; k--) {
            String destino = codigo.get(k).destinoSalto();
            if (destino != null && llegaSinInstrucciones(codigo, k, destino)) {
                codigo.remove(k);
                veces++;
            }
        }
        return veces;
    }

    private static boolean llegaSinInstrucciones(List<InstruccionMips> codigo, int salto, String destino) {
        for (int k = salto + 1; k < codigo.size() && !codigo.get(k).esInstruccion(); k++) {
            if (codigo.get(k).esEtiqueta() && destino.equals(codigo.get(k).op())) {
                return true;
            }
        }
        return false;
    }
}
