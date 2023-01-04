.text
.code16
booting_start:
#TODO save registers
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
callw entry
hlt
booting_on_error:
hlt
.set bytes_code, . - booting_start
.space 510 - bytes_code
.byte 0x55
.byte 0xaa
