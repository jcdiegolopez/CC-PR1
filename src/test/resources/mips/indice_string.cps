let s: string = "hola";
print(s[0]);
print(s[3]);
print(s[1]);
let xs: string[] = ["uno", "dos"];
print(xs[1][2]);
let i: integer = 0;
let invertido: string = "";
while (i < 4) {
    invertido = s[i] + invertido;
    i = i + 1;
}
print(invertido);
try {
    print(s[4]);
} catch (e) {
    print(e);
}
try {
    print(s[-1]);
} catch (e2) {
    print(e2);
}
