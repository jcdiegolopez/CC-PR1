package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;

/**
 * Arreglos, objetos y las operaciones de string del {@code foreach} ({@code strlen}, {@code charat}).
 *
 * <p>Un arreglo es la dirección de un bloque del heap con la forma {@code [longitud][e0][e1]…}, una
 * palabra por casilla: {@code a[i]} está en {@code 4 + 4i(a)}. Un arreglo de arreglos guarda
 * referencias, así que las matrices no necesitan nada aparte.
 *
 * <p>Un objeto es la dirección de un bloque de {@code size} bytes. El TAC trae el offset de cada campo
 * ({@code t = obj.campo @k}), calculado por {@code AsignadorMemoria}; aquí no se recalcula nada. Como
 * los campos del padre van primero, un método heredado funciona igual sobre un objeto hijo.
 *
 * <p>Las direcciones y los chequeos usan solo {@code $t8}/{@code $t9}, que {@code getReg} nunca asigna.
 * {@code astore} y {@code fstore} escriben en el heap, no en una variable: solo leen operandos.
 */
public final class TraductorMemoria {

    /** Bytes de la cabecera de un arreglo (la longitud) y de cada casilla. */
    static final int PALABRA = 4;

    private TraductorMemoria() {
    }

    static void registrarEn(GeneradorMIPS generador) {
        generador.registrar(Instruccion.OP_NUEVO_ARREGLO, TraductorMemoria::nuevoArreglo);
        generador.registrar(Instruccion.OP_LEER_INDICE, TraductorMemoria::leerIndice);
        generador.registrar(Instruccion.OP_ESCRIBIR_INDICE, TraductorMemoria::escribirIndice);
        generador.registrar(Instruccion.OP_LONGITUD, TraductorMemoria::longitud);
        generador.registrar(Instruccion.OP_LONGITUD_CADENA,
                (ins, ctx) -> runtimeCadena(ins, ctx, RuntimeCadenas.STRLEN));
        generador.registrar(Instruccion.OP_CARACTER_EN, TraductorMemoria::caracterEn);
        generador.registrar(Instruccion.OP_NUEVO_OBJETO, TraductorMemoria::nuevoObjeto);
        generador.registrar(Instruccion.OP_LEER_CAMPO, TraductorMemoria::leerCampo);
        generador.registrar(Instruccion.OP_ESCRIBIR_CAMPO, TraductorMemoria::escribirCampo);
        RuntimeMemoria.registrarEn(generador);
    }

    /**
     * {@code t = newarray n}: reserva {@code 4 + 4n} bytes y guarda {@code n} en la cabecera. El registro
     * de {@code n} sigue valiendo después del {@code jal}, porque {@code __alloc} no toca {@code $t0}–{@code $t7}.
     */
    private static void nuevoArreglo(Instruccion ins, ContextoFuncion ctx) {
        AsignadorRegistros regs = ctx.registros();
        ProgramaMips programa = ctx.programa();
        String n = regs.leer(ins.a1());
        programa.requerir(RuntimeMemoria.ALLOC);
        programa.emit(InstruccionMips.de("sll", "$a0", n, "2"));
        programa.emit(InstruccionMips.de("addiu", "$a0", "$a0", String.valueOf(PALABRA)));
        programa.emit(InstruccionMips.de("jal", RuntimeMemoria.ALLOC));
        programa.emit(InstruccionMips.de("sw", n, "0($v0)"));
        String resultado = regs.paraResultado(ins.res());
        programa.emit(InstruccionMips.de("move", resultado, "$v0"));
        regs.definido(ins.res(), resultado);
    }

    /** {@code t = a[i]}. */
    private static void leerIndice(Instruccion ins, ContextoFuncion ctx) {
        AsignadorRegistros regs = ctx.registros();
        regs.antesDePosibleExcepcion();
        String arreglo = regs.leer(ins.a1());
        String indice = regs.leer(ins.a2());
        direccionCasilla(ctx.programa(), arreglo, indice);
        String resultado = regs.paraResultado(ins.res());
        ctx.programa().emit(InstruccionMips.de("lw", resultado, PALABRA + "($t8)"));
        regs.definido(ins.res(), resultado);
    }

    /** {@code a[i] = v}. */
    private static void escribirIndice(Instruccion ins, ContextoFuncion ctx) {
        AsignadorRegistros regs = ctx.registros();
        regs.antesDePosibleExcepcion();
        String arreglo = regs.leer(ins.a1());
        String indice = regs.leer(ins.a2());
        String valor = regs.leer(ins.res());
        direccionCasilla(ctx.programa(), arreglo, indice);
        ctx.programa().emit(InstruccionMips.de("sw", valor, PALABRA + "($t8)"));
    }

    /**
     * Chequea {@code 0 <= i < len(a)} y deja en {@code $t8} la dirección {@code a + 4i}; la casilla está
     * en {@code 4($t8)}. Un índice inválido salta a {@code __error_indice}, que lanza la excepción.
     */
    private static void direccionCasilla(ProgramaMips programa, String arreglo, String indice) {
        programa.requerir(RuntimeMemoria.ERROR_INDICE);
        programa.emit(InstruccionMips.de("bltz", indice, RuntimeMemoria.ERROR_INDICE));
        programa.emit(InstruccionMips.de("lw", "$t8", "0(" + arreglo + ")"));
        programa.emit(InstruccionMips.de("bge", indice, "$t8", RuntimeMemoria.ERROR_INDICE));
        programa.emit(InstruccionMips.de("sll", "$t8", indice, "2"));
        programa.emit(InstruccionMips.de("addu", "$t8", "$t8", arreglo));
    }

