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
| Excepciones | `try Lcatch` · `endtry` · `catch e` | ver sección 4 |
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

Todas las condiciones se evalúan a un operando y se saltan con `ifFalse` (o `if` en `do-while`). Las
etiquetas `L0, L1, …` son únicas en todo el programa.

### `if` / `else`

```
if (a > 5) { a = 0; } else { a = 1; }
```
```
t0 = a > 5
ifFalse t0 goto L0       // falso: rama else
a = 0
goto L1
L0:
a = 1
L1:
```

Sin `else` solo se emite `ifFalse c goto Lfin`, el bloque y `Lfin:`.

### `while`, `do-while` y `for`

Cada ciclo tiene una etiqueta de **inicio**, una de **continue** y una de **fin**. `break` salta al
fin y `continue` a la etiqueta de continue:

| Ciclo | `continue` salta a |
|---|---|
| `while` | el inicio (se reevalúa la condición) |
| `do-while` | la condición, al final del cuerpo |
| `for` | el incremento |

```
while (a > 0) {                     L0:
    if (a == 1) { break; }          t0 = a > 0
    a = a - 1;                      ifFalse t0 goto L1
}                                   t0 = a == 1
                                    ifFalse t0 goto L2
                                    goto L1          // break
                                    L2:
                                    t0 = a - 1
                                    a = t0
                                    goto L0
                                    L1:
```

`do-while` ejecuta el cuerpo primero y repite con `if`:

```
do { i = i + 1; } while (i < 3);    L0:
                                    t0 = i + 1
                                    i = t0
                                    L1:              // continue
                                    t0 = i < 3
                                    if t0 goto L0
                                    L2:              // break
```

`for` emite la inicialización una sola vez, luego condición, cuerpo, incremento y salto al inicio.
La variable declarada en el `for` es local de su ámbito:

```
for (let i: integer = 0; i < 3; i = i + 1) { print(i); }
```
```
i = 0
L0:
t0 = i < 3
ifFalse t0 goto L2
print i
L1:                      // continue → incremento
t0 = i + 1
i = t0
goto L0
L2:
```

### `foreach`

Se recorre con un índice, la longitud (`len`) y una lectura `a[i]`. El arreglo se copia primero a un
temporal, así que si el cuerpo reasigna la variable el ciclo no cambia de arreglo. El arreglo, la
longitud y el índice **no se liberan** hasta salir del ciclo. Sobre un `string` es igual y cada
elemento es un `string` de un carácter.

```
foreach (x in nums) { print(x); }
```
```
t0 = nums                // copia del arreglo
t1 = len t0
t2 = 0                   // índice
L0:
t3 = t2 < t1
ifFalse t3 goto L2
t3 = t0[t2]
x = t3
print x
L1:                      // continue
t3 = t2 + 1
t2 = t3
goto L0
L2:
```

### `switch` con fall-through

El selector se evalúa **una vez** y su temporal no se libera hasta el final. Primero va la cadena de
comparaciones, después los cuerpos en orden. Un caso sin `break` **continúa** con el siguiente
(fall-through); `break` salta al fin. Si ningún caso coincide se salta al `default` o, si no hay, al
fin.

```
switch (op) {                       t0 = op == 1
    case 1: r = 10; break;          if t0 goto L1
    case 2: r = 20;                 t0 = op == 2
    default: r = 30;                if t0 goto L2
}                                   goto L3          // default
                                    L1:
                                    r = 10
                                    goto L0          // break
                                    L2:
                                    r = 20           // sin break: sigue al default
                                    L3:
                                    r = 30
                                    L0:
```

### Funciones y parámetros

Cada función se emite después de `main` como `function nombre, frameSize … endfunc`; el tamaño se
completa al final, cuando se conoce cuántos temporales usó el cuerpo. Los parámetros no se copian:
viven en el registro de activación (sección 5) y se usan por su nombre.

Una llamada evalúa **todos** los argumentos primero y luego emite los `param` en orden, seguidos de
`call f, n`. Si el valor se usa, queda en un temporal (`t = call f, n`); como sentencia sola se emite
`call f, n` sin destino.

```
function saludar(n: string) { print("hola " + n); }
saludar("Ana");
```
```
function main, 24
param "Ana"
call saludar, 1          // sentencia: sin destino
endfunc
function saludar, 36
t0 = "hola " + n
print t0
endfunc
```

`return e` emite `return x` y `return;` emite `return`.

### Recursividad

Una llamada recursiva es una llamada normal: el registro de activación de cada invocación es
independiente, así que los temporales del llamador se conservan durante la llamada.

```
function fib(n: integer): integer {
    if (n < 2) { return n; }
    return fib(n - 1) + fib(n - 2);
}
```
```
function fib, 36
t0 = n < 2
ifFalse t0 goto L0
return n
L0:
t0 = n - 1
param t0
t0 = call fib, 1
t1 = n - 2
param t1
t1 = call fib, 1
t0 = t0 + t1
return t0
endfunc
```

### Funciones anidadas

Una función declarada dentro de otra se **eleva** a nivel superior con la etiqueta `padre_hijo`. La
etiqueta se resuelve por ámbito, así que funciona en la recursión de la anidada, entre hermanas y en
llamadas anteriores a la declaración. Dos funciones homónimas de ámbitos distintos reciben etiquetas
distintas (`h`, `h_1`).

