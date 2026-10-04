package com.lexsynanalyzer.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Recorre el árbol de entornos que dejó el análisis semántico y asigna memoria: offsets de globales,
 * parámetros y locales, tamaño del frame de cada función, nombre TAC único de cada variable y
 * distribución de los campos de cada clase. Es el único lugar que conoce los tamaños de los tipos.
 *
 * <p>Registro de activación: dirección de retorno (offset 0), enlace de control (8), enlace de
 * acceso (16), parámetros desde el 24 ({@code this} primero en métodos) y luego las locales; los
 * bloques internos continúan el offset. Las globales van en una región estática aparte.
 */
public final class AsignadorMemoria {

    public static final int CABECERA = 24;
    public static final int TAMANO_ENTERO = 4;
    public static final int TAMANO_REFERENCIA = 8;

    private static final Pattern FORMA_DE_TEMPORAL = Pattern.compile("t\\d+");

    /** Nombres de variables de un mismo frame (el de {@code main} incluye a las globales). */
    private static final class Unidad {
        final String etiqueta;
        final Set<String> usados = new HashSet<>();
        final List<Layout.Variable> variables = new ArrayList<>();
        int offset = CABECERA;

        Unidad(String etiqueta) {
            this.etiqueta = etiqueta;
        }
    }

    private final Set<String> nombresFuente = new HashSet<>();
    private final List<Unidad> unidades = new ArrayList<>();
    private final List<Layout.Variable> globales = new ArrayList<>();
    private final Entorno global;
    private int offsetGlobal;

    private AsignadorMemoria(Entorno global) {
        this.global = global;
    }

    public static Layout asignar(ResultadoSemantico semantico) {
        AsignadorMemoria asignador = new AsignadorMemoria(semantico.tabla().global());
        Map<String, Layout.Frame> frames = asignador.asignarFrames();
        Map<String, Layout.ClaseLayout> clases = asignador.asignarClases();
        return new Layout(semantico, asignador.globales, frames, clases);
    }

    /** {@code integer} y {@code boolean} ocupan 4 bytes; cadenas, arreglos y objetos son referencias de 8. */
    public static int tamanoDe(TipoDato tipo) {
        return switch (tipo.clase()) {
            case INTEGER, BOOLEAN -> TAMANO_ENTERO;
            default -> TAMANO_REFERENCIA;
        };
    }

    private static int alinear(int offset, int tamano) {
        return (offset + tamano - 1) / tamano * tamano;
    }

    // ------------------------------------------------------------------
    // Frames y variables
    // ------------------------------------------------------------------

    private Map<String, Layout.Frame> asignarFrames() {
        recolectarNombres(global);
        Unidad main = new Unidad("main");
        unidades.add(main);
        recorrer(global, main, Set.of(), null);

        Map<String, Layout.Frame> frames = new LinkedHashMap<>();
        for (Unidad unidad : unidades) {
            frames.putIfAbsent(unidad.etiqueta, new Layout.Frame(unidad.etiqueta, unidad.offset, unidad.variables));
        }
        return frames;
    }

    private void recolectarNombres(Entorno entorno) {
        entorno.simbolos().forEach(simbolo -> nombresFuente.add(simbolo.nombre()));
        entorno.hijos().forEach(this::recolectarNombres);
    }

