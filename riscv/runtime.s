    .section .text.init,"ax",@progbits
    .globl _start
_start:
    la sp, stackTop
    li t0, 0x6000
    csrs mstatus, t0
    la t0, heapStart
    la t1, rtHeapPtr
    sd t0, 0(t1)
    call main
    li a0, 0
    j rtExit

    .text

    .globl rtExit
rtExit:
    li t0, 0x100000
    bnez a0, 1f
    li t1, 0x5555
    sw t1, 0(t0)
    j 2f
1:  slli t1, a0, 16
    li t2, 0x3333
    or t1, t1, t2
    sw t1, 0(t0)
2:  j 2b

rtWrite:
    li t6, 0x10000000
1:  beqz a1, 2f
    lbu t5, 0(a0)
    sb t5, 0(t6)
    addi a0, a0, 1
    addi a1, a1, -1
    j 1b
2:  ret

    .globl printStr
printStr:
    ld a1, 0(a0)
    addi a0, a0, 8
    j rtWrite

    .globl printBool
printBool:
    beqz a0, 1f
    la a0, rtSTrue
    j printStr
1:  la a0, rtSFalse
    j printStr

    .globl printInt
printInt:
    addi sp, sp, -48
    sd ra, 40(sp)
    mv t0, a0
    addi t1, sp, 23
    li t3, 0
    bgez t0, 1f
    li t3, 1
    neg t0, t0
1:  li t2, 10
2:  remu t4, t0, t2
    addi t4, t4, 48
    sb t4, 0(t1)
    addi t1, t1, -1
    divu t0, t0, t2
    bnez t0, 2b
    beqz t3, 3f
    li t4, 45
    sb t4, 0(t1)
    addi t1, t1, -1
3:  addi a0, t1, 1
    addi t5, sp, 24
    sub a1, t5, a0
    call rtWrite
    ld ra, 40(sp)
    addi sp, sp, 48
    ret

    .globl printDos
printDos:
    addi sp, sp, -64
    sd ra, 56(sp)
    sd s0, 48(sp)
    sd s1, 40(sp)
    fsd fs0, 32(sp)
    fmv.d fs0, fa0
    fmv.x.d t0, fs0
    bgez t0, 1f
    fneg.d fs0, fs0
    la a0, rtSMinus
    call printStr
1:  fcvt.l.d s0, fs0, rtz
    fcvt.d.l ft0, s0
    fsub.d ft1, fs0, ft0
    la t0, rtDMillion
    fld ft2, 0(t0)
    fmul.d ft1, ft1, ft2
    fcvt.l.d s1, ft1, rne
    li t0, 10000000000
    blt s1, t0, 2f
    addi s0, s0, 1
    li s1, 0
2:  mv a0, s0
    call printInt
    la a0, rtSDot
    call printStr
    mv t0, s1
    addi t1, sp, 9
    li t3, 10
    li t2, 10
3:  remu t4, t0, t2
    addi t4, t4, 48
    sb t4, 0(t1)
    divu t0, t0, t2
    addi t1, t1, -1
    addi t3, t3, -1
    bnez t3, 3b
    li t3, 10
4:  li t4, 1
    ble t3, t4, 5f
    add t1, sp, t3
    lbu t4, -1(t1)
    li t2, 48
    bne t4, t2, 5f
    addi t3, t3, -1
    j 4b
5:  mv a0, sp
    mv a1, t3
    call rtWrite
    fld fs0, 32(sp)
    ld s1, 40(sp)
    ld s0, 48(sp)
    ld ra, 56(sp)
    addi sp, sp, 64
    ret

    .globl powInt
powInt:
    li t0, 1
    bltz a1, 3f
1:  beqz a1, 2f
    mul t0, t0, a0
    addi a1, a1, -1
    j 1b
2:  mv a0, t0
    ret
3:  li a0, 0
    ret

    .globl powDosInt
powDosInt:
    la t0, rtDOne
    fld ft0, 0(t0)
    fmv.d ft1, ft0
    mv t1, a0
    bgez t1, 1f
    neg t1, t1
1:  beqz t1, 2f
    fmul.d ft0, ft0, fa0
    addi t1, t1, -1
    j 1b
2:  bgez a0, 3f
    fdiv.d ft0, ft1, ft0
3:  fmv.d fa0, ft0
    ret

    .globl rtAlloc
