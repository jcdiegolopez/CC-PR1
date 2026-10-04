# Arquitectura del Compilador Compiscript (Léxico, Sintáctico, Semántico, Código Intermedio e IDE)

Este documento describe la arquitectura modular del compilador e IDE de **Compiscript** (`.cps`), desarrollado en **Java 21** con **ANTLR4** y **Java Swing**.

---

## 1. Visión General del Sistema

El sistema implementa el pipeline clásico de un compilador hasta la generación de **código intermedio de tres direcciones (TAC)**, acoplado a una interfaz gráfica de usuario reactiva y desacoplada del motor de análisis. No se ejecuta el programa ni se genera código objeto: el generador solo produce texto TAC.

```
 Código Fuente (.cps)
         │
         ▼
 ┌──────────────────────┐
 │  LexSynAnalyzerLexer │ ──> CapturingErrorListener (TipoError.LEXICO)
 └──────────────────────┘
         │ Tokens
         ▼
 ┌──────────────────────┐
 │ LexSynAnalyzerParser │ ──> CapturingErrorListener (TipoError.SINTACTICO)
 └──────────────────────┘
         │ ParseTree
         ▼
 ┌──────────────────────┐
 │ AnalizadorSemantico  │ ──> ErrorSemanticoReporter (TipoError.SEMANTICO)
 │   (AST / Visitor)    │ <── TablaSimbolos (Entornos anidados)
 └──────────────────────┘
         │ ResultadoSemantico (tipo por nodo, entorno por nodo, resolución de identificadores)
         │            ── solo si no hubo ningún error ──
         ▼
 ┌──────────────────────┐
 │   AsignadorMemoria   │ ──> Layout: direcciones, registros de activación, layout de clases
 └──────────────────────┘
         │
         ▼
 ┌──────────────────────┐      ExprTAC     (A) variables, expresiones, arreglos, print
 │     GeneradorTAC     │ ──>  ControlTAC  (B) if, ciclos, switch, break/continue
 │   (Visitor raíz)     │      FuncionTAC  (B) funciones, llamadas, recursión, try/catch
 └──────────────────────┘      ClaseTAC    (C) clases, new, this, métodos, herencia
         │ ProgramaTAC
         ▼
 ┌──────────────────────┐
 │    AnalysisResult    │ ──> errores, ParseTree, nombres de reglas, TAC y Layout
 └──────────────────────┘
         │
         ├───> ResultsTablePanel (Tabla de diagnósticos y contador de errores)
         ├───> SyntaxTreePanel   (Explorador visual del ParseTree con JTree)
         ├───> TacPanel          (Código intermedio generado)
         └───> SimbolosPanel     (Tabla de símbolos con offsets, frames y clases)
```

---

## 2. Componentes Principales

### 2.1 Módulo Analyzer (`com.lexsynanalyzer.analyzer`)
- **`LexSynAnalyzer`**: Fachada principal del compilador. Coordina el flujo (léxico -> sintáctico -> semántico -> memoria -> TAC). Si existen errores léxicos o sintácticos, el análisis semántico no se ejecuta para evitar diagnósticos falsos derivados de un árbol incompleto. Si existe **cualquier** error, no se asigna memoria ni se genera TAC.
- **`AnalysisResult`**: `record` inmutable que transporta la lista de errores (`AnalysisError`), el `ParseTree`, el arreglo de nombres de reglas gramaticales (`String[]`), el `ProgramaTAC` (vacío si hubo errores) y el `Layout` de memoria (`null` si hubo errores). Conserva los constructores de 1 y 3 argumentos del proyecto anterior. Diseñado expresamente sin dependencias de paquetes Swing.
- **`AnalysisError`**: `record` con `tipo` (`LEXICO`, `SINTACTICO`, `SEMANTICO`), `linea`, `columna`, `simbolo` y `descripcion` en español.
- **`TipoError`**: Enum con los 3 tipos de fallos detectables.
- **`CapturingErrorListener`**: Listener de ANTLR4 que captura y normaliza las posiciones de los errores en base 1.
- **`MensajesEspanol`**: Traductor de diagnósticos de ANTLR4 a lenguaje natural en español.

---

