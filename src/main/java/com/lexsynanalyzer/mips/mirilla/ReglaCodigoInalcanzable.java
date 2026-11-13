package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;

/**
 * Regla 6: lo que hay entre un salto incondicional ({@code j}, {@code b}, {@code jr}) y la siguiente
 * etiqueta nunca se ejecuta: solo se puede entrar ahí saltando a una etiqueta. Se eliminan esas
 * instrucciones; los comentarios sueltos se dejan (pueden ser el encabezado de lo que sigue).
 */
public final class ReglaCodigoInalcanzable implements Regla {

    @Override
    public String nombre() {
        return "codigo-inalcanzable";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        int veces = 0;
        for (int k = 0; k < codigo.size(); k++) {
            if (!codigo.get(k).esSaltoIncondicional()) {
                continue;
            }
            for (int s = k + 1; s < codigo.size() && !codigo.get(s).esEtiqueta(); ) {
                if (codigo.get(s).esInstruccion()) {
                    codigo.remove(s);
                    veces++;
                } else {
                    s++;
                }
            }
        }
        return veces;
    }
}
