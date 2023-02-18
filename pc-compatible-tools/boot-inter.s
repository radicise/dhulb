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
.text
.code16
set_video_mode: #dhulbDoc-v1: s16 set_video_mode(u8)
	xorb %ah,%ah
	pushw %bp
	movw %sp,%bp
	movb 0x04(%bp),%al
	int $0x10
	jc set_video_mode__on_error
	xorb %al,%al
	popw %bp
	retw
	set_video_mode__on_error:
	movb $0x01,%al
	retw

cursor_pos_x: #dhulbDoc-v1: u8 cursor_pos_x
.globl cursor_pos_x
	.byte 0x00

cursor_pos_y: #dhulbDoc-v1: u8 cursor_pos_y
.globl cursor_pos_y
	.byte 0x00

print_format: #dhulbDoc-v1: u8 print_format
.globl print_format
	.byte 0x07

print: #dhulbDoc-v1: u16 print(a16*u8, u16)
.globl print
	pushw %bp
	movw %sp,%bp
	pushw %si
	pushw %di
	movw 0x04(%bp),%si
	movw $0xb800,%ax
	movw %ax,%es
	movb $0x19,%ah#width
	movb cursor_pos_y(,1),%al
	mulb %ah
	xorb %ch,%ch
	movb cursor_pos_x(,1),%cl
	addw %cx,%ax
	shlw %ax
	movw %ax,%di
	movw 0x06(%bp),%cx
	xorw %dx,%dx
	movb print_format(,1),%ah
	print__read_char:
	lodsb
	testb %al,%al
	jz print__end
	stosw
	incw %dx
	decw %cx
	jz print__end
	jmp print__read_char
	print__end:
	movw %di,%ax
	shrw %ax
	movb $0x19,%cl#width
	divb %cl
	movb %al,cursor_pos_y(,1)
	movb %ah,cursor_pos_x(,1)
	movw %dx,%ax
	popw %di
	popw %si
	movw %bp,%sp
	popw %bp
	retw
.text
___dhulbres__ccd9dafafbc7e0fe__0:
.asciz "HELLO, WORLD, FROM THE DHULB PROGRAMMING LANGUAGE, RUNNING WITH NO UNDERLYING SYSTEM"
.code16
entry:/*dhulbDoc-v200:function;s16 entry() call16;*/
.globl entry
pushw %bp
movw %sp,%bp
xorb %ah,%ah
movb $0x03,%al
xorb %ah,%ah
pushw %ax
movw $set_video_mode,%ax
movw %ax,%bx
callw *%bx
testw %ax,%ax
jz ___dhulbres__ccd9dafafbc7e0fe__1
xorb %ah,%ah
movb $0x01,%al
movw %bp,%sp
popw %bp
retw
___dhulbres__ccd9dafafbc7e0fe__1:
xorw %ax,%ax
pushw %ax
movw $___dhulbres__ccd9dafafbc7e0fe__0,%ax
pushw %ax
movw $print,%ax
movw %ax,%bx
callw *%bx
addw $0x08,%sp
xorw %ax,%ax
movw %bp,%sp
popw %bp
retw
