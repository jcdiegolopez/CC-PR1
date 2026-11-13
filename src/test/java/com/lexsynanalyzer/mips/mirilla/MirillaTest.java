package com.lexsynanalyzer.mips.mirilla;

import com.lexsynanalyzer.mips.InstruccionMips;
import com.lexsynanalyzer.mips.ProgramaMips;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Una prueba por regla: lista de entrada → lista esperada. Las listas se escriben como en MARS, una
 * línea por instrucción: {@code L0:} es una etiqueta y {@code # x} un comentario suelto.
 */
class MirillaTest {

    @Nested
    class MoveRedundante {

        @Test
        void eliminaMoveDeUnRegistroASiMismo() {
            assertRegla(new ReglaMoveRedundante(), 1, """
                    move $t0, $t0
                    move $t1, $t0
                    """, """
                    move $t1, $t0
                    """);
        }
    }

    @Nested
    class GuardarCargar {

        @Test
        void eliminaElLwDelMismoRegistro() {
            assertRegla(new ReglaGuardarCargar(), 1, """
                    sw $t1, 24($fp)
                    # t0 = t0 + 1
                    lw $t1, 24($fp)
                    """, """
                    sw $t1, 24($fp)
                    # t0 = t0 + 1
                    """);
        }

        @Test
        void cambiaElLwAOtroRegistroPorUnMove() {
            assertRegla(new ReglaGuardarCargar(), 1, """
                    sw $t1, g_x
                    lw $t0, g_x
                    """, """
                    sw $t1, g_x
                    move $t0, $t1
                    """);
        }

        @Test
        void noCruzaEtiquetasNiCambiaOtraDireccion() {
            assertRegla(new ReglaGuardarCargar(), 0, """
                    sw $t1, g_x
                    L0:
                    lw $t1, g_x
                    sw $t1, 24($fp)
                    lw $t1, 28($fp)
                    """, """
                    sw $t1, g_x
                    L0:
                    lw $t1, g_x
                    sw $t1, 24($fp)
                    lw $t1, 28($fp)
                    """);
        }
    }

    @Nested
    class AccesoRedundante {

        @Test
        void eliminaLaSegundaCargaIgual() {
            assertRegla(new ReglaAccesoRedundante(), 1, """
                    lw $t0, g_v
                    lw $t0, g_v
                    """, """
                    lw $t0, g_v
                    """);
        }

        @Test
        void eliminaGuardarLoQueSeAcabaDeCargar() {
            assertRegla(new ReglaAccesoRedundante(), 1, """
                    lw $t0, g_a
                    # param a
                    sw $t0, g_a
                    """, """
                    lw $t0, g_a
                    # param a
                    """);
        }

        @Test
        void eliminaElPrimerGuardadoCuandoElSegundoLoPisa() {
            assertRegla(new ReglaAccesoRedundante(), 1, """
                    sw $t0, g_a
                    sw $t1, g_a
                    """, """
                    sw $t1, g_a
                    """);
        }

        @Test
        void noTocaUnaCargaQueCambiaSuPropiaBase() {
            assertRegla(new ReglaAccesoRedundante(), 0, """
                    lw $t0, 0($t0)
                    lw $t0, 0($t0)
                    """, """
                    lw $t0, 0($t0)
                    lw $t0, 0($t0)
                    """);
        }
    }

    @Nested
    class SaltoASiguiente {

        @Test
        void eliminaElSaltoALaEtiquetaDeAbajo() {
            assertRegla(new ReglaSaltoASiguiente(), 2, """
                    j L1
                    # comentario
                    L0:
                    L1:
                    beqz $t0, L2
                    L2:
                    li $t0, 1
                    """, """
                    # comentario
                    L0:
                    L1:
                    L2:
                    li $t0, 1
                    """);
        }

        @Test
        void noEliminaSiHayUnaInstruccionEnMedio() {
            assertRegla(new ReglaSaltoASiguiente(), 0, """
                    j L1
                    li $t0, 1
                    L1:
                    """, """
                    j L1
                    li $t0, 1
                    L1:
                    """);
        }
    }

    @Nested
    class SaltoASalto {

        @Test
        void sigueLaCadenaDeSaltosHastaElDestinoFinal() {
            assertRegla(new ReglaSaltoASalto(), 3, """
                    beqz $t0, L1
                    j L1
                    L1:
                    # goto L2
                    j L2
                    L2:
                    j L3
                    L3:
                    li $t0, 1
                    """, """
                    beqz $t0, L3
                    j L3
                    L1:
                    # goto L2
                    j L3
                    L2:
                    j L3
                    L3:
                    li $t0, 1
                    """);
        }

        @Test
        void noTocaUnCicloDeSaltos() {
            assertRegla(new ReglaSaltoASalto(), 0, """
                    j L1
                    L1:
                    j L2
                    L2:
                    j L1
                    """, """
                    j L1
                    L1:
                    j L2
                    L2:
                    j L1
                    """);
        }
    }

    @Nested
    class CodigoInalcanzable {

        @Test
        void eliminaLoQueHayEntreUnSaltoYLaSiguienteEtiqueta() {
            assertRegla(new ReglaCodigoInalcanzable(), 3, """
                    j L0
                    # print t0
                    lw $t0, 24($fp)
                    li $v0, 1
                    L5:
                    jr $ra
                    syscall
                    """, """
                    j L0
                    # print t0
                    L5:
                    jr $ra
                    """);
        }
    }

    @Nested
    class IdentidadAlgebraica {

        @Test
        void eliminaSumasDeCeroYMultiplicacionesPorUno() {
            assertRegla(new ReglaIdentidadAlgebraica(), 2, """
                    addiu $t0, $t0, 0
                    mul $t1, $t1, 1
                    """, "");
        }

        @Test
        void conOtroDestinoQuedaUnMove() {
            assertRegla(new ReglaIdentidadAlgebraica(), 2, """
                    addu $t2, $zero, $t0
                    li $t1, 0
                    subu $t3, $t0, $t1
                    """, """
                    move $t2, $t0
                    li $t1, 0
                    move $t3, $t0
                    """);
        }

        @Test
        void usaLaConstanteDeUnLiJustoAntes() {
            assertRegla(new ReglaIdentidadAlgebraica(), 2, """
                    li $t0, 1
                    mul $t0, $t1, $t0
                    li $t2, 0
                    mul $t3, $t2, $t1
                    """, """
                    li $t0, 1
                    move $t0, $t1
                    li $t2, 0
                    li $t3, 0
                    """);
        }

        @Test
        void noUsaUnLiAntesDeUnaEtiquetaNiRestaAlReves() {
            assertRegla(new ReglaIdentidadAlgebraica(), 0, """
                    li $t1, 0
                    L0:
                    addu $t2, $t0, $t1
                    subu $t2, $zero, $t0
                    """, """
                    li $t1, 0
                    L0:
                    addu $t2, $t0, $t1
                    subu $t2, $zero, $t0
                    """);
        }
    }

    @Nested
    class ReduccionFuerza {

        @Test
        void multiplicarPorPotenciaDeDosEsUnCorrimiento() {
            assertRegla(new ReglaReduccionFuerza(), 3, """
                    mul $t0, $t1, 8
                    li $t2, 4
                    mul $t3, $t2, $t0
                    li $t4, 1024
                    mul $t4, $t5, $t4
                    """, """
                    sll $t0, $t1, 3
                    li $t2, 4
                    sll $t3, $t0, 2
                    li $t4, 1024
                    sll $t4, $t5, 10
                    """);
        }

        @Test
        void noTocaLoQueNoEsPotenciaDeDos() {
            assertRegla(new ReglaReduccionFuerza(), 0, """
                    li $t1, 7
                    mul $t2, $t0, $t1
                    mul $t2, $t0, -4
                    """, """
                    li $t1, 7
                    mul $t2, $t0, $t1
                    mul $t2, $t0, -4
                    """);
        }
    }

    @Nested
    class InversionRama {

        @Test
        void invierteLaCondicionYQuitaElSalto() {
            assertRegla(new ReglaInversionRama(), 1, """
                    beqz $t1, L8
                    # goto L6
                    j L6
                    L8:
                    """, """
                    bnez $t1, L6
                    # goto L6
                    L8:
                    """);
        }

        @Test
        void noInvierteSiLaEtiquetaNoSigueAlSalto() {
            assertRegla(new ReglaInversionRama(), 0, """
                    bge $t1, $t8, L8
                    j L6
                    li $t0, 1
                    L8:
                    """, """
                    bge $t1, $t8, L8
                    j L6
                    li $t0, 1
                    L8:
                    """);
        }
    }

    @Nested
    class EtiquetaSinUso {

        @Test
        void eliminaLasEtiquetasQueNadieNombra() {
            assertRegla(new ReglaEtiquetaSinUso(), 2, """
                    main:
                    L0:
                    la $t9, L1
                    L1:
                    jal f_suma
                    L2:
                    f_suma:
                    L3:
                    j L0
                    """, """
                    main:
                    L0:
                    la $t9, L1
                    L1:
                    jal f_suma
                    f_suma:
                    j L0
                    """);
        }
    }

    @Nested
    class PuntoFijo {

        @Test
        void unaReglaAbreOportunidadesAOtras() {
            ProgramaMips programa = programa("""
                    main:
                    sw $t0, 24($fp)
                    lw $t0, 24($fp)
                    li $t1, 0
                    addu $t0, $t0, $t1
                    beqz $t0, L1
                    j L2
                    L1:
                    li $t2, 2
                    mul $t3, $t0, $t2
                    j L3
                    li $v0, 1
                    L3:
                    L2:
                    li $v0, 10
                    syscall
                    """);

            Mirilla.Resultado resultado = new Mirilla().optimizar(programa);

            assertEquals(texto("""
                    main:
                    sw $t0, 24($fp)
                    li $t1, 0
                    bnez $t0, L2
                    li $t2, 2
                    sll $t3, $t0, 1
                    L2:
                    li $v0, 10
                    syscall
                    """), texto(resultado.programa().texto()));
            assertEquals(Map.of("sw/lw", 1, "salto-a-siguiente", 1, "codigo-inalcanzable", 1,
                    "identidad-algebraica", 1, "reduccion-de-fuerza", 1, "inversion-de-rama", 1,
                    "etiqueta-sin-uso", 2), resultado.aplicaciones());
            assertEquals(5, Mirilla.eliminadas(programa, resultado.programa()));
        }

        @Test
        void noModificaElProgramaOriginalNiSusDatos() {
            ProgramaMips programa = programa("""
                    main:
                    move $t0, $t0
                    """);
            programa.cadena("hola");

            Mirilla.Resultado resultado = new Mirilla().optimizar(programa);

            assertEquals(2, programa.texto().size());
            assertTrue(resultado.programa().toString().contains("str0: .asciiz \"hola\""));
            assertEquals(List.of("move-redundante"), List.copyOf(resultado.aplicaciones().keySet()));
        }

        @Test
        void unProgramaVacioQuedaIgual() {
            Mirilla.Resultado resultado = new Mirilla().optimizar(ProgramaMips.vacio());

            assertTrue(resultado.programa().estaVacio());
            assertTrue(resultado.aplicaciones().isEmpty());
        }
    }

    // --- Utilidades ---

    private static void assertRegla(Regla regla, int vecesEsperadas, String entrada, String esperado) {
        List<InstruccionMips> codigo = new ArrayList<>(lineas(entrada));
        int veces = regla.aplicar(codigo);
        assertEquals(texto(esperado), texto(codigo), regla.nombre());
        assertEquals(vecesEsperadas, veces, regla.nombre());
    }

    private static ProgramaMips programa(String asm) {
        ProgramaMips programa = ProgramaMips.vacio();
        lineas(asm).forEach(programa::emit);
        return programa;
    }

    private static String texto(String asm) {
        return texto(lineas(asm));
    }

    private static String texto(List<InstruccionMips> codigo) {
        StringBuilder texto = new StringBuilder();
        codigo.forEach(ins -> texto.append(ins).append('\n'));
        return texto.toString();
    }

    /** {@code L0:} → etiqueta, {@code # x} → comentario suelto, lo demás → instrucción. */
    static List<InstruccionMips> lineas(String asm) {
        List<InstruccionMips> codigo = new ArrayList<>();
        for (String linea : asm.strip().split("\n")) {
            linea = linea.strip();
            if (linea.isEmpty()) {
                continue;
            }
            if (linea.startsWith("#")) {
                codigo.add(InstruccionMips.comentarioSuelto(linea.substring(1).strip()));
            } else if (linea.endsWith(":")) {
                codigo.add(InstruccionMips.etiqueta(linea.substring(0, linea.length() - 1)));
            } else {
                int espacio = linea.indexOf(' ');
                String op = espacio < 0 ? linea : linea.substring(0, espacio);
                String[] operandos = espacio < 0 ? new String[0] : linea.substring(espacio + 1).split(",\\s*");
                codigo.add(InstruccionMips.de(op, Arrays.copyOf(operandos, operandos.length)));
            }
        }
        return codigo;
    }
}