### 2.2 Módulo Semántico (`com.lexsynanalyzer.semantic`)
- **`AnalizadorSemantico`**: Subclase de `LexSynAnalyzerBaseVisitor<TipoDato>`. Implementa el recorrido del `ParseTree` calculando tipos sintetizados y manteniendo entornos léxicos heredados.
  - **Fase de declaraciones anticipadas:** Antes de analizar las sentencias de un ámbito, pre-registra funciones y clases para permitir recursión y referencias hacia adelante.
  - **Validación de tipos y operadores:** Aritmética (`+`, `-`, `*`, `/`, `%`), lógica (`&&`, `||`, `!`), relacional (`<`, `<=`, `>`, `>=`), igualdad (`==`, `!=`) y ternario (`?:`).
  - **Control de flujo:** `if`, `while`, `do-while`, `for`, `foreach`, `switch-case`, `break`, `continue`, `return`, `try-catch` y análisis de alcanzabilidad (código inalcanzable tras retornos o saltos).
  - **Estructuras avanzadas (POO):** Clases, herencia simple con detección de ciclos, constructores explícitos e implícitos, instanciación (`new`), resolución de `this`, miembros (atributos y métodos), acceso a propiedades y asignaciones.
  - **Colecciones:** Literales de arreglos homogéneos y multidimensionales (`T[][]`), verificación de índices de tipo `integer`, e iteración `foreach` con ámbito aislado.
- **`TablaSimbolos` y `Entorno`:** Estructura jerárquica de entornos enlazados por puntero al padre. Permite sombreado léxico (*shadowing*) y resolución desde el ámbito más interno hasta el global.
- **`Simbolo`**: Registro de variables, constantes, parámetros, funciones y clases (con su entorno de miembros y enlace a clase padre).
- **`TipoDato`**: Sistema de tipos que modela primitivos (`INTEGER`, `BOOLEAN`, `STRING`, `NULL`, `VOID`), tipos estructurados (`CLASE`, `ARREGLO`) y centinelas (`DESCONOCIDO`, `ERROR`). Incluye verificación de compatibilidad y subtipado mediante herencia.
- **`ResultadoSemantico`**: Lo que el semántico conserva para las fases siguientes: el tipo de cada nodo (`ParseTreeProperty<TipoDato>`, registrado al sobreescribir `visit`), el entorno de cada nodo que abre ámbito (bloque, función, método, clase, `for`, `foreach`, `catch`, `case`), el tipo estático del receptor de cada `obj.miembro` y el entorno donde se resolvió cada identificador de variable.
- **Entornos conservados**: `salirEntorno` no destruye el entorno; además cada `Entorno` registra a sus `hijos()`, por lo que al terminar el análisis queda el árbol completo de ámbitos. Cada entorno guarda las `direcciones` de sus variables, su `tamanoFrame` y su `siguienteOffset`.
- **`Direccion`**: `record` con el nombre TAC, la categoría (`GLOBAL`, `LOCAL`, `PARAM`, `CAMPO`), el offset, el tamaño y el tipo de una variable.
- **`AsignadorMemoria`**: Recorre el árbol de entornos después del semántico y produce un **`Layout`**: offsets de globales (región estática) y de parámetros y locales (en el registro de activación), tamaño base de cada frame, nombre TAC único para variables sombreadas (`x_1`, `x_2`) o con forma de temporal (`t0_v`), etiquetas de función (`f`, `padre_hijo`, `Clase_metodo`) y layout de clases (campos del padre primero, `tamanoInstancia`). Es el único lugar que conoce los tamaños: `integer`/`boolean` = 4 bytes, referencias (`string`, arreglos, objetos) = 8.

---

