package com.lexsynanalyzer.semantic;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que el análisis semántico deja para las fases siguientes: el tipo de cada nodo, el entorno de
 * cada nodo que abre ámbito y el entorno donde se resolvió cada identificador de variable.
 */
public final class ResultadoSemantico {

    private final TablaSimbolos tabla;
    private final ParseTreeProperty<TipoDato> tipos = new ParseTreeProperty<>();
    private final ParseTreeProperty<Entorno> entornos = new ParseTreeProperty<>();
    private final ParseTreeProperty<TipoDato> receptores = new ParseTreeProperty<>();
    private final Map<Token, Entorno> resoluciones = new IdentityHashMap<>();

    ResultadoSemantico(TablaSimbolos tabla) {
        this.tabla = tabla;
    }

    public TablaSimbolos tabla() {
        return tabla;
    }

    public TipoDato tipo(ParseTree nodo) {
        return tipos.get(nodo);
    }

    public Entorno entorno(ParseTree nodo) {
        return entornos.get(nodo);
    }

    /** Tipo estático del objeto sobre el que se accede a {@code .miembro} (nodo {@code PropertyAccessExpr}). */
    public TipoDato tipoReceptor(ParseTree accesoPropiedad) {
        return receptores.get(accesoPropiedad);
    }

    /** Entorno que declara la variable nombrada por {@code identificador} (declaración o uso). */
    public Optional<Entorno> entornoDeclarante(Token identificador) {
        return Optional.ofNullable(resoluciones.get(identificador));
    }

    void registrarTipo(ParseTree nodo, TipoDato tipo) {
        tipos.put(nodo, tipo);
    }

    void registrarEntorno(ParseTree nodo, Entorno entorno) {
        entornos.put(nodo, entorno);
    }

    void registrarReceptor(ParseTree accesoPropiedad, TipoDato tipo) {
        receptores.put(accesoPropiedad, tipo);
    }

    void registrarResolucion(Token identificador, Entorno entorno) {
        resoluciones.put(identificador, entorno);
    }
}
