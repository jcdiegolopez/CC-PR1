// ============================================================
// CATEGORIA: CONTROL DE FLUJO
// ============================================================

// --- Casos EXITOSOS ---

let contador: integer = 0;

if (contador == 0) {
    print("cero");
}

while (contador < 3) {
    contador = contador + 1;
}

do {
    contador = contador - 1;
} while (contador > 0);

for (let i: integer = 0; i < 3; i = i + 1) {
    if (i == 1) {
        continue; // válido dentro de un bucle
    }
    if (i == 2) {
        break; // válido dentro de un bucle
    }
    print(i);
}

function conRetorno(): integer {
    return 1; // válido dentro de una función
}

// --- Casos FALLIDOS ---

// 1) Condición de 'if' no booleana -> ERROR
let numero: integer = 5;
if (numero) {
    print("no debería compilar");
}

// 2) Condición de 'while' no booleana -> ERROR
while (numero) {
    numero = numero - 1;
}

// 3) 'break' fuera de un ciclo o switch -> ERROR
break;

// 4) 'continue' fuera de un ciclo -> ERROR
continue;

// 5) 'return' fuera de una función -> ERROR
return 1;

// 6) Código muerto: instrucción después de un 'return' -> ERROR
function conCodigoMuerto(): integer {
    return 1;
    print("esto nunca se ejecuta");
}
