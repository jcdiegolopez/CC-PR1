// ============================================================
// CATEGORIA: FUNCIONES Y PROCEDIMIENTOS
// ============================================================

// --- Casos EXITOSOS ---

// Función recursiva
function factorial(n: integer): integer {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

// Función anidada / closure
function crearContador(): integer {
    function siguiente(): integer {
        return 1;
    }
    return siguiente();
}

function sumar(a: integer, b: integer): integer {
    return a + b;
}

let resultado: integer = sumar(3, 4);       // llamada correcta
let fact: integer = factorial(5);           // recursión correcta

// --- Casos FALLIDOS ---

// 1) Número incorrecto de argumentos -> ERROR
let malaLlamada: integer = sumar(1, 2, 3);

// 2) Tipo incorrecto de argumento -> ERROR
let malaLlamada2: integer = sumar(1, "dos");

// 3) Tipo de retorno no coincide con el declarado -> ERROR
function retornoIncorrecto(): integer {
    return "esto no es un entero";
}

// 4) Múltiples declaraciones de la misma función (sin soporte de sobrecarga) -> ERROR
function duplicada(a: integer): integer {
    return a;
}
function duplicada(a: integer): integer {
    return a + 1;
}
