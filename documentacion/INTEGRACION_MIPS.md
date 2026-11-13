# Integración del backend MIPS

La persona B ya está integrada en el generador de producción. Esta guía resume su contrato y deja las tareas
de memoria de la persona C como referencia. La teoría (registros, `getReg`, tabla TAC → MIPS) está en
`CODIGO_MIPS.md`; el contrato original está en `docs/proyecto3/guia.md` §3.

## 1. Backend integrado

- TAC tipado de A: `concat`, `streq`, `strne`, `prints`, `printb`.
- `GeneradorMIPS`, `GetReg` (con bloques básicos y variables vivas) y `RegistrosIngenuos`, en modo `INGENUO`
  y `GETREG`.
- Expresiones, control (`if`, ciclos, `switch`, `break`/`continue`), `print` en sus tres versiones y división
  entre cero. Los `switch` de strings comparan contenido, no direcciones.
- Runtime de strings: `__concat`, `__strlen`, `__streq`, `__charat`, `__print_bool`.
- Frames, parámetros, llamadas, retornos y excepciones con pila de manejadores; traza opcional de entrada y
  salida de funciones.
- MARS 4.5.1 en `tools/Mars4_5.jar` (licencia MIT, incluida dentro del jar).
- `bash tools/probar_mars.sh` corre en MARS los `.asm` que dejan los tests.

Validar el backend:

```
mvn clean test
bash tools/probar_mars.sh
```

Un `.asm` suelto se corre así:

```
java -Dfile.encoding=UTF-8 -jar tools/Mars4_5.jar nc sm 1000000 target/asm/funciones_getreg.asm
```

Sin `-Dfile.encoding=UTF-8` las tildes salen mal. MARS agrega una línea en blanco al final de la salida.

## 2. Cómo se enchufa un traductor

`GeneradorMIPS` recorre el TAC y llama al `Traductor` registrado para cada `op`. Los traductores de funciones,
llamadas y excepciones ya se registran en el constructor. Para generar con el `Layout` del análisis semántico:

```java
GeneradorMIPS generador = new GeneradorMIPS(layout, FabricaRegistros.GETREG);
ProgramaMips asm = generador.generar(tac);
```

- `registrar(op, t)` reemplaza al traductor que ya hubiera; el generador registra los marcos y el runtime de
  errores de producción.
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

## 3. Persona B: implementado

- `Ubicaciones` resuelve variables globales, parámetros, locales y temporales del frame a partir de `Layout`.
- `MarcoActivacion` emite prólogos, epílogos y retornos; `TraductorLlamadas` mueve argumentos al frame futuro
  y preserva el valor de retorno en `$v0`.
- `RuntimeErrores` y `TraductorExcepciones` mantienen hasta 16 manejadores activos. Al lanzar, se restaura
  `$sp` y `$fp` del manejador más cercano; el mensaje llega al `catch` como string. Superar el límite o lanzar
  sin `catch` produce un error de ejecución.
- `foreach` sobre strings usa operaciones TAC tipadas `strlen` y `charat`; `switch` sobre strings usa
  `streq`; las variables `catch` tienen tipo `string`.
- Para trazar llamadas, usa `new GeneradorMIPS(layout, FabricaRegistros.GETREG, true)`. La traza imprime
  `→ función` al entrar y `← función` al salir, con indentación por profundidad.
- Las funciones anidadas reciben etiquetas y frames independientes. No se implementan cierres ni acceso a
  variables locales de una función contenedora.

## 4. Persona C

Ya integrado: `RuntimeMemoria` (`__alloc`, `__error_indice`) y `TraductorMemoria` (puntos 1 y 2).
Pendiente: mirilla, `EjecutorMars` y la conexión con `LexSynAnalyzer` y la GUI.

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
   - Arma el `GeneradorMIPS` con `GETREG`, llama a `generar(tac)` y aplica la mirilla.
   - Con errores el TAC llega vacío y `generar` devuelve un programa vacío.

## 5. Tests nuevos

- `PersonaBTest` cubre frames, parámetros, recursión, funciones anidadas, excepciones locales y entre
  funciones, el TAC tipado de `foreach` sobre strings, `switch` de strings y traza en ambos modos.
- Los tests corren el `.asm` en `SimuladorMips` (en `src/test`), que solo soporta lo que se genera hoy.
  Con una instrucción nueva falla con `Instrucción no soportada: ...`. Agrégala ahí con la misma semántica
  que en MARS.
- Antes del PR, ejecuta `mvn clean test` y `bash tools/probar_mars.sh`.
