.text
.code16
set_video_mode:/*dhulbDoc-v200:function;s16 set_video_mode(u8) call16;*/
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
	popw %bp
	retw
cursor_pos_x:/*dhulbDoc-v200:globalvar;u8 cursor_pos_x;*/
.globl cursor_pos_x
	.byte 0x00
cursor_pos_y:/*dhulbDoc-v200:globalvar;u8 cursor_pos_y;*/
.globl cursor_pos_y
	.byte 0x00
print_format:/*dhulbDoc-v200:globalvar;u8 print_format;*/
.globl print_format
	.byte 0x07
print:/*dhulbDoc-v200:function;u16 print(a16*u8, u16) call16;*/
.globl print
	pushw %bp
	movw %sp,%bp
	pushw %si
	pushw %di
	pushw %bx
	movw 0x04(%bp),%si
	movw $0xb800,%ax#TODO make based on a global variable
	movw %ax,%es
	movb $0x50,%ah#width
	movb cursor_pos_y(,1),%al
	mulb %ah
	xorb %ch,%ch
	movb cursor_pos_x(,1),%cl
	addw %cx,%ax
	shlw %ax
	movw %ax,%di
	movw 0x06(%bp),%cx
	movb $0x50,%al#width
	xorb %dh,%dh
	movb %al,%dl
	movb $0x19,%ah#height
	mulb %ah
	movw %ax,%bx
	shlw $1,%bx
	movw %dx,%bp
	shlw $1,%bp
	xorb %dl,%dl
	movb print_format(,1),%ah
	xorb $0x77,%es:1(%di)#TODO make optional
	print__read_char:
	lodsb
	testb %al,%al
	jz print__end
	incw %dx
	cmpb $0x0a,%al
	jz print__lf
	cmpb $0x0d,%al
	jz print__cr
	stosw
	print__cont:
	cmpw %di,%bx
	ja print__no_scroll
	pushw %si
	pushw %di
	movw %es,%si
	pushw %ds
	movw %si,%ds#not using segment override prefix because of 8086 bug through which only the last prefix is used if there is an interrupt at a certain time
	xorw %di,%di
	movw %bp,%si
	pushw %cx
	movw %bx,%cx
	subw %bp,%cx
	shrw $1,%cx
	rep
	movsw
	movb $0x20,%al#should this be a space?
	movw %bp,%cx
	shrw $1,%cx
	rep
	stosw
	popw %cx
	popw %ds
	popw %di
	popw %si
	subw %bp,%di
	print__no_scroll:
	loop print__read_char
	print__end:
	xorb $0x77,%es:1(%di)#TODO make optional
	movw %di,%ax
	shrw %ax
	movb $0x50,%cl#width
	divb %cl
	movb %al,cursor_pos_y(,1)
	movb %ah,cursor_pos_x(,1)
	movw %dx,%ax
	popw %bx
	popw %di
	popw %si
	popw %bp
	retw
	print__lf:
	addw %bp,%di
	/**/#TODO optional `jmp print__cont'
	print__cr:
	pushw %ax
	pushw %dx
	movw %di,%ax
	xorw %dx,%dx
	divw %bp
	subw %dx,%di
	popw %dx
	popw %ax
	jmp print__cont
readScancode:/*dhulbDoc-v201:function;u16 readScancode() call16;*/
.globl readScancode
	xorw %ax,%ax
	int $0x16
	retw
in_buffer:
.byte 0x00
.byte 0x00
in:/*dhulbDoc-v202:function;u8 in() call16;*/
	xorw %ax,%ax
	int $0x16
	cmpb $0x40,%al
	jae in__end
	cmpw $0x1c0d,%ax
	jz in__enter
	jmp in__end
	in__enter:
	movb $0x0a,%al
	retw
	jmp in__end
	#TODO actually process more stuff
	in__end:
	retw
