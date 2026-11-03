# Cómo conectar B y C al backend MIPS

Lo que ya está hecho por la persona A, y lo que B y C necesitan para enchufar lo suyo sin romper `getReg`.
La teoría (registros, `getReg`, tabla TAC → MIPS) está en `CODIGO_MIPS.md`. El contrato completo es
`docs/proyecto3/guia.md` §3.

## 1. Qué ya funciona (rama `proyecto3`)

- TAC tipado de A: `concat`, `streq`, `strne`, `prints`, `printb`.
- `GeneradorMIPS`, `GetReg` (con bloques básicos y variables vivas) y `RegistrosIngenuos`.
- Expresiones, control (`if`, ciclos, `switch`, `break`/`continue`), `print` en sus tres versiones y división
  entre cero.
- Runtime de strings: `__concat`, `__strlen`, `__streq`, `__charat`, `__print_bool`.
- MARS 4.5.1 en `tools/Mars4_5.jar` (licencia MIT, incluida dentro del jar).
- `bash tools/probar_mars.sh` corre en MARS cada `.asm` que dejan los tests. Hoy dan bien 325 de 325.

Comprobarlo después de clonar o hacer pull:

```
mvn clean test                 # 598 tests, 0 fallos
bash tools/probar_mars.sh      # MARS: 325 bien, 0 mal
```

Un `.asm` suelto se corre así:

```
java -Dfile.encoding=UTF-8 -jar tools/Mars4_5.jar nc sm 1000000 target/asm/funciones_getreg.asm
```

Sin `-Dfile.encoding=UTF-8` las tildes salen mal. MARS agrega una línea en blanco al final de la salida.

## 2. Cómo se enchufa un traductor

`GeneradorMIPS` no sabe nada de funciones, arreglos ni excepciones. Lo que hace es recorrer el TAC y llamar al
`Traductor` registrado para cada `op`. Cada quien registra los suyos:

```java
// Ubicaciones, llamadas y RuntimeErrores son de B; los nombres son de ejemplo
GeneradorMIPS generador = new GeneradorMIPS(funcion -> Ubicaciones.de(layout, funcion), FabricaRegistros.GETREG);
generador.registrar(Instruccion.OP_LLAMAR, llamadas::llamar)        // (Instruccion, ContextoFuncion) -> void
         .registrarRutina(GeneradorMIPS.LANZAR, RuntimeErrores::lanzar);
ProgramaMips asm = generador.generar(tac);
```

- `registrar(op, t)` reemplaza al traductor que ya hubiera. Así B sustituye los provisionales de
  `function`/`endfunc`.
- `ContextoFuncion` trae `programa()`, `registros()`, `funcion()`, `tamanoFrame()` y `esMain()`.
- Una rutina del runtime se pide con `programa.requerir("__x")`. Se emite una sola vez al final y puede pedir
  otras. Si alguien pide una rutina que nadie registró, `generar` lanza `IllegalStateException`.
- Un `op` sin traductor no rompe nada: sale como `# PENDIENTE: ...` en el `.asm`.

### Protocolo de registros (obligatorio)

Ningún traductor escribe `$t0`–`$t7` a mano. Los registros se piden a `ctx.registros()`:

```java
String ra = regs.leer(ins.a1());             // puede emitir lw / li / la
String rd = regs.paraResultado(ins.res());   // siempre este para el resultado, nunca ra
programa.emit(InstruccionMips.de("op", rd, ra));
regs.definido(ins.res(), rd);
```

| Antes de… | Llamar a | Si no se llama |
|---|---|---|
| un salto (`j`, `beqz`, `bnez`) o un `return` | `finDeBloque()` | el código destino lee de memoria un valor que solo estaba en un registro |
| un `jal` a una función Compiscript | `antesDeLlamada()` | la función llamada pisa `$t0`–`$t7`, y las globales que lee están viejas |
| algo que puede lanzar (índice, `charat`) | `antesDePosibleExcepcion()` | el `catch` lee variables viejas |
| un `jal` al runtime (`__x`) | nada | — (el runtime solo usa `$a*`, `$v*`, `$t8` y `$t9`) |

