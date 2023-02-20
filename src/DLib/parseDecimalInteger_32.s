.text
parseDecimalInteger:/*dhulbDoc-v200:function;s32 parseDecimalInteger(addr*u8) call32*/
.globl parseDecimalInteger
pushl %esi
pushl %ebx
movl 12(%esp),%esi
xorl %ecx,%ecx
xorl %eax,%eax
lodsb
cmpb $0x2d,%al
setz %bl
jnz parseDecimalInteger_noNeg
parseDecimalInteger_neg:
lodsb
parseDecimalInteger_noNeg:
cmpb $0x30,%al
jz parseDecimalInteger_neg
jmp parseDecimalInteger_in
parseDecimalInteger_loop:
lodsb
parseDecimalInteger_in:
testb %al,%al
jz parseDecimalInteger_ending
cmpb $0x5f,%al
jz parseDecimalInteger_loop
subl $0x30,%al
cmpb $0x09,%al
ja parseDecimalInteger_badFormat
xchg %eax,%ecx
movl $10,%edx
mull %edx
addl %eax,%ecx
xorl %eax,%eax
jmp parseDecimalInteger_loop
parseDecimalInteger_ending:
testb %bl,%bl
jz parseDecmialInteger_noSign
notl %ecx
incl %ecx
parseDecimalInteger_noSign:
movl %ecx,%eax
popl %ebx
popl %esi
retl
parseDecimalInteger_badFormat:
xorl %eax,%eax
notl %eax
popl %ebx
popl %esi
retl
