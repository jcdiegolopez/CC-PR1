package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.parser.LexSynAnalyzerBaseVisitor;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Visitor raíz de la generación de código intermedio. Solo recorre y delega: las expresiones y
 * sentencias simples van a {@link ExprTAC}; el flujo de control a {@link ControlTAC}, las funciones
 * y excepciones a {@link FuncionTAC}, y las clases a {@code ClaseTAC} (C).
 *
 * <p>El código de nivel superior se emite como {@code function main, N ... endfunc}; los cuerpos
 * registrados con {@link #diferir} se emiten después, cada uno completo.
 */
public final class GeneradorTAC extends LexSynAnalyzerBaseVisitor<Void> {

    private final ProgramaTAC programa = new ProgramaTAC();
    private final TempPool temps = new TempPool();
    private final GeneradorEtiquetas etiquetas = new GeneradorEtiquetas();
    private final ResolvedorMemoria memoria;
    private final ExprTAC expr;
    private final ControlTAC control;
    private final FuncionTAC funcion;
    private final List<Runnable> diferidos = new ArrayList<>();

    public GeneradorTAC() {
        this(ResolvedorMemoria.SIMPLE, ExtensionExpr.NINGUNA);
    }

    public GeneradorTAC(ResolvedorMemoria memoria, ExtensionExpr extensionExterna) {
        this.memoria = memoria;
        this.control = new ControlTAC(programa, temps, etiquetas, null, this);
        this.funcion = new FuncionTAC(programa, temps, etiquetas, memoria, null, this, control);
        ExtensionExpr extensionCombinada = new ExtensionExpr() {
            @Override
            public String atomo(PrimaryAtomContext atomo) {
                return extensionExterna.atomo(atomo);
            }

            @Override
            public String llamada(String funcionNombre, CallExprContext llamadaCtx) {
                return funcion.llamada(funcionNombre, llamadaCtx);
            }

            @Override
            public String metodo(String objeto, String metodo, CallExprContext llamadaCtx) {
                return extensionExterna.metodo(objeto, metodo, llamadaCtx);
            }
        };
        this.expr = new ExprTAC(programa, temps, etiquetas, memoria, extensionCombinada);
        this.control.setExpr(this.expr);
        this.funcion.setExpr(this.expr);
    }

    public ProgramaTAC generar(ProgramContext programaFuente) {
        visit(programaFuente);
        return programa;
    }

    /** Emite {@code cuerpo} cuando termine {@code main}; para funciones y métodos. */
    public void diferir(Runnable cuerpo) {
        diferidos.add(cuerpo);
    }

    public ProgramaTAC programa() {
        return programa;
    }

    public TempPool temporales() {
        return temps;
    }

    public GeneradorEtiquetas etiquetas() {
        return etiquetas;
    }

    public ExprTAC expresiones() {
        return expr;
    }

    public ControlTAC control() {
        return control;
    }

    public FuncionTAC funcion() {
        return funcion;
    }

    @Override
    public Void visitProgram(ProgramContext ctx) {
        int cabecera = programa.reservar();
        temps.reiniciar();
        ctx.statement().forEach(this::visit);
        int frame = memoria.tamanoBase("main") + temps.maximoSimultaneos() * 4;
        programa.fijar(cabecera, Instruccion.funcion("main", frame));
        programa.emit(Instruccion.finFuncion());
        // Se recorre por índice: un cuerpo diferido puede registrar otros (funciones anidadas).
        for (int i = 0; i < diferidos.size(); i++) {
            diferidos.get(i).run();
        }
        return null;
    }

    @Override
    public Void visitBlock(BlockContext ctx) {
        ctx.statement().forEach(this::visit);
        return null;
    }

    @Override
    public Void visitStatement(StatementContext ctx) {
        ParseTree sentencia = ctx.getChild(0);
        if (sentencia instanceof BlockContext bloque) {
            return visitBlock(bloque);
        }
        if (esDeExpresiones(sentencia)) {
            expr.visit(sentencia);
            return null;
        }
        if (esDeControl(sentencia)) {
            control.visit(sentencia);
            return null;
        }
        if (esDeFunciones(sentencia)) {
            funcion.visit(sentencia);
            return null;
        }
        throw new UnsupportedOperationException(
                "Aún no implementado en el generador TAC: " + sentencia.getClass().getSimpleName());
    }

    private static boolean esDeExpresiones(ParseTree sentencia) {
        return sentencia instanceof VariableDeclarationContext
                || sentencia instanceof ConstantDeclarationContext
                || sentencia instanceof AssignmentContext
                || sentencia instanceof ExpressionStatementContext
                || sentencia instanceof PrintStatementContext;
    }

    private static boolean esDeControl(ParseTree sentencia) {
        return sentencia instanceof IfStatementContext
                || sentencia instanceof WhileStatementContext
                || sentencia instanceof DoWhileStatementContext
                || sentencia instanceof ForStatementContext
                || sentencia instanceof ForeachStatementContext
                || sentencia instanceof SwitchStatementContext
                || sentencia instanceof BreakStatementContext
                || sentencia instanceof ContinueStatementContext;
    }

    private static boolean esDeFunciones(ParseTree sentencia) {
        return sentencia instanceof FunctionDeclarationContext
                || sentencia instanceof ReturnStatementContext
                || sentencia instanceof TryCatchStatementContext;
    }
}
