ENTRY(_start)
SECTIONS
{
    . = 0x80000000;
    .text : { *(.text.init) *(.text*) }
    .rodata : { *(.rodata*) }
    .data : {
        gcStaticStart = .;
        *(.data*) *(.sdata*)
        gcStaticEnd = .;
    }
    .bss : { *(.bss*) *(.sbss*) *(COMMON) }
    . = ALIGN(16);
    . += 0x1000000;
    stackTop = .;
    heapStart = .;
    . += 0x30000000;
    heapEnd = .;
}
