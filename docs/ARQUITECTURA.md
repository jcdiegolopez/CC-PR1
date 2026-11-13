# Arquitectura del Compilador Compiscript (Léxico, Sintáctico, Semántico, Código Intermedio, MIPS e IDE)

Este documento describe la arquitectura modular del compilador e IDE de **Compiscript** (`.cps`), desarrollado en **Java 21** con **ANTLR4** y **Java Swing**.

---

## 1. Visión General del Sistema

El sistema implementa el pipeline completo de un compilador: análisis, **código intermedio de tres direcciones (TAC)** y **ensamblador MIPS32** para el simulador **MARS 4.5**, acoplado a una interfaz gráfica reactiva y desacoplada del motor. El ensamblador se genera **solo a partir del TAC** (y del `Layout` de memoria): el backend nunca mira el árbol sintáctico. El IDE no ejecuta el programa; el `.asm` se guarda y se corre en MARS.

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
         │ ProgramaTAC (+ Layout)
         ▼
 ┌──────────────────────┐      TraductorExpr        (A) expresiones, saltos, print, strings
 │     GeneradorMIPS    │ ──>  MarcoActivacion,     (B) frames, param/call/return,
 │ (despacho por op TAC)│      TraductorLlamadas,       try/catch
 └──────────────────────┘      TraductorExcepciones
         │                     TraductorMemoria     (C) arreglos, objetos, campos
         │                     AsignadorRegistros: GetReg (A) | RegistrosIngenuos
         │ ProgramaMips (sin optimizar)
         ▼
 ┌──────────────────────┐
 │       Mirilla        │ ──> 10 reglas hasta punto fijo + veces que se aplicó cada una (C)
 └──────────────────────┘
         │ ProgramaMips optimizado
         ▼
 ┌──────────────────────┐
 │    AnalysisResult    │ ──> errores, ParseTree, reglas, TAC, Layout, MIPS (optimizado y sin
 └──────────────────────┘     optimizar), estadísticas de la mirilla y falloMips
         │
         ├───> ResultsTablePanel (Tabla de diagnósticos y contador de errores)
         ├───> SyntaxTreePanel   (Explorador visual del ParseTree con JTree)
         ├───> TacPanel          (Código intermedio generado)
         ├───> SimbolosPanel     (Tabla de símbolos con offsets, frames y clases)
         └───> MipsPanel         (Ensamblador, "Ver sin optimizar", mirilla, Guardar .asm)  ──>  MARS 4.5
