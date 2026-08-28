# Video mostrando el funcionamiento del analizador : https://canva.link/wpopgcpjddslcuu


# Analizador Léxico, Sintáctico y Semántico para Compiscript (ANTLR4 + Java Swing)

**Curso:** Construcción de Compiladores  
**Proyecto:** Laboratorio 01 & 02 — Analizador Léxico, Sintáctico y Semántico con ANTLR4  
**Lenguaje Objetivo:** Compiscript (`.cps`)  
**Plataforma:** Java 21 / Swing GUI / Maven  

---

## 📋 Descripción

Esta aplicación es una herramienta interactiva con interfaz gráfica de usuario (GUI) en Java Swing (estilo VS Code oscuro) que analiza archivos en lenguaje **Compiscript** (`.cps`), realiza su análisis léxico, sintáctico y semántico utilizando **ANTLR4**, y reporta **todos** los errores detectados con su respectiva línea, columna, tipo (LÉXICO, SINTÁCTICO o SEMÁNTICO), símbolo implicado y descripción en español claro.

Adicionalmente, incorpora un visualizador interactivo del **Árbol Sintáctico (`ParseTree`)** con código de colores según el tipo de nodo (reglas, palabras clave, cadenas, números, identificadores y símbolos).

### ✨ Características Principales
1. **Pipeline Completo del Compilador:** Análisis léxico y sintáctico mediante gramática ANTLR4 con recuperación en modo pánico, seguido de análisis semántico mediante Visitor tipado sobre el `ParseTree`.
2. **Sistema de Tipos y Validación Semántica Exhaustiva:**
   - Tipos primitivos (`integer`, `boolean`, `string`, `null`, `void`).
   - Colecciones y arreglos homogéneos y multidimensionales (`T[][]`), indexación y `foreach` con ámbito aislado.
   - Programación Orientada a Objetos: clases, herencia simple con detección de herencia cíclica, constructores explícitos e implícitos, instanciación (`new`), validación de `this`, miembros (atributos y métodos), acceso a propiedades y asignaciones.
   - Ámbitos léxicos jerárquicos con sombreado (*shadowing*), control de flujo (`if`, `while`, `do-while`, `for`, `switch-case`, `break`, `continue`, `return`, `try-catch`) y análisis de alcanzabilidad de código.
3. **Explorador Visual del ParseTree (`SyntaxTreePanel`):**
   - Árbol jerárquico interactivo con `JTree`.
   - Renderizador con colores de VS Code Dark (reglas en verde azulado, palabras clave en azul, literales en naranja/verde claro, etc.).
   - Controles para "Expandir Todo", "Colapsar Todo" y recuento dinámico de nodos.
4. **Mensajes Inteligibles en Español:** Traducción y reformulación de errores crudos de ANTLR4 y emisión de diagnósticos semánticos precisos.
5. **Interfaz Gráfica de Usuario (GUI):**
   - Tema oscuro inspirado en editores modernos.
   - Visor de código fuente `.cps` con numeración de líneas y atajos (`Ctrl+S`).
   - Panel de diagnósticos en pestañas: tabla de errores con clasificación por colores y árbol sintáctico.
   - Resaltado interactivo: al hacer clic en un error, el visor resalta automáticamente la línea en el código fuente.
6. **Procesamiento Asíncrono (`SwingWorker`):** Evita que la interfaz se congele durante el análisis.
7. **Suite de 99 Pruebas Automatizadas:** Cobertura de regresión para los 8 casos oficiales, pruebas semánticas unitarias y pruebas de GUI/árbol en JUnit 5.

---

## 📚 Documentación Técnica

Para información detallada del diseño y funcionamiento del sistema:

- 🏛️ [Arquitectura del Compilador e IDE](docs/ARQUITECTURA.md)
- 🚀 [Guía de Compilación y Ejecución](docs/GUIA_EJECUCION.md)
- 📖 [Catálogo Exhaustivo de Errores Semánticos](docs/ERRORES_SEMANTICOS.md)

