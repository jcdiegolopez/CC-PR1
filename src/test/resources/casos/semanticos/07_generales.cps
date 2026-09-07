// ============================================================
// CATEGORIA: REGLAS GENERALES
// ============================================================

// --- Casos EXITOSOS ---

function sumar(a: integer, b: integer): integer {
    return a + b; // todas las rutas retornan valor
}

let resultado: integer = sumar(1, 2);

// --- Casos FALLIDOS ---

// 1) Parámetros duplicados en la misma función -> ERROR
function conParametrosRepetidos(a: integer, a: integer): integer {
    return a;
}

// 2) Función con tipo de retorno declarado que nunca retorna valor -> ERROR
function sinRetorno(): integer {
    print("no retorno nada");
}

// 3) Expresión sin sentido: una función usada como operando -> ERROR
function identidad(): integer { return 1; }
let sinSentido: integer = identidad * 2;

// 4) Expresión sin sentido: el nombre de una clase usado como valor -> ERROR
class Motor {
    var potencia: integer;
}
let tampocoTieneSentido: integer = Motor + 1;

// 5) Código muerto tras un 'break' dentro de un ciclo -> ERROR
for (let i: integer = 0; i < 3; i = i + 1) {
    break;
    print("inalcanzable");
}
