# Welcome to the C10H15N language!

Created in Spring 2023 by Jostin
_____
src.C10H15N is a language whos primary purpose (at least at the present moment) is to create "math explainers", in other
words, animations that visually display mathematical concepts. One might say "why make a whole language for this" to
which I say, uh... next question.
____

#### Special features I hope to implement

1. Robust linear algebra tools, maybe even a seperate matricies data structure (although it feels kind of redundant).
   Support for multiplication/division, addition/subtracting, rotation, finding inverses/determinants, etc.
2. Support for defining a list of numbers with arithmetic or geometric sequences. `someArray int[] = [0,1,2...10];`
   might make an integer array of 11 numbers from 1-11.
3. Support for like... graphics in general? just the ability to draw things to a screen.
4. Maybe building a graphing calculator on top of the previous feature.

___

## Syntax and stuff

Statically typed, just in case I want to build a compiler over the summer block comments are supported.`ç` starts
one,`ø` ends one. (key combos are option c and o respectively)

### Variable Declarations

_____

- initialization  `var IDENTIFIERLIST type = expression;`
- Declaration `var IDENTIFIERLIST type;`
- Assignment `IDENTIFIER = expression`

### Types

| Syntax    | Description |
|-----------|------------|
| Boolean   | `George`   |
| Interger  | `int`      |
| String    | `String`   |
| Charector | `char`     |
| Double    | `dos`      |

____

### Functions

- Declaration `functionName: returnType(p1,p2...etc) returnType {}`
- The absence of a return statement will be handled by returning the last line of the function. To return `void`, do
  exactly that

___

### Looping

___

##### while loops

- `while (condition/expression) {} ` regular while loop
- `indefinitleyPreform {}` Infinite while loop
- `whileThisIsBasicllyTrue (condition/expression) %error {}` will work with ints and strings

##### for loops

- `foreach (var ∆ vars) {} `
- `for (variable initialization; expression)¬+ {}` for loop

##### Conditionals

standard if, else, elseif

```
if conditional {
} elseif conditional {
} else conditional {
}
```

### Operaters

| Operaters (these will also work with all matricies, unless theres an asterisk)                                                           | symbol    |
|------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| sum                                                                                                                                      | +         |
| multiply (cross product)                                                                                                                 | *         |
| divide                                                                                                                                   | /         |
| subtract                                                                                                                                 | -         |
| exponentiate                                                                                                                             | î         |
| inverse                                                                                                                                  | í         | 
| modulus                                                                                                                                  | %         |
| dot product                                                                                                                              | ˚         |
| equal to                                                                                                                                 | ==        |
| unequal to                                                                                                                               | =/=       |
| All standard comparison operands*                                                                                                        | <,>,<=,>= |
| standard boolean operations, but when used on collections theyre interpreted as set notation (if it makes sense). ex`&&` is intersection | &, !, OR  |
| incrementors and decrementors*                                                                                                           | ++,--     |

----

### Collections

Declarations /initialization

- `collection name int[] = 1,2,3,4;`
- `collection name int[4];`
- `collection linkedList LinkedList<Integer]<<>> = << 1, 2, 3, 4, 5 >> `

- `collection matrix int[][] = [[1, 2, 3]\[4, 5, 6]\ [7, 8, 9]]`
- `collection name int[5]`
- `collection name int<<>>`
- `collection name int[[]] = NUMBER'NUMBER`

Getter

- ` GET name<0>, GET name[0], GET name[[0,0,0]]`

*Matrix Syntax*

Declaration -`name matrix = 2x2;`

- `name matrix = [1,2\3,4];`

Getter

- `name matrix[0,0]`

----

### Special functions

All the matrix stuff as mentioned before. Print `print()`
Create a canvas `canvas (rgb) size(integer,integer)` (also creates a matrix/2d array of the canvas)
You manipulate the canvas by changing the rgb values in the 2darray

Maybe support for standard function LateX function notation, and a graphing calculator built on top of the canvas stuff.

### Keywords Enumeration

WHILE, INDEFINITELYPERFORM, WHILETHISISBASICALLYTRUE, FOR, FOREACH, GEORGE, INTERGER, STRING, MATRIX, CHAR, DOS, GET,
COLLECTION