`$t8` y `$t9` son libres para direcciones y chequeos: `getReg` nunca los asigna. `antesDeInstruccion` y
`siguienteInstruccion` los llama `GeneradorMIPS`, no el traductor.

**La regla de oro:** todo `.cps` nuevo se prueba con `INGENUO` y con `GETREG`. Si los dos modos dan salidas
distintas, falta una de las llamadas de la tabla.

## 3. Persona B

Ya hay una versión que funciona y está probada en MARS: `src/test/java/com/lexsynanalyzer/mips/MipsDePrueba.java`.
Sigue el §3.3 al pie de la letra (direcciones, prólogo, epílogo, `param`/`call`/`return`). Úsala de
referencia. Cuando existan `Ubicaciones`, `MarcoActivacion` y `TraductorLlamadas`, A cambia los tests para
usar tus clases y borra `MipsDePrueba`.

1. **`Ubicaciones implements ResolvedorUbicaciones`**: `nombre → "offset($fp)"`, `"g_x"` o, para el temporal
   `tk`, `tamanoBase + 4k($fp)`. Nunca recibe literales: esos los resuelve `Operandos` (`li`/`la`). Las
   globales se declaran una vez con `programa.dato("g_x", ".word 0")`. `MipsDePrueba` lo hace en el prólogo
   de `main`.
2. **`MarcoActivacion`**: registrar `OP_FUNCION`, `OP_FIN_FUNCION` y `OP_RETORNO`.
   - Etiquetas con `GeneradorMIPS.etiquetaFuncion(f)`, que antepone `f_` a todo menos a `main`.
   - Epílogo: `finDeBloque()` **antes** de la etiqueta `f_<f>_fin`, para cubrir una función que termina sin
     `return`.
   - `return a`: `leer(a)` → `move $v0, r` → `finDeBloque()` → `j f_<f>_fin`.
3. **`TraductorLlamadas`**: `param` solo acumula. En el `call`:
   - `antesDeLlamada()`.
   - Cada argumento: `lw`/`li`/`la` a `$t8` y `sw $t8, (offParam - N)($sp)`. Después de `antesDeLlamada`
     todo está en memoria, así que se lee de ahí. `Vida` ya cuenta los argumentos como usados en el `call`.
     Por eso los `param` tienen que seguir viniendo seguidos justo antes del `call`.
   - `jal`, y si hay destino: `paraResultado(t)` → `move r, $v0` → `definido(t, r)`.
4. **`RuntimeErrores`**: `registrarRutina(GeneradorMIPS.LANZAR, ...)` reemplaza al provisional. Recibe el
   mensaje en `$a0`. `__error_div0` ya salta ahí, así que una división dentro de un `try` funciona en cuanto
   tu `__lanzar` busque el manejador. Solo puede usar `$a*`, `$v*`, `$t8` y `$t9`.
5. **try/catch.** El TAC actual es `try L0` … `endtry` · `goto L1` · `L0:` · `catch err` · … · `L1:`.
   - `L0:` ya hace `finDeBloque()` por el traductor de `label`.
   - `catch e`: `paraResultado(e)` → `move r, $v1` → `definido(e, r)`.
   - La división ya llama a `antesDePosibleExcepcion()`, y un `call` dentro del `try` ya guarda todo con
     `antesDeLlamada()`.
   - Restricción: `Vida` no conoce el salto implícito del error al `catch`. Las variables del programa sí se
     guardan, pero un **temporal** definido antes del `try` no puede leerse dentro del `catch`. El TAC
     del P2 cumple esto hoy; no lo cambies.
6. **TAC tipado pendiente (`ControlTAC`)**: `strlen` y `charat` en el `foreach` sobre strings (§3.1). El MIPS
   de esos dos `op` lo traduce C, con las rutinas `RuntimeCadenas.STRLEN` y `CHARAT`, que ya existen.
7. **Dos bugs que se arreglan en tu `ControlTAC`:**
   - `switch` sobre strings compara direcciones porque emite `==`. Si la expresión es string, usa
     `Instruccion.streq(...)`, que ya existe.
   - La variable del `catch` llega con tipo desconocido, así que `print(err)` imprime una dirección. Si el
     semántico le da tipo `string`, `ExprTAC` ya emite `prints` solo.

