package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;

import java.util.List;

/**
 * Una transformación de la mirilla. Recorre el {@code .text} una vez, lo cambia en el lugar y dice
 * cuántas veces se aplicó; {@link Mirilla} la vuelve a correr hasta que ninguna regla cambie nada.
 *
 * <p>Toda regla mira una ventana de instrucciones consecutivas que <b>no cruza etiquetas</b> (a una
 * etiqueta se puede llegar saltando, así que lo que se sabía antes de ella ya no vale). Los comentarios
 * sueltos sí se saltan: no son código. Solo las reglas de saltos miran etiquetas, y lo hacen a propósito.
 */
public interface Regla {

    /** Nombre corto para las estadísticas de la GUI, por ejemplo {@code salto-a-siguiente}. */
    String nombre();

    /** Aplica la regla sobre todo {@code codigo} (que es mutable) y devuelve cuántas veces lo hizo. */
    int aplicar(List<InstruccionMips> codigo);
}