    /**
     * Asigna las variables de {@code entorno} dentro de {@code unidad} y desciende a sus hijos. Las
     * funciones y métodos abren una unidad nueva. {@code visibles} son los nombres TAC de las
     * variables de los entornos ancestros; {@code funcionPadre} es la etiqueta de la función que
     * encierra al entorno (para nombrar funciones anidadas como {@code padre_hijo}).
     */
    private void recorrer(Entorno entorno, Unidad unidad, Set<String> visibles, String funcionPadre) {
        Set<String> visiblesAqui = new HashSet<>(visibles);
        if (!esClase(entorno)) {
            for (Simbolo simbolo : entorno.simbolos()) {
                if (esVariable(simbolo)) {
                    visiblesAqui.add(asignarVariable(entorno, simbolo, unidad, visibles));
                }
            }
        }
        entorno.setSiguienteOffset(unidad.offset);

        for (Entorno hijo : entorno.hijos()) {
            if (esFuncion(hijo) || esMetodo(hijo)) {
                String etiqueta = etiquetaDe(hijo, funcionPadre);
                Unidad nueva = new Unidad(etiqueta);
                unidades.add(nueva);
                if (esMetodo(hijo)) {
                    reservarThis(hijo, nueva);
                }
                recorrer(hijo, nueva, visiblesAqui, esFuncion(hijo) ? etiqueta : null);
                hijo.setTamanoFrame(nueva.offset);
            } else {
                recorrer(hijo, unidad, visiblesAqui, funcionPadre);
            }
        }
    }

    private String asignarVariable(Entorno entorno, Simbolo simbolo, Unidad unidad, Set<String> visibles) {
        String nombreTac = nombreUnico(simbolo.nombre(), unidad.usados, visibles);
        int tamano = tamanoDe(simbolo.tipo());
        Direccion direccion;
        if (entorno == global) {
            offsetGlobal = alinear(offsetGlobal, tamano);
            direccion = new Direccion(nombreTac, Direccion.Categoria.GLOBAL, offsetGlobal, tamano, simbolo.tipo());
            offsetGlobal += tamano;
        } else {
            Direccion.Categoria categoria = simbolo.categoria() == CategoriaSimbolo.PARAMETRO
                    ? Direccion.Categoria.PARAM
                    : Direccion.Categoria.LOCAL;
            unidad.offset = alinear(unidad.offset, tamano);
            direccion = new Direccion(nombreTac, categoria, unidad.offset, tamano, simbolo.tipo());
            unidad.offset += tamano;
        }

        entorno.direcciones().put(simbolo.nombre(), direccion);
        Layout.Variable variable = new Layout.Variable(simbolo.nombre(), direccion);
        (entorno == global ? globales : unidad.variables).add(variable);
        unidad.usados.add(nombreTac);
        return nombreTac;
    }

    /** {@code this} es el parámetro 0 implícito de todo método. */
    private void reservarThis(Entorno metodo, Unidad unidad) {
        String clase = metodo.padre().map(AsignadorMemoria::nombreDeEntorno).orElse("");
        Direccion direccion = new Direccion("this", Direccion.Categoria.PARAM, unidad.offset,
                TAMANO_REFERENCIA, TipoDato.clase(clase.isEmpty() ? "Objeto" : clase));
        metodo.direcciones().put("this", direccion);
        unidad.variables.add(new Layout.Variable("this", direccion));
        unidad.usados.add("this");
        unidad.offset += TAMANO_REFERENCIA;
    }

    /**
     * El nombre del código fuente, salvo que ya lo use otra variable del mismo frame o una variable
     * visible de un ámbito exterior (sombreado): entonces {@code x_1}, {@code x_2}... Un nombre con
     * forma de temporal ({@code t0}) se emite como {@code t0_v}.
     */
    private String nombreUnico(String nombre, Set<String> usados, Set<String> visibles) {
        String base = FORMA_DE_TEMPORAL.matcher(nombre).matches() ? nombre + "_v" : nombre;
        if (!usados.contains(base) && !visibles.contains(base)) {
            return base;
        }
        for (int i = 1; ; i++) {
            String candidato = base + "_" + i;
            if (!usados.contains(candidato) && !visibles.contains(candidato) && !nombresFuente.contains(candidato)) {
                return candidato;
            }
        }
    }

    /** Misma regla que el generador: {@code f}, {@code padre_hijo} o {@code Clase_metodo}. */
    private static String etiquetaDe(Entorno entorno, String funcionPadre) {
        String nombre = nombreDeEntorno(entorno);
        if (esMetodo(entorno)) {
            return entorno.padre().map(AsignadorMemoria::nombreDeEntorno).orElse("") + "_" + nombre;
        }
        return funcionPadre == null ? nombre : funcionPadre + "_" + nombre;
    }

