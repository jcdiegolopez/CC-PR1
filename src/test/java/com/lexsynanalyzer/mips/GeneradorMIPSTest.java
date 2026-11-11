package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneradorMIPSTest {

    /** Toda variable vive en el mismo lugar: aquí solo importa el recorrido, no la memoria. */
    private static GeneradorMIPS generador() {
        return new GeneradorMIPS(funcion -> nombre -> "0($fp)", FabricaRegistros.INGENUO);
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
    void generaPrólogoYEpílogoYAcumulaParametrosHastaLaLlamada() {
        ProgramaMips mips = generador().generar(tac(
                Instruccion.funcion("main", 24),
                Instruccion.param("5"),
                Instruccion.finFuncion()));

        String asm = mips.toString();
        assertTrue(asm.contains("""
                main:
                    addiu $sp, $sp, -24
                    sw $ra, 0($sp)
                    sw $fp, 8($sp)
                    sw $zero, 16($sp)
                    move $fp, $sp
                    # param 5
                    # endfunc
                main_fin:
                    li $v0, 10
                    syscall
                """), asm);
        assertFalse(asm.contains("PENDIENTE"), asm);
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
        }, FabricaRegistros.INGENUO).generar(tac(
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
        assertTrue(asm.contains("# t0 = 1 + 2\n    li $t0, 1\n    li $t1, 2\n    addu $t2, $t0, $t1\n"), asm);
        assertTrue(asm.contains("# print t0\n"), asm);
        assertFalse(asm.contains("PENDIENTE"), asm);
    }

    @Test
    void lasRutinasPedidasSeEmitenUnaVezAlFinalConSusDependencias() {
        List<String> emitidas = new ArrayList<>();
        GeneradorMIPS generador = generador()
                .registrarRutina("__a", p -> {
                    emitidas.add("__a");
                    p.requerir("__b");
                    p.emit(InstruccionMips.etiqueta("__a"));
                })
                .registrarRutina("__b", p -> {
                    emitidas.add("__b");
                    p.emit(InstruccionMips.etiqueta("__b"));
                })
                .registrar(Instruccion.OP_PARAM, (ins, ctx) -> ctx.programa().requerir("__a"));

        String asm = generador.generar(tac(
                Instruccion.funcion("main", 24), Instruccion.param("1"), Instruccion.param("2"),
                Instruccion.finFuncion())).toString();

        assertEquals(List.of("__a", "__b"), emitidas);
        assertTrue(asm.endsWith("syscall\n__a:\n__b:\n"), asm);
    }

    @Test
    void rutinaSinImplementarEsUnError() {
        GeneradorMIPS generador = generador()
                .registrar(Instruccion.OP_PARAM, (ins, ctx) -> ctx.programa().requerir("__no_existe"));

        assertThrows(IllegalStateException.class, () -> generador.generar(tac(
                Instruccion.funcion("main", 24), Instruccion.param("1"), Instruccion.finFuncion())));
    }

    @Test
    void elAsignadorRecibeElCuerpoDeSuFuncionYElIndiceDeCadaInstruccion() {
        List<String> vistos = new ArrayList<>();
        FabricaRegistros fabrica = (programa, ubicaciones, cuerpo) -> {
            vistos.add("cuerpo " + cuerpo.size());
            return new ConIndice(new RegistrosIngenuos(programa, ubicaciones), vistos);
        };
        new GeneradorMIPS(f -> n -> "0($fp)", fabrica).generar(tac(
                Instruccion.funcion("main", 24), Instruccion.finFuncion(),
                Instruccion.funcion("f", 24), Instruccion.etiqueta("L0"), Instruccion.finFuncion()));

        assertEquals(List.of("cuerpo 2", "0", "1", "cuerpo 3", "0", "1", "2"), vistos);
    }

    /** Anota cada índice que recibe y delega todo lo demás. */
    private record ConIndice(AsignadorRegistros base, List<String> vistos) implements AsignadorRegistros {
        @Override
        public void antesDeInstruccion(int indice) {
            vistos.add(String.valueOf(indice));
        }

        @Override
        public String leer(String operando) {
            return base.leer(operando);
        }

        @Override
        public String paraResultado(String destino) {
            return base.paraResultado(destino);
        }

        @Override
        public void definido(String destino, String registro) {
            base.definido(destino, registro);
        }

        @Override
        public void copia(String destino, String origen) {
            base.copia(destino, origen);
        }

        @Override
        public void siguienteInstruccion() {
            base.siguienteInstruccion();
        }

        @Override
        public void finDeBloque() {
            base.finDeBloque();
        }

        @Override
        public void antesDeLlamada() {
            base.antesDeLlamada();
        }

        @Override
        public void antesDePosibleExcepcion() {
            base.antesDePosibleExcepcion();
        }

        @Override
        public void descartarEnCaptura() {
            base.descartarEnCaptura();
        }
    }
}
