# Generación de código MIPS

El paquete `com.lexsynanalyzer.mips` traduce el TAC (ver `LENGUAJE_INTERMEDIO.md`) a MIPS32 para el
simulador **MARS 4.5**. El ensamblador sale **solo del TAC**: el generador nunca mira el árbol sintáctico.

```
ProgramaTAC ──► GeneradorMIPS ──(un Traductor por op)──► ProgramaMips ──► Mirilla ──► .asm
                    │                                        ▲
                    └─ por función: ResolvedorUbicaciones ───┘  (dónde vive cada nombre)
                                    AsignadorRegistros        (en qué registro está)
```

## 1. Piezas

| Clase | Qué hace |
|---|---|
| `InstruccionMips`, `ProgramaMips` | Una línea de `.text`; el programa con `.data` (strings deduplicados, globales) y `.text`. |
| `GeneradorMIPS` | Parte el TAC en funciones (`function` … `endfunc`), crea un asignador por función y delega cada instrucción en el `Traductor` de su `op`. Comenta cada línea TAC en el `.asm`. Al final emite las rutinas del runtime pedidas. |
| `TraductorExpr` | `=`, binarias, unarias, `goto`/`if`/`ifFalse`, `print`/`prints`/`printb`, `concat`/`streq`/`strne`. |
| `RuntimeCadenas` | `__concat`, `__strlen`, `__streq`, `__charat`, `__print_bool`. |
| `AsignadorRegistros` | Interfaz de `getReg` que usan todos los traductores. |
| `RegistrosIngenuos` | Implementación de referencia: carga y guarda en cada instrucción. |
| `GetReg` | `getReg` del Dragon Book con descriptores, derrame y variables vivas. |
| `BloquesBasicos`, `Vida` | Bloques básicos y análisis de variables vivas / próximo uso. |
| `TraductorMemoria` | `newarray`, `a[i]`, `a[i] = v`, `len`, `strlen`, `charat`, `new`, lectura y escritura de campos. |
| `RuntimeMemoria` | `__alloc` y `__error_indice`. |
| `mirilla.Mirilla`, `mirilla.Regla*` | Optimización por mirilla sobre la lista de `InstruccionMips` (§8). |

`GeneradorMIPS(ubicaciones, FabricaRegistros.GETREG | FabricaRegistros.INGENUO)` elige el asignador.

## 2. Convención de registros

| Registros | Uso |
|---|---|
| `$t0`–`$t7` | Los reparte `getReg`. Caller-saved: se vacían antes de cada `jal` a una función Compiscript. |
| `$t8`, `$t9` | Scratch del generador y del runtime. `getReg` nunca los asigna. |
| `$a0`–`$a3` | Argumentos de syscalls y del runtime. |
| `$v0` | Código de syscall y valor de retorno. `$v1`: mensaje que recibe el `catch`. |
| `$sp`, `$fp`, `$ra` | Pila, frame actual, dirección de retorno. |

Las rutinas del runtime solo tocan `$a*`, `$v*`, `$t8` y `$t9`. Por eso llamarlas con `jal` **no** obliga
a vaciar `$t0`–`$t7`.

## 3. Tabla TAC → MIPS

| TAC | MIPS (registros los elige `getReg`) |
|---|---|
| `x = y` | Nada con `GetReg` (`x` pasa a vivir en el registro de `y`). Ingenuo: `lw` + `sw`. |
| `x = 5` · `x = true` · `x = null` | `li r, 5` · `li r, 1` · `li r, 0` |
| `x = "hola"` | `la r, str0` (string en `.data` con `.asciiz`, deduplicado) |
| `t = a + b` · `-` · `*` | `addu` · `subu` · `mul` |
| `t = a / b` · `t = a % b` | `beq b, $zero, __error_div0` · `div a, b` · `mflo t` / `mfhi t` |
| `t = a < b` (`<= > >= == !=`) | `slt` (`sle sgt sge seq sne`) |
| `t = -a` · `t = !a` | `subu t, $zero, a` · `xori t, a, 1` |
| `goto L` | `j L` |
| `if a goto L` · `ifFalse a goto L` | `bnez a, L` · `beqz a, L` |
| `print a` · `prints a` · `printb a` | syscall 1 · syscall 4 · `jal __print_bool`; después `'\n'` (syscall 11) |
| `t = concat a, b` | `$a0, $a1 ← a, b` · `jal __concat` · `t ← $v0` |
| `t = streq a, b` · `strne` | `jal __streq` · `t ← $v0` (`strne`: `xori t, $v0, 1`) |
| `t = newarray n` | `sll $a0, n, 2` · `addiu $a0, $a0, 4` · `jal __alloc` · `sw n, 0($v0)` · `t ← $v0` |
| `t = a[i]` | `bltz i, __error_indice` · `lw $t8, 0(a)` · `bge i, $t8, __error_indice` · `sll $t8, i, 2` · `addu $t8, $t8, a` · `lw t, 4($t8)` |
| `a[i] = v` | El mismo chequeo y dirección · `sw v, 4($t8)` |
| `t = len a` | `lw t, 0(a)` |
| `t = strlen s` | `jal __strlen` (`s` en `$a0`) · `t ← $v0` |
| `t = charat s, i` | `bltz i, __error_indice` · `jal __strlen` · `bge i, $v0, __error_indice` · `jal __charat` · `t ← $v0` |
| `t = new C, size` | `li $a0, size` · `jal __alloc` · `t ← $v0` |
| `t = obj.campo @k` · `obj.campo @k = v` | `lw t, k(obj)` · `sw v, k(obj)` |

