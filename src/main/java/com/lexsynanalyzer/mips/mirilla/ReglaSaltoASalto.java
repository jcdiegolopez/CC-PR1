package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regla 5: {@code j L1} … {@code L1: j L2} → {@code j L2}. Vale también para los saltos condicionales
 * ({@code beqz r, L1} → {@code beqz r, L2}). Se sigue la cadena entera; si es un ciclo de saltos
 * ({@code L1: j L2}, {@code L2: j L1}) no se toca, porque no hay un destino final.
 */
public final class ReglaSaltoASalto implements Regla {

    @Override
    public String nombre() {
        return "salto-a-salto";
    }

    @Override
    public int aplicar(List<InstruccionMips> codigo) {
        Map<String, Integer> etiquetas = Ventana.etiquetas(codigo);
        int veces = 0;
        for (int k = 0; k < codigo.size(); k++) {
            String destino = codigo.get(k).destinoSalto();
            if (destino == null) {
                continue;
            }
            String finalDestino = destinoFinal(codigo, etiquetas, destino);
            if (finalDestino != null && !finalDestino.equals(destino)) {
                codigo.set(k, Ventana.conDestino(codigo.get(k), finalDestino));
                veces++;
            }
        }
        return veces;
    }

    /** Etiqueta a la que se llega siguiendo los {@code j} encadenados; {@code null} si es un ciclo. */
    private static String destinoFinal(List<InstruccionMips> codigo, Map<String, Integer> etiquetas, String inicio) {
        Set<String> vistas = new HashSet<>();
        vistas.add(inicio);
        String actual = inicio;
        while (true) {
            Integer posicion = etiquetas.get(actual);
            int primera = posicion == null ? -1 : Ventana.primeraInstruccion(codigo, posicion);
            if (primera < 0 || !Ventana.esSaltoAEtiqueta(codigo.get(primera))) {
                return actual;
            }
            String siguiente = codigo.get(primera).destinoSalto();
            if (!vistas.add(siguiente)) {
                return null;
            }
            actual = siguiente;
        }
    }
}
