# C10H15N


The name is cringe but I was 16 what are you gonna do 

Created in Spring 2023 by Jostin
_____
src.C10H15N is a language whos primary purpose (at least at the present moment) is to create "math explainers", in other
words, animations that visually display mathematical concepts. One might say "why make a whole language for this" to
which I say, uh... next question.
____
=======
Created in Spring 2023 by Jostin.


C10H15N is a tiny programming language for making math explainers, matrix experiments, and weird little ray-traced point-cloud animations. Why make a whole language for this? next question.

Files usually use `.mexplainerth`.

---

## Run It

From the repo root:

```bash
javac -d out/classes $(find src -name '*.java')
java -cp out/classes src.C10H15N testInput/VarTest.mexplainerth
```

Run the ray tracer / GIF thing:

```bash
java -cp out/classes src.C10H15N testInput/metalRayTracer.mexplainerth
```

That writes `photoreal_spin.gif` when it finishes.

---

## Program Shape

Most plain statements end with `;`. Blocks use `{ ... }`.

```mexplainerth
var x interger = 10;
x = x + 1;
return x;
```

Block comments:

```mexplainerth
ç
comment goes here
ø
```

---

## Values And Types

```mexplainerth
10
3.14
"jostin"
true
false
```

| Type | Meaning |
|---|---|
| `interger` | integer |
| `dos` | decimal / double |
| `george` | boolean |
| `string` | string |
| `char` | character keyword |
| `matrix` | matrix value |
| `object` | class instance / native object |

The language is statically typed-ish. The spelling is part of the artifact.

---

## Variables

```mexplainerth
var x interger = 10;
var y dos = 3.14;
var name string = "jostin";
var ready george = true;
var a,b interger = 0;

x = x + 1;
```

Variable declarations currently include an assignment. Assignments can update variables, object fields, and matrix cells.

---

## Functions

```mexplainerth
square : dos(interger x) {
    return x î 2;
}

return square(9);
```

Multiple parameters work:

```mexplainerth
add : interger(interger a, interger b) {
    return a + b;
}
```

Empty parameter lists work too:

```mexplainerth
meaning : interger() {
    return 42;
}
```

---

## Classes And Objects

```mexplainerth
class Point {
    var x interger = 0;
    var y interger = 0;

    init : interger(interger startX, interger startY) {
        this.x = startX;
        this.y = startY;
        return 0;
    }

    move : interger(interger dx, interger dy) {
        x = x + dx;
        y = y + dy;
        return x + y;
    }
}

var p object = Point(3, 4);
p.move(10, 20);
return p.x + p.y;
```

Inside methods, fields can be accessed as `this.x` or bare `x`.

---

## Conditionals

```mexplainerth
if (x < 10) {
    x = 10;
} elseif (x == 20) {
    x = 21;
} else {
    x = 0;
}
```

---

## Loops

For loops:

```mexplainerth
for (var i interger = 0; i < 10) ¬+ {
    print(i);
}
```

Use `¬-` to count down:

```mexplainerth
for (var i interger = 10; i > 0) ¬- {
    print(i);
}
```

While loops:

```mexplainerth
while (x < 10) {
    x = x + 1;
}
```

Infinite loop:

```mexplainerth
indefinitlypreform {
    break;
}
```

Fuzzy loop:

```mexplainerth
whilethisisbasicallytrue (counter < target) 10% {
    counter = counter + 1;
}
```

Foreach loops:

```mexplainerth
foreach (value ∆ values) {
    print(value);
}
```

`break;` exits loops.

---

## Operators

| Operation | Symbol |
|---|---|
| add | `+` |
| subtract | `-` |
| multiply | `*` |
| divide | `/` |
| exponent | `î` |
| inverse | `í` |
| modulo | `%%` |
| dot product | `˚` |
| equal | `==` |
| comparisons | `<`, `>`, `<=`, `>=` |
| boolean and/or/not | `&`, `|`, `!` |

Matrix operations use the same arithmetic operators where it makes sense.

---

## Collections

Matrices are the useful collection right now.

```mexplainerth
collection point matrix[[]] = [[1]\[2]\[3]];
collection scratch matrix[[]] = 200`100;
```

Arrays and linked lists have parser/evaluator support, but they are rougher than matrices:

```mexplainerth
collection nums interger[] = [1,2,3];
collection empty interger[10];
collection list interger<<>> = <<1,2,3>>;
```

Matrix indexing is the reliable path right now; array/list indexing still needs love.

---

## Matrices

Literal matrix:

```mexplainerth
collection a matrix[[]] = [[1,2,3]\[4,5,6]];
collection b matrix[[]] = [[7,8]\[9,10]\[11,12]];
collection product matrix[[]] = a * b;
```

Sized matrix:

```mexplainerth
var width interger = 200;
var height interger = 200;
collection zBuffer matrix[[]] = width`height;
```

Getter and setter:

```mexplainerth
zBuffer[[y,x]] = 10000.0;
var depth dos = get zBuffer[[y,x]];
```

Vector helper style:

```mexplainerth
vec3 : matrix(dos x, dos y, dos z) {
    return [[x]\[y]\[z]];
}
```

Dot product returns a `1x1` matrix, so grab the cell:

```mexplainerth
dot : dos(matrix a, matrix b) {
    collection d matrix[[]] = a ˚ b;
    return get d[[0,0]];
}
```

---

## Canvas And GIFs

Create a canvas:

```mexplainerth
var screen object = Canvas();
var big object = Canvas(600, 400, 1200, 800);
```

The first two numbers are the pixel grid. The last two are the window size.

Draw pixels:

```mexplainerth
screen.setPixel(x, y, 255, 128, 0);
```

Other canvas methods:

```mexplainerth
screen.setGridSize(200, 200);
screen.setWindowSize(800, 800);
screen.captureFrame();
screen.collapse("spin.gif", 4);
screen.exportPPM("frame.ppm");
```

`collapse` turns captured frames into a GIF. The second argument is the output scale factor.

---

## Built-Ins

```mexplainerth
print("hello");
random();
random(-1.0, 1.0);
sin(angle);
cos(angle);
Canvas();
Canvas(width, height);
Canvas(width, height, windowWidth, windowHeight);
```

`print` writes its arguments directly, without adding a newline.

---

## Keywords

```text
var return collection class matrixsize
if elseif else
while for foreach break indefinitlypreform whilethisisbasicallytrue
get true false
interger dos george string char matrix object
```

---

## Samples

| File | What it is |
|---|---|
| `testInput/VarTest.mexplainerth` | variables |
| `testInput/functionTest.mexplainerth` | functions |
| `testInput/conditionalTester.mexplainerth` | if / elseif / else |
| `testInput/LOOPTest.mexplainerth` | loops |
| `testInput/foreachTest.mexplainerth` | foreach over collections and objects |
| `testInput/matrixMath.mexplainerth` | matrix multiplication |
| `testInput/matrixDynamicIndex.mexplainerth` | matrix indexing with variables |
| `testInput/classTest.mexplainerth` | classes and methods |
| `testInput/canvasTest.mexplainerth` | drawing to a canvas |
| `testInput/rayTracer.mexplainerth` | old ASCII ray tracer |
| `testInput/metalRayTracer.mexplainerth` | path-traced point-cloud GIF monster |

---

## typos are intentional

`interger` and `indefinitlypreform` are spelled exactly like that.

`%` belongs to `whilethisisbasicallytrue`; use `%%` for modulo.

`=/=` appears in older grammar notes, but the lexer does not currently recognize it. Use `!(a == b)` for now.
