package com.lexsynanalyzer.mips;

/**
 * Lo que el asignador de registros necesita saber de la memoria (dueño de la implementación real:
 * {@code Ubicaciones}, persona B). El asignador nunca calcula offsets por su cuenta: los toma de aquí,
 * que a su vez los toma del {@code Layout} del P2.
 */
@FunctionalInterface
public interface ResolvedorUbicaciones {

    /**
     * Operando de memoria MIPS donde vive la variable o el temporal {@code nombre} de la función que
     * se está traduciendo, listo para {@code lw}/{@code sw}: {@code 28($fp)} para locales,
     * parámetros y temporales, o {@code g_x} para globales. Nunca recibe literales.
     */
    String direccion(String nombre);
}
