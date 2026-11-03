package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.analyzer.AnalysisResult;
import com.lexsynanalyzer.analyzer.LexSynAnalyzer;
import com.lexsynanalyzer.semantic.Direccion;
import com.lexsynanalyzer.semantic.Layout;
import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.ProgramaTAC;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compila un {@code .cps} a MIPS para los tests de la persona A, mientras no existan
 * {@code Ubicaciones}, {@code MarcoActivacion} ni {@code TraductorLlamadas} (persona B). Es <b>solo
 * de prueba</b> y sigue el contrato §3.3 del plan:
 * <ul>
 *   <li>globales en {@code .data} como {@code g_x}; variables del frame en {@code offset($fp)}; el
 *       temporal {@code tk} en {@code tamanoBase + 4k($fp)};</li>
 *   <li>prólogo {@code addiu $sp, $sp, -N; sw $ra; sw $fp; sw $zero; move $fp, $sp} y epílogo en
 *       {@code f_<f>_fin};</li>
 *   <li>{@code param} se acumula; en el {@code call} se llama a {@code antesDeLlamada()} y cada
 *       argumento se escribe con {@code $t8} directo en el frame futuro: {@code (offParam - N)($sp)}.</li>
 * </ul>
 * No hay enlace de acceso: una función anidada no puede leer las locales de la que la contiene.
 */
final class MipsDePrueba {

    private static final Pattern TEMPORAL = Pattern.compile("t(\\d+)");

    private final Layout layout;
    private final Map<String, Integer> tamanos = new HashMap<>();
    private final List<String> argumentos = new ArrayList<>();

    private MipsDePrueba(Layout layout, ProgramaTAC tac) {
        this.layout = layout;
        for (Instruccion ins : tac.instrucciones()) {
            if (ins.op().equals(Instruccion.OP_FUNCION)) {
                tamanos.put(ins.a1(), Integer.parseInt(ins.a2()));
            }
        }
    }

    static ProgramaMips compilar(String fuente, FabricaRegistros modo) {
        AnalysisResult resultado = LexSynAnalyzer.analizarTexto(fuente);
        assertTrue(resultado.exitoso(), () -> "El programa de prueba debe compilar: " + resultado.errores());
        MipsDePrueba marco = new MipsDePrueba(resultado.layout(), resultado.tac());
        GeneradorMIPS generador = new GeneradorMIPS(funcion -> ubicaciones(resultado.layout(), funcion), modo);
        generador.registrar(Instruccion.OP_FUNCION, (ins, ctx) -> marco.prologo(ctx));
        generador.registrar(Instruccion.OP_FIN_FUNCION, (ins, ctx) -> marco.epilogo(ctx));
        generador.registrar(Instruccion.OP_PARAM, (ins, ctx) -> marco.argumentos.add(ins.a1()));
        generador.registrar(Instruccion.OP_LLAMAR, marco::llamar);
        generador.registrar(Instruccion.OP_RETORNO, MipsDePrueba::retornar);
        return generador.generar(resultado.tac());
    }

    static ResolvedorUbicaciones ubicaciones(Layout layout, String funcion) {
        Map<String, String> direcciones = new HashMap<>();
        layout.globales().forEach(v -> direcciones.put(v.direccion().nombreTac(), "g_" + v.direccion().nombreTac()));
        Layout.Frame frame = layout.frames().get(funcion);
        if (frame != null) {
            frame.variables().forEach(v ->
                    direcciones.put(v.direccion().nombreTac(), v.direccion().offset() + "($fp)"));
        }
        int base = layout.tamanoBase(funcion);
        return nombre -> {
            Matcher temporal = TEMPORAL.matcher(nombre);
            if (temporal.matches()) {
                return (base + 4 * Integer.parseInt(temporal.group(1))) + "($fp)";
            }
            String direccion = direcciones.get(nombre);
            if (direccion == null) {
                throw new IllegalArgumentException("Sin dirección en " + funcion + ": " + nombre);
            }
            return direccion;
        };
    }

