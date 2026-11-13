package com.lexsynanalyzer.mips;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Corre un {@code .asm} en MARS 4.5 real, por consola, desde JUnit:
 * {@code java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -jar tools/Mars4_5.jar nc ae98 se99 sm <pasos> archivo.asm}.
 *
 * <ul>
 *   <li>{@code -Djava.awt.headless=true} (y sin {@code DISPLAY} en el entorno): MARS inicializa Swing aunque
 *   corra por consola. Con un {@code DISPLAY} heredado que no se puede abrir (CI, SSH) lanza {@code AWTError},
 *   el programa no corre y solo se ve el aviso del límite de pasos.</li>
 *   <li>{@code nc}: sin el aviso de copyright, la salida es solo la del programa.</li>
 *   <li>{@code sm}: empieza en {@code main}.</li>
 *   <li>{@code <pasos>}: límite de instrucciones, para que un ciclo infinito no cuelgue el test. Al llegar
 *   al límite MARS sale con 0 y solo lo avisa en la salida; por eso se busca ese mensaje.</li>
 *   <li>{@code ae98} / {@code se99}: un error de ensamblado o de ejecución (dirección inválida) sale con 98
 *   o 99, distinto del 0 normal y del 1 que deja {@code __lanzar} con {@code syscall 17}.</li>
 * </ul>
 * Además hay un tope de {@link #TIEMPO_MAXIMO_SEGUNDOS} por si MARS se queda esperando entrada.
 * Si {@code tools/Mars4_5.jar} no está, {@link #disponible()} es {@code false} y los tests se saltan.
 */
final class EjecutorMars {

    static final Path JAR = Path.of("tools", "Mars4_5.jar");
    static final int PASOS_MAXIMOS = 1_000_000;
    static final long TIEMPO_MAXIMO_SEGUNDOS = 10;
    static final int CODIGO_ERROR_ENSAMBLADO = 98;
    static final int CODIGO_ERROR_EJECUCION = 99;
    private static final String AVISO_LIMITE = "Program terminated when maximum step limit";

    enum Estado {
        /** El programa terminó solo ({@code syscall 10} o {@code 17}). */
        TERMINO,
        LIMITE_DE_PASOS,
        ERROR_ENSAMBLADO,
        ERROR_EJECUCION,
        TIEMPO_AGOTADO
    }

    /**
     * @param salida       lo que MARS escribió (stdout y stderr juntos), sin espacios al final
     * @param codigoSalida código de salida del proceso ({@code -1} si se mató por tiempo)
     */
    record Resultado(String salida, int codigoSalida, Estado estado) {
    }

    private EjecutorMars() {
    }

    static boolean disponible() {
        return Files.isRegularFile(JAR);
    }

    static Resultado ejecutar(String asm) {
        return ejecutar(asm, PASOS_MAXIMOS);
    }

    static Resultado ejecutar(String asm, int pasos) {
        Path archivo = null;
        try {
            archivo = Files.createTempFile("compiscript", ".asm");
            Files.writeString(archivo, asm, StandardCharsets.UTF_8);
            return ejecutar(archivo, pasos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (archivo != null) {
                try {
                    Files.deleteIfExists(archivo);
                } catch (IOException ignorada) {
                    // Es un temporal: si no se puede borrar, lo borra el sistema.
                }
            }
        }
    }

    static Resultado ejecutar(Path asm, int pasos) {
        if (!disponible()) {
            throw new IllegalStateException("No existe " + JAR.toAbsolutePath());
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder comando = new ProcessBuilder(List.of(java, "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8",
                "-jar", JAR.toString(),
                "nc", "ae" + CODIGO_ERROR_ENSAMBLADO, "se" + CODIGO_ERROR_EJECUCION, "sm", String.valueOf(pasos),
                asm.toString()))
                .redirectErrorStream(true);
        comando.environment().remove("DISPLAY");
        try {
            Process mars = comando.start();
            mars.getOutputStream().close();
            CompletableFuture<String> salida = CompletableFuture.supplyAsync(() -> leer(mars.getInputStream()));
            if (!mars.waitFor(TIEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS)) {
                mars.destroyForcibly().waitFor();
                return new Resultado(salida.join().stripTrailing(), -1, Estado.TIEMPO_AGOTADO);
            }
            String texto = salida.join().replace("\r\n", "\n").stripTrailing();
            int codigo = mars.exitValue();
            return new Resultado(texto, codigo, estado(texto, codigo));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la ejecución de MARS", e);
        }
    }

    private static Estado estado(String salida, int codigo) {
        if (codigo == CODIGO_ERROR_ENSAMBLADO) {
            return Estado.ERROR_ENSAMBLADO;
        }
        if (codigo == CODIGO_ERROR_EJECUCION) {
            return Estado.ERROR_EJECUCION;
        }
        return salida.contains(AVISO_LIMITE) ? Estado.LIMITE_DE_PASOS : Estado.TERMINO;
    }

    private static String leer(InputStream entrada) {
        try (entrada) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