rtAlloc:
    addi sp, sp, -96
    sd a1, 0(sp)
    sd a2, 8(sp)
    sd a3, 16(sp)
    sd a4, 24(sp)
    sd a5, 32(sp)
    sd a6, 40(sp)
    sd a7, 48(sp)
    sd t5, 56(sp)
    sd t6, 64(sp)
    sd ra, 88(sp)
    addi a0, a0, 15
    andi a0, a0, -16
    addi a0, a0, 16
    sd a0, 72(sp)

    la t0, rtHeapPtr
    ld t1, 0(t0)
    add t2, t1, a0
    bltu t2, t1, rtAllocCollect
    la t3, heapEnd
    bgtu t2, t3, rtAllocCollect
    ori t3, a0, 1
    sd t3, 0(t1)
    sd zero, 8(t1)
    sd t2, 0(t0)
    addi a0, t1, 16
    j rtAllocDone

rtAllocCollect:
    call rtCollect
    ld t2, 72(sp)
    la t0, heapStart
    la t1, rtHeapPtr
    ld t1, 0(t1)
rtAllocFindFree:
    bgeu t0, t1, rtOom
    ld t3, 0(t0)
    andi t4, t3, 1
    bnez t4, rtAllocNextBlock
    andi t5, t3, -16
    bltu t5, t2, rtAllocNextBlock
    sub t6, t5, t2
    li t4, 32
    bltu t6, t4, rtAllocTakeWhole
    ori t4, t2, 1
    sd t4, 0(t0)
    sd zero, 8(t0)
    add t4, t0, t2
    sd t6, 0(t4)
    sd zero, 8(t4)
    addi a0, t0, 16
    j rtAllocDone
rtAllocTakeWhole:
    ori t5, t5, 1
    sd t5, 0(t0)
    sd zero, 8(t0)
    addi a0, t0, 16
    j rtAllocDone
rtAllocNextBlock:
    andi t3, t3, -16
    add t0, t0, t3
    j rtAllocFindFree
rtAllocDone:
    ld a1, 0(sp)
    ld a2, 8(sp)
    ld a3, 16(sp)
    ld a4, 24(sp)
    ld a5, 32(sp)
    ld a6, 40(sp)
    ld a7, 48(sp)
    ld t5, 56(sp)
    ld t6, 64(sp)
    ld ra, 88(sp)
    addi sp, sp, 96
    ret

    .globl rtCollect
rtCollect:
    addi sp, sp, -112
    sd s0, 0(sp)
    sd s1, 8(sp)
    sd s2, 16(sp)
    sd s3, 24(sp)
    sd s4, 32(sp)
    sd s5, 40(sp)
    sd s6, 48(sp)
    sd s7, 56(sp)
    sd s8, 64(sp)
    sd s9, 72(sp)
    sd s10, 80(sp)
    sd s11, 88(sp)
    sd ra, 104(sp)

    la s0, gcStaticStart
    la s1, gcStaticEnd
rtCollectStaticRoots:
    bgeu s0, s1, rtCollectStackBegin
    ld a0, 0(s0)
    call rtMark
    addi s0, s0, 8
    j rtCollectStaticRoots

rtCollectStackBegin:
    mv s0, sp
    la s1, stackTop
rtCollectStackRoots:
    bgeu s0, s1, rtCollectSweep
    ld a0, 0(s0)
    call rtMark
    addi s0, s0, 8
    j rtCollectStackRoots

rtCollectSweep:
    call rtSweep
    ld s0, 0(sp)
    ld s1, 8(sp)
    ld s2, 16(sp)
    ld s3, 24(sp)
    ld s4, 32(sp)
    ld s5, 40(sp)
    ld s6, 48(sp)
    ld s7, 56(sp)
    ld s8, 64(sp)
    ld s9, 72(sp)
    ld s10, 80(sp)
    ld s11, 88(sp)
    ld ra, 104(sp)
    addi sp, sp, 112
    ret

rtMark:
    addi sp, sp, -64
    sd ra, 56(sp)
    sd s0, 48(sp)
    sd s1, 40(sp)
    sd s2, 32(sp)
    mv s0, a0
    la s1, heapStart
    la s2, rtHeapPtr
    ld s2, 0(s2)
rtMarkFindBlock:
    bgeu s1, s2, rtMarkDone
    addi t0, s1, 16
    beq s0, t0, rtMarkBlockFound
    ld t1, 0(s1)
    andi t1, t1, -16
    add s1, s1, t1
    j rtMarkFindBlock

rtMarkBlockFound:
    ld t0, 0(s1)
    andi t1, t0, 1
    beqz t1, rtMarkDone
    andi t1, t0, 2
    bnez t1, rtMarkDone
    ori t0, t0, 2
    sd t0, 0(s1)
    andi t1, t0, -16
    add s2, s1, t1
    addi s0, s1, 16
rtMarkPayload:
    bgeu s0, s2, rtMarkDone
    ld a0, 0(s0)
    call rtMark
    addi s0, s0, 8
    j rtMarkPayload

