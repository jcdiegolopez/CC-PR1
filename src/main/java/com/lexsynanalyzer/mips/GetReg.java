package com.lexsynanalyzer.mips;

import com.lexsynanalyzer.tac.Instruccion;
import com.lexsynanalyzer.tac.TempPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * {@code getReg} del Dragon Book (§8.6): reparte {@code $t0}–{@code $t7} recordando qué valor tiene
 * cada registro, para no volver a cargar de memoria lo que ya está en uno.
 *
 * <p>Lleva dos descriptores:
 * <ul>
 *   <li><b>de registro</b>: qué nombres tiene cada registro ({@code $t2 → {t0, x}} después de
 *       {@code x = t0});</li>
 *   <li><b>de dirección</b>: en qué registro está cada nombre y si ese valor es más nuevo que el de
 *       memoria (<i>sucio</i>).</li>
 * </ul>
 *
 * <p>Cuando no hay registro libre se <b>derrama</b> uno: se prefiere uno cuyos valores ya estén en
 * memoria o no se vuelvan a usar; si no hay, el de próximo uso más lejano, guardando antes sus
 * valores sucios ({@code sw ... # derrame x}). Los registros de los operandos de la instrucción
 * actual quedan fijados y nunca se desalojan a media instrucción.
 *
 * <p>Usa {@link Vida}: un temporal muerto no se guarda nunca. Las variables del programa siempre se
 * guardan al cerrar un bloque, porque otra función, un {@code catch} o la siguiente vuelta del ciclo
 * pueden leerlas de memoria.
 */
public final class GetReg implements AsignadorRegistros {

    static final List<String> REGISTROS = List.of("$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7");

    private final ProgramaMips programa;
    private final ResolvedorUbicaciones ubicaciones;
    private final Vida vida;

    /** Descriptor de registro: nombres cuyo valor actual está en cada registro. */
    private final Map<String, Set<String>> contenido = new LinkedHashMap<>();
    /** Descriptor de dirección: registro de cada nombre que está en uno. */
    private final Map<String, String> registroDe = new HashMap<>();
    /** Nombres cuyo valor en registro todavía no se guardó en memoria. */
    private final Set<String> sucios = new HashSet<>();
    /** Registros que usa la instrucción TAC actual. */
    private final Set<String> fijados = new HashSet<>();
    /** Nombres que alguna vez se derramaron (sus recargas también se comentan). */
    private final Set<String> derramados = new HashSet<>();
    private int actual;

    public GetReg(ProgramaMips programa, ResolvedorUbicaciones ubicaciones, List<Instruccion> cuerpo) {
        this.programa = Objects.requireNonNull(programa);
        this.ubicaciones = Objects.requireNonNull(ubicaciones);
        this.vida = Vida.analizar(cuerpo);
        REGISTROS.forEach(r -> contenido.put(r, new LinkedHashSet<>()));
    }

    @Override
    public void antesDeInstruccion(int indice) {
        actual = indice;
    }

    @Override
    public String leer(String operando) {
        if (Operandos.esLiteral(operando)) {
            String registro = registroLibre();
            if (Operandos.esInmediato(operando)) {
                programa.emit(InstruccionMips.de("li", registro, Operandos.valorInmediato(operando)));
            } else {
                String etiqueta = programa.cadena(Operandos.contenidoCadena(operando));
                programa.emit(InstruccionMips.de("la", registro, etiqueta));
            }
            fijados.add(registro);
            return registro;
        }
        String registro = registroDe.get(operando);
        if (registro == null) {
            registro = registroLibre();
            InstruccionMips carga = InstruccionMips.de("lw", registro, ubicaciones.direccion(operando));
            programa.emit(derramados.contains(operando) ? carga.conComentario("derrame " + operando) : carga);
            asociar(operando, registro);
        }
        fijados.add(registro);
        return registro;
    }

    @Override
    public String paraResultado(String destino) {
        String propio = registroDe.get(destino);
        if (propio != null && contenido.get(propio).equals(Set.of(destino))) {
            fijados.add(propio);
            return propio;
        }
        for (String registro : REGISTROS) {
            if (fijados.contains(registro) && contenido.get(registro).stream().noneMatch(this::seNecesitaDespues)) {
                vaciar(registro);
                return registro;
            }
        }
        String registro = registroLibre();
        fijados.add(registro);
        return registro;
    }

    @Override
    public void definido(String destino, String registro) {
        if (!contenido.containsKey(registro)) {
            throw new IllegalArgumentException("definido() recibe un registro de paraResultado(), no " + registro
                    + ". Para un valor en $v0: r = paraResultado(x); move r, $v0; definido(x, r).");
        }
        for (String nombre : List.copyOf(contenido.get(registro))) {
            if (!nombre.equals(destino) && sucios.contains(nombre) && seNecesita(nombre)) {
                throw new IllegalStateException("La instrucción escribió en " + registro + ", que tenía el valor"
                        + " sin guardar de " + nombre + ". El resultado debe ir en el registro de paraResultado().");
            }
        }
        vaciar(registro);
        quitar(destino);
        asociar(destino, registro);
        sucios.add(destino);
        fijados.add(registro);
    }

    /** {@code x = y} no emite nada: {@code x} pasa a vivir en el registro de {@code y}. */
    @Override
    public void copia(String destino, String origen) {
        if (destino.equals(origen)) {
            return;
        }
        String registro = leer(origen);
        quitar(destino);
        asociar(destino, registro);
        sucios.add(destino);
    }

    /** Libera los registros fijados y olvida los temporales que ya no se vuelven a leer. */
    @Override
    public void siguienteInstruccion() {
        fijados.clear();
        for (String nombre : List.copyOf(registroDe.keySet())) {
            if (TempPool.esTemporal(nombre) && !vida.vivaDespues(actual, nombre)) {
                quitar(nombre);
            }
        }
    }

    @Override
    public void finDeBloque() {
        guardarSucios(this::seNecesitaAlCerrar);
        olvidarTodo();
    }

    @Override
    public void antesDeLlamada() {
        finDeBloque();
    }

    @Override
    public void antesDePosibleExcepcion() {
        guardarSucios(nombre -> !TempPool.esTemporal(nombre));
    }

    @Override
    public void descartarEnCaptura() {
        olvidarTodo();
        fijados.clear();
    }

    // --- Elección de registro ---

    /** Un registro sin valores útiles, desalojando (y derramando si hace falta) el mejor candidato. */
    private String registroLibre() {
        List<String> candidatos = new ArrayList<>();
        for (String registro : REGISTROS) {
            if (!fijados.contains(registro)) {
                if (contenido.get(registro).isEmpty()) {
                    return registro;
                }
                candidatos.add(registro);
            }
        }
        if (candidatos.isEmpty()) {
            throw new IllegalStateException("La instrucción TAC usa más de " + REGISTROS.size() + " registros a la vez");
        }
        String victima = null;
        for (boolean soloGratis : new boolean[]{true, false}) {
            for (String registro : candidatos) {
                if (soloGratis && !desalojoGratis(registro)) {
                    continue;
                }
                if (victima == null || proximoUso(registro) > proximoUso(victima)) {
                    victima = registro;
                }
            }
            if (victima != null) {
                break;
            }
        }
        derramar(victima);
        return victima;
    }

    /** Desalojar no cuesta un {@code sw}: todo lo que tiene ya está en memoria o no se usará. */
    private boolean desalojoGratis(String registro) {
        return contenido.get(registro).stream().noneMatch(n -> sucios.contains(n) && seNecesita(n));
    }

    /** Próximo uso más cercano entre los nombres del registro: mientras más lejos, mejor víctima. */
    private int proximoUso(String registro) {
        int minimo = Vida.SIN_USO;
        for (String nombre : contenido.get(registro)) {
            int uso = vida.usaEn(actual, nombre) ? actual : vida.proximoUso(actual, nombre);
            minimo = Math.min(minimo, uso);
        }
        return minimo;
    }

    private void derramar(String registro) {
        for (String nombre : contenido.get(registro)) {
            if (sucios.contains(nombre) && seNecesita(nombre)) {
                programa.emit(InstruccionMips.de("sw", registro, ubicaciones.direccion(nombre))
                        .conComentario("derrame " + nombre));
                derramados.add(nombre);
            }
        }
        vaciar(registro);
    }

    // --- Descriptores ---

    private void guardarSucios(Predicate<String> hayQueGuardar) {
        for (String registro : REGISTROS) {
            for (String nombre : contenido.get(registro)) {
                if (sucios.contains(nombre) && hayQueGuardar.test(nombre)) {
                    programa.emit(InstruccionMips.de("sw", registro, ubicaciones.direccion(nombre)));
                    sucios.remove(nombre);
                }
            }
        }
    }

    private void olvidarTodo() {
        contenido.values().forEach(Set::clear);
        registroDe.clear();
        sucios.clear();
    }

    private void asociar(String nombre, String registro) {
        contenido.get(registro).add(nombre);
        registroDe.put(nombre, registro);
    }

    private void quitar(String nombre) {
        String registro = registroDe.remove(nombre);
        if (registro != null) {
            contenido.get(registro).remove(nombre);
        }
        sucios.remove(nombre);
    }

    private void vaciar(String registro) {
        for (String nombre : List.copyOf(contenido.get(registro))) {
            quitar(nombre);
        }
    }

    /**
     * ¿Hay que conservar el valor de {@code nombre} en este punto? Las variables siempre; un temporal
     * solo si la instrucción actual o una posterior lo lee.
     */
    private boolean seNecesita(String nombre) {
        return !TempPool.esTemporal(nombre) || vida.vivaAntes(actual, nombre) || vida.vivaDespues(actual, nombre);
    }

    /**
     * Al cerrar un bloque: un temporal que la instrucción actual ya leyó (su registro está fijado,
     * como la condición de un {@code beqz}) y que nadie lee después, no se guarda. Si todavía no lo
     * leyó, sí: el traductor lo cargará de memoria.
     */
    private boolean seNecesitaAlCerrar(String nombre) {
        if (!TempPool.esTemporal(nombre) || vida.vivaDespues(actual, nombre)) {
            return true;
        }
        return vida.usaEn(actual, nombre) && !fijados.contains(registroDe.get(nombre));
    }

    /** Como {@link #seNecesita}, pero ignorando la lectura de la instrucción actual (ya se hizo). */
    private boolean seNecesitaDespues(String nombre) {
        return !TempPool.esTemporal(nombre) || vida.vivaDespues(actual, nombre);
    }
}
