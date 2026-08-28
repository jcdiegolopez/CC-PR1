# Arquitectura del Compilador Compiscript (Léxico, Sintáctico, Semántico e IDE)

Este documento describe la arquitectura modular del compilador e IDE de **Compiscript** (`.cps`), desarrollado en **Java 21** con **ANTLR4** y **Java Swing**.

---

## 1. Visión General del Sistema

El sistema implementa el pipeline clásico de un compilador hasta la fase de análisis semántico, acoplado a una interfaz gráfica de usuario reactiva y desacoplada del motor de análisis.

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
         │
         ▼
 ┌──────────────────────┐
 │    AnalysisResult    │ ──> Transporta errores, ParseTree y nombres de reglas
 └──────────────────────┘
         │
         ├───> ResultsTablePanel (Tabla de diagnósticos y contador de errores)
         └───> SyntaxTreePanel (Explorador visual del ParseTree con JTree)
```

---

## 2. Componentes Principales

### 2.1 Módulo Analyzer (`com.lexsynanalyzer.analyzer`)
- **`LexSynAnalyzer`**: Fachada principal del compilador. Coordina el flujo de análisis (léxico -> sintáctico -> semántico condicionado). Si existen errores léxicos o sintácticos, el análisis semántico no se ejecuta para evitar diagnósticos falsos derivados de un árbol incompleto.
- **`AnalysisResult`**: `record` inmutable que transporta la lista de errores (`AnalysisError`), el `ParseTree` y el arreglo de nombres de reglas gramaticales (`String[]`). Diseñado expresamente sin dependencias de paquetes Swing.
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

---

### 2.3 Módulo GUI (`com.lexsynanalyzer.gui`)
- **`LexSynAnalyzerGui`**: Ventana principal construida en Java Swing con tema oscuro inspirado en editores de código (VS Code Dark). Incluye visor con numeración de líneas, barra de herramientas interactiva, procesamiento asíncrono con `SwingWorker` para no congelar la UI y pestañas inferiores.
- **`ResultsTablePanel`**: Banner de estado dinámico (verde para compilación limpia, rojo para errores) y `JTable` personalizada que lista y clasifica los errores por tipo. Al hacer clic en un registro, resalta automáticamente la línea en el editor.
- **`SyntaxTreePanel`**: Explorador en árbol interactivo (`JTree`) que convierte el `ParseTree` de ANTLR4 en nodos visuales. Aplica resaltado de sintaxis (reglas no terminales en verde azulado, palabras clave en azul, literales en naranja/verde claro, identificadores en azul claro) y ofrece botones para expandir o colapsar nodos.

---

## 3. Desacoplamiento Arquitectónico

1. **Separación Motor - Interfaz:** El paquete `com.lexsynanalyzer.analyzer` y `com.lexsynanalyzer.semantic` no importan ninguna clase de `javax.swing.*` ni `java.awt.*`.
2. **Transporte de Datos:** La GUI consume exclusivamente `AnalysisResult` a través del método público `LexSynAnalyzer.analyze(File)` o `LexSynAnalyzer.analizarTexto(String)`.
3. **Manejo de Errores Semánticos en Cascada:** Al evaluar una expresión con error, el visitor produce `TipoDato.ERROR`. Los operadores o asignaciones que reciben `ERROR` suprimen mensajes derivados para evitar abrumar al usuario con falsos diagnósticos.