rtMarkDone:
    ld s2, 32(sp)
    ld s1, 40(sp)
    ld s0, 48(sp)
    ld ra, 56(sp)
    addi sp, sp, 64
    ret

rtSweep:
    la t0, heapStart
    la t1, rtHeapPtr
    ld t1, 0(t1)
rtSweepNext:
    bgeu t0, t1, rtSweepDone
    ld t2, 0(t0)
    andi t3, t2, -16
    andi t4, t2, 1
    beqz t4, rtSweepFreeRun
    andi t4, t2, 2
    beqz t4, rtSweepFreeRun
    andi t2, t2, -3
    sd t2, 0(t0)
    add t0, t0, t3
    j rtSweepNext

rtSweepFreeRun:
    add t4, t0, t3
    mv t5, t3
rtSweepMerge:
    bgeu t4, t1, rtSweepFinishRun
    ld t2, 0(t4)
    andi t3, t2, 1
    beqz t3, rtSweepAbsorbBlock
    andi t3, t2, 2
    bnez t3, rtSweepFinishRun
rtSweepAbsorbBlock:
    andi t3, t2, -16
    add t5, t5, t3
    add t4, t4, t3
    j rtSweepMerge
rtSweepFinishRun:
    sd t5, 0(t0)
    sd zero, 8(t0)
    mv t0, t4
    j rtSweepNext
rtSweepDone:
    ret

    .globl rtStrEq
rtStrEq:
    ld t0, 0(a0)
    ld t1, 0(a1)
    bne t0, t1, 2f
1:  beqz t0, 3f
    lbu t2, 8(a0)
    lbu t3, 8(a1)
    bne t2, t3, 2f
    addi a0, a0, 1
    addi a1, a1, 1
    addi t0, t0, -1
    j 1b
2:  li a0, 0
    ret
3:  li a0, 1
    ret

    .globl matAlloc
matAlloc:
    bltz a0, rtBounds
    bltz a1, rtBounds
    addi sp, sp, -32
    sd ra, 24(sp)
    sd s0, 16(sp)
    sd s1, 8(sp)
    mv s0, a0
    mv s1, a1
    mul a0, s0, s1
    slli a0, a0, 3
    addi a0, a0, 16
    call rtAlloc
    sd s0, 0(a0)
    sd s1, 8(a0)
    ld s1, 8(sp)
    ld s0, 16(sp)
    ld ra, 24(sp)
    addi sp, sp, 32
    ret

    .globl matGet
matGet:
    ld t0, 0(a0)
    bgeu a1, t0, rtBounds
    ld t0, 8(a0)
    bgeu a2, t0, rtBounds
    mul t1, a1, t0
    add t1, t1, a2
    slli t1, t1, 3
    add t1, t1, a0
    fld fa0, 16(t1)
    ret

    .globl matSet
matSet:
    ld t0, 0(a0)
    bgeu a1, t0, rtBounds
    ld t0, 8(a0)
    bgeu a2, t0, rtBounds
    mul t1, a1, t0
    add t1, t1, a2
    slli t1, t1, 3
    add t1, t1, a0
    fsd fa0, 16(t1)
    ret

    .globl matMul
matMul:
    addi sp, sp, -64
    sd ra, 56(sp)
    sd s0, 48(sp)
    sd s1, 40(sp)
    sd s2, 32(sp)
    sd s3, 24(sp)
    sd s4, 16(sp)
    sd s5, 8(sp)
    mv s0, a0
    mv s1, a1
    ld t0, 8(s0)
    ld t1, 0(s1)
    bne t0, t1, rtDims
    ld a0, 0(s0)
    ld a1, 8(s1)
    call matAlloc
    mv s2, a0
    ld s3, 0(s0)
    ld s4, 8(s1)
    ld s5, 8(s0)
    li t0, 0
1:  bge t0, s3, 6f
    li t1, 0
2:  bge t1, s4, 5f
    fmv.d.x ft0, zero
    li t2, 0
3:  bge t2, s5, 4f
    mul t3, t0, s5
    add t3, t3, t2
    slli t3, t3, 3
    add t3, t3, s0
    fld ft1, 16(t3)
    mul t4, t2, s4
    add t4, t4, t1
    slli t4, t4, 3
    add t4, t4, s1
    fld ft2, 16(t4)
    fmadd.d ft0, ft1, ft2, ft0
    addi t2, t2, 1
    j 3b
4:  mul t3, t0, s4
    add t3, t3, t1
    slli t3, t3, 3
    add t3, t3, s2
    fsd ft0, 16(t3)
    addi t1, t1, 1
    j 2b
