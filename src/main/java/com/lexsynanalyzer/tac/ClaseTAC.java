package com.lexsynanalyzer.tac;

import com.lexsynanalyzer.parser.LexSynAnalyzerBaseVisitor;
import com.lexsynanalyzer.parser.LexSynAnalyzerParser.*;
import com.lexsynanalyzer.semantic.Layout;
import com.lexsynanalyzer.semantic.TipoDato;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clases y objetos: cuerpos de métodos ({@code Clase_metodo}), {@code new} con inicializadores de
 * campos y constructor, {@code this} y llamadas a método.
 *
 * <p>{@code this} es el parámetro 0 implícito de todo método. El método se resuelve con el tipo
 * declarado del objeto (despacho estático): una variable {@code Animal} que guarda un {@code Perro}
 * llama a {@code Animal_hablar}.
 */
public final class ClaseTAC extends LexSynAnalyzerBaseVisitor<Void> implements ExtensionExpr {

    private final ProgramaTAC programa;
    private final TempPool temps;
    private final ResolvedorMemoria memoria;
    private final Layout layout;
    private final GeneradorTAC generador;
    private ExprTAC expr;
    private Map<String, ClassDeclarationContext> declaraciones;

    public ClaseTAC(ProgramaTAC programa, TempPool temps, ResolvedorMemoria memoria, Layout layout,
                    GeneradorTAC generador) {
        this.programa = programa;
        this.temps = temps;
        this.memoria = memoria;
        this.layout = layout;
        this.generador = generador;
    }

    public void setExpr(ExprTAC expr) {
        this.expr = expr;
    }

    // ------------------------------------------------------------------
    // Declaración: los métodos se emiten después de main
    // ------------------------------------------------------------------

    @Override
    public Void visitClassDeclaration(ClassDeclarationContext ctx) {
        String clase = ctx.Identifier(0).getText();
        for (ClassMemberContext miembro : ctx.classMember()) {
            FunctionDeclarationContext metodo = miembro.functionDeclaration();
            if (metodo != null) {
                String etiqueta = clase + "_" + metodo.Identifier().getText();
                generador.diferir(() -> emitirMetodo(metodo, etiqueta));
            }
        }
        return null;
    }

    private void emitirMetodo(FunctionDeclarationContext metodo, String etiqueta) {
        int cabecera = programa.reservar();
        temps.reiniciar();
        generador.visitBlock(metodo.block());
        int frame = memoria.tamanoBase(etiqueta) + temps.maximoSimultaneos() * 4;
        programa.fijar(cabecera, Instruccion.funcion(etiqueta, frame));
        programa.emit(Instruccion.finFuncion());
    }

    // ------------------------------------------------------------------
    // new / this
    // ------------------------------------------------------------------

    @Override
    public String atomo(PrimaryAtomContext atomo) {
        if (atomo instanceof ThisExprContext) {
            return "this";
        }
        return instanciar((NewExprContext) atomo);
    }

    /**
     * {@code t = new Clase, tamaño}; luego los inicializadores de campos (padre primero) y, si la
     * jerarquía tiene constructor, {@code param t}, los argumentos y {@code call}.
     */
    private String instanciar(NewExprContext ctx) {
        String clase = ctx.Identifier().getText();
        Layout.ClaseLayout distribucion = requerirLayout().clase(clase)
                .orElseThrow(() -> new IllegalStateException("Clase sin layout: " + clase));

        String objeto = temps.nuevo();
        programa.emit(Instruccion.nuevoObjeto(objeto, clase, distribucion.tamanoInstancia()));
        for (Layout.ClaseLayout nivel : layout.jerarquia(clase)) {
            inicializarCampos(objeto, declaracion(ctx, nivel.nombre()));
        }

        layout.etiquetaConstructor(clase).ifPresent(constructor -> {
            int cantidad = pasarArgumentos(objeto, ctx.arguments());
            programa.emit(Instruccion.llamar(constructor, cantidad + 1));
        });
        return objeto;
    }

    private void inicializarCampos(String objeto, ClassDeclarationContext declaracion) {
        if (declaracion == null) {
            return;
        }
        for (ClassMemberContext miembro : declaracion.classMember()) {
            if (miembro.variableDeclaration() != null && miembro.variableDeclaration().initializer() != null) {
                VariableDeclarationContext campo = miembro.variableDeclaration();
                asignarCampo(objeto, campo.Identifier().getText(), campo.initializer().expression());
            } else if (miembro.constantDeclaration() != null) {
                ConstantDeclarationContext campo = miembro.constantDeclaration();
                asignarCampo(objeto, campo.Identifier().getText(), campo.expression());
            }
        }
    }

