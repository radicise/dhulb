.text
.code16
booting_start:
ljmp $0x07c0,$0x0005
#TODO save registers
movw $0x07c0,%ax
movw %ax,%ds
read:
movw $0x0211,%ax
movw $0x0002,%cx
movb $0x00,%dh
movw $0x07c0,%bx
movw %bx,%es
movw %bx,%ds
movw %bx,%ss
movw $0xfff0,%sp
movw $0x0200,%bx
clc
int $0x13
jc booting_on_error
cld
callw entry
prgm_aft:
nop
nop
nop
nop
jmp prgm_aft
booting_on_error:
nop
nop
nop
nop
jmp booting_on_error
.set bytes_code, . - booting_start
.space 510 - bytes_code
.byte 0x55
.byte 0xaa
