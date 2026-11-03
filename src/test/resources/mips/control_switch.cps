let op: integer = 2;
let r: integer = 0;
switch (op) {
    case 1:
        r = 10;
        break;
    case 2:
        r = 20;
    default:
        r = r + 30;
}
print(r);
op = 5;
switch (op) {
    case 1:
        print(1);
        break;
    default:
        print(99);
}
op = 1;
switch (op) {
    case 1:
        print(1);
        break;
    case 2:
        print(2);
}