    private void asignarCampo(String objeto, String campo, ExpressionContext valor) {
        String v = expr.visit(valor);
        programa.emit(Instruccion.escribirCampo(objeto, campo, v));
        temps.liberar(v);
    }

    // ------------------------------------------------------------------
    // Llamadas a método
    // ------------------------------------------------------------------

    /** {@code param objeto}, los argumentos y {@code call Clase_metodo, n + 1}. */
    @Override
    public String metodo(String objeto, String metodo, CallExprContext llamada) {
        String etiqueta = etiquetaMetodo(metodo, llamada);
        int cantidad = pasarArgumentos(objeto, llamada.arguments());
        temps.liberar(objeto);
        if (esSentencia(llamada)) {
            programa.emit(Instruccion.llamar(etiqueta, cantidad + 1));
            return null;
        }
        String destino = temps.nuevo();
        programa.emit(Instruccion.llamar(destino, etiqueta, cantidad + 1));
        return destino;
    }

    private String etiquetaMetodo(String metodo, CallExprContext llamada) {
        LeftHandSideContext lhs = (LeftHandSideContext) llamada.getParent();
        int posicion = lhs.suffixOp().indexOf(llamada);
        TipoDato receptor = requerirLayout().semantico().tipoReceptor(lhs.suffixOp(posicion - 1));
        if (receptor == null || receptor.clase() != TipoDato.Clase.CLASE) {
            throw new IllegalStateException("No se conoce la clase del objeto que recibe '" + metodo + "'");
        }
        String clase = receptor.nombreClase();
        return layout.etiquetaMetodo(clase, metodo).orElse(clase + "_" + metodo);
    }

    /**
     * Evalúa los argumentos y luego emite {@code param this} y un {@code param} por argumento. El
     * objeto no se libera mientras se arman los argumentos. Devuelve la cantidad de argumentos.
     */
    private int pasarArgumentos(String objeto, ArgumentsContext argumentos) {
        List<String> valores = new ArrayList<>();
        if (argumentos != null) {
            for (ExpressionContext argumento : argumentos.expression()) {
                valores.add(expr.visit(argumento));
            }
        }
        programa.emit(Instruccion.param(objeto));
        for (String valor : valores) {
            programa.emit(Instruccion.param(valor));
            temps.liberar(valor);
        }
        return valores.size();
    }

    /** {@code obj.m();}: la llamada es lo único que hay en la sentencia, su valor no se usa. */
    private static boolean esSentencia(CallExprContext llamada) {
        LeftHandSideContext lhs = (LeftHandSideContext) llamada.getParent();
        if (lhs.suffixOp().indexOf(llamada) != lhs.suffixOp().size() - 1) {
            return false;
        }
        ParseTree actual = lhs;
        while (actual.getParent() != null && !(actual.getParent() instanceof ExpressionStatementContext)) {
            if (actual.getParent().getChildCount() != 1) {
                return false;
            }
            actual = actual.getParent();
        }
        return actual.getParent() != null;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Layout requerirLayout() {
        if (layout == null) {
            throw new UnsupportedOperationException(
                    "Las clases requieren el Layout de AsignadorMemoria: use new GeneradorTAC(layout)");
        }
        return layout;
    }

    /** Declaración de {@code clase} en el programa; se indexan todas la primera vez que se pide una. */
    private ClassDeclarationContext declaracion(ParserRuleContext desde, String clase) {
        if (declaraciones == null) {
            declaraciones = new HashMap<>();
            ParseTree raiz = desde;
            while (raiz.getParent() != null) {
                raiz = raiz.getParent();
            }
            indexar(raiz);
        }
        return declaraciones.get(clase);
    }

    private void indexar(ParseTree nodo) {
        if (nodo instanceof ClassDeclarationContext clase) {
            declaraciones.putIfAbsent(clase.Identifier(0).getText(), clase);
        }
        for (int i = 0; i < nodo.getChildCount(); i++) {
            indexar(nodo.getChild(i));
        }
    }
}