    /** {@code t = len a}: la longitud está en la cabecera. */
    private static void longitud(Instruccion ins, ContextoFuncion ctx) {
        AsignadorRegistros regs = ctx.registros();
        String arreglo = regs.leer(ins.a1());
        String resultado = regs.paraResultado(ins.res());
        ctx.programa().emit(InstruccionMips.de("lw", resultado, "0(" + arreglo + ")"));
        regs.definido(ins.res(), resultado);
    }

    /** {@code t = strlen s}: llama a {@code __strlen} con {@code s} en {@code $a0}. */
    private static void runtimeCadena(Instruccion ins, ContextoFuncion ctx, String rutina) {
        AsignadorRegistros regs = ctx.registros();
        ProgramaMips programa = ctx.programa();
        String cadena = regs.leer(ins.a1());
        programa.requerir(rutina);
        programa.emit(InstruccionMips.de("move", "$a0", cadena));
        programa.emit(InstruccionMips.de("jal", rutina));
        String resultado = regs.paraResultado(ins.res());
        programa.emit(InstruccionMips.de("move", resultado, "$v0"));
        regs.definido(ins.res(), resultado);
    }

    /**
     * {@code t = charat s, i} ({@code s[i]} y el {@code foreach} sobre un string). {@code __charat} no valida
     * el índice, así que antes se mide el string con {@code __strlen} y se chequea {@code 0 <= i < len(s)},
     * igual que en un arreglo. Las dos rutinas solo tocan {@code $a*}, {@code $v0} y {@code $t8}/{@code $t9}.
     */
    private static void caracterEn(Instruccion ins, ContextoFuncion ctx) {
        AsignadorRegistros regs = ctx.registros();
        ProgramaMips programa = ctx.programa();
        regs.antesDePosibleExcepcion();
        String cadena = regs.leer(ins.a1());
        String indice = regs.leer(ins.a2());
        programa.requerir(RuntimeMemoria.ERROR_INDICE);
        programa.requerir(RuntimeCadenas.STRLEN);
        programa.requerir(RuntimeCadenas.CHARAT);
        programa.emit(InstruccionMips.de("bltz", indice, RuntimeMemoria.ERROR_INDICE));
        programa.emit(InstruccionMips.de("move", "$a0", cadena));
        programa.emit(InstruccionMips.de("jal", RuntimeCadenas.STRLEN));
        programa.emit(InstruccionMips.de("bge", indice, "$v0", RuntimeMemoria.ERROR_INDICE));
        programa.emit(InstruccionMips.de("move", "$a0", cadena));
        programa.emit(InstruccionMips.de("move", "$a1", indice));
        programa.emit(InstruccionMips.de("jal", RuntimeCadenas.CHARAT));
        String resultado = regs.paraResultado(ins.res());
        programa.emit(InstruccionMips.de("move", resultado, "$v0"));
        regs.definido(ins.res(), resultado);
    }

    /**
     * {@code t = new Clase, size}. Una clase sin campos pide una palabra igual, para que dos objetos
     * distintos nunca tengan la misma dirección.
     */
    private static void nuevoObjeto(Instruccion ins, ContextoFuncion ctx) {
        AsignadorRegistros regs = ctx.registros();
        ProgramaMips programa = ctx.programa();
        int tamano = Math.max(Integer.parseInt(ins.a2()), PALABRA);
        programa.requerir(RuntimeMemoria.ALLOC);
        programa.emit(InstruccionMips.de("li", "$a0", String.valueOf(tamano)));
        programa.emit(InstruccionMips.de("jal", RuntimeMemoria.ALLOC));
        String resultado = regs.paraResultado(ins.res());
        programa.emit(InstruccionMips.de("move", resultado, "$v0"));
        regs.definido(ins.res(), resultado);
    }

    /** {@code t = obj.campo @k} → {@code lw t, k(obj)}. */
    private static void leerCampo(Instruccion ins, ContextoFuncion ctx) {
        AsignadorRegistros regs = ctx.registros();
        String objeto = regs.leer(ins.a1());
        int offset = offsetCampo(ins.a2());
        String resultado = regs.paraResultado(ins.res());
        ctx.programa().emit(InstruccionMips.de("lw", resultado, offset + "(" + objeto + ")"));
        regs.definido(ins.res(), resultado);
    }

    /** {@code obj.campo @k = v} → {@code sw v, k(obj)}. */
    private static void escribirCampo(Instruccion ins, ContextoFuncion ctx) {
        AsignadorRegistros regs = ctx.registros();
        String objeto = regs.leer(ins.a1());
        String valor = regs.leer(ins.res());
        int offset = offsetCampo(ins.a2());
        ctx.programa().emit(InstruccionMips.de("sw", valor, offset + "(" + objeto + ")"));
    }

    /** Offset de {@code "campo @k"}. Sin {@code @k} el TAC se generó sin {@code Layout}: no hay MIPS posible. */
    static int offsetCampo(String campo) {
        int arroba = campo.lastIndexOf(" @");
        if (arroba < 0) {
            throw new IllegalStateException("El campo " + campo + " no trae su offset (@k) en el TAC");
        }
        return Integer.parseInt(campo.substring(arroba + 2));
    }
}
