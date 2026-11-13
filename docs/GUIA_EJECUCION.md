# Guía de Compilación y Ejecución — Compiscript Analyzer

Esta guía proporciona los pasos necesarios para compilar el proyecto, ejecutar la suite completa de pruebas automatizadas (incluidas la generación de código intermedio y de ensamblador MIPS), lanzar la aplicación gráfica (IDE) y correr el ensamblador generado en MARS 4.5.

---

## 1. Requisitos Previos

- **Java Development Kit (JDK):** Versión 21 o superior.
- **Apache Maven:** Versión 3.8+ instalada y configurada en el `PATH`.
- **MARS 4.5:** ya viene en el repositorio como `tools/Mars4_5.jar`; no hay que instalar nada. Se usa por consola
  desde las pruebas y desde el IDE, y también se puede abrir con doble clic para usar su interfaz.

Para comprobar las versiones instaladas:
```powershell
java -version
mvn -version
```

---

## 2. Compilación del Proyecto

Para compilar la gramática ANTLR4 y generar los archivos `.class` de Java:
```powershell
mvn clean compile
```

---

## 3. Ejecución de Pruebas Automatizadas

El proyecto cuenta con una suite completa de pruebas unitarias y de integración construida con JUnit 5:

### 3.1 Ejecutar toda la batería de pruebas
```powershell
mvn test
```

### 3.2 Ejecutar una clase de prueba específica
- **Pruebas de Clases y POO:**
  ```powershell
  mvn test -Dtest=ClasesSemanticasTest
  ```
- **Pruebas de Arreglos y Foreach:**
  ```powershell
  mvn test -Dtest=ArreglosSemanticosTest
  ```
- **Pruebas de Árbol Sintáctico (GUI):**
  ```powershell
  mvn test -Dtest=SyntaxTreePanelTest
  ```
- **Pruebas de Regresión de los 8 Casos Oficiales (léxico/sintáctico):**
  ```powershell
  mvn test -Dtest=CasosPruebaTest
  ```
- **Pruebas de Regresión de los 7 Casos por Regla Semántica:**
  ```powershell
  mvn test -Dtest=CasosSemanticosTest
  ```
- **Pruebas de Tipos, Funciones, Ámbitos y Flujo:**
  ```powershell
  mvn test -Dtest=TiposSemanticosTest,FuncionesSemanticasTest,AmbitosSemanticosTest,FlujoSemanticoTest,TablaSimbolosTest
  ```

### 3.3 Pruebas de código intermedio (TAC)

Los casos están en `src/test/resources/tac/` como pares `nombre.cps` + `nombre.tac.esperado`: la prueba
genera el TAC del `.cps` y lo compara línea a línea con el esperado. Los archivos `error_*.cps` son
casos fallidos: deben reportar el error y **no** generar TAC.

| Clase de prueba | Qué cubre |
|---|---|
| `GeneradorTACExpresionesTest` | Variables, aritmética, lógicas con cortocircuito, ternario, arreglos, reciclaje de temporales (`(a+b)*(c-d)` usa 2) y que generar no ejecuta el programa. |
| `GeneradorTACFlujoTest` | `if/else`, `while`, `do-while`, `for`, `foreach`, `switch`, `break`, `continue`, funciones, recursión, funciones anidadas y `try/catch`, con un caso fallido por funcionalidad. |
| `GeneradorTACClasesTest` | Clases, `new`, constructores, `this`, métodos, herencia y despacho estático. |
| `GeneradorTACRegresionTest` | Etiquetas de funciones anidadas y nombres de las variables de `foreach` y `catch`. |
| `SinTacConErroresTest` | Con error léxico, sintáctico o semántico no hay TAC; los 8 casos del proyecto anterior. |
| `AsignadorMemoriaTest` | Tabla de símbolos ampliada: offsets, frames, sombreado y layout de clases. |
| `InstruccionTest`, `ProgramaTACTest`, `TempPoolTest`, `GeneradorEtiquetasTest` | Piezas del generador. |
| `TacYSimbolosPanelTest` | Pestañas de código intermedio y tabla de símbolos. |

