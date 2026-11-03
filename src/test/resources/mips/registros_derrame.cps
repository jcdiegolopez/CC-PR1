let a: integer = 1;
let b: integer = 2;
let c: integer = 3;
let d: integer = 4;
let e: integer = 5;
let f: integer = 6;
let g: integer = 7;
let h: integer = 8;
let i: integer = 9;
let j: integer = 10;
let r: integer = (a * b) + ((c * d) + ((e * f) + ((g * h) + ((i * j) + ((a * c) + ((b * d) + ((e * g) + ((f * h) + (i * a)))))))));
print(r);
let s: integer = 0;
let k: integer = 0;
while (k < 3) {
    s = s + (a * b) + ((c * d) + ((e * f) + ((g * h) + ((i * j) + (k * k)))));
    k = k + 1;
}
print(s);
print(a + b + c + d + e + f + g + h + i + j);
