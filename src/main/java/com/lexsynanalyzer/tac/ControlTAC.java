package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.parser.LexSynAnalyzerBaseVisitor;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Flujo de control: {@code if/else}, {@code while}, {@code do-while}, {@code for}, {@code foreach},
 * {@code switch} (con fall-through), {@code break} y {@code continue}.
 *
 * <p>Mantiene una pila de etiquetas {@code (break, continue, tryDepth)} para que cada sentencia de
 * salto sepa adónde ir y cierre los manejadores {@code try} que correspondan antes de saltar.
 */
public final class ControlTAC extends LexSynAnalyzerBaseVisitor<Void> {

    private final ProgramaTAC programa;
    private final TempPool temps;
    private final GeneradorEtiquetas etiquetas;
    private final ResolvedorMemoria memoria;
    private ExprTAC expr;
    private final GeneradorTAC generador;

    /** Pila de destinos para {@code break} / {@code continue}. */
    private final Deque<SaltosPendientes> pilaBreakContinue = new ArrayDeque<>();

    /** Pila de bloques try activos. */
    private final Deque<Boolean> pilaTry = new ArrayDeque<>();

    record SaltosPendientes(String breakLabel, String continueLabel, int tryDepth) {}

    public ControlTAC(ProgramaTAC programa, TempPool temps, GeneradorEtiquetas etiquetas,
                      ResolvedorMemoria memoria, ExprTAC expr, GeneradorTAC generador) {
        this.programa = programa;
        this.temps = temps;
        this.etiquetas = etiquetas;
        this.memoria = memoria;
        this.expr = expr;
        this.generador = generador;
    }

    public void setExpr(ExprTAC expr) {
        this.expr = expr;
    }

    // --- Acceso a la pila de try (para FuncionTAC) ---

    public void pushTry() {
        pilaTry.push(Boolean.TRUE);
    }

    public void popTry() {
        pilaTry.pop();
    }

    public int profundidadTry() {
        return pilaTry.size();
    }

    // ------------------------------------------------------------------
    // if / else
    // ------------------------------------------------------------------

    @Override
    public Void visitIfStatement(IfStatementContext ctx) {
        String condicion = expr.visit(ctx.expression());
        List<BlockContext> bloques = ctx.block();

        if (bloques.size() == 1) {
            // if sin else
            String fin = etiquetas.nueva();
            programa.emit(Instruccion.saltoSiFalso(condicion, fin));
            temps.liberar(condicion);
            generador.visitBlock(bloques.get(0));
            programa.emit(Instruccion.etiqueta(fin));
        } else {
            // if con else
            String sino = etiquetas.nueva();
            String fin = etiquetas.nueva();
            programa.emit(Instruccion.saltoSiFalso(condicion, sino));
            temps.liberar(condicion);
            generador.visitBlock(bloques.get(0));
            programa.emit(Instruccion.salto(fin));
            programa.emit(Instruccion.etiqueta(sino));
            generador.visitBlock(bloques.get(1));
            programa.emit(Instruccion.etiqueta(fin));
        }
        return null;
    }

    // ------------------------------------------------------------------
    // while
    // ------------------------------------------------------------------

    @Override
    public Void visitWhileStatement(WhileStatementContext ctx) {
        String inicio = etiquetas.nueva();
        String fin = etiquetas.nueva();

        programa.emit(Instruccion.etiqueta(inicio));
        String condicion = expr.visit(ctx.expression());
        programa.emit(Instruccion.saltoSiFalso(condicion, fin));
        temps.liberar(condicion);

        pilaBreakContinue.push(new SaltosPendientes(fin, inicio, pilaTry.size()));
        generador.visitBlock(ctx.block());
        pilaBreakContinue.pop();

        programa.emit(Instruccion.salto(inicio));
        programa.emit(Instruccion.etiqueta(fin));
        return null;
    }

    // ------------------------------------------------------------------
    // do-while
    // ------------------------------------------------------------------