Un arreglo es la dirección de un bloque del heap `[longitud][e0][e1]…`, una palabra por casilla; una matriz
es un arreglo de referencias a arreglos. Un objeto es un bloque de `size` bytes y cada campo está en el
offset que fijó `AsignadorMemoria` (los campos del padre van primero, así que un método heredado funciona
igual sobre un objeto hijo). Los accesos con índice llaman antes a `antesDePosibleExcepcion()`, para que un
`catch` lea de memoria valores al día.

Los booleanos son `0`/`1`, así que las comparaciones de MARS ya dan un booleano válido y `!` es un `xor`
con 1. `div` trunca hacia cero, igual que Compiscript: `-17 / 5 = -3`, `-17 % 5 = -2`.

Antes de cada salto (`j`, `bnez`, `beqz`) y de cada etiqueta se cierra el bloque básico (`finDeBloque`).

## 4. `getReg`

### 4.1 La idea

MIPS solo calcula entre registros, pero hay 8 registros y muchas variables. Cargar y guardar todo en cada
instrucción (lo que hace `RegistrosIngenuos`) es correcto pero lento. `GetReg` **recuerda** qué valor tiene
cada registro para no volver a cargarlo, y lo guarda en memoria solo cuando hace falta.

Lleva dos descriptores:

- **De registro**: qué nombres tiene cada registro. `$t2 → {t0, x}` después de `x = t0`.
- **De dirección**: en qué registro está cada nombre y si está **sucio** (el registro tiene un valor más
  nuevo que la memoria).

### 4.2 Protocolo de un traductor

```java
String ra = regs.leer("a");            // ¿ya está en un registro? si no, lw
String rb = regs.leer("b");
String rt = regs.paraResultado("t0");  // dónde dejar el resultado (puede derramar)
emit("addu", rt, ra, rb);
regs.definido("t0", rt);               // t0 vive en rt y está sucio
```

### 4.3 El algoritmo

- `leer(y)`: si `y` está en un registro, se usa; si no, se toma un registro libre y se emite `lw`. Los
  literales se cargan con `li`/`la` y no se recuerdan.
- `paraResultado(x)`: en este orden,
  1. el registro que ya tiene solo a `x` (se sobrescribe, `x` se redefine);
  2. el registro de un operando de esta instrucción cuyo valor **no se vuelve a usar** (un temporal muerto);
  3. un registro libre o, si no hay, una **víctima**.
- `copia(x, y)`: no emite nada. `x` se agrega al descriptor del registro de `y`.
- **Elección de víctima**: primero los registros cuyo desalojo es gratis (todo lo que tienen ya está en
  memoria o no se vuelve a usar). Entre esos, o si no hay, el de **próximo uso más lejano**. Lo sucio y
  necesario se guarda antes: `sw $t6, 24($fp)  # derrame t0`. Su recarga posterior también lleva
  `# derrame t0`.
- Los registros de los operandos de la instrucción actual quedan **fijados**: nunca se desalojan a media
  instrucción.
- `siguienteInstruccion()`: libera los fijados y olvida los temporales muertos.
- `finDeBloque()`: guarda las variables sucias y los temporales vivos, y olvida todo. El código al que se
  salta puede llegar desde varios lugares; lo único que todos comparten es la memoria.
- `antesDeLlamada()`: igual que `finDeBloque()`. La función llamada puede pisar `$t0`–`$t7` y leer
  globales de memoria.
- `antesDePosibleExcepcion()` (antes de `/` y `%`): guarda las variables sucias sin olvidar los registros,
  porque si la división salta al `catch`, el `catch` las lee de memoria.

