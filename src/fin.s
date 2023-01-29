.data
.text
.code32
allocate:#/*dhulbDoc-v200:function;a32 allocate(u32) call32;*/
pushl %ebp
movl %esp,%ebp

					pushl %ebx
					pushl $0x00000000
					pushl $0xffffffff
					pushl $0x00000021
					pushl $0x00000003
					pushl 8(%ebp)
					pushl $0x00000000
					movl %esp,%ebx
					movl $90,%eax
					int $0x80
					cmpl $0xfffff000,%eax
					ja allocate_lin32_err
					addl $0x18,%esp
					popl %ebx
					movl %ebp,%esp # not needed if ebx was the first thing in stack space after the base pointer
					popl %ebp
					ret
					allocate_lin32_err:
					addl $0x18,%esp
					popl %ebx
					movl %ebp,%esp
					popl %ebp
					xorl %eax,%eax
					ret
				exit:#/*dhulbDoc-v200:function;a32 exit(u32) call32;*/
pushl %ebp
movl %esp,%ebp

				movl %eax,%ebx
				xorl %eax,%eax
				incl %eax
				int $0x80
			x:#/*dhulbDoc-v200:function;a32 x(u32, a32*a32*u8) call32;*/
pushl %ebp
movl %esp,%ebp