```powershell
mvn test -Dtest="GeneradorTAC*Test,SinTacConErroresTest,AsignadorMemoriaTest"
```

Para ver el TAC de un caso nuevo, abra el `.cps` en el IDE (sección 4) y revise la pestaña
*Código Intermedio (TAC)*.

### 3.4 Pruebas de código MIPS

Los casos están en `src/test/resources/mips/` como pares `nombre.cps` + `nombre.salida` (lo que el programa
debe imprimir en MARS). Hay al menos un caso exitoso y uno fallido por funcionalidad de la rúbrica:
`variables`, `aritmetica`, `logicas`, `cortocircuito`, `arreglos`, `matriz`, `error_indice`, `control_if`,
`control_while`, `control_do_while`, `control_for`, `control_ciclos`, `control_switch`, `foreach`,
`foreach_string`, `funciones`, `parametros`, `anidadas`, `recursion_factorial`, `recursion_fib`,
`recursion_mutua`, `traza_fib`, `clases`, `constructor`, `herencia`, `herencia_constructor`, `sobrescritura`,
`try_indice`, `try_div0`, `try_anidado`, `try_con_break_return`, `try_en_funcion`, `division_cero` y
`registros_derrame`. `division_cero` y `error_indice` son errores sin `try`: imprimen el mensaje y terminan
con código 1.

| Clase de prueba | Qué cubre |
|---|---|
| `BateriaMipsTest` | Todos los casos de la carpeta, con el asignador ingenuo y con `getReg`, en `SimuladorMips`. |
| `EjecucionMarsTest` | Los mismos casos en **MARS 4.5 real**: tal como los entrega el IDE (`getReg` + mirilla) y en modo ingenuo. Se salta si falta `tools/Mars4_5.jar`. |
| `MirillaTest`, `MirillaMipsTest` | Cada regla de la mirilla (entrada → salida esperada) y cada caso optimizado imprime lo mismo. |
| `ProgramasAleatoriosTest` | 300 programas aleatorios contra un intérprete de referencia, con y sin mirilla. |
| `GetRegTest`, `VidaTest`, `BloquesBasicosTest` | `getReg`, variables vivas, bloques básicos y derrame. |
| `PersonaBTest`, `MemoriaMipsTest`, `EjecucionMipsTest` | Frames, llamadas, excepciones, traza, arreglos, objetos y herencia. |
| `SinAsmConErroresTest` | Con error léxico, sintáctico o semántico no hay TAC ni ensamblador. |
| `MipsPanelTest` | Pestaña MIPS: mostrar, guardar, ver sin optimizar y ejecutar en MARS. |

```powershell
mvn test -Dtest="BateriaMipsTest,EjecucionMarsTest,Mirilla*Test"
```

Cada prueba deja el `.asm` en `target/asm/<caso>_<modo>.asm` (y `_opt.asm` el optimizado). Para correrlos
todos en MARS desde la terminal y compararlos con su `.salida`:
```bash
bash tools/probar_mars.sh
```

Para agregar un caso: cree `nombre.cps` y `nombre.salida` en `src/test/resources/mips/`; las pruebas de la
batería lo toman solas. Si el nombre empieza con `traza_`, se compila en modo traza (abajo). Si termina con un
error sin `try`, agréguelo a `CasosMips.TERMINAN_CON_ERROR` (código de salida 1).

---

## 4. Ejecución de la Interfaz Gráfica (IDE)

Para iniciar la aplicación gráfica de escritorio:
```powershell
mvn exec:java
```

