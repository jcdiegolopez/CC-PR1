package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;
import com.lexsynanalyzer.mips.ProgramaMips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optimización por mirilla sobre la lista de {@link InstruccionMips} del {@code .text} (nunca sobre texto).
 * Corre todas las reglas, en orden, hasta que una pasada completa no cambia nada: una regla puede abrir
 * una oportunidad a otra (un {@code sw/lw} deja un {@code move $x, $x}; quitar una etiqueta deja código
 * inalcanzable).
 *
 * <p>No toca {@code .data} ni el programa recibido: devuelve uno nuevo, así la GUI puede mostrar los dos.
 */
public final class Mirilla {

    /** Tope de pasadas; todas las reglas acortan o simplifican el código, así que se llega antes. */
    static final int MAX_PASADAS = 100;

    private final List<Regla> reglas;

    public Mirilla() {
        this(reglasPorDefecto());
    }

    public Mirilla(List<Regla> reglas) {
        this.reglas = List.copyOf(reglas);
    }

    public static List<Regla> reglasPorDefecto() {
        return List.of(new ReglaMoveRedundante(), new ReglaGuardarCargar(), new ReglaAccesoRedundante(),
                new ReglaSaltoASiguiente(), new ReglaSaltoASalto(), new ReglaCodigoInalcanzable(),
                new ReglaIdentidadAlgebraica(), new ReglaReduccionFuerza(), new ReglaInversionRama(),
                new ReglaEtiquetaSinUso());
    }

    /**
     * Programa optimizado y cuántas veces se aplicó cada regla, en el orden de las reglas y solo las que
     * se aplicaron al menos una vez.
     */
    public record Resultado(ProgramaMips programa, Map<String, Integer> aplicaciones) {

        public Resultado {
            aplicaciones = Collections.unmodifiableMap(new LinkedHashMap<>(aplicaciones));
        }
    }

    public Resultado optimizar(ProgramaMips programa) {
        if (programa.estaVacio()) {
            return new Resultado(programa, Map.of());
        }
        List<InstruccionMips> codigo = new ArrayList<>(programa.texto());
        int[] veces = new int[reglas.size()];
        for (int pasada = 0; pasada < MAX_PASADAS; pasada++) {
            int cambios = 0;
            for (int r = 0; r < reglas.size(); r++) {
                int n = reglas.get(r).aplicar(codigo);
                veces[r] += n;
                cambios += n;
            }
            if (cambios == 0) {
                break;
            }
        }
        Map<String, Integer> aplicaciones = new LinkedHashMap<>();
        for (int r = 0; r < reglas.size(); r++) {
            if (veces[r] > 0) {
                aplicaciones.merge(reglas.get(r).nombre(), veces[r], Integer::sum);
            }
        }
        return new Resultado(programa.conTexto(codigo), aplicaciones);
    }

    /** Instrucciones reales que la mirilla quitó. */
    public static int eliminadas(ProgramaMips original, ProgramaMips optimizado) {
        return original.cantidadInstrucciones() - optimizado.cantidadInstrucciones();
    }
}