    /** {@code funcion:suma#3} → {@code suma}; {@code clase:Perro} → {@code Perro}. */
    private static String nombreDeEntorno(Entorno entorno) {
        String nombre = entorno.nombre();
        int inicio = nombre.indexOf(':') + 1;
        int fin = nombre.lastIndexOf('#');
        return nombre.substring(inicio, fin > inicio ? fin : nombre.length());
    }

    private static boolean esVariable(Simbolo simbolo) {
        return simbolo.categoria() == CategoriaSimbolo.VARIABLE
                || simbolo.categoria() == CategoriaSimbolo.CONSTANTE
                || simbolo.categoria() == CategoriaSimbolo.PARAMETRO;
    }

    private static boolean esFuncion(Entorno entorno) {
        return entorno.nombre().startsWith("funcion:");
    }

    private static boolean esMetodo(Entorno entorno) {
        return entorno.nombre().startsWith("metodo:");
    }

    private static boolean esClase(Entorno entorno) {
        return entorno.nombre().startsWith("clase:");
    }

    // ------------------------------------------------------------------
    // Clases
    // ------------------------------------------------------------------

    private Map<String, Layout.ClaseLayout> asignarClases() {
        Map<String, Simbolo> simbolos = new LinkedHashMap<>();
        recolectarClases(global, simbolos);

        Map<String, Layout.ClaseLayout> clases = new LinkedHashMap<>();
        for (String nombre : simbolos.keySet()) {
            asignarClase(nombre, simbolos, clases, new HashMap<>());
        }
        return clases;
    }

    private static void recolectarClases(Entorno entorno, Map<String, Simbolo> clases) {
        for (Simbolo simbolo : entorno.simbolos()) {
            if (simbolo.categoria() == CategoriaSimbolo.CLASE && simbolo.entornoMiembros() != null) {
                clases.putIfAbsent(simbolo.nombre(), simbolo);
            }
        }
        entorno.hijos().forEach(hijo -> recolectarClases(hijo, clases));
    }

    /** Los campos del padre van primero y conservan su offset; los propios continúan después. */
    private static Layout.ClaseLayout asignarClase(String nombre, Map<String, Simbolo> simbolos,
                                                   Map<String, Layout.ClaseLayout> clases,
                                                   Map<String, Boolean> enCurso) {
        Layout.ClaseLayout hecha = clases.get(nombre);
        Simbolo simbolo = simbolos.get(nombre);
        if (hecha != null || simbolo == null || enCurso.put(nombre, true) != null) {
            return hecha;
        }

        List<Layout.Variable> campos = new ArrayList<>();
        int offset = 0;
        String padre = simbolos.containsKey(simbolo.clasePadre()) ? simbolo.clasePadre() : null;
        if (padre != null) {
            Layout.ClaseLayout layoutPadre = asignarClase(padre, simbolos, clases, enCurso);
            if (layoutPadre != null) {
                campos.addAll(layoutPadre.campos());
                offset = layoutPadre.tamanoInstancia();
            }
        }

        Set<String> metodos = new LinkedHashSet<>();
        Entorno miembros = simbolo.entornoMiembros();
        for (Simbolo miembro : miembros.simbolos()) {
            if (miembro.categoria() == CategoriaSimbolo.FUNCION) {
                metodos.add(miembro.nombre());
                continue;
            }
            int tamano = tamanoDe(miembro.tipo());
            offset = alinear(offset, tamano);
            Direccion direccion = new Direccion(miembro.nombre(), Direccion.Categoria.CAMPO, offset, tamano,
                    miembro.tipo());
            miembros.direcciones().put(miembro.nombre(), direccion);
            campos.add(new Layout.Variable(miembro.nombre(), direccion));
            offset += tamano;
        }

        Layout.ClaseLayout layout = new Layout.ClaseLayout(nombre, padre, campos, offset, metodos);
        clases.put(nombre, layout);
        return layout;
    }
}
