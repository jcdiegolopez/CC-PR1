package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regla 10 (extra): una etiqueta que ninguna instrucción nombra (ni {@code j}/{@code b*}, ni {@code jal},
 * ni {@code la}) se elimina; {@code main} se queda siempre. Así otras reglas pueden juntar bloques: si
 * la etiqueta seguía a un {@code j}, el código de después pasa a ser inalcanzable y la regla 6 lo quita.
 */
public final class ReglaEtiquetaSinUso implements Regla {

    private static final String MAIN = "main";

    @Override
    public String nombre() {
        return "etiqueta-sin-uso";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        Set<String> usadas = new HashSet<>();
        usadas.add(MAIN);
        for (InstruccionMips ins : codigo) {
            if (ins.esInstruccion()) {
                usadas.addAll(ins.operandos());
            }
        }
        int veces = 0;
        for (int k = codigo.size() - 1; k >= 0; k--) {
            if (codigo.get(k).esEtiqueta() && !usadas.contains(codigo.get(k).op())) {
                codigo.remove(k);
                veces++;
            }
        }
        return veces;
    }
}