    private void prologo(ContextoFuncion ctx) {
        ProgramaMips p = ctx.programa();
        if (ctx.esMain()) {
            layout.globales().forEach(v -> p.dato("g_" + v.direccion().nombreTac(), ".word 0"));
        }
        p.emit(InstruccionMips.etiqueta(GeneradorMIPS.etiquetaFuncion(ctx.funcion())));
        p.emit(InstruccionMips.de("addiu", "$sp", "$sp", String.valueOf(-ctx.tamanoFrame())));
        p.emit(InstruccionMips.de("sw", "$ra", "0($sp)"));
        p.emit(InstruccionMips.de("sw", "$fp", "8($sp)"));
        p.emit(InstruccionMips.de("sw", "$zero", "16($sp)"));
        p.emit(InstruccionMips.de("move", "$fp", "$sp"));
    }

    private void epilogo(ContextoFuncion ctx) {
        ProgramaMips p = ctx.programa();
        ctx.registros().finDeBloque();
        p.emit(InstruccionMips.etiqueta(fin(ctx.funcion())));
        if (ctx.esMain()) {
            p.emit(InstruccionMips.de("li", "$v0", "10"));
            p.emit(InstruccionMips.de("syscall"));
            return;
        }
        p.emit(InstruccionMips.de("lw", "$ra", "0($fp)"));
        p.emit(InstruccionMips.de("addiu", "$sp", "$fp", String.valueOf(ctx.tamanoFrame())));
        p.emit(InstruccionMips.de("lw", "$fp", "8($fp)"));
        p.emit(InstruccionMips.de("jr", "$ra"));
    }

    private static String fin(String funcion) {
        return GeneradorMIPS.etiquetaFuncion(funcion) + "_fin";
    }

    /** {@code return a}: el valor va en {@code $v0}; se cierra el bloque y se salta al epílogo. */
    private static void retornar(Instruccion ins, ContextoFuncion ctx) {
        if (ins.a1() != null) {
            String valor = ctx.registros().leer(ins.a1());
            ctx.programa().emit(InstruccionMips.de("move", "$v0", valor));
        }
        ctx.registros().finDeBloque();
        ctx.programa().emit(InstruccionMips.de("j", fin(ctx.funcion())));
    }

    private void llamar(Instruccion ins, ContextoFuncion ctx) {
        String funcion = ins.a1();
        ProgramaMips p = ctx.programa();
        ResolvedorUbicaciones aqui = ubicaciones(layout, ctx.funcion());
        ctx.registros().antesDeLlamada();
        int tamano = tamanos.get(funcion);
        List<Integer> offsets = layout.frames().get(funcion).variables().stream()
                .map(Layout.Variable::direccion)
                .filter(d -> d.categoria() == Direccion.Categoria.PARAM)
                .map(Direccion::offset).sorted(Comparator.naturalOrder()).toList();
        for (int i = 0; i < argumentos.size(); i++) {
            String argumento = argumentos.get(i);
            if (Operandos.esInmediato(argumento)) {
                p.emit(InstruccionMips.de("li", "$t8", Operandos.valorInmediato(argumento)));
            } else if (Operandos.esCadena(argumento)) {
                p.emit(InstruccionMips.de("la", "$t8", p.cadena(Operandos.contenidoCadena(argumento))));
            } else {
                p.emit(InstruccionMips.de("lw", "$t8", aqui.direccion(argumento)));
            }
            p.emit(InstruccionMips.de("sw", "$t8", (offsets.get(i) - tamano) + "($sp)"));
        }
        argumentos.clear();
        p.emit(InstruccionMips.de("jal", GeneradorMIPS.etiquetaFuncion(funcion)));
        if (ins.res() != null) {
            String resultado = ctx.registros().paraResultado(ins.res());
            p.emit(InstruccionMips.de("move", resultado, "$v0"));
            ctx.registros().definido(ins.res(), resultado);
        }
    }
}
