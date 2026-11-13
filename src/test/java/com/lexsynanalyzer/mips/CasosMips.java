package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.mips.mirilla.Mirilla;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * La batería de {@code src/test/resources/mips}: cada {@code <caso>.cps} con su {@code <caso>.salida}.
 * Convenciones de nombre:
 * <ul>
 *   <li>{@code traza_*}: se compila en modo traza; la salida esperada incluye las líneas {@code -> f} / {@code <- f}.</li>
 *   <li>{@link #TERMINAN_CON_ERROR}: un error sin {@code try}; la salida termina con el mensaje y el código es 1.</li>
 * </ul>
 */
final class CasosMips {

    static final Path CARPETA = Path.of("src", "test", "resources", "mips");
    static final Set<String> TERMINAN_CON_ERROR = Set.of("division_cero", "error_indice");

    private CasosMips() {
    }

    static List<String> todos() {
        try (Stream<Path> archivos = Files.list(CARPETA)) {
            return archivos.map(p -> p.getFileName().toString())
                    .filter(nombre -> nombre.endsWith(".cps"))
                    .map(nombre -> nombre.substring(0, nombre.length() - ".cps".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean usaTraza(String caso) {
        return caso.startsWith("traza_");
    }

    static int codigoEsperado(String caso) {
        return TERMINAN_CON_ERROR.contains(caso) ? 1 : 0;
    }

    static String fuente(String caso) {
        return leer(caso + ".cps");
    }

    static String salida(String caso) {
        return leer(caso + ".salida");
    }

    static ProgramaMips compilar(String caso, FabricaRegistros modo, boolean optimizar) {
        ProgramaMips programa = MipsDePrueba.compilar(fuente(caso), modo, usaTraza(caso));
        return optimizar ? new Mirilla().optimizar(programa).programa() : programa;
    }

    private static String leer(String archivo) {
        try {
            return Files.readString(CARPETA.resolve(archivo)).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
