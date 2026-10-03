package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.parser.LexSynAnalyzerBaseVisitor;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

/**
 * Variables, aritmética, lógicas, ternario, arreglos y {@code print}.
 *
 * <p>Cada {@code visit} de expresión devuelve el operando donde quedó el resultado: un literal, una
 * variable o un temporal. Quien consume el operando es quien lo libera ({@link TempPool#liberar}
 * ignora lo que no sea un temporal). En las operaciones se liberan los operandos <b>antes</b> de
 * pedir el temporal del resultado, para reutilizarlos.
 */
public final class ExprTAC extends LexSynAnalyzerBaseVisitor<String> {

    private final ProgramaTAC programa;
    private final TempPool temps;
    private final GeneradorEtiquetas etiquetas;
    private final ResolvedorMemoria memoria;
    private final ExtensionExpr extension;

    public ExprTAC(ProgramaTAC programa, TempPool temps, GeneradorEtiquetas etiquetas,
                   ResolvedorMemoria memoria, ExtensionExpr extension) {
        this.programa = programa;
        this.temps = temps;
        this.etiquetas = etiquetas;
        this.memoria = memoria;
        this.extension = extension;
    }

    // --- Sentencias ---

    @Override
    public String visitVariableDeclaration(VariableDeclarationContext ctx) {
        if (ctx.initializer() != null) {
            inicializar(ctx.Identifier(), ctx.initializer().expression());
        }
        return null;
    }

    @Override
    public String visitConstantDeclaration(ConstantDeclarationContext ctx) {
        inicializar(ctx.Identifier(), ctx.expression());
        return null;
    }

    private void inicializar(TerminalNode id, ExpressionContext valor) {
        String v = visit(valor);
        programa.emit(Instruccion.asignar(memoria.nombreTac(id), v));
        temps.liberar(v);
    }

    /** {@code x = e;} o {@code obj.campo = e;} como sentencia. */
    @Override
    public String visitAssignment(AssignmentContext ctx) {
        String valor;
        if (ctx.expression().size() == 1) {
            valor = visit(ctx.expression(0));
            programa.emit(Instruccion.asignar(memoria.nombreTac(ctx.Identifier()), valor));
        } else {
            String objeto = visit(ctx.expression(0));
            valor = visit(ctx.expression(1));
            programa.emit(Instruccion.escribirCampo(objeto, ctx.Identifier().getText(), valor));
            temps.liberar(objeto);
        }
        temps.liberar(valor);
        return null;
    }

    @Override
    public String visitExpressionStatement(ExpressionStatementContext ctx) {
        temps.liberar(visit(ctx.expression()));
        return null;
    }

    @Override
    public String visitPrintStatement(PrintStatementContext ctx) {
        String v = visit(ctx.expression());
        programa.emit(Instruccion.imprimir(v));
        temps.liberar(v);
        return null;
    }

    // --- Asignación como expresión ---

    @Override
    public String visitExpression(ExpressionContext ctx) {
        return visit(ctx.assignmentExpr());
    }

    /** {@code a = b = 3}: el valor de la asignación es la variable asignada. */
    @Override
    public String visitAssignExpr(AssignExprContext ctx) {
        LeftHandSideContext lhs = ctx.lhs;
        List<SuffixOpContext> sufijos = lhs.suffixOp();
        if (sufijos.isEmpty()) {
            String valor = visit(ctx.assignmentExpr());
            if (!(lhs.primaryAtom() instanceof IdentifierExprContext id)) {
                throw new UnsupportedOperationException("Asignación a un destino que no es variable");
            }
            String destino = memoria.nombreTac(id.Identifier());
            programa.emit(Instruccion.asignar(destino, valor));
            temps.liberar(valor);
            return destino;
        }
        if (!(sufijos.get(sufijos.size() - 1) instanceof IndexExprContext ultimo)) {
            throw new UnsupportedOperationException("Asignación a un destino que no es variable ni elemento");
        }
        String arreglo = evaluarSufijos(lhs, sufijos.size() - 1);
        String indice = visit(ultimo.expression());
        String valor = visit(ctx.assignmentExpr());
        programa.emit(Instruccion.escribirIndice(arreglo, indice, valor));
        temps.liberar(arreglo);
        temps.liberar(indice);
        return valor;
    }

