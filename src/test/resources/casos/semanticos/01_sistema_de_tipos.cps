// ============================================================
// CATEGORIA: SISTEMA DE TIPOS
// Este archivo NO tiene errores léxicos ni sintácticos, solo
// errores semánticos, para que el analizador semántico se
// ejecute completo (recordar: si hay error léxico/sintáctico
// el análisis semántico no corre).
// ============================================================

// --- Casos EXITOSOS (no deberían reportar error) ---
let a: integer = 5;
let b: integer = 10;
let suma: integer = a + b;

let x: boolean = true;
let y: boolean = false;
let logico: boolean = x && y;

let m: integer = 3;
let n: integer = 7;
let comparacion: boolean = m < n;

let texto: string = "hola";
texto = "mundo";

// --- Casos FALLIDOS (deben reportar error semántico) ---

// 1) Operación aritmética con operando booleano -> ERROR
let malaSuma: integer = a + x;

// 2) Operación lógica con operando entero -> ERROR
let malaLogica: boolean = a && b;

// 3) Comparación entre tipos incompatibles -> ERROR
let malaComparacion: boolean = a == texto;

// 4) Asignación de tipo incompatible a variable declarada -> ERROR
let numero: integer = "no soy un entero";

// 5) Reasignación con tipo incompatible -> ERROR
a = "cadena";
