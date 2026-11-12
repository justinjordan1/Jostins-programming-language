ENTRY(_start)
SECTIONS
{
    . = 0x80000000;
    .text : { *(.text.init) *(.text*) }
    .rodata : { *(.rodata*) }
    .data : { *(.data*) *(.sdata*) }
    .bss : { *(.bss*) *(.sbss*) *(COMMON) }
    . = ALIGN(16);
    . += 0x1000000;
    __stack_top = .;
    __heap_start = .;
    . += 0x30000000;
    __heap_end = .;
}
