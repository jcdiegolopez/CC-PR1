# Guía de Compilación y Ejecución — Compiscript Analyzer

Esta guía proporciona los pasos necesarios para compilar el proyecto, ejecutar la suite completa de pruebas automatizadas (incluida la generación de código intermedio) y lanzar la aplicación gráfica (IDE).

---

## 1. Requisitos Previos

- **Java Development Kit (JDK):** Versión 21 o superior.
- **Apache Maven:** Versión 3.8+ instalada y configurada en el `PATH`.

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
5. **Limpiar:** Restablece el editor, la tabla de resultados, el árbol sintáctico y las pestañas de código intermedio y tabla de símbolos.

La sintaxis del TAC y sus convenciones están en [`documentacion/LENGUAJE_INTERMEDIO.md`](../documentacion/LENGUAJE_INTERMEDIO.md).