5:  addi t0, t0, 1
    j 1b
6:  mv a0, s2
    ld s5, 8(sp)
    ld s4, 16(sp)
    ld s3, 24(sp)
    ld s2, 32(sp)
    ld s1, 40(sp)
    ld s0, 48(sp)
    ld ra, 56(sp)
    addi sp, sp, 64
    ret

    .globl matAdd
matAdd:
    li a2, 0
    j rtMatAddsub
    .globl matSub
matSub:
    li a2, 1
rtMatAddsub:
    addi sp, sp, -48
    sd ra, 40(sp)
    sd s0, 32(sp)
    sd s1, 24(sp)
    sd s2, 16(sp)
    mv s0, a0
    mv s1, a1
    mv s2, a2
    ld t0, 0(s0)
    ld t1, 0(s1)
    bne t0, t1, rtDims
    ld t0, 8(s0)
    ld t1, 8(s1)
    bne t0, t1, rtDims
    ld a0, 0(s0)
    ld a1, 8(s0)
    call matAlloc
    ld t0, 0(s0)
    ld t1, 8(s0)
    mul t0, t0, t1
    li t1, 0
1:  bge t1, t0, 4f
    slli t2, t1, 3
    add t3, t2, s0
    fld ft0, 16(t3)
    add t3, t2, s1
    fld ft1, 16(t3)
    bnez s2, 2f
    fadd.d ft0, ft0, ft1
    j 3f
2:  fsub.d ft0, ft0, ft1
3:  add t3, t2, a0
    fsd ft0, 16(t3)
    addi t1, t1, 1
    j 1b
4:  ld s2, 16(sp)
    ld s1, 24(sp)
    ld s0, 32(sp)
    ld ra, 40(sp)
    addi sp, sp, 48
    ret

    .globl matPrint
matPrint:
    addi sp, sp, -48
    sd ra, 40(sp)
    sd s0, 32(sp)
    sd s1, 24(sp)
    sd s2, 16(sp)
    mv s0, a0
    la a0, rtSOpen
    call printStr
    li s1, 0
1:  ld t0, 0(s0)
    bge s1, t0, 6f
    beqz s1, 2f
    la a0, rtSComma
    call printStr
2:  la a0, rtSOpen
    call printStr
    li s2, 0
3:  ld t0, 8(s0)
    bge s2, t0, 5f
    beqz s2, 4f
    la a0, rtSComma
    call printStr
4:  mv a0, s0
    mv a1, s1
    mv a2, s2
    call matGet
    call printDos
    addi s2, s2, 1
    j 3b
5:  la a0, rtSClose
    call printStr
    addi s1, s1, 1
    j 1b
6:  la a0, rtSClose
    call printStr
    ld s2, 16(sp)
    ld s1, 24(sp)
    ld s0, 32(sp)
    ld ra, 40(sp)
    addi sp, sp, 48
    ret

rtDims:
    la a0, rtSDims
    j rtDie
    .globl rtDivZero
rtDivZero:
    la a0, rtSDivzero
    j rtDie
    .globl rtNoMethod
rtNoMethod:
    la a0, rtSNomethod
    j rtDie
    .globl rtBounds
rtBounds:
    la a0, rtSBounds
    j rtDie
rtOom:
    la a0, rtSOom
rtDie:
    call printStr
    li a0, 65
    j rtExit

    .section .rodata
    .balign 8
rtDMillion: .double 10000000000.0
rtDOne:     .double 1.0
    .balign 8
rtSTrue:  .quad 4
            .ascii "true"
    .balign 8
rtSFalse: .quad 5
            .ascii "false"
    .balign 8
rtSMinus: .quad 1
            .ascii "-"
    .balign 8
rtSDot:   .quad 1
            .ascii "."
    .balign 8
rtSOpen:  .quad 1
            .ascii "["
    .balign 8
rtSClose: .quad 1
            .ascii "]"
    .balign 8
rtSComma: .quad 2
            .ascii ", "
    .balign 8
rtSDims:  .quad 2f - 1f
1:          .ascii "Runtime error: matrix dimensions do not match\n"
2:
    .balign 8
rtSBounds: .quad 2f - 1f
1:          .ascii "Runtime error: matrix index out of bounds\n"
2:
    .balign 8
rtSOom:   .quad 2f - 1f
1:          .ascii "Runtime error: out of memory\n"
2:
    .balign 8
rtSDivzero: .quad 2f - 1f
1:          .ascii "Runtime error: division by zero\n"
2:
    .balign 8
rtSNomethod: .quad 2f - 1f
1:          .ascii "Runtime error: object has no such method\n"
2:

    .data
    .balign 8
rtHeapPtr: .quad 0
