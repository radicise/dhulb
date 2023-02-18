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
