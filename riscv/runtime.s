    .section .text.init,"ax",@progbits
    .globl _start
_start:
    la sp, rt_stack_top
    li t0, 0x6000
    csrs mstatus, t0
    la t0, rt_heap
    la t1, rt_heap_ptr
    sd t0, 0(t1)
    call main
    li a0, 0
    j rt_exit

    .text

    .globl rt_exit
rt_exit:
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

rt_write:
    li t6, 0x10000000
1:  beqz a1, 2f
    lbu t5, 0(a0)
    sb t5, 0(t6)
    addi a0, a0, 1
    addi a1, a1, -1
    j 1b
2:  ret

    .globl print_str
print_str:
    ld a1, 0(a0)
    addi a0, a0, 8
    j rt_write

    .globl print_bool
print_bool:
    beqz a0, 1f
    la a0, rt_s_true
    j print_str
1:  la a0, rt_s_false
    j print_str

    .globl print_int
print_int:
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
    call rt_write
    ld ra, 40(sp)
    addi sp, sp, 48
    ret

    .globl print_dos
print_dos:
    addi sp, sp, -64
    sd ra, 56(sp)
    sd s0, 48(sp)
    sd s1, 40(sp)
    fsd fs0, 32(sp)
    fmv.d fs0, fa0
    fmv.x.d t0, fs0
    bgez t0, 1f
    fneg.d fs0, fs0
    la a0, rt_s_minus
    call print_str
1:  fcvt.l.d s0, fs0, rtz
    fcvt.d.l ft0, s0
    fsub.d ft1, fs0, ft0
    la t0, rt_d_million
    fld ft2, 0(t0)
    fmul.d ft1, ft1, ft2
    fcvt.l.d s1, ft1, rne
    li t0, 1000000
    blt s1, t0, 2f
    addi s0, s0, 1
    li s1, 0
2:  mv a0, s0
    call print_int
    la a0, rt_s_dot
    call print_str
    mv t0, s1
    addi t1, sp, 5
    li t3, 6
    li t2, 10
3:  remu t4, t0, t2
    addi t4, t4, 48
    sb t4, 0(t1)
    divu t0, t0, t2
    addi t1, t1, -1
    addi t3, t3, -1
    bnez t3, 3b
    li t3, 6
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
    call rt_write
    fld fs0, 32(sp)
    ld s1, 40(sp)
    ld s0, 48(sp)
    ld ra, 56(sp)
    addi sp, sp, 64
    ret

    .globl pow_int
pow_int:
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

    .globl pow_dos_int
pow_dos_int:
    la t0, rt_d_one
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

    .globl mat_alloc
mat_alloc:
    mul t0, a0, a1
    slli t0, t0, 3
    addi t0, t0, 16
    la t1, rt_heap_ptr
    ld t2, 0(t1)
    add t3, t2, t0
    addi t3, t3, 15
    andi t3, t3, -16
    la t4, rt_heap_end
    bgtu t3, t4, rt_oom
    sd t3, 0(t1)
    sd a0, 0(t2)
    sd a1, 8(t2)
    mv a0, t2
    ret

    .globl mat_get
mat_get:
    ld t0, 0(a0)
    bgeu a1, t0, rt_bounds
    ld t0, 8(a0)
    bgeu a2, t0, rt_bounds
    mul t1, a1, t0
    add t1, t1, a2
    slli t1, t1, 3
    add t1, t1, a0
    fld fa0, 16(t1)
    ret

    .globl mat_set
mat_set:
    ld t0, 0(a0)
    bgeu a1, t0, rt_bounds
    ld t0, 8(a0)
    bgeu a2, t0, rt_bounds
    mul t1, a1, t0
    add t1, t1, a2
    slli t1, t1, 3
    add t1, t1, a0
    fsd fa0, 16(t1)
    ret

    .globl mat_mul
mat_mul:
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
    bne t0, t1, rt_dims
    ld a0, 0(s0)
    ld a1, 8(s1)
    call mat_alloc
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

    .globl mat_add
mat_add:
    li a2, 0
    j rt_mat_addsub
    .globl mat_sub
mat_sub:
    li a2, 1
rt_mat_addsub:
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
    bne t0, t1, rt_dims
    ld t0, 8(s0)
    ld t1, 8(s1)
    bne t0, t1, rt_dims
    ld a0, 0(s0)
    ld a1, 8(s0)
    call mat_alloc
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

    .globl mat_print
mat_print:
    addi sp, sp, -48
    sd ra, 40(sp)
    sd s0, 32(sp)
    sd s1, 24(sp)
    sd s2, 16(sp)
    mv s0, a0
    la a0, rt_s_open
    call print_str
    li s1, 0
1:  ld t0, 0(s0)
    bge s1, t0, 6f
    beqz s1, 2f
    la a0, rt_s_comma
    call print_str
2:  la a0, rt_s_open
    call print_str
    li s2, 0
3:  ld t0, 8(s0)
    bge s2, t0, 5f
    beqz s2, 4f
    la a0, rt_s_comma
    call print_str
4:  mv a0, s0
    mv a1, s1
    mv a2, s2
    call mat_get
    call print_dos
    addi s2, s2, 1
    j 3b
5:  la a0, rt_s_close
    call print_str
    addi s1, s1, 1
    j 1b
6:  la a0, rt_s_close
    call print_str
    ld s2, 16(sp)
    ld s1, 24(sp)
    ld s0, 32(sp)
    ld ra, 40(sp)
    addi sp, sp, 48
    ret

rt_dims:
    la a0, rt_s_dims
    j rt_die
rt_bounds:
    la a0, rt_s_bounds
    j rt_die
rt_oom:
    la a0, rt_s_oom
rt_die:
    call print_str
    li a0, 65
    j rt_exit

    .section .rodata
    .balign 8
rt_d_million: .double 1000000.0
rt_d_one:     .double 1.0
    .balign 8
rt_s_true:  .quad 4
            .ascii "true"
    .balign 8
rt_s_false: .quad 5
            .ascii "false"
    .balign 8
rt_s_minus: .quad 1
            .ascii "-"
    .balign 8
rt_s_dot:   .quad 1
            .ascii "."
    .balign 8
rt_s_open:  .quad 1
            .ascii "["
    .balign 8
rt_s_close: .quad 1
            .ascii "]"
    .balign 8
rt_s_comma: .quad 2
            .ascii ", "
    .balign 8
rt_s_dims:  .quad 2f - 1f
1:          .ascii "Runtime error: matrix dimensions do not match\n"
2:
    .balign 8
rt_s_bounds: .quad 2f - 1f
1:          .ascii "Runtime error: matrix index out of bounds\n"
2:
    .balign 8
rt_s_oom:   .quad 2f - 1f
1:          .ascii "Runtime error: out of memory\n"
2:

    .data
    .balign 8
rt_heap_ptr: .quad 0

    .bss
    .balign 16
rt_heap:      .space 16777216
rt_heap_end:
rt_stack:     .space 1048576
rt_stack_top:
