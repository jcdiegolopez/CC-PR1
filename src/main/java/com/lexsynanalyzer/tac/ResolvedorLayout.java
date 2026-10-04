package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.semantic.Layout;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * {@link ResolvedorMemoria} respaldado por el {@link Layout} de {@code AsignadorMemoria}: tamaños de
 * frame reales y nombres únicos para variables sombreadas. Lo que no es una variable con dirección
 * (nombres de funciones, por ejemplo) conserva el nombre del código fuente.
 */
public final class ResolvedorLayout implements ResolvedorMemoria {

    private final Layout layout;

    public ResolvedorLayout(Layout layout) {
        this.layout = layout;
    }

    public Layout layout() {
        return layout;
    }

    @Override
    public int tamanoBase(String funcion) {
        return layout.tamanoBase(funcion);
    }

    @Override
    public String nombreTac(TerminalNode identificador) {
        return layout.nombreTac(identificador.getSymbol())
                .orElseGet(() -> SIMPLE.nombreTac(identificador));
    }
}
