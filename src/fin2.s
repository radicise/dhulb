.data
.text
.code16
exit:/*dhulbDoc-v200:function;s16 exit(s16) call16;*/
pushw %bp
movw %sp,%bp
movw 0x04(%bp),%ax

				movl %eax,%ebx
				xorl %eax,%eax
				incl %eax
				int $0x80
			xorb %ah,%ah
movb $0x01,%al
movw %bp,%sp
popw %bp
retw