    @Override
    public Void visitDoWhileStatement(DoWhileStatementContext ctx) {
        String inicio = etiquetas.nueva();
        String continueLabel = etiquetas.nueva();
        String fin = etiquetas.nueva();

        programa.emit(Instruccion.etiqueta(inicio));

        pilaBreakContinue.push(new SaltosPendientes(fin, continueLabel, pilaTry.size()));
        generador.visitBlock(ctx.block());
        pilaBreakContinue.pop();

        programa.emit(Instruccion.etiqueta(continueLabel));
        String condicion = expr.visit(ctx.expression());
        programa.emit(Instruccion.saltoSi(condicion, inicio));
        temps.liberar(condicion);
        programa.emit(Instruccion.etiqueta(fin));
        return null;
    }

    // ------------------------------------------------------------------
    // for
    // ------------------------------------------------------------------

    @Override
    public Void visitForStatement(ForStatementContext ctx) {
        // Inicialización: variableDeclaration, assignment, o ';'
        if (ctx.variableDeclaration() != null) {
            expr.visit(ctx.variableDeclaration());
        } else if (ctx.assignment() != null) {
            expr.visit(ctx.assignment());
        }

        String inicio = etiquetas.nueva();
        String continueLabel = etiquetas.nueva();
        String fin = etiquetas.nueva();

        // Determinar condición e incremento analizando los hijos
        ExpressionContext condicionExpr = null;
        ExpressionContext incrementoExpr = null;

        int idxSeparador = encontrarSeparadorFor(ctx);
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof ExpressionContext exprCtx) {
                if (i < idxSeparador) {
                    condicionExpr = exprCtx;
                } else {
                    incrementoExpr = exprCtx;
                }
            }
        }

        programa.emit(Instruccion.etiqueta(inicio));
        if (condicionExpr != null) {
            String condicion = expr.visit(condicionExpr);
            programa.emit(Instruccion.saltoSiFalso(condicion, fin));
            temps.liberar(condicion);
        }

        pilaBreakContinue.push(new SaltosPendientes(fin, continueLabel, pilaTry.size()));
        generador.visitBlock(ctx.block());
        pilaBreakContinue.pop();

        programa.emit(Instruccion.etiqueta(continueLabel));
        if (incrementoExpr != null) {
            temps.liberar(expr.visit(incrementoExpr));
        }
        programa.emit(Instruccion.salto(inicio));
        programa.emit(Instruccion.etiqueta(fin));
        return null;
    }

    private static int encontrarSeparadorFor(ForStatementContext ctx) {
        // Si el init fue ';' (child 2), el segundo ';' directo es el separador.
        // Si el init fue varDecl o assignment, el primer ';' directo es el separador.
        boolean emptyInit = ctx.getChildCount() > 2 && ";".equals(ctx.getChild(2).getText());
        int puntoYComaContados = 0;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (";".equals(ctx.getChild(i).getText())) {
                puntoYComaContados++;
                if (emptyInit && puntoYComaContados == 2) {
                    return i;
                }
                if (!emptyInit && puntoYComaContados == 1) {
                    return i;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    // ------------------------------------------------------------------
    // foreach
    // ------------------------------------------------------------------

    @Override
    public Void visitForeachStatement(ForeachStatementContext ctx) {
        String arreglo = expr.visit(ctx.expression());
        if (!TempPool.esTemporal(arreglo)) {
            // Se itera sobre una copia: si el cuerpo reasigna la variable, el ciclo no cambia de arreglo.
            String copia = temps.nuevo();
            programa.emit(Instruccion.asignar(copia, arreglo));
            arreglo = copia;
        }
        String variable = memoria.nombreTac(ctx.Identifier());

        String longitud = temps.nuevo();
        String indice = temps.nuevo();
        programa.emit(Instruccion.longitud(longitud, arreglo));
        programa.emit(Instruccion.asignar(indice, "0"));

        String inicio = etiquetas.nueva();
        String continueLabel = etiquetas.nueva();
        String fin = etiquetas.nueva();

        programa.emit(Instruccion.etiqueta(inicio));
        String condicion = temps.nuevo();
        programa.emit(Instruccion.binaria(condicion, indice, "<", longitud));
        programa.emit(Instruccion.saltoSiFalso(condicion, fin));
        temps.liberar(condicion);

        // variable = arreglo[indice]
        String elemento = temps.nuevo();
        programa.emit(Instruccion.leerIndice(elemento, arreglo, indice));
        programa.emit(Instruccion.asignar(variable, elemento));
        temps.liberar(elemento);

        pilaBreakContinue.push(new SaltosPendientes(fin, continueLabel, pilaTry.size()));
        generador.visitBlock(ctx.block());
        pilaBreakContinue.pop();

        programa.emit(Instruccion.etiqueta(continueLabel));
        String incremento = temps.nuevo();
        programa.emit(Instruccion.binaria(incremento, indice, "+", "1"));
        programa.emit(Instruccion.asignar(indice, incremento));
        temps.liberar(incremento);
        programa.emit(Instruccion.salto(inicio));
        programa.emit(Instruccion.etiqueta(fin));

        temps.liberar(longitud);
        temps.liberar(indice);
        temps.liberar(arreglo);
        return null;
    }

    // ------------------------------------------------------------------
    // switch con fall-through
    // ------------------------------------------------------------------

    @Override
    public Void visitSwitchStatement(SwitchStatementContext ctx) {
        String selector = expr.visit(ctx.expression());
        String fin = etiquetas.nueva();

        List<SwitchCaseContext> casos = ctx.switchCase();
        DefaultCaseContext defaultCase = ctx.defaultCase();

        String[] etiquetasCasos = new String[casos.size()];
        for (int i = 0; i < casos.size(); i++) {
            etiquetasCasos[i] = etiquetas.nueva();
        }
        String etiquetaDefault = defaultCase != null ? etiquetas.nueva() : fin;

        // Cadena de comparaciones con saltos
        for (int i = 0; i < casos.size(); i++) {
            String valorCaso = expr.visit(casos.get(i).expression());
            String comparacion = temps.nuevo();
            programa.emit(Instruccion.binaria(comparacion, selector, "==", valorCaso));
            programa.emit(Instruccion.saltoSi(comparacion, etiquetasCasos[i]));
            temps.liberar(valorCaso);
            temps.liberar(comparacion);
        }

        programa.emit(Instruccion.salto(etiquetaDefault));

        // Cuerpos con fall-through
        pilaBreakContinue.push(new SaltosPendientes(fin, null, pilaTry.size()));
        for (int i = 0; i < casos.size(); i++) {
            programa.emit(Instruccion.etiqueta(etiquetasCasos[i]));
            for (StatementContext stmt : casos.get(i).statement()) {
                generador.visit(stmt);
            }
        }

        if (defaultCase != null) {
            programa.emit(Instruccion.etiqueta(etiquetaDefault));
            for (StatementContext stmt : defaultCase.statement()) {
                generador.visit(stmt);
            }
        }
        pilaBreakContinue.pop();

        programa.emit(Instruccion.etiqueta(fin));
        temps.liberar(selector);
        return null;
    }

    // ------------------------------------------------------------------
    // break / continue
    // ------------------------------------------------------------------

    @Override
    public Void visitBreakStatement(BreakStatementContext ctx) {
        SaltosPendientes saltos = pilaBreakContinue.peek();
        if (saltos != null) {
            int tryExits = pilaTry.size() - saltos.tryDepth();
            for (int i = 0; i < tryExits; i++) {
                programa.emit(Instruccion.finIntentar());
            }
            programa.emit(Instruccion.salto(saltos.breakLabel()));
        }
        return null;
    }

    @Override
    public Void visitContinueStatement(ContinueStatementContext ctx) {
        for (SaltosPendientes saltos : pilaBreakContinue) {
            if (saltos.continueLabel() != null) {
                int tryExits = pilaTry.size() - saltos.tryDepth();
                for (int i = 0; i < tryExits; i++) {
                    programa.emit(Instruccion.finIntentar());
                }
                programa.emit(Instruccion.salto(saltos.continueLabel()));
                return null;
            }
        }
        return null;
    }
}