### 4.4 Ejemplo paso a paso

TAC (`a` y `b` en memoria):

```
t0 = a + b
t1 = t0 * a
x = t1
```

| TAC | MIPS | Descriptor de registro después | Por qué |
|---|---|---|---|
| `t0 = a + b` | `lw $t0, a` · `lw $t1, b` · `addu $t2, $t0, $t1` | `$t0:{a} $t1:{b} $t2:{t0*}` | `a` y `b` no estaban en registros. `*` = sucio. |
| `t1 = t0 * a` | `mul $t2, $t2, $t0` | `$t0:{a} $t1:{b} $t2:{t1*}` | `t0` y `a` ya estaban: **cero cargas**. `t0` muere aquí, así que su registro se reusa para `t1`. |
| `x = t1` | *(nada)* | `$t2:{t1*, x*}` → `t1` muere → `$t2:{x*}` | Copia: solo cambia el descriptor. |
| `endfunc` | `sw $t2, x` | todo vacío | Fin de bloque: `x` es variable, se guarda. `t0`/`t1` muertos: **no** se guardan. |

El asignador ingenuo emite 8 `lw`/`sw` para esto; `GetReg`, 3 (test `GetRegTest`).

### 4.5 Bloques básicos y variables vivas

`BloquesBasicos` divide cada función con los líderes del Dragon Book §8.4: la primera instrucción, cada
etiqueta, y toda instrucción después de `goto`, `if`, `ifFalse`, `call` o `return`.

`Vida` calcula, por punto fijo sobre los bloques, qué nombres siguen **vivos** (se leerán antes de
reescribirse), y luego el **próximo uso** de cada nombre dentro de su bloque. Un temporal puede estar vivo
entre bloques (en `a && b` el resultado cruza una etiqueta), por eso el análisis es global a la función y
no solo por bloque. Los argumentos de un `call` cuentan como leídos en el `call`, porque el traductor de
llamadas los carga de memoria ahí.

Las **variables** del programa se consideran siempre necesarias al cerrar un bloque: otra función (si es
global), un `catch` o la siguiente vuelta del ciclo las leen de memoria.

### 4.6 Cómo se sabe que `GetReg` es correcto

Todos los programas de `src/test/resources/mips/` se corren con **los dos** asignadores y deben dar la
misma salida (`EjecucionMipsTest`). Si `GetReg` tuviera un error, la salida sería distinta. Además, el test
`getRegAccedeMenosAMemoria` comprueba que `GetReg` siempre escribe menos `lw`/`sw`:

| Programa | `lw`/`sw` ingenuo | `lw`/`sw` `GetReg` |
|---|---|---|
| `variables` | 25 | 10 |
| `aritmetica` | 69 | 6 |
| `logicas` | 82 | 47 |
| `control_if` | 33 | 23 |
| `control_ciclos` | 84 | 53 |
| `control_switch` | 29 | 19 |
| `strings` | 53 | 16 |
| `registros_derrame` (10 temporales vivos a la vez) | 149 | 58 (con derrames) |
| `funciones` (recursión, 4 parámetros, global modificada en la llamada) | 174 | 105 |
| `bloques` (variables sombreadas y declaradas en bloques) | 62 | 26 |
| `literales` (valores de más de 16 bits y límites de 32 bits) | 22 | 4 |

## 5. Runtime y errores

| Rutina | Entrada → salida |
|---|---|
| `__concat` | `$a0`, `$a1` → `$v0` = string nuevo. Mide ambos, reserva `len(a) + len(b) + 1` con `sbrk` (syscall 9, redondeado a 4 bytes), copia byte a byte. |
| `__strlen` | `$a0` → `$v0` = largo. |
| `__streq` | `$a0`, `$a1` → `$v0` = 1 si el contenido es igual. |
| `__charat` | `$a0` = s, `$a1` = i → `$v0` = string nuevo de 1 carácter. No valida `i`. |
| `__print_bool` | `$a0` = 0/1 → imprime `false`/`true`. |
| `__error_div0` | Carga `"División entre cero"` en `$a0` y salta a `__lanzar`. |
| `__alloc` | `$a0` = bytes → `$v0` = bloque nuevo (`sbrk`, redondeado a 4 bytes). MARS lo entrega en cero, así que los campos sin inicializador valen `0`/`null`. |
| `__error_indice` | Carga `"Índice fuera de rango"` en `$a0` y salta a `__lanzar`. |