---

## 🚀 Requisitos del Sistema

- **Java JDK 21** o superior.
- **Apache Maven 3.8+**.

---

## 🛠️ Compilación y Ejecución

### 1. Compilar el proyecto
```bash
mvn clean compile
```

### 2. Ejecutar la Suite de Pruebas Unitarias (99 tests)
```bash
mvn test
```

### 3. Ejecutar la Interfaz Gráfica (GUI)
```bash
mvn exec:java
```

---

## 📁 Estructura del Proyecto

```
CC-LAB01/
├── pom.xml                                           # Configuración Maven (ANTLR4 plugin, compiler release 21)
├── README.md                                         # Documentación general
├── docs/
│   ├── ARQUITECTURA.md                               # Documento de arquitectura y módulos
│   ├── GUIA_EJECUCION.md                             # Guía de compilación y comandos de prueba
│   └── ERRORES_SEMANTICOS.md                         # Catálogo de reglas y errores semánticos
├── src/
│   ├── main/
│   │   ├── antlr4/com/lexsynanalyzer/parser/
│   │   │   └── LexSynAnalyzer.g4                     # Gramática oficial de Compiscript
│   │   └── java/com/lexsynanalyzer/
│   │       ├── analyzer/
│   │       │   ├── AnalysisError.java                # Record: tipo, línea, columna, símbolo, descripción
│   │       │   ├── AnalysisResult.java               # Transporte desacoplado de errores, ParseTree y nombres
│   │       │   ├── CapturingErrorListener.java       # Implementación de ANTLRErrorListener
│   │       │   ├── LexSynAnalyzer.java               # Fachada pública del compilador
│   │       │   ├── MensajesEspanol.java              # Traductor de mensajes ANTLR -> español
│   │       │   └── TipoError.java                    # Enum: LEXICO | SINTACTICO | SEMANTICO
│   │       ├── gui/
│   │       │   ├── LexSynAnalyzerGui.java            # Ventana principal Swing con pestañas
│   │       │   ├── ResultsTablePanel.java            # Panel de tabla de resultados y banner de estado
│   │       │   └── SyntaxTreePanel.java              # Panel visual del ParseTree con JTree oscuro
│   │       └── semantic/
│   │           ├── AnalizadorSemantico.java          # Visitor de validación semántica completa
│   │           ├── CategoriaSimbolo.java             # Enum de categorías de identificadores
│   │           ├── Entorno.java                      # Ámbito léxico local con puntero a padre
│   │           ├── ErrorSemanticoReporter.java       # Colector y deduplicador de errores semánticos
│   │           ├── Simbolo.java                      # Entrada de tabla de símbolos y miembros de clase
│   │           ├── TablaSimbolos.java                # Tabla de símbolos con resolución anidada
│   │           └── TipoDato.java                     # Sistema de tipos, arreglos y subtipado/herencia
│   └── test/
│       ├── java/com/lexsynanalyzer/
│       │   ├── CasosPruebaTest.java                  # Regresión de los 8 casos .cps oficiales
│       │   ├── analyzer/                             # Pruebas léxicas y sintácticas
│       │   ├── gui/
│       │   │   └── SyntaxTreePanelTest.java          # Pruebas del árbol sintáctico y GUI
│       │   └── semantic/                             # Batería de pruebas semánticas
│       │       ├── AmbitosSemanticosTest.java
│       │       ├── ArreglosSemanticosTest.java
│       │       ├── ClasesSemanticasTest.java
│       │       ├── ErrorSemanticoReporterTest.java
│       │       ├── FlujoSemanticoTest.java
│       │       ├── FuncionesSemanticasTest.java
│       │       ├── TablaSimbolosTest.java
│       │       └── TiposSemanticosTest.java
│       └── resources/casos/                          # Archivos de prueba .cps oficiales
```
