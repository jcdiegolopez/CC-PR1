# Catálogo de Errores Semánticos — Compiscript

Este catálogo detalla todas las reglas semánticas validadas por el compilador, acompañadas de su mensaje diagnóstico en español y ejemplos representativos.

---

## 1. Clases, Objetos y Herencia (POO)

| Regla / Situación | Diagnóstico Emitido | Ejemplo de Código Inválido |
|---|---|---|
| **Uso de `this` fuera de clase** | `'this' solo puede usarse dentro del cuerpo de una clase.` | `let x = this;` |
| **Acceso a atributo o método inexistente** | `La clase '<Clase>' no contiene ningún atributo o método llamado '<nombre>'.` | `let u = new Usuario(); print(u.correo);` |
| **Instanciación de clase no declarada** | `La clase '<Clase>' no está declarada en este ámbito.` | `let x = new Inexistente();` |
| **Instanciación de un símbolo que no es clase** | `'<nombre>' es una variable y no se puede instanciar con 'new'.` | `let v: integer = 1; let x = new v();` |
| **Discrepancia de argumentos en constructor** | `El constructor de '<Clase>' espera X argumento(s), pero se recibieron Y.` | `class A { function constructor(n: string) {} } let x = new A();` |
| **Constructor por defecto con argumentos** | `La clase '<Clase>' no define un constructor con parámetros y espera 0 argumentos, pero se recibieron X.` | `class A {} let x = new A(1, 2);` |
| **Herencia de clase no declarada** | `La clase padre '<Padre>' no está declarada en este ámbito.` | `class B : PadreInexistente {}` |
| **Herencia de símbolo no clase** | `'<nombre>' no es una clase y no se puede heredar de ella.` | `let noClase: integer = 5; class B : noClase {}` |
| **Herencia cíclica** | `Herencia cíclica detectada: la clase '<Clase>' no puede heredar de sí misma directa o indirectamente.` | `class A : B {} class B : A {}` |
| **Asignación a método** | `'<nombre>' es un método de la clase '<Clase>' y no admite asignaciones.` | `let c = new Calc(); c.sumar = 10;` |
| **Invocación de atributo como método** | `'<nombre>' es un atributo de la clase '<Clase>' y no puede invocarse como método.` | `let a = new Auto(); a.velocidad();` |
| **Asignación a constante de clase** | `La constante '<nombre>' de la clase '<Clase>' no puede recibir un nuevo valor.` | `c.CODIGO_FIJO = 200;` |

---

## 2. Arreglos y Colecciones

| Regla / Situación | Diagnóstico Emitido | Ejemplo de Código Inválido |
|---|---|---|
| **Arreglo heterogéneo** | `Los elementos del arreglo deben ser del mismo tipo; se esperaba '<T1>', pero se encontró '<T2>'.` | `let arr = [1, "dos", 3];` |
| **Arreglos anidados incompatibles** | `Los elementos del arreglo deben ser del mismo tipo; se esperaba 'integer[]', pero se encontró 'string[]'.` | `let mat = [[1, 2], ["a", "b"]];` |
| **Índice no entero** | `El índice de acceso a un arreglo debe ser de tipo 'integer', pero es de tipo '<T>'.` | `let elem = arr["cero"];` |
| **Indexar tipo no arreglo ni cadena** | `Solo se pueden indexar arreglos o cadenas, pero se intentó indexar un tipo '<T>'.` | `let num: integer = 5; let x = num[0];` |
| **`foreach` con expresión no iterable** | `La sentencia 'foreach' requiere un arreglo o una cadena, pero se recibió un tipo '<T>'.` | `foreach (x in 123) { print(x); }` |
| **Variable de `foreach` fuera de ámbito** | `El identificador '<nombre>' no está declarado en este ámbito.` | `foreach (n in arr) {} print(n);` |

---

## 3. Tipos, Asignaciones y Operadores

| Regla / Situación | Diagnóstico Emitido | Ejemplo de Código Inválido |
|---|---|---|
| **Incompatibilidad en asignación** | `La variable '<nombre>' es de tipo '<T1>' y no puede recibir un valor de tipo '<T2>'.` | `let x: integer = "hola";` |
| **Reasignación a constante** | `La constante '<nombre>' no puede recibir un nuevo valor después de su declaración.` | `const PI: integer = 3; PI = 4;` |
| **Uso de variable no inicializada** | `La variable '<nombre>' se usa antes de asignarle un valor.` | `let x: integer; print(x);` |
| **Operación aritmética incompatible** | `El operador '+' no puede aplicarse a '<T1>' y '<T2>'; se esperaban dos valores integer o dos valores string.` | `let x = true + 5;` |
| **Operación lógica incompatible** | `El operador '&&' solo admite operandos 'boolean', pero se recibió '<T>'.` | `let x = 1 && true;` |
| **Comparación de igualdad incompatible** | `El operador '==' no puede comparar '<T1>' con '<T2>' porque son tipos incompatibles.` | `let x = 1 == "uno";` |
| **Ramas ternarias incompatibles** | `Las dos ramas del operador ternario deben producir tipos compatibles, pero son '<T1>' y '<T2>'.` | `let x = true ? 1 : "no";` |

---

## 4. Funciones y Control de Flujo

| Regla / Situación | Diagnóstico Emitido | Ejemplo de Código Inválido |
|---|---|---|
| **Identificador no declarado** | `El identificador '<nombre>' no está declarado en este ámbito.` | `print(noExiste);` |
| **Redeclaración de símbolo** | `La variable '<nombre>' ya está declarada en este ámbito; use otro nombre.` | `let x = 1; let x = 2;` |
| **Cantidad incorrecta de argumentos** | `La función '<nombre>' espera X argumento(s), pero se recibieron Y.` | `function f(a: integer) {} f(1, 2);` |
| **Tipo de argumento incompatible** | `El parámetro '<param>' de '<func>' es de tipo '<T1>' y no puede recibir un valor de tipo '<T2>'.` | `function f(a: integer) {} f("uno");` |
| **Invocación de variable no función** | `'<nombre>' es una variable y no puede invocarse como función.` | `let x = 5; x();` |
| **Función sin retorno** | `La función '<nombre>' declara el tipo de retorno '<T>', pero ninguna de sus rutas devuelve un valor.` | `function f(): integer {}` |
| **`return` fuera de función** | `'return' solo puede usarse dentro de una función.` | `return 5;` |
| **`break` / `continue` fuera de ciclo** | `'break' solo puede usarse dentro de un ciclo o de un 'switch'.` | `break;` |
| **Condición no booleana** | `La condición del 'if' debe ser de tipo 'boolean', pero es de tipo '<T>'.` | `if (123) {}` |
| **Código inalcanzable** | `Esta sentencia nunca se ejecuta: el flujo ya termina antes de llegar aquí.` | `return 1; print("inalcanzable");` |
| **Selector y case incompatibles** | `El valor del 'case' es de tipo '<T1>' y no puede compararse con el selector, que es de tipo '<T2>'.` | `switch ("a") { case 1: break; }` |
