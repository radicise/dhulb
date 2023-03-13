print:/*dhulbDoc-v300:function;s32 print(a32*u8) call32;*/
pushl %esi
movl 8(%esp),%esi
xorl %ecx,%ecx
decl %ecx
movw %ds,%ax
movw %ax,%es
xorb %al,%al
repnz
scasb
notl %ecx
decl %ecx
pushl 8(%esp)
pushl $0x01#$$STDOUT$$
calll write
addl $0x0c,%esp
popl %esi
retl