```

---

## 2. Componentes Principales

### 2.1 Módulo Analyzer (`com.lexsynanalyzer.analyzer`)
- **`LexSynAnalyzer`**: Fachada principal del compilador. Coordina el flujo (léxico -> sintáctico -> semántico -> memoria -> TAC -> MIPS -> mirilla). Si existen errores léxicos o sintácticos, el análisis semántico no se ejecuta para evitar diagnósticos falsos derivados de un árbol incompleto. Si existe **cualquier** error, no se asigna memoria ni se genera TAC ni ensamblador. El MIPS se genera con `FabricaRegistros.GETREG`. Si el backend lanza una excepción con un programa válido (por ejemplo un closure, que no soporta), se conservan el análisis y el TAC y la causa queda en `falloMips`.
- **`AnalysisResult`**: `record` inmutable que transporta la lista de errores (`AnalysisError`), el `ParseTree`, el arreglo de nombres de reglas gramaticales (`String[]`), el `ProgramaTAC` (vacío si hubo errores), el `Layout` de memoria (`null` si hubo errores), el `ProgramaMips` optimizado (`mips`) y sin optimizar (`mipsSinOptimizar`), un `Map<String, Integer>` con las veces que se aplicó cada regla de la mirilla y `falloMips`. Conserva los constructores de 1, 3 y 5 argumentos de los proyectos anteriores. Diseñado expresamente sin dependencias de paquetes Swing.
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

### 2.4 Módulo de Código MIPS (`com.lexsynanalyzer.mips`)
- **`GeneradorMIPS`**: Raíz del backend. Recibe solo el `ProgramaTAC` y el `Layout`. Parte el TAC en funciones (`function` … `endfunc`), crea un `AsignadorRegistros` por función y **delega cada instrucción en el `Traductor` registrado para su `op`**, igual que `GeneradorTAC` delega por tipo de nodo. Escribe cada línea TAC como comentario en el `.asm` y al final emite las rutinas del runtime que algún traductor pidió con `requerir`.
- **`InstruccionMips`** / **`ProgramaMips`**: Una línea de `.text` guardada estructurada (tipo, mnemónico, operandos, comentario) y no como texto, para que la mirilla la pueda analizar. El programa tiene `.data` (strings deduplicados `str0, str1…`, globales `g_x`, tablas del runtime) y `.text`, y `toString()` produce el `.asm` para MARS.
- **`AsignadorRegistros`**: Interfaz por la que todo traductor pide registros (`leer`, `paraResultado`, `definido`, `finDeBloque`, `antesDeLlamada`, `antesDePosibleExcepcion`); nadie escribe `$t3` a mano. Dos implementaciones: **`GetReg`** (algoritmo `getReg` con descriptores de registro y de dirección, derrame y variables vivas de `BloquesBasicos` + `Vida`) y **`RegistrosIngenuos`** (carga y guarda en cada instrucción; backend de referencia siempre correcto). `$t0`–`$t7` los asigna `getReg`; `$t8`/`$t9` son de uso interno.
- **`ResolvedorUbicaciones`** / **`Ubicaciones`**: Dónde vive cada nombre TAC: global (`g_x`), local o parámetro (`offset($fp)`), temporal en el frame, literal o string.
- **Traductores** (uno por dueño): `TraductorExpr` (asignación, aritmética, relacionales, lógicas, saltos, `print`, `concat`/`streq`); `MarcoActivacion`, `TraductorLlamadas` y `TraductorExcepciones` (prólogo/epílogo, `param`/`call`/`return`, pila de manejadores de `try`/`catch`); `TraductorMemoria` (arreglos en el heap como `[longitud][e0][e1]…` con chequeo de límites, objetos de `tamanoInstancia` bytes y campos por el offset `@k` que trae el TAC).
- **Runtime** (`RuntimeCadenas`, `RuntimeErrores`, `RuntimeMemoria`, `RuntimeTraza`): rutinas en MIPS (`__concat`, `__strlen`, `__charat`, `__alloc`, `__lanzar`, `__error_indice`…) que solo usan `$a*`, `$v*`, `$t8` y `$t9`, así llamarlas no obliga a vaciar los registros de `getReg`. Un error sin `try` imprime el mensaje y termina con código 1 (`syscall 17`).
- **`mirilla.Mirilla`** y **`mirilla.Regla*`**: Optimización por mirilla sobre la lista de `InstruccionMips`. Una clase por regla (`move` redundante, `sw`/`lw`, acceso redundante, salto a la siguiente, salto a salto, código inalcanzable, identidades algebraicas, reducción de fuerza, inversión de rama, etiqueta sin uso). Las aplica en orden hasta que una pasada no cambia nada; la ventana nunca cruza etiquetas. Devuelve un programa nuevo (el original sigue disponible) y el conteo por regla.

Ver la convención de registros, la tabla TAC → MIPS, `getReg` paso a paso, el runtime y cada regla de la mirilla con su antes/después en [`documentacion/CODIGO_MIPS.md`](../documentacion/CODIGO_MIPS.md).

---

### 2.5 Módulo GUI (`com.lexsynanalyzer.gui`)
- **`LexSynAnalyzerGui`**: Ventana principal construida en Java Swing con tema oscuro inspirado en editores de código (VS Code Dark). Incluye visor con numeración de líneas, barra de herramientas interactiva, procesamiento asíncrono con `SwingWorker` para no congelar la UI y pestañas inferiores.
- **`ResultsTablePanel`**: Banner de estado dinámico (verde para compilación limpia, rojo para errores) y `JTable` personalizada que lista y clasifica los errores por tipo. Al hacer clic en un registro, resalta automáticamente la línea en el editor.
- **`SyntaxTreePanel`**: Explorador en árbol interactivo (`JTree`) que convierte el `ParseTree` de ANTLR4 en nodos visuales. Aplica resaltado de sintaxis (reglas no terminales en verde azulado, palabras clave en azul, literales en naranja/verde claro, identificadores en azul claro) y ofrece botones para expandir o colapsar nodos.
- **`TacPanel`** (pestaña *Código Intermedio (TAC)*): Muestra el programa TAC con numeración de líneas y colores por tipo de línea (cabeceras de función, etiquetas, saltos y excepciones). Si el programa tiene errores muestra: *"No se generó código intermedio porque el programa tiene errores."*
- **`SimbolosPanel`** (pestaña *Tabla de Símbolos / Registros de Activación*): Tabla con el ámbito (región estática, cada `function` con su tamaño final de frame incluidos los temporales, cada `class` con su tamaño de instancia), nombre fuente, nombre TAC, categoría, tipo, offset y tamaño de cada variable, parámetro y campo.
- **`MipsPanel`** (pestaña *Código Ensamblador (MIPS)*): Muestra el `.asm` optimizado con numeración de líneas y resaltado (etiquetas, directivas, saltos, strings y comentarios). El botón *Ver sin optimizar* alterna con la versión anterior a la mirilla, y una línea resume la mirilla (`Mirilla: 16 instrucciones eliminadas (sw/lw ×4, …)`). *Copiar* y *Guardar .asm* (UTF-8, nombre sugerido a partir del `.cps`) dejan el archivo listo para MARS. Con errores muestra: *"No se generó código ensamblador porque el programa tiene errores."*; si el backend falló con un programa válido, muestra la causa.

---

## 3. Desacoplamiento Arquitectónico

1. **Separación Motor - Interfaz:** Los paquetes `com.lexsynanalyzer.analyzer`, `com.lexsynanalyzer.semantic`, `com.lexsynanalyzer.tac` y `com.lexsynanalyzer.mips` no importan ninguna clase de `javax.swing.*` ni `java.awt.*`.
2. **Transporte de Datos:** La GUI consume exclusivamente `AnalysisResult` a través del método público `LexSynAnalyzer.analyze(File)` o `LexSynAnalyzer.analizarTexto(String)`.
3. **Manejo de Errores Semánticos en Cascada:** Al evaluar una expresión con error, el visitor produce `TipoDato.ERROR`. Los operadores o asignaciones que reciben `ERROR` suprimen mensajes derivados para evitar abrumar al usuario con falsos diagnósticos.
4. **Sin código intermedio ni ensamblador con errores:** La generación depende de un árbol completo y de tipos válidos, por lo que solo ocurre con cero errores léxicos, sintácticos y semánticos. Así los errores del proyecto anterior conservan su comportamiento (no se repiten ni entran en ciclo) y ni el TAC ni el MIPS reflejan nunca un programa inválido.
5. **El backend solo ve el TAC:** `GeneradorMIPS` recibe `ProgramaTAC` + `Layout` y nunca el `ParseTree`. Lo que el TAC no dice por sí mismo (si `+` es suma o concatenación, el offset de un campo) se agregó al TAC como instrucciones tipadas (`concat`, `streq`, `prints`, `charat`, `obj.campo @k`), documentadas en `LENGUAJE_INTERMEDIO.md`.
6. **Un punto de extensión por fase:** `GeneradorTAC` delega por tipo de nodo y `GeneradorMIPS` por `op` TAC; agregar una construcción es registrar un traductor nuevo, sin tocar los de los demás. Los registros se piden siempre a `AsignadorRegistros`, así que `RegistrosIngenuos` y `GetReg` se intercambian sin cambiar ningún traductor.
7. **Dueños por archivo:** Cada clase de los paquetes `tac` y `mips` tiene un único responsable (A: expresiones, temporales, registros y `getReg`; B: flujo, funciones, frames y excepciones; C: clases, memoria, mirilla, MARS y GUI), lo que permitió trabajar en paralelo sin conflictos.

---

## 4. Verificación

- **`SimuladorMips`** (en `src/test`): simulador mínimo del subconjunto de MIPS que se genera, para comprobar la salida de cada programa en cada `mvn test`. Todos los casos de `src/test/resources/mips` (`.cps` + `.salida`) corren con los dos asignadores, con y sin mirilla, y 300 programas aleatorios se comparan contra un intérprete de referencia independiente del compilador.
- **`EjecutorMars`** (en `src/test`): corre los mismos programas en **MARS 4.5 real** desde JUnit (`java -Djava.awt.headless=true -jar tools/Mars4_5.jar nc ae98 se99 sm 1000000 archivo.asm`, tope de 10 s) y compara salida y código de salida. Si `tools/Mars4_5.jar` no está, esas pruebas se saltan.
- **`tools/probar_mars.sh`**: corre en MARS todos los `.asm` que deja `mvn test` en `target/asm/`.
