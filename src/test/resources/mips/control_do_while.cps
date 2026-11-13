let i: integer = 10;
do {
    print(i);
    i = i + 1;
} while (i < 3);

let s: integer = 0;
let k: integer = 1;
do {
    s = s + k;
    k = k + 1;
    if (k == 4) {
        continue;
    }
} while (k <= 5);
print(s);

let b: integer = 0;
do {
    b = b + 1;
    if (b == 3) {
        break;
    }
} while (true);
print(b);