    @Override
    public String visitPropertyAssignExpr(PropertyAssignExprContext ctx) {
        String objeto = visit(ctx.lhs);
        String valor = visit(ctx.assignmentExpr());
        programa.emit(Instruccion.escribirCampo(objeto, ctx.Identifier().getText(), valor));
        temps.liberar(objeto);
        return valor;
    }

    @Override
    public String visitExprNoAssign(ExprNoAssignContext ctx) {
        return visit(ctx.conditionalExpr());
    }

    // --- Ternario ---

    /** {@code c ? a : b}: un solo temporal recibe el resultado en ambas ramas. */
    @Override
    public String visitTernaryExpr(TernaryExprContext ctx) {
        String condicion = visit(ctx.logicalOrExpr());
        if (ctx.expression().isEmpty()) {
            return condicion;
        }
        String sino = etiquetas.nueva();
        String fin = etiquetas.nueva();
        programa.emit(Instruccion.saltoSiFalso(condicion, sino));
        temps.liberar(condicion);
        String resultado = temps.nuevo();

        String entonces = visit(ctx.expression(0));
        programa.emit(Instruccion.asignar(resultado, entonces));
        temps.liberar(entonces);
        programa.emit(Instruccion.salto(fin));

        programa.emit(Instruccion.etiqueta(sino));
        String otro = visit(ctx.expression(1));
        programa.emit(Instruccion.asignar(resultado, otro));
        temps.liberar(otro);
        programa.emit(Instruccion.etiqueta(fin));
        return resultado;
    }

    // --- Lógicas y aritmética ---

    @Override
    public String visitLogicalOrExpr(LogicalOrExprContext ctx) {
        return cortocircuito(ctx, true);
    }

    @Override
    public String visitLogicalAndExpr(LogicalAndExprContext ctx) {
        return cortocircuito(ctx, false);
    }

    /**
     * {@code a && b && c} / {@code a || b || c}. El resultado vive en un único temporal que se
     * asigna en cada operando evaluado; si el primero ya es un temporal se usa como resultado.
     */
    private String cortocircuito(ParserRuleContext ctx, boolean esOr) {
        if (ctx.getChildCount() == 1) {
            return visit(ctx.getChild(0));
        }
        String fin = etiquetas.nueva();
        String resultado = visit(ctx.getChild(0));
        if (!TempPool.esTemporal(resultado)) {
            String variable = resultado;
            resultado = temps.nuevo();
            programa.emit(Instruccion.asignar(resultado, variable));
        }
        for (int i = 2; i < ctx.getChildCount(); i += 2) {
            programa.emit(esOr ? Instruccion.saltoSi(resultado, fin) : Instruccion.saltoSiFalso(resultado, fin));
            String operando = visit(ctx.getChild(i));
            programa.emit(Instruccion.asignar(resultado, operando));
            temps.liberar(operando);
        }
        programa.emit(Instruccion.etiqueta(fin));
        return resultado;
    }

    @Override
    public String visitEqualityExpr(EqualityExprContext ctx) {
        return binarias(ctx);
    }

    @Override
    public String visitRelationalExpr(RelationalExprContext ctx) {
        return binarias(ctx);
    }

    @Override
    public String visitAdditiveExpr(AdditiveExprContext ctx) {
        return binarias(ctx);
    }

    @Override
    public String visitMultiplicativeExpr(MultiplicativeExprContext ctx) {
        return binarias(ctx);
    }

