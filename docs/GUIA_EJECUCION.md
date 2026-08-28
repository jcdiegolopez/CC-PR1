# Guía de Compilación y Ejecución — Compiscript Analyzer

Esta guía proporciona los pasos necesarios para compilar el proyecto, ejecutar la suite completa de pruebas automatizadas y lanzar la aplicación gráfica (IDE).

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
- **Pruebas de Regresión de los 8 Casos Oficiales:**
  ```powershell
  mvn test -Dtest=CasosPruebaTest
  ```
- **Pruebas de Tipos, Funciones, Ámbitos y Flujo:**
  ```powershell
  mvn test -Dtest=TiposSemanticosTest,FuncionesSemanticasTest,AmbitosSemanticosTest,FlujoSemanticoTest,TablaSimbolosTest
  ```

---

## 4. Ejecución de la Interfaz Gráfica (IDE)

Para iniciar la aplicación gráfica de escritorio:
```powershell
mvn exec:java
```

### Uso del IDE:
1. **Abrir Archivo:** Haga clic en el botón `Abrir Archivo (.cps)` o navegue en el explorador de archivos. Se incluyen casos de ejemplo en `src/test/resources/casos/`.
2. **Edición:** Modifique el código fuente directamente en el visor con numeración de líneas. Guarde con `Ctrl+S` o con el botón `Guardar`.
3. **Analizar:** Haga clic en `Analizar`. El sistema procesará el código mediante un hilo en segundo plano (`SwingWorker`) para mantener la interfaz fluida.
4. **Pestañas de Diagnósticos:**
   - **Tabla de Errores y Diagnósticos:** Muestra la lista de fallos categorizados (`LÉXICO`, `SINTÁCTICO`, `SEMÁNTICO`). Haga clic en cualquier fila para saltar y resaltar la línea en el editor.
   - **Árbol Sintáctico (ParseTree):** Muestra el árbol jerárquico generado por ANTLR4. Utilice los botones `Expandir Todo` y `Colapsar Todo` para navegar por las reglas y tokens.
5. **Limpiar:** Restablece el editor, la tabla de resultados y el árbol sintáctico.