Cada rutina se emite **una vez** y solo si el programa la usa (`ProgramaMips.requerir`).

`__lanzar` y el marco de activación (`function`/`endfunc`) están integrados en `GeneradorMIPS`. El runtime
mantiene hasta 16 manejadores `try/catch`, restaura el frame al lanzar y entrega el mensaje al `catch` como
string. Sin manejador, imprime `Error en tiempo de ejecución: <mensaje>` y termina con código 1 (syscall 17).
Los prólogos/epílogos, `param`/`call`/`return` y el trazado opcional de funciones también están integrados.

## 6. Pruebas

- `TraductorExprTest`: forma del MIPS de cada instrucción.
- `BloquesBasicosTest`, `VidaTest`, `GetRegTest`: líderes, vivas, descriptores y derrame.
- `RuntimeCadenasTest`: llama a `__strlen`, `__charat`, `__concat` y `__streq` con `jal` y comprueba que
  `$t0`–`$t7` siguen intactos después (la convención del runtime).
- `EjecucionMipsTest`: `variables`, `aritmetica`, `logicas`, `control_if`, `control_ciclos` (while,
  do-while, for, break, continue, ciclos anidados), `control_switch` (con fall-through), `strings`,
  `registros_derrame`, `funciones`, `bloques`, `literales` y `division_cero`, en los dos modos.
- `MemoriaMipsTest`: `arreglos`, `matriz`, `foreach_string`, `clases`, `herencia` (tres niveles, campo,
  método y constructor heredados, sobrescritura), `try_indice` (el error ocurre dentro de una función) y
  `error_indice` (sin `try`: mensaje y código 1), en los dos modos. `indice_string` lee `s[i]`, también
  sobre un elemento de un arreglo de strings y fuera de rango dentro de un `try`.
- `ProgramasAleatoriosTest`: **prueba diferencial**. 300 programas aleatorios (semilla fija) con enteros
  que desbordan, booleanos con cortocircuito, strings, ternarios, if/else, while, do-while, switch,
  break, continue, bloques que sombrean variables, llamadas (una función recursiva y otra que modifica
  una global) y expresiones de hasta 12 niveles que obligan a derramar. La salida correcta la calcula un
  intérprete en Java independiente del compilador, así que también detecta errores del TAC.

Para comprobar que los tests sí detectan errores se metieron a propósito cinco fallas (no guardar al
cerrar un bloque, reusar un registro todavía necesario, no guardar un temporal antes de leerlo, `<=`
como `<`, y variables vivas que no cruzan bloques). Las cinco hacen fallar tests.

Los tests usan `SimuladorMips` (en `src/test`), un simulador mínimo del subconjunto de MIPS que se genera,
para que la salida se compruebe en cada `mvn test` sin MARS. **No reemplaza a MARS.** Cada `.asm` queda en
`target/asm/` (los aleatorios en `target/asm/aleatorios/`, con su `.cps` y su `.salida`). Los 325 programas
dan la salida esperada en MARS 4.5:

```
java -Dfile.encoding=UTF-8 -jar tools/Mars4_5.jar nc sm 1000000 target/asm/control_ciclos_getreg.asm
```

Con `-Dfile.encoding=UTF-8` las tildes salen bien. MARS agrega una línea en blanco al final de la salida.

`Ubicaciones`, `MarcoActivacion` y `TraductorLlamadas` implementan las direcciones (globales `g_x`, frame
`offset($fp)`, temporal `tk` en `tamanoBase + 4k($fp)`), el prólogo/epílogo y `param`/`call`/`return`.
`MipsDePrueba` delega ahora al generador de producción. Las funciones anidadas tienen etiquetas y frames
independientes, pero no cierres ni acceso a locales de la función contenedora.

`bash tools/probar_mars.sh` corre en MARS todos los `.asm` de `target/asm/` (después de `mvn test`).
Cómo conectan B y C sus traductores: `INTEGRACION_MIPS.md`.

## 7. Limitaciones conocidas

- Un entero literal que no cabe en 32 bits se reduce a 32 bits (`2147483648` es `-2147483648`), igual que
  el desborde de `addu` y `mul`.
- `print` de un arreglo u objeto imprime su dirección.
- Un parámetro sin tipo anotado se trata como entero; los parámetros string requieren anotación explícita.
- Los strings son inmutables: `s[i]` se puede leer (es `charat`), pero `s[i] = v` es un error semántico.
- Acceder a un campo o a una casilla de una referencia `null` no se chequea: MARS se detiene con un error
  de dirección inválida.
- Los caracteres no ASCII (`ó`, `í`) dependen de que MARS lea el `.asm` como UTF-8.

