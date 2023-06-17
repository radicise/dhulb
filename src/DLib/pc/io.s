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
fbprint:/*dhulbDoc-v300:function;u16 fbprint(a16*u8, u16, a16*VGAMode3Out) call16;*/
.globl fbprint
	pushw %bp
	movw %sp,%bp
	pushw %si
	pushw %di
	movw 0x04(%bp),%si
	movw 0x08(%bp),%bx
	movw (%bx),%ax#seg
	movw %ax,%es
	movb 0x02(%bx),%ah#width
	movb 0x05(%bx),%al#y
	mulb %ah
	xorb %ch,%ch
	movb 0x04(%bx),%cl#x
	addw %cx,%ax
	shlw %ax
	movw %ax,%di
	movw 0x06(%bp),%cx
	movb 0x02(%bx),%al#width
	xorb %dh,%dh
	movb %al,%dl
	movb 0x03(%bx),%ah#height
	mulb %ah
	movw %ax,%bp
	shlw $1,%bp
	movb 0x06(%bx),%ah#format
	pushw %bx
	movw %bp,%bx
	movw %dx,%bp
	shlw $1,%bp
	xorb %dl,%dl
	xorb $0x77,%es:1(%di)#TODO make optional
	fbprint__read_char:
	lodsb
	/*
	testb %al,%al
	jz fbprint__end
	*/#TODO perhaps make this optional or have a different function, which allows this to take place
	incw %dx
	#TODO avoid checking for control codes if the value is in the printable range
	cmpb $0x0a,%al
	jz fbprint__lf
	cmpb $0x0d,%al
	jz fbprint__cr
	cmpb $0x1b,%al
	jz fbprint__esc
	stosw
	fbprint__cont:
	cmpw %di,%bx
	ja fbprint__no_scroll
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
	fbprint__no_scroll:
	loop fbprint__read_char
	fbprint__end:
	xorb $0x77,%es:1(%di)#TODO make optional
	movw %di,%ax
	shrw %ax
	popw %bx
	movb 0x02(%bx),%cl#width
	divb %cl
	movb %al,0x05(%bx)#y
	movb %ah,0x04(%bx)#x
	movw %dx,%ax
	popw %di
	popw %si
	popw %bp
	retw
	fbprint__lf:
	addw %bp,%di
	/**/#TODO optional `jmp fbprint__cont'
	fbprint__cr:
	pushw %ax
	pushw %dx
	movw %di,%ax
	xorw %dx,%dx
	divw %bp
	subw %dx,%di
	popw %dx
	popw %ax
	jmp fbprint__cont
	fbprint__esc:
	pushw %es
	pushw %bx
	pushw %dx
	movw %sp,%bx
	movw %ss:0x06(%bx),%bx
	pushw %cx
	pushw %si
	pushw %bx
	decw %cx
	pushw %cx
	pushw %si
	movw %di,%ax
	shrw %ax
	movb 0x02(%bx),%cl#width
	divb %cl
	xchgb %al,%ah
	movw %ax,0x04(%bx)#x, y
	callw fbprint_escapeHandler
	addw $0x06,%sp
	popw %si
	addw %ax,%si
	popw %dx
	addw %ax,%dx
	movw %sp,%bx
	subw %ax,%ss:(%bx)
	movw %ss:0x06(%bx),%bx
	movb 0x02(%bx),%ah#width
	movb 0x05(%bx),%al#y
	mulb %ah
	xorb %ch,%ch
	movb 0x04(%bx),%cl#x
	addw %cx,%ax
	popw %cx
	shlw %ax
	movw %ax,%di
	movb 0x06(%bx),%ah#format
	popw %bx
	popw %es
	#jmp fbprint__end
	jmp fbprint__cont
readScancode:/*dhulbDoc-v201:function;u16 readScancode() call16;*/
.globl readScancode
	xorw %ax,%ax
	int $0x16
	retw
in_buffer:#TODO make the `in' function block until all other threads executing it have finished executing it
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
in:/*dhulbDoc-v202:function;u8 in() call16;*/
	xorw %ax,%ax
	orb in_buffer,%al
	jnz in__buffered
	int $0x16
	cmpb $0x40,%al
	jae in__end
	cmpw $0x1c0d,%ax
	jz in__enter
	cmpw $0x4800,%ax
	jz in__up
	cmpw $0x5000,%ax
	jz in__down
	cmpw $0x4b00,%ax
	jz in__left
	cmpw $0x4d00,%ax
	jz in__right
	jmp in__end
	in__up:
	movw $0x5b,2+in_buffer
	movw $0x3141,in_buffer
	movw $0x1b,%ax
	retw
	in__down:
	movw $0x5b,2+in_buffer
	movw $0x3142,in_buffer
	movw $0x1b,%ax
	retw
	in__right:
	movw $0x5b,2+in_buffer
	movw $0x3143,in_buffer
	movw $0x1b,%ax
	retw
	in__left:
	movw $0x5b,2+in_buffer
	movw $0x3144,in_buffer
	movw $0x1b,%ax
	retw
	in__enter:
	movw $0x0a,%ax
	retw
	jmp in__end
	xorb %ah,%ah
	#TODO actually process more stuff
	in__end:
	retw
	in__buffered:
	pushw %di
	movw %ds,%ax
	movw %ax,%es
	movw $0x10,%cx
	xorw %ax,%ax
	movw $in_buffer,%di
	repnz
	scasb
	decw %di
	decw %di
	movb (%di),%al
	movb %ah,(%di)
	popw %di
	retw
CHS_read:/*dhulbDoc-v301:function;s16 CHS_read(u16, a16, u16, u8, u8, u8, u8) call16;*/
#u16 seg 4(%bp)
#a16 dest 6(%bp)
#u16 cylinder 8(%bp)
#u8 head 10(%bp)
#u8 sector 12(%bp)
#u8 disk 14(%bp)
#u8 count 16(%bp)
pushw %bp
movw %sp,%bp
movw 4(%bp),%ax
movw %ax,%es
movb 16(%bp),%al
movb $0x02,%ah
movb 14(%bp),%dl
movb 10(%bp),%dh
xorb %cl,%cl
movb 9(%bp),%ch
shrw $2,%cx
orb $0x3f,12(%bp)
orb 12(%bp),%cl
movb 8(%bp),%ch
movw 6(%bp),%bx
int $0x13
xorb %ah,%ah
setnc %al
decw %ax
popw %bp
retw
CHS_readData:/*dhulbDoc-v301:function;s16 CHS_readData(a16, u16, u8, u8, u8, u8) call16;*/
pushw %bp
movw %sp,%bp
pushw 14(%bp)
pushw 12(%bp)
pushw 10(%bp)
pushw 8(%bp)
pushw 6(%bp)
pushw 4(%bp)
pushw %ds
callw CHS_read
movw %bp,%sp
popw %bp
retw