**Limitación documentada:** el registro de activación reserva el enlace de acceso (offset 16), pero
una función anidada **no captura** las variables locales de su padre.

### `try` / `catch`

El lenguaje no tiene `throw`. Lo que dispara el `catch` son errores de ejecución **implícitos**:
índice fuera de rango en `a[i]` y división o módulo por cero. `try Lcatch` instala el manejador,
`endtry` lo quita y `catch e` recibe el mensaje de error como `string`.

```
try { let x: integer = a[5]; print(x); } catch (err) { print(err); }
```
```
try L0
t0 = a[5]
x = t0
print x
endtry
goto L1
L0:
catch err
print err
L1:
```

Un `return`, `break` o `continue` que sale de uno o más `try` emite un `endtry` por cada uno **antes**
del salto; si no, el manejador quedaría activo:

```
while (x < 3) {
    try { x = x + 1; if (x == 2) { continue; } } catch (e) { print(e); }
}
```
```
L0:
t0 = x < 3
ifFalse t0 goto L1
try L2
t0 = x + 1
x = t0
t0 = x == 2
ifFalse t0 goto L4
endtry                   // sale del try antes del continue
goto L0
L4:
endtry
goto L3
L2:
catch e
print e
L3:
goto L0
L1:
```

## 5. Memoria, clases y objetos

### Tamaños de los tipos

| Tipo | Bytes |
|---|---|
| `integer`, `boolean` | 4 |
| `string`, arreglos, objetos (referencias) | 8 |
| Temporal | 4 |

Cada variable se alinea a su propio tamaño. `AsignadorMemoria` es el único lugar que conoce estos
números.

### Registro de activación

```
 offset 0   dirección de retorno      (8)
 offset 8   enlace de control         (8)   = frame del llamador
 offset 16  enlace de acceso          (8)   = frame del padre léxico (reservado)
 offset 24  parámetros (this primero en métodos)
            variables locales (los bloques internos continúan el offset)
            temporales (máximo simultáneo × 4)
```

`function f, N` lleva el tamaño total del frame. Por ejemplo, `function suma(a: integer, b: integer)`
con un temporal ocupa `24 + 4 + 4 + 4 = 36`. Las variables declaradas directamente en el nivel
superior son **globales**: van en una región estática aparte (offset desde 0), no en el frame de
`main`. Las variables de bloques internos del nivel superior sí son locales de `main`.

La pestaña *Tabla de Símbolos / Registros de Activación* del IDE muestra el offset y el tamaño de cada
variable, parámetro y campo.

### Nombres de variables sombreadas

Si una variable sombrea a otra visible (un `let x` interno dentro de otro `x`, o un parámetro con el
nombre de una global), la interna se emite como `x_1`, `x_2`… Así dos variables distintas nunca
comparten nombre en el TAC:

```
let x: integer = 1;          →   x = 1
{                                x_1 = 2
    let x: integer = 2;          print x_1
    print(x);                    print x
}
print(x);
```

### Layout de clases

Los campos del padre van **primero** y conservan su offset; los propios continúan después.
`tamanoInstancia` es el tamaño total. Los métodos no ocupan espacio en el objeto.

```
class Animal { let nombre: string; let patas: integer = 4; }   nombre @0 (8), patas @8 (4)  → 12
class Perro : Animal { let raza: string = "mestizo"; }         … + raza @16 (8)             → 24
```

### `new`, inicializadores y constructor

`new Clase(args)` reserva el objeto, ejecuta los inicializadores de campos (del padre primero) y, si
la jerarquía tiene constructor (un método `constructor` o con el nombre de la clase, propio o
heredado), lo llama con el objeto como primer parámetro:

```
let p: Perro = new Perro("Toby");
```
```
t0 = new Perro, 24
t0.patas = 4
t0.raza = "mestizo"
param t0
param "Toby"
call Animal_constructor, 2       // Perro hereda el constructor de Animal
p = t0
```

Sin constructor en la jerarquía solo se emite `t0 = new Clase, tamaño` y los inicializadores.

### Métodos y `this`

Cada método se emite después de `main` como `function Clase_metodo, N`. `this` es el **parámetro 0
implícito**: en la llamada se pasa primero el objeto y `n` cuenta también a `this`.

```
print(p.hablar());     →   param p
                           t0 = call Perro_hablar, 1
                           print t0

function Perro_hablar, 36
t0 = this.nombre
t0 = t0 + " ladra."
return t0
endfunc
```

Los campos se leen y escriben siempre a través de un objeto: `t = obj.campo`, `obj.campo = v`.

### Herencia y despacho estático

Un método heredado y no sobrescrito se llama con la etiqueta de la clase que lo declara
(`b.h()` con `h` declarado en `A` → `call A_h`). El método se resuelve con el **tipo declarado** de
la variable, no con el del objeto que guarda:

```
let a: Animal = p;     // p es un Perro
print(a.hablar());     →   param a
                           t0 = call Animal_hablar, 1
```

Es una **limitación documentada**: no hay tabla virtual. Por la misma razón, dentro de
`Animal_describir` la llamada `this.hablar()` va a `Animal_hablar` aunque el objeto sea un `Perro`.

### Supuestos

- Dentro de un método, los campos se acceden con `this.campo`. Un campo usado por su nombre solo (sin
  `this.`) se emite con ese nombre y no como lectura del objeto.
- Los inicializadores de campos se evalúan en el punto del `new`, así que no deben usar `this`.
