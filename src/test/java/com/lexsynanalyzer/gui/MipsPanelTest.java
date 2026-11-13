package com.lexsynanalyzer.gui;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.mips.EjecutorMars;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MipsPanelTest {

    private static final String PROGRAMA = """
            let saludo: string = "¿Qué tal? # no es comentario";
            print(saludo);
            print(1 + 2);
            """;

    @Test
    void muestraElAsmTalCual() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(PROGRAMA);
        MipsPanel panel = new MipsPanel();

        panel.mostrarMips(resultado.mips(), resultado.mipsSinOptimizar(), resultado.mirilla(), "saludo");

        assertEquals(resultado.mips().toString(), panel.getTexto().replace("\r\n", "\n"));
        assertTrue(panel.getMensaje().startsWith("[OK]"), panel.getMensaje());
        assertTrue(panel.accionesHabilitadas());
    }

    @Test
    void guardaElAsmEnUtf8() throws Exception {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(PROGRAMA);
        MipsPanel panel = new MipsPanel();
        panel.mostrarMips(resultado.mips(), resultado.mipsSinOptimizar(), resultado.mirilla(), "saludo");

        Path archivo = directorio.resolve("saludo.asm");
        panel.guardarEn(archivo);

        assertEquals(resultado.mips().toString(), Files.readString(archivo, StandardCharsets.UTF_8));
    }

    @TempDir
    Path directorio;

    @Test
    void conErrores_muestraElMensajeYNoDejaGuardar() {
        MipsPanel panel = new MipsPanel();

        panel.mostrarSinMips();

        assertEquals(MipsPanel.MENSAJE_SIN_MIPS, panel.getMensaje());
        assertEquals("", panel.getTexto());
        assertFalse(panel.accionesHabilitadas());
    }

    @Test
    void verSinOptimizar_alternaEntreLasDosVersiones() {
        AnalysisResult a = LexSynAnalyzer.analizarTexto("print(1);");
        AnalysisResult b = LexSynAnalyzer.analizarTexto("print(1); print(2);");
        MipsPanel panel = new MipsPanel();
        panel.mostrarMips(a.mips(), b.mips(), Map.of("prueba", 1), "x");

        panel.setMostrarSinOptimizar(true);
        assertEquals(b.mips().toString(), panel.getTexto().replace("\r\n", "\n"));

        panel.setMostrarSinOptimizar(false);
        assertEquals(a.mips().toString(), panel.getTexto().replace("\r\n", "\n"));
    }

    @Test
    void resumenDeLaMirilla_cuentaEliminadasYReglas() {
        Map<String, Integer> reglas = new LinkedHashMap<>();
        reglas.put("salto a la siguiente", 5);
        reglas.put("sw/lw", 12);

        assertEquals("Mirilla: 17 instrucciones eliminadas (salto a la siguiente ×5, sw/lw ×12)",
                MipsPanel.resumenMirilla(17, reglas));
        assertEquals("Mirilla: 1 instrucción eliminada (move ×1)", MipsPanel.resumenMirilla(1, Map.of("move", 1)));
        assertEquals("Mirilla: no aplicó ninguna transformación.", MipsPanel.resumenMirilla(0, Map.of()));
    }

    @Test
    void elNumeralDentroDeUnStringNoAbreComentario() {
        assertEquals(-1, MipsPanel.inicioComentario("str0: .asciiz \"a # b\""));
        assertEquals(14, MipsPanel.inicioComentario("    li $t0, 1 # x"));
    }

    // --- Ejecutar en MARS ---

    @Test
    void ejecutarEnMars_muestraLaSalidaYComoTermino() throws Exception {
        MipsPanel panel = new MipsPanel(asm -> new EjecutorMars.Resultado("hola\n3", 0, EjecutorMars.Estado.TERMINO),
                true);
        mostrar(panel);

        panel.ejecutarEnMars().get(5, TimeUnit.SECONDS);
        esperar(() -> panel.getEstadoMars().startsWith("MARS"));

        assertEquals("hola\n3", panel.getSalidaMars());
        assertTrue(panel.getEstadoMars().contains("versión optimizada"), panel.getEstadoMars());
        assertTrue(panel.getEstadoMars().contains("terminó con código 0"), panel.getEstadoMars());
        assertTrue(panel.ejecutarHabilitado());
    }

    @Test
    void ejecutarEnMars_correLaVersionQueSeEstaViendo() throws Exception {
        String[] recibido = new String[1];
        MipsPanel panel = new MipsPanel(asm -> {
            recibido[0] = asm;
            return new EjecutorMars.Resultado("", 0, EjecutorMars.Estado.TERMINO);
        }, true);
        AnalysisResult resultado = mostrar(panel);
        panel.setMostrarSinOptimizar(true);

        panel.ejecutarEnMars().get(5, TimeUnit.SECONDS);
        esperar(() -> panel.getEstadoMars().startsWith("MARS"));

        assertEquals(resultado.mipsSinOptimizar().toString(), recibido[0]);
        assertTrue(panel.getEstadoMars().contains("versión sin optimizar"), panel.getEstadoMars());
    }

    @Test
    void ejecutarEnMars_avisaElLimiteDePasosYLosErrores() {
        MipsPanel panel = new MipsPanel(asm -> null, true);
        mostrar(panel);

        panel.mostrarResultadoMars(new EjecutorMars.Resultado("", 0, EjecutorMars.Estado.LIMITE_DE_PASOS),
                "versión optimizada", 1200);
        assertTrue(panel.getEstadoMars().contains("ciclo infinito"), panel.getEstadoMars());

        panel.mostrarResultadoMars(new EjecutorMars.Resultado("Error...", 99, EjecutorMars.Estado.ERROR_EJECUCION),
                "versión optimizada", 300);
        assertTrue(panel.getEstadoMars().contains("error en tiempo de ejecución"), panel.getEstadoMars());
        assertEquals("Error...", panel.getSalidaMars());
    }

    @Test
    void sinMarsElBotonQuedaDeshabilitado() {
        MipsPanel panel = new MipsPanel(asm -> null, false);
        mostrar(panel);

        assertTrue(panel.accionesHabilitadas());
        assertFalse(panel.ejecutarHabilitado());
    }

    @Test
    void unaEjecucionQueTerminaDespuesDeCambiarDeProgramaSeDescarta() throws Exception {
        CountDownLatch soltar = new CountDownLatch(1);
        MipsPanel panel = new MipsPanel(asm -> {
            try {
                soltar.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new EjecutorMars.Resultado("viejo", 0, EjecutorMars.Estado.TERMINO);
        }, true);
        mostrar(panel);
        var trabajo = panel.ejecutarEnMars();

        mostrar(panel);
        soltar.countDown();
        trabajo.get(5, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("", panel.getSalidaMars());
        assertTrue(panel.ejecutarHabilitado());
    }

    @Test
    void ejecutarEnMarsReal() throws Exception {
        assumeTrue(EjecutorMars.disponible(), "No está " + EjecutorMars.JAR);
        MipsPanel panel = new MipsPanel();
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("print(1 + 2);");
        panel.mostrarMips(resultado.mips(), resultado.mipsSinOptimizar(), resultado.mirilla(), "suma");

        panel.ejecutarEnMars().get(15, TimeUnit.SECONDS);
        esperar(() -> panel.getEstadoMars().startsWith("MARS"));

        assertEquals("3", panel.getSalidaMars());
        assertTrue(panel.getEstadoMars().contains("código 0"), panel.getEstadoMars());
    }

    private static AnalysisResult mostrar(MipsPanel panel) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(PROGRAMA);
        panel.mostrarMips(resultado.mips(), resultado.mipsSinOptimizar(), resultado.mirilla(), "saludo");
        return resultado;
    }

    /** {@code done()} del SwingWorker corre en el hilo de Swing poco después de que {@code get()} vuelve. */
    private static void esperar(BooleanSupplier condicion) throws Exception {
        long limite = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < limite) {
            boolean[] listo = new boolean[1];
            SwingUtilities.invokeAndWait(() -> listo[0] = condicion.getAsBoolean());
            if (listo[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("La ejecución en MARS no terminó a tiempo");
    }
}
