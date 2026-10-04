package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.parser.LexSynAnalyzerBaseVisitor;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Generación de código intermedio para funciones, parámetros, retornos, llamadas, recursión
 * y {@code try / catch}.
 *
 * <p>Las funciones de nivel superior y anidadas se emiten después de {@code main} mediante
 * {@link GeneradorTAC#diferir}. Las funciones anidadas se elevan a nivel superior con etiqueta
 * {@code padre_hijo}.
 *
 * <p>En {@code try / catch}, cualquier {@code return}, {@code break} o {@code continue} emitirá
 * {@code endtry} antes de realizar el salto para no dejar activo el manejador.
 */
public final class FuncionTAC extends LexSynAnalyzerBaseVisitor<Void> {

    private final ProgramaTAC programa;
    private final TempPool temps;
    private final GeneradorEtiquetas etiquetas;
    private final ResolvedorMemoria memoria;
    private ExprTAC expr;
    private final GeneradorTAC generador;
    private final ControlTAC control;

    /** Pila de nombres de funciones actualmente en traducción (para jerarquía padre_hijo). */
    private final Deque<String> pilaNombresFunciones = new ArrayDeque<>();

    /** Conjunto de funciones declaradas conocidas para resolución de nombres anidados. */
    private final Set<String> funcionesConocidas = new HashSet<>();

    public FuncionTAC(ProgramaTAC programa, TempPool temps, GeneradorEtiquetas etiquetas,
                      ResolvedorMemoria memoria, ExprTAC expr, GeneradorTAC generador,
                      ControlTAC control) {
        this.programa = programa;
        this.temps = temps;
        this.etiquetas = etiquetas;
        this.memoria = memoria;
        this.expr = expr;
        this.generador = generador;
        this.control = control;
    }

    public void setExpr(ExprTAC expr) {
        this.expr = expr;
    }

    // ------------------------------------------------------------------
    // Declaración de funciones
    // ------------------------------------------------------------------

    @Override
    public Void visitFunctionDeclaration(FunctionDeclarationContext ctx) {
        String id = ctx.Identifier().getText();
        String nombreFuncion = memoria.etiquetaFuncion(ctx.Identifier()).orElse(
                pilaNombresFunciones.isEmpty() ? id : pilaNombresFunciones.peek() + "_" + id);

        funcionesConocidas.add(nombreFuncion);

        // Se difiere la emisión del cuerpo para que quede después de main
        generador.diferir(() -> emitirCuerpoFuncion(ctx, nombreFuncion));
        return null;
    }

    private void emitirCuerpoFuncion(FunctionDeclarationContext ctx, String nombreTac) {
        pilaNombresFunciones.push(nombreTac);

        int cabecera = programa.reservar();
        temps.reiniciar();

        generador.visitBlock(ctx.block());

        int frame = memoria.tamanoBase(nombreTac) + temps.maximoSimultaneos() * 4;
        programa.fijar(cabecera, Instruccion.funcion(nombreTac, frame));
        programa.emit(Instruccion.finFuncion());

        pilaNombresFunciones.pop();
    }

    // ------------------------------------------------------------------
    // Retorno
    // ------------------------------------------------------------------

    @Override
    public Void visitReturnStatement(ReturnStatementContext ctx) {
        String valor = null;
        if (ctx.expression() != null) {
            valor = expr.visit(ctx.expression());
        }

        // Si salimos de uno o más bloques try activos, emitir endtry antes del retorno
        int tries = control.profundidadTry();
        for (int i = 0; i < tries; i++) {
            programa.emit(Instruccion.finIntentar());
        }

        if (valor != null) {
            programa.emit(Instruccion.retorno(valor));
            temps.liberar(valor);
        } else {
            programa.emit(Instruccion.retorno());
        }
        return null;
    }

    // ------------------------------------------------------------------
    // try / catch
    // ------------------------------------------------------------------

    @Override
    public Void visitTryCatchStatement(TryCatchStatementContext ctx) {
        String etiquetaCatch = etiquetas.nueva();
        String etiquetaFin = etiquetas.nueva();

        programa.emit(Instruccion.intentar(etiquetaCatch));
        control.pushTry();
        generador.visitBlock(ctx.block(0));
        control.popTry();
        programa.emit(Instruccion.finIntentar());
        programa.emit(Instruccion.salto(etiquetaFin));

        programa.emit(Instruccion.etiqueta(etiquetaCatch));
        String errVariable = memoria.nombreTac(ctx.Identifier());
        programa.emit(Instruccion.capturar(errVariable));
        generador.visitBlock(ctx.block(1));

        programa.emit(Instruccion.etiqueta(etiquetaFin));
        return null;
    }

    // ------------------------------------------------------------------
    // Llamadas a función (integración con ExtensionExpr)
    // ------------------------------------------------------------------

    public String llamada(String funcion, CallExprContext llamadaCtx) {
        // La etiqueta sale de la resolución por ámbito (recursión en anidadas, hermanas, llamadas
        // antes de la declaración); sin ella, se prueba con la función que se está emitiendo.
        Optional<String> resuelta = etiquetaResuelta(llamadaCtx);
        String nombreReal = resuelta.orElse(funcion);
        if (resuelta.isEmpty() && !pilaNombresFunciones.isEmpty()) {
            String candidata = pilaNombresFunciones.peek() + "_" + funcion;
            if (funcionesConocidas.contains(candidata)) {
                nombreReal = candidata;
            }
        }

        List<String> operandosArgs = new ArrayList<>();
        if (llamadaCtx.arguments() != null && llamadaCtx.arguments().expression() != null) {
            for (ExpressionContext argCtx : llamadaCtx.arguments().expression()) {
                operandosArgs.add(expr.visit(argCtx));
            }
        }

        for (String argOp : operandosArgs) {
            programa.emit(Instruccion.param(argOp));
            temps.liberar(argOp);
        }

        int numArgs = operandosArgs.size();
        if (esLlamadaComoSentencia(llamadaCtx)) {
            programa.emit(Instruccion.llamar(nombreReal, numArgs));
            return null;
        } else {
            String destino = temps.nuevo();
            programa.emit(Instruccion.llamar(destino, nombreReal, numArgs));
            return destino;
        }
    }

    private Optional<String> etiquetaResuelta(CallExprContext llamada) {
        if (llamada.getParent() instanceof LeftHandSideContext lhs
                && lhs.primaryAtom() instanceof IdentifierExprContext id
                && lhs.suffixOp().indexOf(llamada) == 0) {
            return memoria.etiquetaFuncion(id.Identifier());
        }
        return Optional.empty();
    }

    private static boolean esLlamadaComoSentencia(CallExprContext llamada) {
        if (!(llamada.getParent() instanceof LeftHandSideContext lhs)) {
            return false;
        }
        if (lhs.suffixOp().size() != 1) {
            return false;
        }
        if (!(lhs.primaryAtom() instanceof IdentifierExprContext)) {
            return false;
        }

        ParseTree actual = lhs;
        while (actual != null) {
            ParseTree padre = actual.getParent();
            if (padre instanceof ExpressionStatementContext) {
                return true;
            }
            if (padre instanceof UnaryExprContext unary && unary.getChildCount() > 1) {
                return false;
            }
            if (padre instanceof MultiplicativeExprContext mul && mul.unaryExpr().size() > 1) {
                return false;
            }
            if (padre instanceof AdditiveExprContext add && add.multiplicativeExpr().size() > 1) {
                return false;
            }
            if (padre instanceof RelationalExprContext rel && rel.additiveExpr().size() > 1) {
                return false;
            }
            if (padre instanceof EqualityExprContext eq && eq.relationalExpr().size() > 1) {
                return false;
            }
            if (padre instanceof LogicalAndExprContext and && and.equalityExpr().size() > 1) {
                return false;
            }
            if (padre instanceof LogicalOrExprContext or && or.logicalAndExpr().size() > 1) {
                return false;
            }
            if (padre instanceof TernaryExprContext ternary && !ternary.expression().isEmpty()) {
                return false;
            }
            if (padre instanceof AssignExprContext || padre instanceof PropertyAssignExprContext) {
                return false;
            }
            if (padre instanceof StatementContext || padre instanceof VariableDeclarationContext
                    || padre instanceof ConstantDeclarationContext || padre instanceof PrintStatementContext
                    || padre instanceof ReturnStatementContext) {
                return false;
            }
            actual = padre;
        }
        return false;
    }
}