## 4. Persona C

1. **`RuntimeMemoria`**: `registrarRutina("__alloc", ...)`. Recibe los bytes en `$a0` y devuelve la dirección
   en `$v0` (syscall 9). Solo puede usar `$a*`, `$v*`, `$t8` y `$t9`.
2. **`TraductorMemoria`** (`newarray`, `aload`, `astore`, `len`, `strlen`, `charat`, `new`, `fload`, `fstore`).
   En el TAC se ven así: `t0 = newarray 3`, `t0[1] = 2`, `t0 = a[5]`.
   - Índice:
     - `antesDePosibleExcepcion()` primero.
     - Luego el chequeo con `$t8`/`$t9`: `bltz i, __error_indice`, `lw $t8, 0(a)`,
       `bge i, $t8, __error_indice`.
     - Después la dirección en `$t8`.
   - `__error_indice` se escribe igual que `__error_div0` en `TraductorExpr` (`la $a0, msg` + `j __lanzar`,
     con `requerir(GeneradorMIPS.LANZAR)`). Mensaje: `Índice fuera de rango`.
   - `astore` y `fstore` solo leen (`leer`). No llaman a `paraResultado` ni a `definido`, porque escriben en el
     heap y no en una variable.
   - `newarray n`: el tamaño va en `$a0`, luego `jal __alloc`, y `n` se guarda en `0($v0)`. El registro de `n`
     sigue valiendo después del `jal`, porque el runtime no toca `$t0`–`$t7`.
   - `strlen` y `charat` se traducen igual que `TraductorExpr.cadenas`: `leer` → `move $a0/$a1` →
     `jal` → `paraResultado` → `move r, $v0` → `definido`. `__charat` no valida el índice.
3. **Mirilla.** Trabaja sobre el `ProgramaMips` ya generado. Tres cuidados con lo que produce `GetReg`:
   - Ninguna regla de cargas y guardados puede cruzar una etiqueta, un salto ni un `jal`. Por ejemplo,
     `inc()` modifica la global `a`: después del `jal` hay que volver a leerla.
   - `sw`/`lw` sobre el heap (`k($t8)`, `0($v0)`) pueden apuntar a la misma dirección con otro registro base.
     Solo se eliminan con la misma dirección escrita igual **y** sin ningún `sw` ni `jal` en medio.
   - No borres los comentarios `# derrame tN`: un test los busca y sirven para la defensa.
4. **`EjecutorMars`**:
   - Comando: `java -Dfile.encoding=UTF-8 -jar tools/Mars4_5.jar nc sm 1000000 archivo.asm`.
   - Ignora los espacios y los saltos de línea al final de la salida.
   - Si el jar no existe, el test se salta con `Assumptions`.
   - `tools/probar_mars.sh` hace lo mismo en bash.
   - El código de `syscall 17` es el código de salida del proceso. Comprobado: `division_cero` sale con 1 y
     un programa normal con 0.
5. **`LexSynAnalyzer`**:
   - Arma el `GeneradorMIPS` con `GETREG`, registra los traductores de B y C, llama a `generar(tac)` y aplica
     la mirilla.
   - Con errores el TAC llega vacío y `generar` devuelve un programa vacío.
   - Mientras `Ubicaciones` no exista, sirve el `ubicaciones(layout, funcion)` de `MipsDePrueba`.

## 5. Tests nuevos

- Cada caso va como `src/test/resources/mips/<caso>.cps` + `<caso>.salida`, con una línea por `print`.
- Para que corra en los dos modos, agrega el nombre a `PROGRAMAS` en `EjecucionMipsTest` (archivo de A: avisa
  en el PR) o crea tu propia clase con `MipsDePrueba.compilar(fuente, modo)`.
- Los tests corren el `.asm` en `SimuladorMips` (en `src/test`), que solo soporta lo que se genera hoy.
  Con una instrucción nueva falla con `Instrucción no soportada: ...`. Agrégala ahí con la misma semántica
  que en MARS.
- Antes del PR: `mvn test` y `bash tools/probar_mars.sh`, los dos en verde.
