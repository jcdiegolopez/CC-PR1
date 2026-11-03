package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneradorMIPSTest {

    /** Toda variable vive en el mismo lugar: aquí solo importa el recorrido, no la memoria. */
    private static GeneradorMIPS generador() {
        return new GeneradorMIPS(funcion -> nombre -> "0($fp)", RegistrosIngenuos::new);
    }

    private static ProgramaTAC tac(Instruccion... instrucciones) {
        ProgramaTAC programa = new ProgramaTAC();
        for (Instruccion instruccion : instrucciones) {
            programa.emit(instruccion);
        }
        return programa;
    }

    @Test
    void tacVacioNoGeneraEnsamblador() {
        assertTrue(generador().generar(ProgramaTAC.vacio()).estaVacio());
    }

    @Test
    void mainTerminaConSyscallDeSalidaYLoNoTraducidoQuedaPendiente() {
        ProgramaMips mips = generador().generar(tac(
                Instruccion.funcion("main", 24),
                Instruccion.imprimir("5"),
                Instruccion.finFuncion()));

        assertEquals("""
                .text
                .globl main
                main:
                    # PENDIENTE: print 5
                    # endfunc
                    li $v0, 10
                    syscall
                """, mips.toString());
    }

    @Test
    void funcionesLlevanPrefijoYEtiquetasDelTacSeConservan() {
        ProgramaMips mips = generador().generar(tac(
                Instruccion.funcion("main", 24),
                Instruccion.finFuncion(),
                Instruccion.funcion("suma", 36),
                Instruccion.etiqueta("L0"),
                Instruccion.finFuncion()));

        String asm = mips.toString();
        assertTrue(asm.contains("\nf_suma:\n"), asm);
        assertTrue(asm.contains("\nL0:\n"), asm);
        assertTrue(asm.contains("jr $ra"), asm);
    }

    @Test
    void traductorRegistradoRecibeLaInstruccionYElContexto() {
        List<String> vistos = new ArrayList<>();
        GeneradorMIPS generador = generador().registrar(Instruccion.OP_PRINT, (instruccion, contexto) -> {
            vistos.add(contexto.funcion() + "/" + contexto.tamanoFrame() + "/" + instruccion.a1());
            String r = contexto.registros().leer(instruccion.a1());
            contexto.programa().emit(InstruccionMips.de("move", "$a0", r));
        });

        String asm = generador.generar(tac(
                Instruccion.funcion("main", 28),
                Instruccion.imprimir("x"),
                Instruccion.finFuncion())).toString();

        assertEquals(List.of("main/28/x"), vistos);
        assertTrue(asm.contains("""
                    # print x
                    lw $t0, 0($fp)
                    move $a0, $t0
                """), asm);
    }

    @Test
    void cadaFuncionRecibeSusPropiasUbicaciones() {
        List<String> pedidas = new ArrayList<>();
        new GeneradorMIPS(funcion -> {
            pedidas.add(funcion);
            return nombre -> "0($fp)";
        }, RegistrosIngenuos::new).generar(tac(
                Instruccion.funcion("main", 24), Instruccion.finFuncion(),
                Instruccion.funcion("fib", 36), Instruccion.finFuncion()));

        assertEquals(List.of("main", "fib"), pedidas);
    }

    @Test
    void losRegistrosSeLiberanDespuesDeCadaInstruccionTac() {
        GeneradorMIPS generador = generador().registrar(Instruccion.OP_PARAM,
                (instruccion, contexto) -> contexto.registros().leer(instruccion.a1()));
        ProgramaTAC programa = new ProgramaTAC();
        programa.emit(Instruccion.funcion("main", 24));
        for (int i = 0; i < 20; i++) {
            programa.emit(Instruccion.param("a"));
        }
        programa.emit(Instruccion.finFuncion());

        // Con 8 registros, 20 lecturas solo caben si el generador libera entre instrucciones.
        generador.generar(programa);
    }

    @Test
    void instruccionFueraDeFuncionEsUnError() {
        assertThrows(IllegalStateException.class,
                () -> generador().generar(tac(Instruccion.imprimir("1"))));
    }

    @Test
    void recorreElTacRealDelPipeline() {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto("print(1 + 2);");

        String asm = generador().generar(resultado.tac()).toString();

        assertTrue(asm.startsWith(".text\n.globl main\nmain:\n"), asm);
        assertTrue(asm.contains("# PENDIENTE: t0 = 1 + 2"), asm);
        assertTrue(asm.contains("# PENDIENTE: print t0"), asm);
    }
}
