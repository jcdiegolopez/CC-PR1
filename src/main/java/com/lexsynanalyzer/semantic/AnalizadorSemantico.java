package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.parser.LexSynAnalyzerBaseVisitor;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.AdditiveExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.AssignExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.AssignmentContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.BaseTypeContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.BlockContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.CallExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ClassDeclarationContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ConstantDeclarationContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.EqualityExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ExprNoAssignContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ExpressionContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ForStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ForeachStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.FunctionDeclarationContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.IdentifierExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.IndexExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.LeftHandSideContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.LiteralExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.LogicalAndExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.LogicalOrExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.MultiplicativeExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.NewExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ParameterContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.PrimaryExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ProgramContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.PropertyAssignExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.RelationalExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.StatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.SuffixOpContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.TernaryExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ThisExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.TryCatchStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.TypeContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.UnaryExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.VariableDeclarationContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Visitor que recorre el ParseTree de ANTLR y valida el significado del programa.
 *
 * <p>Cada método {@code visit...} de una expresión devuelve su {@link TipoDato} (atributo
 * sintetizado); el ámbito vigente se mantiene como contexto heredado dentro de la
 * {@link TablaSimbolos}. Una expresión inválida devuelve {@link TipoDato#ERROR} para que las
 * expresiones que dependen de ella no repitan el mismo mensaje.
 *
 * <p>Las estructuras avanzadas (clases, herencia, {@code this}, arreglos e índices) devuelven
 * {@link TipoDato#DESCONOCIDO}: sus subexpresiones sí se recorren, pero su validación
 * corresponde a la fase de estructuras avanzadas.
 */
public class AnalizadorSemantico extends LexSynAnalyzerBaseVisitor<TipoDato> {

    private final TablaSimbolos tabla = new TablaSimbolos();
    private final ErrorSemanticoReporter reporter;
    private int bloquesAbiertos;

    public AnalizadorSemantico(ErrorSemanticoReporter reporter) {
        this.reporter = Objects.requireNonNull(reporter);
    }

    /** Punto de entrada: analiza el programa completo partiendo del ámbito global. */
    public void analizar(ProgramContext programa) {
        visitProgram(Objects.requireNonNull(programa));
    }

    public TablaSimbolos tabla() {
        return tabla;
    }

    // ------------------------------------------------------------------
    // Programa, bloques y ámbitos
    // ------------------------------------------------------------------

    @Override
    public TipoDato visitProgram(ProgramContext ctx) {
        recorrerAmbito(ctx.statement());
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitBlock(BlockContext ctx) {
        entrarBloque("bloque");
        recorrerAmbito(ctx.statement());
        tabla.salirEntorno();
        return TipoDato.VOID;
    }

    /** Registra las declaraciones anticipadas del ámbito y luego recorre sus sentencias. */
    protected void recorrerAmbito(List<StatementContext> statements) {
        registrarDeclaracionesAnticipadas(statements);
        for (StatementContext statement : statements) {
            visit(statement);
        }
    }

    /**
     * Declara funciones y clases antes de recorrer el ámbito, de modo que una función pueda
     * llamarse a sí misma o a otra declarada más abajo en el mismo ámbito.
     */
    private void registrarDeclaracionesAnticipadas(List<StatementContext> statements) {
        for (StatementContext statement : statements) {
            if (statement.functionDeclaration() != null) {
                declararFuncion(statement.functionDeclaration());
            } else if (statement.classDeclaration() != null) {
                declararClase(statement.classDeclaration());
            }
        }
    }

    protected void declararFuncion(FunctionDeclarationContext ctx) {
        Token nombre = ctx.Identifier().getSymbol();
        TipoDato retorno = ctx.type() == null ? TipoDato.VOID : resolverTipo(ctx.type());
        Simbolo simbolo = Simbolo.funcion(nombre.getText(), parametrosDe(ctx), retorno,
                nombre.getLine(), columna(nombre));

        if (!tabla.declarar(simbolo)) {
            reportarRedeclaracion(nombre, "La función");
        }
    }

    protected List<Simbolo.Parametro> parametrosDe(FunctionDeclarationContext ctx) {
        List<Simbolo.Parametro> parametros = new ArrayList<>();
        if (ctx.parameters() == null) {
            return parametros;
        }
        for (ParameterContext parametro : ctx.parameters().parameter()) {
            Token token = parametro.Identifier().getSymbol();
            TipoDato tipo = parametro.type() == null ? TipoDato.DESCONOCIDO : resolverTipo(parametro.type());
            parametros.add(new Simbolo.Parametro(token.getText(), tipo, token.getLine(), columna(token)));
        }
        return parametros;
    }

    private void declararClase(ClassDeclarationContext ctx) {
        Token nombre = ctx.Identifier(0).getSymbol();
        if (!tabla.declarar(Simbolo.clase(nombre.getText(), nombre.getLine(), columna(nombre)))) {
            reportarRedeclaracion(nombre, "La clase");
        }
    }

    @Override
    public TipoDato visitFunctionDeclaration(FunctionDeclarationContext ctx) {
        // Los parámetros, el cuerpo y el retorno se validan en la fase de funciones.
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitClassDeclaration(ClassDeclarationContext ctx) {
        // El contenido de la clase se valida en la fase de estructuras avanzadas.
        return TipoDato.VOID;
    }

    // ------------------------------------------------------------------
    // Declaraciones de variables y constantes
    // ------------------------------------------------------------------

    @Override
    public TipoDato visitVariableDeclaration(VariableDeclarationContext ctx) {
        Token nombre = ctx.Identifier().getSymbol();
        TipoDato declarado = ctx.typeAnnotation() == null
                ? null
                : resolverTipo(ctx.typeAnnotation().type());

        boolean inicializada = ctx.initializer() != null;
        TipoDato tipo = declarado == null ? TipoDato.DESCONOCIDO : declarado;

        if (inicializada) {
            TipoDato valor = visit(ctx.initializer().expression());
            if (declarado == null) {
                tipo = tipoInferido(valor);
            } else {
                exigirAsignable(declarado, valor, ctx.initializer().expression(), nombre.getText(),
                        "La variable '" + nombre.getText() + "'");
            }
        }

        declararVariable(nombre, tipo, false, inicializada);
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitConstantDeclaration(ConstantDeclarationContext ctx) {
        Token nombre = ctx.Identifier().getSymbol();
        TipoDato declarado = ctx.typeAnnotation() == null
                ? null
                : resolverTipo(ctx.typeAnnotation().type());

        TipoDato valor = visit(ctx.expression());
        TipoDato tipo;
        if (declarado == null) {
            tipo = tipoInferido(valor);
        } else {
            tipo = declarado;
            exigirAsignable(declarado, valor, ctx.expression(), nombre.getText(),
                    "La constante '" + nombre.getText() + "'");
        }

        declararVariable(nombre, tipo, true, true);
        return TipoDato.VOID;
    }

    protected void declararVariable(Token nombre, TipoDato tipo, boolean constante, boolean inicializada) {
        Simbolo simbolo = Simbolo.variable(nombre.getText(), tipo, nombre.getLine(), columna(nombre),
                constante, inicializada);
        if (!tabla.declarar(simbolo)) {
            reportarRedeclaracion(nombre, constante ? "La constante" : "La variable");
        }
    }

    /** Sin anotación de tipo, {@code null} no aporta información suficiente para inferir. */
    private static TipoDato tipoInferido(TipoDato valor) {
        if (valor == null || valor.esError() || valor.clase() == TipoDato.Clase.NULL) {
            return TipoDato.DESCONOCIDO;
        }
        return valor;
    }

    // ------------------------------------------------------------------
    // Asignaciones
    // ------------------------------------------------------------------

    @Override
    public TipoDato visitAssignment(AssignmentContext ctx) {
        if (ctx.Identifier() != null && ctx.expression().size() == 1) {
            asignar(ctx.Identifier().getSymbol(), visit(ctx.expression(0)), ctx.expression(0));
            return TipoDato.VOID;
        }

        // Asignación a propiedad: se valida en la fase de estructuras avanzadas.
        for (ExpressionContext expresion : ctx.expression()) {
            visit(expresion);
        }
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitAssignExpr(AssignExprContext ctx) {
        TipoDato valor = visit(ctx.assignmentExpr());
        Token identificador = identificadorSimple(ctx.lhs);
        if (identificador == null) {
            visit(ctx.lhs);
            return valor;
        }
        return asignar(identificador, valor, ctx.assignmentExpr());
    }

    @Override
    public TipoDato visitPropertyAssignExpr(PropertyAssignExprContext ctx) {
        // Asignación a propiedad: se valida en la fase de estructuras avanzadas.
        visit(ctx.lhs);
        visit(ctx.assignmentExpr());
        return TipoDato.DESCONOCIDO;
    }

    private TipoDato asignar(Token identificador, TipoDato valor, ParserRuleContext origen) {
        Optional<Simbolo> encontrado = tabla.buscar(identificador.getText());
        if (encontrado.isEmpty()) {
            reportar(identificador, "La variable '" + identificador.getText()
                    + "' no está declarada en este ámbito.");
            return TipoDato.ERROR;
        }

        Simbolo simbolo = encontrado.get();
        if (simbolo.constante()) {
            reportar(identificador, "La constante '" + identificador.getText()
                    + "' no puede recibir un nuevo valor después de su declaración.");
            return simbolo.tipo();
        }
        if (simbolo.categoria() == CategoriaSimbolo.FUNCION || simbolo.categoria() == CategoriaSimbolo.CLASE) {
            reportar(identificador, "'" + identificador.getText() + "' es "
                    + descripcionCategoria(simbolo.categoria()) + " y no admite asignaciones.");
            return TipoDato.ERROR;
        }

        exigirAsignable(simbolo.tipo(), valor, origen, identificador.getText(),
                "La variable '" + identificador.getText() + "'");

        if (!simbolo.inicializado()) {
            tabla.actualizar(comoInicializado(simbolo));
        }
        return simbolo.tipo();
    }

    /** Devuelve el token del identificador cuando el lado izquierdo es un nombre simple. */
    private static Token identificadorSimple(LeftHandSideContext ctx) {
        if (ctx == null || !ctx.suffixOp().isEmpty()) {
            return null;
        }
        if (ctx.primaryAtom() instanceof IdentifierExprContext identificador) {
            return identificador.Identifier().getSymbol();
        }
        return null;
    }

    private static Simbolo comoInicializado(Simbolo simbolo) {
        return new Simbolo(simbolo.nombre(), simbolo.categoria(), simbolo.tipo(), simbolo.linea(),
                simbolo.columna(), simbolo.constante(), true, simbolo.parametros(), simbolo.tipoRetorno());
    }

    private static String descripcionCategoria(CategoriaSimbolo categoria) {
        return switch (categoria) {
            case FUNCION -> "una función";
            case CLASE -> "una clase";
            case CONSTANTE -> "una constante";
            case PARAMETRO -> "un parámetro";
            case VARIABLE -> "una variable";
        };
    }

    // ------------------------------------------------------------------
    // Sentencias que abren su propio ámbito
    // ------------------------------------------------------------------

    @Override
    public TipoDato visitForStatement(ForStatementContext ctx) {
        entrarBloque("for");
        if (ctx.variableDeclaration() != null) {
            visit(ctx.variableDeclaration());
        } else if (ctx.assignment() != null) {
            visit(ctx.assignment());
        }
        visitarCabeceraFor(ctx);
        visit(ctx.block());
        tabla.salirEntorno();
        return TipoDato.VOID;
    }

    /** Recorre la condición y el avance del {@code for}; la fase de flujo valida la condición. */
    protected void visitarCabeceraFor(ForStatementContext ctx) {
        for (ExpressionContext expresion : ctx.expression()) {
            visit(expresion);
        }
    }

    @Override
    public TipoDato visitForeachStatement(ForeachStatementContext ctx) {
        TipoDato iterable = visit(ctx.expression());
        entrarBloque("foreach");

        // El tipo del elemento se afina en la fase de estructuras avanzadas.
        TipoDato tipoElemento = iterable != null && iterable.clase() == TipoDato.Clase.ARREGLO
                ? iterable.tipoElemento()
                : TipoDato.DESCONOCIDO;
        declararVariable(ctx.Identifier().getSymbol(), tipoElemento, false, true);

        visit(ctx.block());
        tabla.salirEntorno();
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitTryCatchStatement(TryCatchStatementContext ctx) {
        visit(ctx.block(0));

        entrarBloque("catch");
        declararVariable(ctx.Identifier().getSymbol(), TipoDato.DESCONOCIDO, false, true);
        visit(ctx.block(1));
        tabla.salirEntorno();
        return TipoDato.VOID;
    }

    // ------------------------------------------------------------------
    // Expresiones
    // ------------------------------------------------------------------

    @Override
    public TipoDato visitExpression(ExpressionContext ctx) {
        return visit(ctx.assignmentExpr());
    }

    @Override
    public TipoDato visitExprNoAssign(ExprNoAssignContext ctx) {
        return visit(ctx.conditionalExpr());
    }

    @Override
    public TipoDato visitTernaryExpr(TernaryExprContext ctx) {
        TipoDato condicion = visit(ctx.logicalOrExpr());
        if (ctx.expression().isEmpty()) {
            return condicion;
        }

        exigirBooleano(condicion, ctx.logicalOrExpr(), "La condición del operador ternario");
        TipoDato siVerdadero = visit(ctx.expression(0));
        TipoDato siFalso = visit(ctx.expression(1));

        if (indeterminado(siVerdadero)) {
            return indeterminado(siFalso) ? TipoDato.DESCONOCIDO : siFalso;
        }
        if (indeterminado(siFalso)) {
            return siVerdadero;
        }
        if (!siVerdadero.esCompatibleCon(siFalso)) {
            reportar(ctx.expression(1).getStart(), "Las dos ramas del operador ternario deben producir"
                    + " tipos compatibles, pero son '" + siVerdadero + "' y '" + siFalso + "'.");
            return TipoDato.DESCONOCIDO;
        }
        return siVerdadero;
    }

    @Override
    public TipoDato visitLogicalOrExpr(LogicalOrExprContext ctx) {
        return tipoDeOperacionLogica(ctx, ctx.logicalAndExpr());
    }

    @Override
    public TipoDato visitLogicalAndExpr(LogicalAndExprContext ctx) {
        return tipoDeOperacionLogica(ctx, ctx.equalityExpr());
    }

    private TipoDato tipoDeOperacionLogica(ParserRuleContext ctx, List<? extends ParserRuleContext> operandos) {
        TipoDato resultado = visit(operandos.get(0));
        if (operandos.size() == 1) {
            return resultado;
        }

        exigirBooleanoDeOperador(resultado, operador(ctx, 1));
        for (int i = 1; i < operandos.size(); i++) {
            exigirBooleanoDeOperador(visit(operandos.get(i)), operador(ctx, 2 * i - 1));
        }
        return TipoDato.BOOLEAN;
    }

    @Override
    public TipoDato visitEqualityExpr(EqualityExprContext ctx) {
        TipoDato izquierda = visit(ctx.relationalExpr(0));
        for (int i = 1; i < ctx.relationalExpr().size(); i++) {
            Token operador = operador(ctx, 2 * i - 1);
            TipoDato derecha = visit(ctx.relationalExpr(i));
            if (!indeterminado(izquierda) && !indeterminado(derecha) && !izquierda.esCompatibleCon(derecha)) {
                reportar(operador, "El operador '" + operador.getText() + "' no puede comparar '"
                        + izquierda + "' con '" + derecha + "' porque son tipos incompatibles.");
            }
            izquierda = TipoDato.BOOLEAN;
        }
        return izquierda;
    }

    @Override
    public TipoDato visitRelationalExpr(RelationalExprContext ctx) {
        TipoDato izquierda = visit(ctx.additiveExpr(0));
        if (ctx.additiveExpr().size() == 1) {
            return izquierda;
        }

        exigirEnteroDeOperador(izquierda, operador(ctx, 1));
        for (int i = 1; i < ctx.additiveExpr().size(); i++) {
            exigirEnteroDeOperador(visit(ctx.additiveExpr(i)), operador(ctx, 2 * i - 1));
        }
        return TipoDato.BOOLEAN;
    }

    @Override
    public TipoDato visitAdditiveExpr(AdditiveExprContext ctx) {
        TipoDato izquierda = visit(ctx.multiplicativeExpr(0));
        for (int i = 1; i < ctx.multiplicativeExpr().size(); i++) {
            Token operador = operador(ctx, 2 * i - 1);
            izquierda = tipoDeSumaOResta(izquierda, visit(ctx.multiplicativeExpr(i)), operador);
        }
        return izquierda;
    }

    @Override
    public TipoDato visitMultiplicativeExpr(MultiplicativeExprContext ctx) {
        TipoDato izquierda = visit(ctx.unaryExpr(0));
        for (int i = 1; i < ctx.unaryExpr().size(); i++) {
            Token operador = operador(ctx, 2 * i - 1);
            izquierda = tipoDeOperacionEntera(izquierda, visit(ctx.unaryExpr(i)), operador);
        }
        return izquierda;
    }

    /** {@code +} admite dos enteros o la concatenación de dos cadenas; {@code -} solo enteros. */
    private TipoDato tipoDeSumaOResta(TipoDato izquierda, TipoDato derecha, Token operador) {
        if (!"+".equals(operador.getText())) {
            return tipoDeOperacionEntera(izquierda, derecha, operador);
        }
        if (indeterminado(izquierda) || indeterminado(derecha)) {
            return propagar(izquierda, derecha);
        }
        if (izquierda.clase() == TipoDato.Clase.STRING && derecha.clase() == TipoDato.Clase.STRING) {
            return TipoDato.STRING;
        }
        if (izquierda.esNumerico() && derecha.esNumerico()) {
            return TipoDato.INTEGER;
        }
        reportar(operador, "El operador '+' no puede aplicarse a '" + izquierda + "' y '" + derecha
                + "'; se esperaban dos valores integer o dos valores string.");
        return TipoDato.ERROR;
    }

    private TipoDato tipoDeOperacionEntera(TipoDato izquierda, TipoDato derecha, Token operador) {
        if (indeterminado(izquierda) || indeterminado(derecha)) {
            return propagar(izquierda, derecha);
        }
        if (izquierda.esNumerico() && derecha.esNumerico()) {
            return TipoDato.INTEGER;
        }
        reportar(operador, "El operador '" + operador.getText() + "' no puede aplicarse a '" + izquierda
                + "' y '" + derecha + "'; se esperaban valores integer.");
        return TipoDato.ERROR;
    }

    @Override
    public TipoDato visitUnaryExpr(UnaryExprContext ctx) {
        if (ctx.primaryExpr() != null) {
            return visit(ctx.primaryExpr());
        }

        Token operador = ((TerminalNode) ctx.getChild(0)).getSymbol();
        TipoDato operando = visit(ctx.unaryExpr());
        if ("!".equals(operador.getText())) {
            exigirBooleanoDeOperador(operando, operador);
            return TipoDato.BOOLEAN;
        }
        exigirEnteroDeOperador(operando, operador);
        return TipoDato.INTEGER;
    }

    @Override
    public TipoDato visitPrimaryExpr(PrimaryExprContext ctx) {
        if (ctx.literalExpr() != null) {
            return visit(ctx.literalExpr());
        }
        if (ctx.leftHandSide() != null) {
            return visit(ctx.leftHandSide());
        }
        return visit(ctx.expression());
    }

    @Override
    public TipoDato visitLiteralExpr(LiteralExprContext ctx) {
        if (ctx.Literal() != null) {
            return ctx.Literal().getText().startsWith("\"") ? TipoDato.STRING : TipoDato.INTEGER;
        }
        if (ctx.arrayLiteral() != null) {
            // La homogeneidad del arreglo se valida en la fase de estructuras avanzadas.
            for (ExpressionContext elemento : ctx.arrayLiteral().expression()) {
                visit(elemento);
            }
            return TipoDato.DESCONOCIDO;
        }
        return switch (ctx.getText()) {
            case "true", "false" -> TipoDato.BOOLEAN;
            case "null" -> TipoDato.NULL;
            default -> TipoDato.DESCONOCIDO;
        };
    }

    @Override
    public TipoDato visitLeftHandSide(LeftHandSideContext ctx) {
        TipoDato tipo = visit(ctx.primaryAtom());
        for (SuffixOpContext sufijo : ctx.suffixOp()) {
            tipo = aplicarSufijo(tipo, sufijo);
        }
        return tipo;
    }

    /**
     * Aplica llamada, índice o acceso a propiedad sobre el tipo acumulado. Las subexpresiones se
     * recorren siempre; el tipo resultante se resuelve en fases posteriores.
     */
    protected TipoDato aplicarSufijo(TipoDato tipo, SuffixOpContext sufijo) {
        if (sufijo instanceof CallExprContext llamada) {
            visitarArgumentos(llamada);
        } else if (sufijo instanceof IndexExprContext indice) {
            visit(indice.expression());
        }
        return TipoDato.DESCONOCIDO;
    }

    protected List<TipoDato> visitarArgumentos(CallExprContext ctx) {
        List<TipoDato> tipos = new ArrayList<>();
        if (ctx.arguments() == null) {
            return tipos;
        }
        for (ExpressionContext argumento : ctx.arguments().expression()) {
            tipos.add(visit(argumento));
        }
        return tipos;
    }

    @Override
    public TipoDato visitIdentifierExpr(IdentifierExprContext ctx) {
        Token identificador = ctx.Identifier().getSymbol();
        Optional<Simbolo> encontrado = tabla.buscar(identificador.getText());
        if (encontrado.isEmpty()) {
            reportar(identificador, "El identificador '" + identificador.getText()
                    + "' no está declarado en este ámbito.");
            return TipoDato.ERROR;
        }

        Simbolo simbolo = encontrado.get();
        if (simbolo.categoria() == CategoriaSimbolo.FUNCION || simbolo.categoria() == CategoriaSimbolo.CLASE) {
            return TipoDato.DESCONOCIDO;
        }
        if (!simbolo.inicializado()) {
            reportar(identificador, "La variable '" + identificador.getText()
                    + "' se usa antes de asignarle un valor.");
        }
        return simbolo.tipo();
    }

    @Override
    public TipoDato visitNewExpr(NewExprContext ctx) {
        // La existencia de la clase y su constructor se validan en estructuras avanzadas.
        if (ctx.arguments() != null) {
            for (ExpressionContext argumento : ctx.arguments().expression()) {
                visit(argumento);
            }
        }
        return TipoDato.DESCONOCIDO;
    }

    @Override
    public TipoDato visitThisExpr(ThisExprContext ctx) {
        // El uso válido de 'this' se valida en la fase de estructuras avanzadas.
        return TipoDato.DESCONOCIDO;
    }

    // ------------------------------------------------------------------
    // Tipos declarados
    // ------------------------------------------------------------------

    protected TipoDato resolverTipo(TypeContext ctx) {
        TipoDato tipo = resolverTipoBase(ctx.baseType());
        int dimensiones = (ctx.getChildCount() - 1) / 2;
        for (int i = 0; i < dimensiones; i++) {
            tipo = TipoDato.arreglo(tipo);
        }
        return tipo;
    }

    private TipoDato resolverTipoBase(BaseTypeContext ctx) {
        if (ctx.Identifier() != null) {
            // Que la clase exista se comprueba en la fase de estructuras avanzadas.
            return TipoDato.clase(ctx.Identifier().getText());
        }
        return switch (ctx.getText()) {
            case "integer" -> TipoDato.INTEGER;
            case "boolean" -> TipoDato.BOOLEAN;
            case "string" -> TipoDato.STRING;
            default -> TipoDato.DESCONOCIDO;
        };
    }

    // ------------------------------------------------------------------
    // Utilidades de validación y reporte
    // ------------------------------------------------------------------

    protected void exigirAsignable(TipoDato destino, TipoDato valor, ParserRuleContext origen,
                                   String simbolo, String sujeto) {
        if (destino == null || indeterminado(valor) || destino.esCompatibleCon(valor)) {
            return;
        }
        reporter.reportar(origen.getStart().getLine(), columna(origen.getStart()), simbolo,
                sujeto + " es de tipo '" + destino + "' y no puede recibir un valor de tipo '" + valor + "'.");
    }

    protected void exigirBooleano(TipoDato tipo, ParserRuleContext origen, String sujeto) {
        if (indeterminado(tipo) || tipo.esBooleano()) {
            return;
        }
        reporter.reportar(origen.getStart().getLine(), columna(origen.getStart()), origen.getText(),
                sujeto + " debe ser de tipo 'boolean', pero es de tipo '" + tipo + "'.");
    }

    private void exigirBooleanoDeOperador(TipoDato tipo, Token operador) {
        if (indeterminado(tipo) || tipo.esBooleano()) {
            return;
        }
        reportar(operador, "El operador '" + operador.getText() + "' solo admite operandos 'boolean',"
                + " pero se recibió '" + tipo + "'.");
    }

    private void exigirEnteroDeOperador(TipoDato tipo, Token operador) {
        if (indeterminado(tipo) || tipo.esNumerico()) {
            return;
        }
        reportar(operador, "El operador '" + operador.getText() + "' solo admite operandos 'integer',"
                + " pero se recibió '" + tipo + "'.");
    }

    protected void reportar(Token token, String descripcion) {
        reporter.reportar(token.getLine(), columna(token), token.getText(), descripcion);
    }

    private void reportarRedeclaracion(Token nombre, String sujeto) {
        reportar(nombre, sujeto + " '" + nombre.getText()
                + "' ya está declarada en este ámbito; use otro nombre.");
    }

    /** Un tipo indeterminado ya provocó un error o no se puede conocer: no se vuelve a reportar. */
    protected static boolean indeterminado(TipoDato tipo) {
        return tipo == null || tipo.esError() || tipo.clase() == TipoDato.Clase.DESCONOCIDO;
    }

    private static TipoDato propagar(TipoDato izquierda, TipoDato derecha) {
        boolean hayError = (izquierda != null && izquierda.esError()) || (derecha != null && derecha.esError());
        return hayError ? TipoDato.ERROR : TipoDato.DESCONOCIDO;
    }

    private static Token operador(ParserRuleContext ctx, int indiceHijo) {
        return ((TerminalNode) ctx.getChild(indiceHijo)).getSymbol();
    }

    protected static int columna(Token token) {
        return token.getCharPositionInLine() + 1;
    }

    protected void entrarBloque(String nombre) {
        tabla.entrarEntorno(nombre + "#" + (++bloquesAbiertos));
    }

    @Override
    protected TipoDato defaultResult() {
        return TipoDato.DESCONOCIDO;
    }

    @Override
    protected TipoDato aggregateResult(TipoDato acumulado, TipoDato siguiente) {
        return siguiente == null ? acumulado : siguiente;
    }
}