### 2.3 Módulo de Código Intermedio (`com.lexsynanalyzer.tac`)
- **`GeneradorTAC`**: Visitor raíz, **segundo recorrido** del árbol, separado del semántico. Solo delega: expresiones a `ExprTAC`, flujo de control a `ControlTAC`, funciones y excepciones a `FuncionTAC`, clases a `ClaseTAC`. Emite el código de nivel superior como `function main, N … endfunc` y después los cuerpos de funciones y métodos.
- **`Instruccion`** / **`ProgramaTAC`**: Una instrucción de tres direcciones (`op, arg1, arg2, resultado`) y la lista ordenada de instrucciones. `ProgramaTAC.reservar()`/`fijar()` permiten completar `function f, frameSize` al final, cuando ya se conoce cuántos temporales usó la función.
- **`TempPool`** / **`GeneradorEtiquetas`**: Temporales `t0, t1, …` con reciclaje (se reinician en cada función) y etiquetas `L0, L1, …` únicas por programa.
- **`ResolvedorMemoria`** / **`ResolvedorLayout`**: Interfaz por la que el generador consulta nombres TAC y tamaños de frame; `ResolvedorLayout` la implementa con el `Layout` de `AsignadorMemoria`. El generador nunca decide nombres ni tamaños por su cuenta.
- **`ExtensionExpr`**: Punto donde `ExprTAC` delega llamadas a función (`FuncionTAC`) y `new`/`this`/llamadas a método (`ClaseTAC`).
- **`ClaseTAC`**: Emite los métodos como `Clase_metodo` con `this` como parámetro 0, `new` (reserva, inicializadores de campos del padre primero y llamada al constructor propio o heredado) y llamadas a método con **despacho estático** por el tipo declarado.

Ver el detalle de cada instrucción y convención en [`documentacion/LENGUAJE_INTERMEDIO.md`](../documentacion/LENGUAJE_INTERMEDIO.md).

---

### 2.4 Módulo GUI (`com.lexsynanalyzer.gui`)
- **`LexSynAnalyzerGui`**: Ventana principal construida en Java Swing con tema oscuro inspirado en editores de código (VS Code Dark). Incluye visor con numeración de líneas, barra de herramientas interactiva, procesamiento asíncrono con `SwingWorker` para no congelar la UI y pestañas inferiores.
- **`ResultsTablePanel`**: Banner de estado dinámico (verde para compilación limpia, rojo para errores) y `JTable` personalizada que lista y clasifica los errores por tipo. Al hacer clic en un registro, resalta automáticamente la línea en el editor.
- **`SyntaxTreePanel`**: Explorador en árbol interactivo (`JTree`) que convierte el `ParseTree` de ANTLR4 en nodos visuales. Aplica resaltado de sintaxis (reglas no terminales en verde azulado, palabras clave en azul, literales en naranja/verde claro, identificadores en azul claro) y ofrece botones para expandir o colapsar nodos.
- **`TacPanel`** (pestaña *Código Intermedio (TAC)*): Muestra el programa TAC con numeración de líneas y colores por tipo de línea (cabeceras de función, etiquetas, saltos y excepciones). Si el programa tiene errores muestra: *"No se generó código intermedio porque el programa tiene errores."*
- **`SimbolosPanel`** (pestaña *Tabla de Símbolos / Registros de Activación*): Tabla con el ámbito (región estática, cada `function` con su tamaño final de frame incluidos los temporales, cada `class` con su tamaño de instancia), nombre fuente, nombre TAC, categoría, tipo, offset y tamaño de cada variable, parámetro y campo.

---

## 3. Desacoplamiento Arquitectónico

1. **Separación Motor - Interfaz:** Los paquetes `com.lexsynanalyzer.analyzer`, `com.lexsynanalyzer.semantic` y `com.lexsynanalyzer.tac` no importan ninguna clase de `javax.swing.*` ni `java.awt.*`.
2. **Transporte de Datos:** La GUI consume exclusivamente `AnalysisResult` a través del método público `LexSynAnalyzer.analyze(File)` o `LexSynAnalyzer.analizarTexto(String)`.
3. **Manejo de Errores Semánticos en Cascada:** Al evaluar una expresión con error, el visitor produce `TipoDato.ERROR`. Los operadores o asignaciones que reciben `ERROR` suprimen mensajes derivados para evitar abrumar al usuario con falsos diagnósticos.
4. **Sin código intermedio con errores:** La generación depende de un árbol completo y de tipos válidos, por lo que solo ocurre con cero errores léxicos, sintácticos y semánticos. Así los errores del proyecto anterior conservan su comportamiento (no se repiten ni entran en ciclo) y el TAC nunca refleja un programa inválido.
5. **Dueños por archivo:** Cada clase del paquete `tac` tiene un único responsable (A: expresiones y temporales; B: flujo, funciones y excepciones; C: clases, memoria y GUI), lo que permitió trabajar en paralelo sin conflictos.