### Uso del IDE:
1. **Abrir Archivo:** Haga clic en el botón `Abrir Archivo (.cps)` o navegue en el explorador de archivos. Se incluyen casos de ejemplo en `src/test/resources/casos/` y casos de código intermedio en `src/test/resources/tac/`.
2. **Edición:** Modifique el código fuente directamente en el visor con numeración de líneas. Guarde con `Ctrl+S` o con el botón `Guardar`.
3. **Analizar:** Haga clic en `Analizar`. El sistema procesará el código mediante un hilo en segundo plano (`SwingWorker`) para mantener la interfaz fluida.
4. **Pestañas de Diagnósticos:**
   - **Tabla de Errores y Diagnósticos:** Muestra la lista de fallos categorizados (`LÉXICO`, `SINTÁCTICO`, `SEMÁNTICO`). Haga clic en cualquier fila para saltar y resaltar la línea en el editor.
   - **Árbol Sintáctico (ParseTree):** Muestra el árbol jerárquico generado por ANTLR4. Utilice los botones `Expandir Todo` y `Colapsar Todo` para navegar por las reglas y tokens.
   - **Código Intermedio (TAC):** Muestra el código de tres direcciones con numeración de líneas; las cabeceras de función, etiquetas y saltos van en colores distintos. Si el programa tiene cualquier error léxico, sintáctico o semántico, la pestaña indica *"No se generó código intermedio porque el programa tiene errores."* El programa nunca se ejecuta.
   - **Tabla de Símbolos / Registros de Activación:** Cada variable, parámetro y campo con su ámbito, nombre en el TAC, categoría (`GLOBAL`, `LOCAL`, `PARAM`, `CAMPO`), tipo, offset y tamaño. Cada función muestra el tamaño final de su frame y cada clase el tamaño de una instancia.
   - **Código Ensamblador (MIPS):** El `.asm` para MARS 4.5, con numeración de líneas y colores (etiquetas, directivas, saltos, strings y comentarios). La línea superior resume la mirilla (cuántas instrucciones quitó y qué reglas aplicó).
     - `Ver sin optimizar` / `Ver optimizado`: alterna con el ensamblador anterior a la mirilla.
     - `Copiar`: copia al portapapeles la versión que se está viendo.
     - `Ejecutar en MARS`: corre en MARS 4.5 la versión que se está viendo y muestra su salida en un panel debajo del código, con el código de salida y el tiempo. Si el programa se cicla, MARS se detiene a 1 000 000 de instrucciones y el panel lo indica. El botón se deshabilita si no encuentra `tools/Mars4_5.jar` (abra el IDE desde la carpeta del proyecto).
     - `Guardar .asm`: guarda la versión que se está viendo en UTF-8, con el nombre del `.cps`.
     - Con errores, la pestaña indica *"No se generó código ensamblador porque el programa tiene errores."*
5. **Limpiar:** Restablece el editor, la tabla de resultados, el árbol sintáctico y las pestañas de código intermedio, tabla de símbolos y ensamblador.

La sintaxis del TAC y sus convenciones están en [`documentacion/LENGUAJE_INTERMEDIO.md`](../documentacion/LENGUAJE_INTERMEDIO.md); el ensamblador, en [`documentacion/CODIGO_MIPS.md`](../documentacion/CODIGO_MIPS.md).

---

## 5. Ejecución en MARS 4.5

**Desde la terminal** (desde la raíz del repositorio):
```bash
java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -jar tools/Mars4_5.jar nc sm 1000000 programa.asm
```
- `nc`: sin el aviso de copyright. `sm`: empieza en `main`. `1000000`: tope de instrucciones, por si el programa se cicla.
- `-Dfile.encoding=UTF-8`: las tildes de los mensajes (`Índice fuera de rango`) salen bien.
- `-Djava.awt.headless=true`: evita que MARS falle al iniciar si hay un `DISPLAY` que no se puede abrir (SSH, contenedores).
- El código de salida es 0 si el programa terminó bien y 1 si hubo un error sin `try` (división entre cero o índice fuera de rango).

**Con la interfaz de MARS:** abra `tools/Mars4_5.jar`, cargue el `.asm` guardado desde el IDE (`File → Open`), ensamble (`F3`) y ejecute (`F5`). En `Settings` active *Initialize Program Counter to global 'main' if defined*.

**Modo traza** (árbol de activación): el generador puede imprimir `-> función` al entrar y `<- función` al salir, con sangría por profundidad. El IDE no lo activa; se genera desde código con `new GeneradorMIPS(layout, FabricaRegistros.GETREG, true)`. El caso `traza_fib` ya lo usa: después de `mvn test`, corra en MARS `target/asm/traza_fib_getreg.asm`.