## 8. Optimización por mirilla

`Mirilla.optimizar(programa)` trabaja sobre la lista de `InstruccionMips` del `.text`, nunca sobre texto.
Devuelve un `ProgramaMips` nuevo (mismos `.data` y rutinas) y cuántas veces se aplicó cada regla; el original
queda intacto para "Ver sin optimizar". Corre todas las reglas en orden y repite hasta que una pasada entera
no cambie nada (tope de 100 pasadas), porque una regla abre oportunidades a otra.

Regla de oro: la ventana **no cruza etiquetas**, porque a una etiqueta se puede llegar saltando y lo que se
sabía antes ya no vale. Los comentarios sueltos sí se saltan. Las reglas de memoria solo miran dos
instrucciones **seguidas** con la dirección escrita igual (`24($fp)`, `g_x`, `4($t8)`): no suponen nada sobre
dos direcciones escritas distinto, así que un `jal` o un `sw` en medio corta la ventana.

| # | Nombre en la GUI | Antes | Después |
|---|---|---|---|
| 1 | `move-redundante` | `move $x, $x` | (nada) |
| 2 | `sw/lw` | `sw $r, M` · `lw $r, M` | `sw $r, M` |
| | | `sw $r, M` · `lw $s, M` | `sw $r, M` · `move $s, $r` |
| 3 | `acceso-redundante` | `lw $r, M` · `lw $r, M` | `lw $r, M` |
| | | `lw $r, M` · `sw $r, M` | `lw $r, M` |
| | | `sw $a, M` · `sw $b, M` | `sw $b, M` |
| 4 | `salto-a-siguiente` | `j L` (o `beqz r, L`) · `L:` | `L:` |
| 5 | `salto-a-salto` | `j L1` … `L1: j L2` | `j L2` … (también `beqz`/`bnez`/`bge`…) |
| 6 | `codigo-inalcanzable` | `j`/`jr` · instrucciones · `L:` | `j`/`jr` · `L:` |
| 7 | `identidad-algebraica` | `addiu $d, $s, 0`, `addu $d, $s, $zero`, `subu $d, $s, 0`, `mul $d, $s, 1` | `move $d, $s` (o nada si `$d = $s`) |
| | | `mul $d, $s, 0` | `li $d, 0` |
| 8 | `reduccion-de-fuerza` | `mul $d, $s, 2^k` | `sll $d, $s, k` |
| 9 | `inversion-de-rama` | `beqz r, L1` · `j L2` · `L1:` | `bnez r, L2` · `L1:` |
| 10 | `etiqueta-sin-uso` | `L:` que ninguna instrucción nombra | (nada); `main` se queda siempre |

Detalles que hacen correcta cada regla:

- **3:** en las dos primeras formas el registro base de `M` no puede ser `$r` (`lw $t0, 0($t0)` dos veces
  avanza dos nodos de una lista).
- **5:** se sigue la cadena entera de `j`; si es un ciclo (`L1: j L2`, `L2: j L1`) no se toca.
- **6:** solo se borran instrucciones; los comentarios se quedan.
- **7 y 8:** la constante puede ser un inmediato, `$zero` o un registro cargado con `li` **justo antes**
  (es lo que produce `GetReg`: `li $t1, 4` · `mul $t2, $t0, $t1`). Ese `li` no se borra: el registro puede
  usarse después. `sll` da los mismos 32 bits bajos que `mul` aunque haya desborde.
- **10:** cuenta como uso cualquier operando (`j`, `b*`, `jal`, `la $t9, L0` del `try`). Al quitar la etiqueta
  de una rutina de error que nadie llama, la regla 6 borra su cuerpo en la pasada siguiente.

Efecto sobre los programas de `src/test/resources/mips` (modo `GETREG`, el del IDE): 3583 → 3513
instrucciones. Donde más quita es en código con saltos y llamadas (`funciones`: 410 → 394; `control_ciclos`:
178 → 170, con 3 inversiones de rama). En programas sin saltos `GetReg` ya deja pocas cargas de más, así que
a veces solo cae una etiqueta; en modo ingenuo todos los programas quedan más cortos.

Pruebas: `MirillaTest` (una por regla, lista de entrada → lista esperada, y el punto fijo), `MirillaMipsTest`
(cada programa de prueba, optimizado, imprime lo mismo en los dos modos) y `ProgramasAleatoriosTest`, que
corre los 300 programas aleatorios también después de la mirilla. `tools/probar_mars.sh` corre en MARS los
`target/asm/*_opt.asm`.
