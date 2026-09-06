// ============================================================
// CATEGORIA: LISTAS Y ESTRUCTURAS DE DATOS
// ============================================================

// --- Casos EXITOSOS ---

let notas: integer[] = [90, 85, 100];
let primera: integer = notas[0];

let matriz: integer[][] = [[1, 2], [3, 4]];

foreach (nota in notas) {
    print(nota);
}

// --- Casos FALLIDOS ---

// 1) Elementos de arreglo con tipos distintos -> ERROR
let mixto: integer[] = [1, 2, "tres"];

// 2) Índice de acceso a arreglo que no es 'integer' -> ERROR
let invalido: integer = notas["cero"];

// 3) foreach sobre algo que no es arreglo ni cadena -> ERROR
let numero: integer = 5;
foreach (item in numero) {
    print(item);
}

// 4) Indexar algo que no es arreglo ni cadena -> ERROR
let x: integer = 10;
let y: integer = x[0];
