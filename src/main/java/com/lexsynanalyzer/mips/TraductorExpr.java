package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;

import java.util.Map;

/**
 * Asignación, aritmética, comparaciones, unarias, saltos, {@code print} y operaciones de strings.
 *
 * <p>Toda instrucción sigue el mismo patrón: pedir los operandos a {@link AsignadorRegistros}, emitir
 * la operación MIPS y avisar dónde quedó el resultado. Los booleanos son {@code 0}/{@code 1}, así
 * que las comparaciones de MARS ({@code slt}, {@code seq}, …) ya dan un booleano válido.
 *
 * <p>Cada {@code print} termina con un salto de línea (syscall 11).
 */
public final class TraductorExpr {

    /** Rutina a la que salta una división o un módulo entre cero. */
    public static final String ERROR_DIVISION = "__error_div0";
    public static final String MENSAJE_DIVISION = "División entre cero";

    /** Mnemónico MIPS de cada operador binario que no necesita chequeo. */
    private static final Map<String, String> MNEMONICOS = Map.of(
            "+", "addu", "-", "subu", "*", "mul",
            "<", "slt", "<=", "sle", ">", "sgt", ">=", "sge", "==", "seq", "!=", "sne");

    private TraductorExpr() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        generador.registrar(Instruccion.OP_ASIGNAR, TraductorExpr::asignar);
        MNEMONICOS.forEach((operador, mnemonico) ->
                generador.registrar(operador, (ins, ctx) -> binaria(ins, ctx, mnemonico)));
        generador.registrar("/", (ins, ctx) -> division(ins, ctx, "mflo"));
        generador.registrar("%", (ins, ctx) -> division(ins, ctx, "mfhi"));
        generador.registrar(Instruccion.OP_NEGAR, (ins, ctx) -> unaria(ins, ctx, "subu", "$zero", null));
        generador.registrar(Instruccion.OP_NO, (ins, ctx) -> unaria(ins, ctx, "xori", null, "1"));
        generador.registrar(Instruccion.OP_SALTO, TraductorExpr::salto);
        generador.registrar(Instruccion.OP_SI, (ins, ctx) -> saltoCondicional(ins, ctx, "bnez"));
        generador.registrar(Instruccion.OP_SI_NO, (ins, ctx) -> saltoCondicional(ins, ctx, "beqz"));
        generador.registrar(Instruccion.OP_PRINT, (ins, ctx) -> imprimirConSyscall(ins, ctx, "1"));
        generador.registrar(Instruccion.OP_PRINT_CADENA, (ins, ctx) -> imprimirConSyscall(ins, ctx, "4"));
        generador.registrar(Instruccion.OP_PRINT_BOOLEANO, TraductorExpr::imprimirBooleano);
        generador.registrar(Instruccion.OP_CONCAT, (ins, ctx) -> cadenas(ins, ctx, RuntimeCadenas.CONCAT, false));
        generador.registrar(Instruccion.OP_STREQ, (ins, ctx) -> cadenas(ins, ctx, RuntimeCadenas.STREQ, false));
        generador.registrar(Instruccion.OP_STRNE, (ins, ctx) -> cadenas(ins, ctx, RuntimeCadenas.STREQ, true));
        generador.registrarRutina(ERROR_DIVISION, TraductorExpr::errorDivision);
    }

    /** {@code x = y}: con {@code GetReg} no emite nada; {@code x} pasa a vivir en el registro de {@code y}. */
    private static void asignar(Instruccion ins, ContextoFuncion ctx) {
        ctx.registros().copia(ins.res(), ins.a1());
    }

    private static void binaria(Instruccion ins, ContextoFuncion ctx, String mnemonico) {
        AsignadorRegistros regs = ctx.registros();
        String izquierda = regs.leer(ins.a1());
        String derecha = regs.leer(ins.a2());
        String resultado = regs.paraResultado(ins.res());
        ctx.programa().emit(InstruccionMips.de(mnemonico, resultado, izquierda, derecha));
        regs.definido(ins.res(), resultado);
    }

    /**
     * {@code /} y {@code %}: MIPS no falla al dividir entre cero, así que se chequea antes. {@code div}
     * deja el cociente en {@code LO} y el residuo en {@code HI}; los dos truncan hacia cero, igual
     * que Compiscript.
     */
    private static void division(Instruccion ins, ContextoFuncion ctx, String mover) {
        AsignadorRegistros regs = ctx.registros();
        regs.antesDePosibleExcepcion();
        String dividendo = regs.leer(ins.a1());
        String divisor = regs.leer(ins.a2());
        ctx.programa().requerir(ERROR_DIVISION);
        ctx.programa().emit(InstruccionMips.de("beq", divisor, "$zero", ERROR_DIVISION));
        ctx.programa().emit(InstruccionMips.de("div", dividendo, divisor));
        String resultado = regs.paraResultado(ins.res());
        ctx.programa().emit(InstruccionMips.de(mover, resultado));
        regs.definido(ins.res(), resultado);
    }

    /** {@code -a} es {@code 0 - a}; {@code !a} es {@code a xor 1} (los booleanos son 0/1). */
    private static void unaria(Instruccion ins, ContextoFuncion ctx, String mnemonico, String antes, String despues) {
        AsignadorRegistros regs = ctx.registros();
        String operando = regs.leer(ins.a1());
        String resultado = regs.paraResultado(ins.res());
        ctx.programa().emit(antes != null
                ? InstruccionMips.de(mnemonico, resultado, antes, operando)
                : InstruccionMips.de(mnemonico, resultado, operando, despues));
        regs.definido(ins.res(), resultado);
    }

    private static void salto(Instruccion ins, ContextoFuncion ctx) {
        ctx.registros().finDeBloque();
        ctx.programa().emit(InstruccionMips.de("j", ins.a1()));
    }

    /** La condición se lee antes de cerrar el bloque: los {@code sw} de cierre no tocan su registro. */
    private static void saltoCondicional(Instruccion ins, ContextoFuncion ctx, String mnemonico) {
        String condicion = ctx.registros().leer(ins.a1());
        ctx.registros().finDeBloque();
        ctx.programa().emit(InstruccionMips.de(mnemonico, condicion, ins.a2()));
    }

    /** {@code print} (syscall 1, entero) y {@code prints} (syscall 4, dirección de un string). */
    private static void imprimirConSyscall(Instruccion ins, ContextoFuncion ctx, String syscall) {
        String valor = ctx.registros().leer(ins.a1());
        ctx.programa().emit(InstruccionMips.de("move", "$a0", valor));
        ctx.programa().emit(InstruccionMips.de("li", "$v0", syscall));
        ctx.programa().emit(InstruccionMips.de("syscall"));
        saltoDeLinea(ctx.programa());
    }

    private static void imprimirBooleano(Instruccion ins, ContextoFuncion ctx) {
        String valor = ctx.registros().leer(ins.a1());
        ctx.programa().requerir(RuntimeCadenas.PRINT_BOOL);
        ctx.programa().emit(InstruccionMips.de("move", "$a0", valor));
        ctx.programa().emit(InstruccionMips.de("jal", RuntimeCadenas.PRINT_BOOL));
        saltoDeLinea(ctx.programa());
    }

    static void saltoDeLinea(ProgramaMips programa) {
        programa.emit(InstruccionMips.de("li", "$a0", "10"));
        programa.emit(InstruccionMips.de("li", "$v0", "11"));
        programa.emit(InstruccionMips.de("syscall"));
    }

    /**
     * {@code concat}, {@code streq} y {@code strne} llaman al runtime con los operandos en
     * {@code $a0}/{@code $a1}. El runtime no toca {@code $t0}–{@code $t7}, así que no hace falta
     * {@code antesDeLlamada()}.
     */
    private static void cadenas(Instruccion ins, ContextoFuncion ctx, String rutina, boolean negar) {
        AsignadorRegistros regs = ctx.registros();
        String izquierda = regs.leer(ins.a1());
        String derecha = regs.leer(ins.a2());
        ctx.programa().requerir(rutina);
        ctx.programa().emit(InstruccionMips.de("move", "$a0", izquierda));
        ctx.programa().emit(InstruccionMips.de("move", "$a1", derecha));
        ctx.programa().emit(InstruccionMips.de("jal", rutina));
        String resultado = regs.paraResultado(ins.res());
        ctx.programa().emit(negar
                ? InstruccionMips.de("xori", resultado, "$v0", "1")
                : InstruccionMips.de("move", resultado, "$v0"));
        regs.definido(ins.res(), resultado);
    }

    private static void errorDivision(ProgramaMips programa) {
        programa.requerir(GeneradorMIPS.LANZAR);
        programa.emit(InstruccionMips.etiqueta(ERROR_DIVISION));
        programa.emit(InstruccionMips.de("la", "$a0", programa.cadena(MENSAJE_DIVISION)));
        programa.emit(InstruccionMips.de("j", GeneradorMIPS.LANZAR));
    }
}
