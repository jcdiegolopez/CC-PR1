# Lenguaje intermedio (TAC) de Compiscript

Código de tres direcciones generado por `com.lexsynanalyzer.tac`. Solo se **genera texto**: el programa
nunca se ejecuta ni se produce código objeto. Con cualquier error léxico, sintáctico o semántico no se
genera TAC.

## 1. Instrucciones

| Categoría | Instrucción | Ejemplo |
|---|---|---|
| Asignación | `x = y` | `a = 5` |
| Binaria | `t = a op b` (`+ - * / % < <= > >= == !=`) | `t0 = a + b` |
| Unaria | `t = -a` · `t = !a` | `t1 = !t0` |
| Etiqueta / salto | `L:` · `goto L` | `goto L2` |
| Condicional | `if a goto L` · `ifFalse a goto L` | `ifFalse t0 goto L1` |
| Función | `function f, frameSize` … `endfunc` | `function suma, 40` |
| Llamada | `param a` · `t = call f, n` · `call f, n` | `t2 = call suma, 2` |
| Retorno | `return a` · `return` | `return t3` |
| Arreglos | `t = newarray n` · `t = a[i]` · `a[i] = b` · `t = len a` | `t0 = a[i]` |
| Objetos | `t = new Clase, size` · `t = obj.campo` · `obj.campo = b` | `t0 = new Perro, 24` |
| Excepciones | `try Lcatch` · `endtry` · `catch e` | ver sección de B |
| Salida | `print a` | `print t0` |

Un operando es un **literal** (`5`, `"hola"`, `true`, `false`, `null`), una **variable** o un
**temporal**.

## 2. Convenciones generales

1. Temporales `t0, t1, …`; se reinician en cada función. Etiquetas `L0, L1, …` únicas por programa.
2. El código de nivel superior se emite como `function main, N … endfunc`. Los cuerpos de funciones y
   métodos se emiten **después**, cada uno completo.
3. `N` (`frameSize`) = cabecera (24) + parámetros + locales + (máximo de temporales simultáneos × 4).
4. Las variables usan el nombre del código fuente. Una variable del usuario cuyo nombre tenga forma de
   temporal (`t0`, `t12`) se emite con sufijo `_v` (`t0_v`) para no confundirse con un temporal.
5. `+` entre `string` es concatenación y usa el mismo `+` en el TAC.

## 3. Expresiones

### Variables y constantes

`let`, `var` y `const` con inicializador emiten una asignación; sin inicializador no emiten nada.
La asignación también es expresión: `a = b = 3` emite `b = 3` y `a = b`.

```
let a: integer = 6;     →   a = 6
var r: integer = a + 1; →   t0 = a + 1
                            r = t0
```

### Aritmética y reciclaje de temporales

Cada operador binario evalúa sus dos operandos, **libera los temporales de ambos y después pide el
temporal del resultado**, así que el resultado reutiliza uno de ellos. `(a + b) * (c - d)` usa 2
temporales, no 3:

```
t0 = a + b
t1 = c - d
t0 = t0 * t1
```

`a + a + a + a` usa uno solo (`t0 = a + a`, `t0 = t0 + a`, …). Un temporal que se vuelve a leer más
tarde **no** se libera (selector del `switch`, índice y arreglo del `foreach`, objeto `this` mientras
se arman los argumentos).

### Lógicas y comparaciones

`!`, `-` (unarios), `== != < <= > >=` son instrucciones binarias o unarias normales.

`&&` y `||` hacen **cortocircuito**. El resultado vive en **un solo temporal** que se asigna en cada
operando evaluado:

```
z = a && b || x > 3
```
```
t0 = a
ifFalse t0 goto L1      // a && b: si a es falso, no se evalúa b
t0 = b
L1:
if t0 goto L0           // || : si ya es verdadero, no se evalúa x > 3
t1 = x > 3
t0 = t1
L0:
z = t0
```

### Ternario

`c ? a : b` asigna el resultado a **un solo temporal** en ambas ramas:

```
ifFalse c goto L0
t0 = a
goto L1
L0:
t0 = b
L1:
```

### Arreglos

Un literal reserva el arreglo y asigna cada elemento. Los multidimensionales son literales anidados;
`a[i][j]` encadena lecturas.

```
let m = [[1, 2], [3, 4]];  →   t0 = newarray 2
                               t1 = newarray 2
                               t1[0] = 1
                               t1[1] = 2
                               t0[0] = t1
                               …
m[1][0] = m[0][1];         →   t0 = m[1]
                               t1 = m[0]
                               t1 = t1[1]
                               t0[0] = t1
```

La lectura `a[i]` también puede ser el destino de una asignación (`a[i] = x`); el índice de salida
fuera de rango es un error de ejecución, no de compilación.

### `print`

`print(e)` evalúa `e` y emite `print x`.

## 4. Flujo de control, funciones y excepciones

*(Sección de la Persona B: if/while/do/for/foreach/switch con fall-through, funciones, recursión,
try/catch.)*

## 5. Clases, objetos y registro de activación

*(Sección de la Persona C: layout de clases, `new`, constructor, herencia, despacho estático,
registro de activación y tamaños de tipos.)*
