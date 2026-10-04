package com.lexsynanalyzer.semantic;

import com.lexsynanalyzer.parser.LexSynAnalyzerBaseVisitor;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.AdditiveExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.AssignExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.AssignmentContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.BaseTypeContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.BlockContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.BreakStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.CallExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ClassDeclarationContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ClassMemberContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ConstantDeclarationContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ContinueStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.DoWhileStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.EqualityExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ExprNoAssignContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ExpressionContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ForStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ForeachStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.FunctionDeclarationContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.IdentifierExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.IfStatementContext;
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
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.PropertyAccessExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.PropertyAssignExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.RelationalExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ReturnStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.StatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.SuffixOpContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.SwitchCaseContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.SwitchStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.TernaryExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.ThisExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.TryCatchStatementContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.TypeContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.UnaryExprContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.VariableDeclarationContext;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.WhileStatementContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Visitor que recorre el ParseTree de ANTLR y valida el significado semántico del programa.
 *
 * <p>Cada método {@code visit...} de una expresión devuelve su {@link TipoDato} (atributo
 * sintetizado); el ámbito vigente se mantiene como contexto heredado dentro de la
 * {@link TablaSimbolos}. Una expresión inválida devuelve {@link TipoDato#ERROR} para que las
 * expresiones que dependen de ella no repitan el mismo mensaje.
 *
 * <p>Valida tipos primitivos, funciones, estructuras de control, ámbitos léxicos, y estructuras
 * avanzadas (clases, herencia, constructores, {@code this}, {@code new}, atributos, métodos,
 * arreglos homogéneos y multidimensionales, indexación y {@code foreach}).
 */
public class AnalizadorSemantico extends LexSynAnalyzerBaseVisitor<TipoDato> {

    private final TablaSimbolos tabla = new TablaSimbolos();
    private final ResultadoSemantico resultado = new ResultadoSemantico(tabla);
    private final ErrorSemanticoReporter reporter;
    private final Deque<Simbolo> funcionesActivas = new ArrayDeque<>();
    private Simbolo claseActual = null;
    private int bloquesAbiertos;
    private int ciclosActivos;
    private int switchesActivos;

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

    /** Tipos, entornos y resoluciones que se conservan para la generación de código intermedio. */
    public ResultadoSemantico resultado() {
        return resultado;
    }

    /** Además de visitar, guarda el tipo calculado para cada nodo. */
    @Override
    public TipoDato visit(ParseTree arbol) {
        TipoDato tipo = super.visit(arbol);
        if (tipo != null) {
            resultado.registrarTipo(arbol, tipo);
        }
        return tipo;
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
        resultado.registrarEntorno(ctx, tabla.actual());
        recorrerAmbito(ctx.statement());
        tabla.salirEntorno();
        return TipoDato.VOID;
    }

    /** Registra las declaraciones anticipadas del ámbito y luego recorre sus sentencias. */
    protected void recorrerAmbito(List<StatementContext> statements) {
        registrarDeclaracionesAnticipadas(statements);

        boolean flujoInterrumpido = false;
        boolean inalcanzableReportado = false;
        for (StatementContext statement : statements) {
            if (flujoInterrumpido && !inalcanzableReportado) {
                reportar(statement.getStart(), "Esta sentencia nunca se ejecuta: el flujo ya termina"
                        + " antes de llegar aquí.");
                inalcanzableReportado = true;
            }
            visit(statement);
            flujoInterrumpido = flujoInterrumpido || interrumpeElFlujo(statement);
        }
    }

    /** Una sentencia que corta el flujo deja inalcanzable a todo lo que la sigue en su ámbito. */
    private static boolean interrumpeElFlujo(StatementContext statement) {
        return statement.returnStatement() != null
                || statement.breakStatement() != null
                || statement.continueStatement() != null;
    }

    /**
     * Declara funciones y clases antes de recorrer el ámbito, de modo que una función o clase
     * pueda invocarse o instanciarse recursivamente o antes de su definición textual.
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
        } else {
            resultado.registrarResolucion(nombre, tabla.actual());
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
        String clasePadre = ctx.Identifier().size() > 1 ? ctx.Identifier(1).getText() : null;
        Simbolo simboloClase = Simbolo.clase(nombre.getText(), clasePadre, nombre.getLine(), columna(nombre));

        if (!tabla.declarar(simboloClase)) {
            reportarRedeclaracion(nombre, "La clase");
            return;
        }

        // Registrar miembros de la clase en su entorno de miembros
        for (ClassMemberContext member : ctx.classMember()) {
            if (member.functionDeclaration() != null) {
                FunctionDeclarationContext fnCtx = member.functionDeclaration();
                Token fnNombre = fnCtx.Identifier().getSymbol();
                TipoDato fnRetorno = fnCtx.type() == null ? TipoDato.VOID : resolverTipo(fnCtx.type());
                Simbolo metodo = Simbolo.funcion(fnNombre.getText(), parametrosDe(fnCtx), fnRetorno,
                        fnNombre.getLine(), columna(fnNombre));
                if (!simboloClase.entornoMiembros().declarar(metodo)) {
                    reportarRedeclaracion(fnNombre, "El miembro");
                }
            } else if (member.variableDeclaration() != null) {
                VariableDeclarationContext varCtx = member.variableDeclaration();
                Token varNombre = varCtx.Identifier().getSymbol();
                TipoDato varTipo = varCtx.typeAnnotation() == null
                        ? TipoDato.DESCONOCIDO
                        : resolverTipo(varCtx.typeAnnotation().type());
                Simbolo varSim = Simbolo.variable(varNombre.getText(), varTipo, varNombre.getLine(),
                        columna(varNombre), false, varCtx.initializer() != null);
                if (!simboloClase.entornoMiembros().declarar(varSim)) {
                    reportarRedeclaracion(varNombre, "El atributo");
                }
            } else if (member.constantDeclaration() != null) {
                ConstantDeclarationContext constCtx = member.constantDeclaration();
                Token constNombre = constCtx.Identifier().getSymbol();
                TipoDato constTipo = constCtx.typeAnnotation() == null
                        ? TipoDato.DESCONOCIDO
                        : resolverTipo(constCtx.typeAnnotation().type());
                Simbolo constSim = Simbolo.variable(constNombre.getText(), constTipo, constNombre.getLine(),
                        columna(constNombre), true, true);
                if (!simboloClase.entornoMiembros().declarar(constSim)) {
                    reportarRedeclaracion(constNombre, "La constante");
                }
            }
        }
    }

    @Override
    public TipoDato visitFunctionDeclaration(FunctionDeclarationContext ctx) {
        Token nombre = ctx.Identifier().getSymbol();
        TipoDato retorno = ctx.type() == null ? TipoDato.VOID : resolverTipo(ctx.type());
        List<Simbolo.Parametro> parametros = parametrosDe(ctx);

        // El entorno de la función cuelga del entorno donde se declaró
        entrarBloque("funcion:" + nombre.getText());
        resultado.registrarEntorno(ctx, tabla.actual());
        declararParametros(parametros);
        registrarResolucionDeParametros(ctx);

        funcionesActivas.push(Simbolo.funcion(nombre.getText(), parametros, retorno,
                nombre.getLine(), columna(nombre)));
        // Un ciclo exterior no alcanza al cuerpo de la función: 'break' y 'continue' no cruzan
        int ciclosExteriores = ciclosActivos;
        int switchesExteriores = switchesActivos;
        ciclosActivos = 0;
        switchesActivos = 0;

        recorrerAmbito(ctx.block().statement());

        ciclosActivos = ciclosExteriores;
        switchesActivos = switchesExteriores;
        funcionesActivas.pop();
        tabla.salirEntorno();

        exigirRetornoPresente(ctx, nombre, retorno);
        return TipoDato.VOID;
    }

    private void declararParametros(List<Simbolo.Parametro> parametros) {
        for (Simbolo.Parametro parametro : parametros) {
            Simbolo simbolo = new Simbolo(parametro.nombre(), CategoriaSimbolo.PARAMETRO, parametro.tipo(),
                    parametro.linea(), parametro.columna(), false, true, List.of(), null);
            if (!tabla.declarar(simbolo)) {
                reporter.reportar(parametro.linea(), parametro.columna(), parametro.nombre(),
                        "El parámetro '" + parametro.nombre() + "' está repetido en la lista de parámetros.");
            }
        }
    }

    private void registrarResolucionDeParametros(FunctionDeclarationContext ctx) {
        if (ctx.parameters() == null) {
            return;
        }
        for (ParameterContext parametro : ctx.parameters().parameter()) {
            resultado.registrarResolucion(parametro.Identifier().getSymbol(), tabla.actual());
        }
    }

    /** Una función con tipo de retorno declarado debe tener al menos un 'return' con valor. */
    private void exigirRetornoPresente(FunctionDeclarationContext ctx, Token nombre, TipoDato retorno) {
        if (retorno.clase() == TipoDato.Clase.VOID || contieneRetornoConValor(ctx.block())) {
            return;
        }
        reportar(nombre, "La función '" + nombre.getText() + "' declara el tipo de retorno '" + retorno
                + "', pero ninguna de sus rutas devuelve un valor.");
    }

    /** Busca un 'return expr' dentro del cuerpo sin descender a funciones o clases anidadas. */
    private static boolean contieneRetornoConValor(ParseTree nodo) {
        if (nodo instanceof ReturnStatementContext retorno) {
            return retorno.expression() != null;
        }
        if (nodo instanceof FunctionDeclarationContext || nodo instanceof ClassDeclarationContext) {
            return false;
        }
        for (int i = 0; i < nodo.getChildCount(); i++) {
            if (contieneRetornoConValor(nodo.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TipoDato visitClassDeclaration(ClassDeclarationContext ctx) {
        Token nombre = ctx.Identifier(0).getSymbol();
        String nombreClase = nombre.getText();

        if (ctx.Identifier().size() > 1) {
            Token tokenPadre = ctx.Identifier(1).getSymbol();
            String nombrePadre = tokenPadre.getText();
            Optional<Simbolo> optPadre = tabla.buscar(nombrePadre);
            if (optPadre.isEmpty()) {
                reportar(tokenPadre, "La clase padre '" + nombrePadre + "' no está declarada en este ámbito.");
            } else if (optPadre.get().categoria() != CategoriaSimbolo.CLASE) {
                reportar(tokenPadre, "'" + nombrePadre + "' no es una clase y no se puede heredar de ella.");
            } else if (tieneCicloDeHerencia(nombreClase, nombrePadre)) {
                reportar(tokenPadre, "Herencia cíclica detectada: la clase '" + nombreClase
                        + "' no puede heredar de sí misma directa o indirectamente.");
            }
        }

        Optional<Simbolo> optClase = tabla.buscar(nombreClase);
        if (optClase.isEmpty()) {
            return TipoDato.VOID;
        }
        Simbolo claseSimbolo = optClase.get();

        Simbolo clasePrevia = claseActual;
        claseActual = claseSimbolo;

        // Entrar en el entorno de la clase
        tabla.entrarEntorno("clase:" + nombreClase);
        resultado.registrarEntorno(ctx, tabla.actual());
        if (claseSimbolo.entornoMiembros() != null) {
            for (Simbolo miembro : claseSimbolo.entornoMiembros().simbolos()) {
                tabla.declarar(miembro);
            }
        }

        for (ClassMemberContext member : ctx.classMember()) {
            if (member.functionDeclaration() != null) {
                visitarMetodoDeClase(member.functionDeclaration());
            } else if (member.variableDeclaration() != null) {
                visitarAtributoDeClase(member.variableDeclaration());
            } else if (member.constantDeclaration() != null) {
                visitarConstanteDeClase(member.constantDeclaration());
            }
        }

        tabla.salirEntorno();
        claseActual = clasePrevia;
        return TipoDato.VOID;
    }

    private void visitarMetodoDeClase(FunctionDeclarationContext ctx) {
        Token nombre = ctx.Identifier().getSymbol();
        TipoDato retorno = ctx.type() == null ? TipoDato.VOID : resolverTipo(ctx.type());
        List<Simbolo.Parametro> parametros = parametrosDe(ctx);

        entrarBloque("metodo:" + nombre.getText());
        resultado.registrarEntorno(ctx, tabla.actual());
        declararParametros(parametros);
        registrarResolucionDeParametros(ctx);

        funcionesActivas.push(Simbolo.funcion(nombre.getText(), parametros, retorno,
                nombre.getLine(), columna(nombre)));
        int ciclosExteriores = ciclosActivos;
        int switchesExteriores = switchesActivos;
        ciclosActivos = 0;
        switchesActivos = 0;

        recorrerAmbito(ctx.block().statement());

        ciclosActivos = ciclosExteriores;
        switchesActivos = switchesExteriores;
        funcionesActivas.pop();
        tabla.salirEntorno();

        exigirRetornoPresente(ctx, nombre, retorno);
    }

    private void visitarAtributoDeClase(VariableDeclarationContext ctx) {
        Token nombre = ctx.Identifier().getSymbol();
        TipoDato declarado = ctx.typeAnnotation() == null
                ? null
                : resolverTipo(ctx.typeAnnotation().type());

        if (ctx.initializer() != null) {
            TipoDato valor = visit(ctx.initializer().expression());
            if (declarado != null) {
                exigirAsignable(declarado, valor, ctx.initializer().expression(), nombre.getText(),
                        "El atributo '" + nombre.getText() + "'");
            }
        }
    }

    private void visitarConstanteDeClase(ConstantDeclarationContext ctx) {
        Token nombre = ctx.Identifier().getSymbol();
        TipoDato declarado = ctx.typeAnnotation() == null
                ? null
                : resolverTipo(ctx.typeAnnotation().type());

        TipoDato valor = visit(ctx.expression());
        if (declarado != null) {
            exigirAsignable(declarado, valor, ctx.expression(), nombre.getText(),
                    "La constante '" + nombre.getText() + "'");
        }
    }

    private boolean tieneCicloDeHerencia(String subClase, String superClase) {
        if (Objects.equals(subClase, superClase)) {
            return true;
        }
        String actual = superClase;
        int maxNivel = 100;
        while (actual != null && maxNivel-- > 0) {
            Optional<Simbolo> sim = tabla.buscar(actual);
            if (sim.isEmpty() || sim.get().categoria() != CategoriaSimbolo.CLASE) {
                break;
            }
            String padre = sim.get().clasePadre();
            if (Objects.equals(padre, subClase)) {
                return true;
            }
            actual = padre;
        }
        return false;
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
        } else {
            resultado.registrarResolucion(nombre, tabla.actual());
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

        // Asignación a propiedad: expression '.' Identifier '=' expression ';'
        if (ctx.expression().size() == 2 && ctx.Identifier() != null) {
            TipoDato objetoTipo = visit(ctx.expression(0));
            Token propiedad = ctx.Identifier().getSymbol();
            TipoDato valor = visit(ctx.expression(1));
            asignarPropiedad(objetoTipo, propiedad, valor, ctx.expression(1));
            return TipoDato.VOID;
        }

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
        TipoDato objetoTipo = visit(ctx.lhs);
        Token propiedad = ctx.Identifier().getSymbol();
        TipoDato valor = visit(ctx.assignmentExpr());
        return asignarPropiedad(objetoTipo, propiedad, valor, ctx.assignmentExpr());
    }

    private TipoDato asignar(Token identificador, TipoDato valor, ParserRuleContext origen) {
        Optional<Simbolo> encontrado = tabla.buscar(identificador.getText());
        if (encontrado.isEmpty()) {
            reportar(identificador, "La variable '" + identificador.getText()
                    + "' no está declarada en este ámbito.");
            return TipoDato.ERROR;
        }

        Simbolo simbolo = encontrado.get();
        registrarResolucion(identificador);
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

    private TipoDato asignarPropiedad(TipoDato objetoTipo, Token propiedad, TipoDato valor, ParserRuleContext origen) {
        if (indeterminado(objetoTipo)) {
            return TipoDato.DESCONOCIDO;
        }
        if (objetoTipo.clase() != TipoDato.Clase.CLASE) {
            reportar(propiedad, "No se puede asignar la propiedad '" + propiedad.getText()
                    + "' a un valor de tipo '" + objetoTipo + "'.");
            return TipoDato.ERROR;
        }

        Optional<Simbolo> optClase = tabla.buscar(objetoTipo.nombreClase());
        if (optClase.isEmpty() || optClase.get().categoria() != CategoriaSimbolo.CLASE) {
            reportar(propiedad, "La clase '" + objetoTipo.nombreClase() + "' no está declarada.");
            return TipoDato.ERROR;
        }

        Simbolo clase = optClase.get();
        Optional<Simbolo> optMiembro = clase.buscarMiembro(propiedad.getText(), tabla);
        if (optMiembro.isEmpty()) {
            reportar(propiedad, "La clase '" + objetoTipo.nombreClase() + "' no contiene el atributo '"
                    + propiedad.getText() + "'.");
            return TipoDato.ERROR;
        }

        Simbolo miembro = optMiembro.get();
        if (miembro.categoria() == CategoriaSimbolo.FUNCION) {
            reportar(propiedad, "'" + propiedad.getText() + "' es un método de la clase '"
                    + objetoTipo.nombreClase() + "' y no admite asignaciones.");
            return TipoDato.ERROR;
        }
        if (miembro.constante()) {
            reportar(propiedad, "La constante '" + propiedad.getText() + "' de la clase '"
                    + objetoTipo.nombreClase() + "' no puede recibir un nuevo valor.");
            return miembro.tipo();
        }

        exigirAsignable(miembro.tipo(), valor, origen, propiedad.getText(),
                "El atributo '" + propiedad.getText() + "' de la clase '" + objetoTipo.nombreClase() + "'");
        return miembro.tipo();
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
                simbolo.columna(), simbolo.constante(), true, simbolo.parametros(), simbolo.tipoRetorno(),
                simbolo.clasePadre(), simbolo.entornoMiembros());
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
        resultado.registrarEntorno(ctx, tabla.actual());
        if (ctx.variableDeclaration() != null) {
            visit(ctx.variableDeclaration());
        } else if (ctx.assignment() != null) {
            visit(ctx.assignment());
        }
        visitarCabeceraFor(ctx);
        visitarCuerpoDeCiclo(ctx.block());
        tabla.salirEntorno();
        return TipoDato.VOID;
    }

    protected void visitarCabeceraFor(ForStatementContext ctx) {
        int ultimoPuntoYComa = -1;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof TerminalNode terminal && ";".equals(terminal.getText())) {
                ultimoPuntoYComa = i;
            }
        }

        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (!(ctx.getChild(i) instanceof ExpressionContext expresion)) {
                continue;
            }
            TipoDato tipo = visit(expresion);
            if (i < ultimoPuntoYComa) {
                exigirBooleano(tipo, expresion, "La condición del 'for'");
            }
        }
    }

    @Override
    public TipoDato visitForeachStatement(ForeachStatementContext ctx) {
        TipoDato iterable = visit(ctx.expression());
        Token variable = ctx.Identifier().getSymbol();

        if (!indeterminado(iterable) && iterable.clase() != TipoDato.Clase.ARREGLO && iterable.clase() != TipoDato.Clase.STRING) {
            reportar(ctx.expression().getStart(), "La sentencia 'foreach' requiere un arreglo o una cadena, pero se recibió un tipo '"
                    + iterable + "'.");
        }

        TipoDato tipoElemento;
        if (iterable != null && iterable.clase() == TipoDato.Clase.ARREGLO) {
            tipoElemento = iterable.tipoElemento();
        } else if (iterable != null && iterable.clase() == TipoDato.Clase.STRING) {
            tipoElemento = TipoDato.STRING;
        } else {
            tipoElemento = TipoDato.DESCONOCIDO;
        }

        entrarBloque("foreach");
        resultado.registrarEntorno(ctx, tabla.actual());
        declararVariable(variable, tipoElemento, false, true);

        visitarCuerpoDeCiclo(ctx.block());
        tabla.salirEntorno();
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitTryCatchStatement(TryCatchStatementContext ctx) {
        visit(ctx.block(0));

        entrarBloque("catch");
        resultado.registrarEntorno(ctx, tabla.actual());
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
        if (!siVerdadero.esCompatibleCon(siFalso, tabla)) {
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
            if (!indeterminado(izquierda) && !indeterminado(derecha) && !izquierda.esCompatibleCon(derecha, tabla)) {
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
            List<ExpressionContext> elementos = ctx.arrayLiteral().expression();
            if (elementos.isEmpty()) {
                return TipoDato.arreglo(TipoDato.DESCONOCIDO);
            }
            TipoDato tipoEsperado = visit(elementos.get(0));
            for (int i = 1; i < elementos.size(); i++) {
                TipoDato tipoActual = visit(elementos.get(i));
                if (!indeterminado(tipoEsperado) && !indeterminado(tipoActual)
                        && !tipoEsperado.esCompatibleCon(tipoActual, tabla)) {
                    reportar(elementos.get(i).getStart(), "Los elementos del arreglo deben ser del mismo tipo; se esperaba '"
                            + tipoEsperado + "', pero se encontró '" + tipoActual + "'.");
                    return TipoDato.ERROR;
                }
            }
            return TipoDato.arreglo(tipoEsperado);
        }
        return switch (ctx.getText()) {
            case "true", "false" -> TipoDato.BOOLEAN;
            case "null" -> TipoDato.NULL;
            default -> TipoDato.DESCONOCIDO;
        };
    }

    @Override
    public TipoDato visitLeftHandSide(LeftHandSideContext ctx) {
        List<SuffixOpContext> sufijos = ctx.suffixOp();
        Token nombre = ctx.primaryAtom() instanceof IdentifierExprContext identificador
                ? identificador.Identifier().getSymbol()
                : null;

        TipoDato tipo;
        int indiceSufijo = 0;
        if (nombre != null && !sufijos.isEmpty() && sufijos.get(0) instanceof CallExprContext llamada) {
            tipo = tipoDeLlamada(nombre, llamada);
            indiceSufijo = 1;
        } else {
            tipo = visit(ctx.primaryAtom());
        }

        while (indiceSufijo < sufijos.size()) {
            SuffixOpContext sufijo = sufijos.get(indiceSufijo);
            if (sufijo instanceof PropertyAccessExprContext prop) {
                resultado.registrarReceptor(prop, tipo);
                Token propToken = prop.Identifier().getSymbol();
                if (indiceSufijo + 1 < sufijos.size() && sufijos.get(indiceSufijo + 1) instanceof CallExprContext llamada) {
                    tipo = tipoDeLlamadaMetodo(tipo, propToken, llamada);
                    indiceSufijo += 2;
                } else {
                    tipo = tipoDeAccesoPropiedad(tipo, propToken);
                    indiceSufijo += 1;
                }
            } else if (sufijo instanceof IndexExprContext indice) {
                tipo = tipoDeIndexacion(tipo, indice);
                indiceSufijo += 1;
            } else if (sufijo instanceof CallExprContext llamada) {
                visitarArgumentos(llamada);
                if (!indeterminado(tipo)) {
                    reportar(llamada.getStart(), "La expresión no es una función ni un método y no puede invocarse.");
                }
                tipo = TipoDato.ERROR;
                indiceSufijo += 1;
            } else {
                tipo = TipoDato.DESCONOCIDO;
                indiceSufijo += 1;
            }
        }
        return tipo;
    }

    private TipoDato tipoDeAccesoPropiedad(TipoDato tipo, Token propToken) {
        if (indeterminado(tipo)) {
            return TipoDato.DESCONOCIDO;
        }
        if (tipo.clase() != TipoDato.Clase.CLASE) {
            reportar(propToken, "No se puede acceder a la propiedad '" + propToken.getText()
                    + "' en un valor de tipo '" + tipo + "'.");
            return TipoDato.ERROR;
        }

        Optional<Simbolo> optClase = tabla.buscar(tipo.nombreClase());
        if (optClase.isEmpty() || optClase.get().categoria() != CategoriaSimbolo.CLASE) {
            reportar(propToken, "La clase '" + tipo.nombreClase() + "' no está declarada.");
            return TipoDato.ERROR;
        }

        Simbolo clase = optClase.get();
        Optional<Simbolo> optMiembro = clase.buscarMiembro(propToken.getText(), tabla);
        if (optMiembro.isEmpty()) {
            reportar(propToken, "La clase '" + tipo.nombreClase() + "' no contiene ningún atributo o método llamado '"
                    + propToken.getText() + "'.");
            return TipoDato.ERROR;
        }

        Simbolo miembro = optMiembro.get();
        return miembro.categoria() == CategoriaSimbolo.FUNCION ? miembro.tipoRetorno() : miembro.tipo();
    }

    private TipoDato tipoDeLlamadaMetodo(TipoDato tipo, Token propToken, CallExprContext llamada) {
        List<TipoDato> tiposArg = visitarArgumentos(llamada);
        List<ExpressionContext> args = argumentosDe(llamada);

        if (indeterminado(tipo)) {
            return TipoDato.DESCONOCIDO;
        }
        if (tipo.clase() != TipoDato.Clase.CLASE) {
            reportar(propToken, "No se puede invocar el método '" + propToken.getText()
                    + "' en un valor de tipo '" + tipo + "'.");
            return TipoDato.ERROR;
        }

        Optional<Simbolo> optClase = tabla.buscar(tipo.nombreClase());
        if (optClase.isEmpty() || optClase.get().categoria() != CategoriaSimbolo.CLASE) {
            reportar(propToken, "La clase '" + tipo.nombreClase() + "' no está declarada.");
            return TipoDato.ERROR;
        }

        Simbolo clase = optClase.get();
        Optional<Simbolo> optMiembro = clase.buscarMiembro(propToken.getText(), tabla);
        if (optMiembro.isEmpty()) {
            reportar(propToken, "La clase '" + tipo.nombreClase() + "' no contiene ningún método llamado '"
                    + propToken.getText() + "'.");
            return TipoDato.ERROR;
        }

        Simbolo miembro = optMiembro.get();
        if (miembro.categoria() != CategoriaSimbolo.FUNCION) {
            reportar(propToken, "'" + propToken.getText() + "' es un atributo de la clase '"
                    + tipo.nombreClase() + "' y no puede invocarse como método.");
            return TipoDato.ERROR;
        }

        List<Simbolo.Parametro> params = miembro.parametros();
        if (args.size() != params.size()) {
            reportar(propToken, "El método '" + propToken.getText() + "' de la clase '" + tipo.nombreClase()
                    + "' espera " + params.size() + " argumento(s), pero se recibieron " + args.size() + ".");
            return miembro.tipoRetorno();
        }

        for (int i = 0; i < params.size(); i++) {
            Simbolo.Parametro param = params.get(i);
            exigirAsignable(param.tipo(), tiposArg.get(i), args.get(i), param.nombre(),
                    "El parámetro '" + param.nombre() + "' del método '" + propToken.getText() + "'");
        }
        return miembro.tipoRetorno();
    }

    private TipoDato tipoDeIndexacion(TipoDato tipo, IndexExprContext indice) {
        TipoDato tipoIndice = visit(indice.expression());
        if (indeterminado(tipo)) {
            return TipoDato.DESCONOCIDO;
        }
        if (tipo.clase() != TipoDato.Clase.ARREGLO && tipo.clase() != TipoDato.Clase.STRING) {
            reportar(indice.getStart(), "Solo se pueden indexar arreglos o cadenas, pero se intentó indexar un tipo '"
                    + tipo + "'.");
            return TipoDato.ERROR;
        }
        if (!indeterminado(tipoIndice) && !tipoIndice.esNumerico()) {
            reportar(indice.expression().getStart(), "El índice de acceso a un arreglo debe ser de tipo 'integer', pero es de tipo '"
                    + tipoIndice + "'.");
        }
        return tipo.clase() == TipoDato.Clase.ARREGLO ? tipo.tipoElemento() : TipoDato.STRING;
    }

    /** Valida que el nombre invocado sea una función y que los argumentos calcen con sus parámetros. */
    private TipoDato tipoDeLlamada(Token nombre, CallExprContext llamada) {
        Optional<Simbolo> encontrado = tabla.buscar(nombre.getText());
        if (encontrado.isEmpty()) {
            visitarArgumentos(llamada);
            reportar(nombre, "La función '" + nombre.getText() + "' no está declarada en este ámbito.");
            return TipoDato.ERROR;
        }

        Simbolo simbolo = encontrado.get();
        if (simbolo.categoria() != CategoriaSimbolo.FUNCION) {
            visitarArgumentos(llamada);
            reportar(nombre, "'" + nombre.getText() + "' es " + descripcionCategoria(simbolo.categoria())
                    + " y no puede invocarse como función.");
            return TipoDato.ERROR;
        }

        registrarResolucion(nombre);
        List<ExpressionContext> argumentos = argumentosDe(llamada);
        List<TipoDato> tipos = visitarArgumentos(llamada);
        List<Simbolo.Parametro> parametros = simbolo.parametros();

        if (argumentos.size() != parametros.size()) {
            reportar(nombre, "La función '" + nombre.getText() + "' espera " + parametros.size()
                    + " argumento(s), pero se recibieron " + argumentos.size() + ".");
            return simbolo.tipoRetorno();
        }

        for (int i = 0; i < parametros.size(); i++) {
            Simbolo.Parametro parametro = parametros.get(i);
            exigirAsignable(parametro.tipo(), tipos.get(i), argumentos.get(i), parametro.nombre(),
                    "El parámetro '" + parametro.nombre() + "' de '" + nombre.getText() + "'");
        }
        return simbolo.tipoRetorno();
    }

    private static List<ExpressionContext> argumentosDe(CallExprContext ctx) {
        return ctx.arguments() == null ? List.of() : ctx.arguments().expression();
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
        registrarResolucion(identificador);
        if (simbolo.categoria() == CategoriaSimbolo.FUNCION) {
            reportar(identificador, "La función '" + identificador.getText()
                    + "' no puede utilizarse como un valor; debe invocarse con paréntesis.");
            return TipoDato.ERROR;
        }
        if (simbolo.categoria() == CategoriaSimbolo.CLASE) {
            reportar(identificador, "La clase '" + identificador.getText()
                    + "' no puede utilizarse como un valor; use 'new " + identificador.getText()
                    + "(...)' para crear una instancia.");
            return TipoDato.ERROR;
        }
        if (!simbolo.inicializado()) {
            reportar(identificador, "La variable '" + identificador.getText()
                    + "' se usa antes de asignarle un valor.");
        }
        return simbolo.tipo();
    }

    @Override
    public TipoDato visitNewExpr(NewExprContext ctx) {
        Token nombreClaseToken = ctx.Identifier().getSymbol();
        String nombreClase = nombreClaseToken.getText();
        Optional<Simbolo> optClase = tabla.buscar(nombreClase);

        List<TipoDato> tiposArgumentos = new ArrayList<>();
        List<ExpressionContext> argumentos = ctx.arguments() == null ? List.of() : ctx.arguments().expression();
        for (ExpressionContext arg : argumentos) {
            tiposArgumentos.add(visit(arg));
        }

        if (optClase.isEmpty()) {
            reportar(nombreClaseToken, "La clase '" + nombreClase + "' no está declarada en este ámbito.");
            return TipoDato.ERROR;
        }

        Simbolo simbolo = optClase.get();
        if (simbolo.categoria() != CategoriaSimbolo.CLASE) {
            reportar(nombreClaseToken, "'" + nombreClase + "' es " + descripcionCategoria(simbolo.categoria())
                    + " y no se puede instanciar con 'new'.");
            return TipoDato.ERROR;
        }

        // Buscar constructor explícito (llamado 'constructor' o con el nombre de la clase)
        Optional<Simbolo> constructor = simbolo.buscarMiembro("constructor", tabla);
        if (constructor.isEmpty()) {
            constructor = simbolo.buscarMiembro(nombreClase, tabla);
        }

        if (constructor.isPresent() && constructor.get().categoria() == CategoriaSimbolo.FUNCION) {
            Simbolo constrSim = constructor.get();
            List<Simbolo.Parametro> params = constrSim.parametros();
            if (argumentos.size() != params.size()) {
                reportar(nombreClaseToken, "El constructor de '" + nombreClase + "' espera " + params.size()
                        + " argumento(s), pero se recibieron " + argumentos.size() + ".");
            } else {
                for (int i = 0; i < params.size(); i++) {
                    Simbolo.Parametro param = params.get(i);
                    exigirAsignable(param.tipo(), tiposArgumentos.get(i), argumentos.get(i), param.nombre(),
                            "El argumento para el parámetro '" + param.nombre() + "' del constructor de '" + nombreClase + "'");
                }
            }
        } else {
            // Constructor por defecto sin argumentos
            if (!argumentos.isEmpty()) {
                reportar(nombreClaseToken, "La clase '" + nombreClase
                        + "' no define un constructor con parámetros y espera 0 argumentos, pero se recibieron "
                        + argumentos.size() + ".");
            }
        }

        return TipoDato.clase(nombreClase);
    }

    @Override
    public TipoDato visitThisExpr(ThisExprContext ctx) {
        if (claseActual == null) {
            reportar(ctx.getStart(), "'this' solo puede usarse dentro del cuerpo de una clase.");
            return TipoDato.ERROR;
        }
        return TipoDato.clase(claseActual.nombre());
    }

    // ------------------------------------------------------------------
    // Control de flujo
    // ------------------------------------------------------------------

    @Override
    public TipoDato visitIfStatement(IfStatementContext ctx) {
        exigirBooleano(visit(ctx.expression()), ctx.expression(), "La condición del 'if'");
        for (BlockContext bloque : ctx.block()) {
            visit(bloque);
        }
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitWhileStatement(WhileStatementContext ctx) {
        exigirBooleano(visit(ctx.expression()), ctx.expression(), "La condición del 'while'");
        visitarCuerpoDeCiclo(ctx.block());
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitDoWhileStatement(DoWhileStatementContext ctx) {
        visitarCuerpoDeCiclo(ctx.block());
        exigirBooleano(visit(ctx.expression()), ctx.expression(), "La condición del 'do-while'");
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitSwitchStatement(SwitchStatementContext ctx) {
        TipoDato selector = visit(ctx.expression());

        switchesActivos++;
        for (SwitchCaseContext caso : ctx.switchCase()) {
            TipoDato tipoCaso = visit(caso.expression());
            if (!indeterminado(selector) && !indeterminado(tipoCaso) && !selector.esCompatibleCon(tipoCaso, tabla)) {
                reportar(caso.expression().getStart(), "El valor del 'case' es de tipo '" + tipoCaso
                        + "' y no puede compararse con el selector, que es de tipo '" + selector + "'.");
            }
            entrarBloque("case");
            resultado.registrarEntorno(caso, tabla.actual());
            recorrerAmbito(caso.statement());
            tabla.salirEntorno();
        }

        if (ctx.defaultCase() != null) {
            entrarBloque("default");
            resultado.registrarEntorno(ctx.defaultCase(), tabla.actual());
            recorrerAmbito(ctx.defaultCase().statement());
            tabla.salirEntorno();
        }
        switchesActivos--;
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitBreakStatement(BreakStatementContext ctx) {
        if (ciclosActivos == 0 && switchesActivos == 0) {
            reportar(ctx.getStart(), "'break' solo puede usarse dentro de un ciclo o de un 'switch'.");
        }
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitContinueStatement(ContinueStatementContext ctx) {
        if (ciclosActivos == 0) {
            reportar(ctx.getStart(), "'continue' solo puede usarse dentro de un ciclo.");
        }
        return TipoDato.VOID;
    }

    @Override
    public TipoDato visitReturnStatement(ReturnStatementContext ctx) {
        Simbolo funcion = funcionesActivas.peek();
        if (funcion == null) {
            if (ctx.expression() != null) {
                visit(ctx.expression());
            }
            reportar(ctx.getStart(), "'return' solo puede usarse dentro de una función.");
            return TipoDato.VOID;
        }

        TipoDato esperado = funcion.tipoRetorno();
        if (ctx.expression() == null) {
            if (esperado.clase() != TipoDato.Clase.VOID) {
                reportar(ctx.getStart(), "La función '" + funcion.nombre() + "' debe devolver un valor de tipo '"
                        + esperado + "'.");
            }
            return TipoDato.VOID;
        }

        TipoDato valor = visit(ctx.expression());
        if (esperado.clase() == TipoDato.Clase.VOID) {
            reportar(ctx.getStart(), "La función '" + funcion.nombre() + "' no declara tipo de retorno,"
                    + " por lo que no puede devolver un valor.");
            return TipoDato.VOID;
        }

        exigirAsignable(esperado, valor, ctx.expression(), funcion.nombre(),
                "El valor devuelto por '" + funcion.nombre() + "'");
        return TipoDato.VOID;
    }

    protected void visitarCuerpoDeCiclo(BlockContext bloque) {
        ciclosActivos++;
        visit(bloque);
        ciclosActivos--;
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
        if (destino == null || indeterminado(valor) || destino.esCompatibleCon(valor, tabla)) {
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

    private void registrarResolucion(Token identificador) {
        tabla.entornoDe(identificador.getText())
                .ifPresent(entorno -> resultado.registrarResolucion(identificador, entorno));
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
