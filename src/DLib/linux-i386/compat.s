print:/*dhulbDoc-v300:function;s32 print(a32*u8) call32;*/
pushl %edi
movl 8(%esp),%edi
xorl %ecx,%ecx
decl %ecx
movw %ds,%ax
movw %ax,%es
xorb %al,%al
repnz
scasb
notl %ecx
decl %ecx
movl %ecx,%eax
pushl %ecx
pushl 12(%esp)
pushl $0x01#$$STDOUT$$
calll write
addl $0x0c,%esp
popl %edi
retl