    /** Operadores binarios asociativos a la izquierda: {@code operando (op operando)*}. */
    private String binarias(ParserRuleContext ctx) {
        String izquierda = visit(ctx.getChild(0));
        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            String operador = ctx.getChild(i).getText();
            String derecha = visit(ctx.getChild(i + 1));
            temps.liberar(izquierda);
            temps.liberar(derecha);
            String resultado = temps.nuevo();
            programa.emit(Instruccion.binaria(resultado, izquierda, operador, derecha));
            izquierda = resultado;
        }
        return izquierda;
    }

    @Override
    public String visitUnaryExpr(UnaryExprContext ctx) {
        if (ctx.unaryExpr() == null) {
            return visit(ctx.primaryExpr());
        }
        String operando = visit(ctx.unaryExpr());
        temps.liberar(operando);
        String resultado = temps.nuevo();
        programa.emit("-".equals(ctx.getChild(0).getText())
                ? Instruccion.negar(resultado, operando)
                : Instruccion.no(resultado, operando));
        return resultado;
    }

    // --- Primarias, literales y arreglos ---

    @Override
    public String visitPrimaryExpr(PrimaryExprContext ctx) {
        return ctx.expression() != null ? visit(ctx.expression()) : visit(ctx.getChild(0));
    }

    @Override
    public String visitLiteralExpr(LiteralExprContext ctx) {
        return ctx.arrayLiteral() != null ? visit(ctx.arrayLiteral()) : ctx.getText();
    }

    /** {@code [a, b]} → {@code t = newarray 2}, luego un {@code t[i] = e} por elemento. */
    @Override
    public String visitArrayLiteral(ArrayLiteralContext ctx) {
        String arreglo = temps.nuevo();
        programa.emit(Instruccion.nuevoArreglo(arreglo, String.valueOf(ctx.expression().size())));
        for (int i = 0; i < ctx.expression().size(); i++) {
            String elemento = visit(ctx.expression(i));
            programa.emit(Instruccion.escribirIndice(arreglo, String.valueOf(i), elemento));
            temps.liberar(elemento);
        }
        return arreglo;
    }

    // --- Acceso: variables, índices, propiedades, llamadas ---

    @Override
    public String visitLeftHandSide(LeftHandSideContext ctx) {
        return evaluarSufijos(ctx, ctx.suffixOp().size());
    }

    @Override
    public String visitIdentifierExpr(IdentifierExprContext ctx) {
        return memoria.nombreTac(ctx.Identifier());
    }

    @Override
    public String visitNewExpr(NewExprContext ctx) {
        return extension.atomo(ctx);
    }

    @Override
    public String visitThisExpr(ThisExprContext ctx) {
        return extension.atomo(ctx);
    }

    /** Evalúa el átomo y aplica los primeros {@code cantidad} sufijos. */
    private String evaluarSufijos(LeftHandSideContext ctx, int cantidad) {
        List<SuffixOpContext> sufijos = ctx.suffixOp();
        String actual = visit(ctx.primaryAtom());
        for (int i = 0; i < cantidad; i++) {
            SuffixOpContext sufijo = sufijos.get(i);
            if (sufijo instanceof IndexExprContext indexado) {
                String indice = visit(indexado.expression());
                temps.liberar(actual);
                temps.liberar(indice);
                String resultado = temps.nuevo();
                programa.emit(Instruccion.leerIndice(resultado, actual, indice));
                actual = resultado;
            } else if (sufijo instanceof PropertyAccessExprContext propiedad) {
                String nombre = propiedad.Identifier().getText();
                if (i + 1 < cantidad && sufijos.get(i + 1) instanceof CallExprContext llamada) {
                    actual = extension.metodo(actual, nombre, llamada);
                    i++;
                } else {
                    temps.liberar(actual);
                    String resultado = temps.nuevo();
                    programa.emit(Instruccion.leerCampo(resultado, actual, nombre));
                    actual = resultado;
                }
            } else {
                actual = extension.llamada(actual, (CallExprContext) sufijo);
            }
        }
        return actual;
    }
}
