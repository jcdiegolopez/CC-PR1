package com.lexsynanalyzer.tac;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lo que el generador necesita saber de la asignación de memoria (dueño de la implementación real:
 * {@code AsignadorMemoria}, persona C). El generador nunca decide nombres ni tamaños por su cuenta.
 */
public interface ResolvedorMemoria {

    /** Bytes del frame antes de los temporales: cabecera (24) + parámetros + locales. */
    int tamanoBase(String funcion);

    /** Nombre con el que aparece en el TAC el identificador de una declaración o de un uso. */
    String nombreTac(TerminalNode identificador);

    /**
     * Etiqueta de la función que nombra {@code identificador} (en su declaración o en una llamada),
     * resuelta por ámbito. Vacío si no se conoce: el generador decide por su cuenta.
     */
    default Optional<String> etiquetaFuncion(TerminalNode identificador) {
        return Optional.empty();
    }

    /** Mientras no exista {@code AsignadorMemoria}: nombre del código fuente y frame de solo cabecera. */
    ResolvedorMemoria SIMPLE = new ResolvedorMemoria() {
        private final Pattern CHOCA_CON_TEMPORAL = Pattern.compile("t\\d+");

        @Override
        public int tamanoBase(String funcion) {
            return 24;
        }

        /**
         * Una variable del usuario llamada {@code t0} se confundiría con un temporal (y
         * {@link TempPool#liberar} podría liberarla), así que se renombra.
         */
        @Override
        public String nombreTac(TerminalNode identificador) {
            String nombre = identificador.getText();
            return CHOCA_CON_TEMPORAL.matcher(nombre).matches() ? nombre + "_v" : nombre;
        }
    };
}
