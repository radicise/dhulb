.text
parseDecimalInteger:/*dhulbDoc-v200:function;s32 parseDecimalInteger(a32*u8) call32;*/
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
strlen:/*dhulbDoc-v300:function;u32 strlen(a32*u8) call32;*/
.globl strlen
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
popl %edi
retl
bcmp:/*dhulbDoc-v300:function;s8 bcmp(a32*u8, a32*u8, u32) call32*/
.globl bcmp
movw %ds,%ax
movw %ax,%es
movl 4(%esp),%esi
movl 8(%esp),%edi
movl 12(%esp),%ecx
repz
cmpsb
setb %al
rorb $1,%al
cbtw
setnz %al
orb %ah,%al
retl
memcpy:/*dhulbDoc-v301:function;a32 memcpy(a32, a32, u32) call32*/
.globl memcpy
pushl %ebp
movl %esp,%ebp
pushl %esi
pushl %edi
pushl %ebx
movl 8(%ebp),%edi
movw %ds,%ax
movw %ax,%es
movl %edi,%eax
movl 12(%ebp),%esi
movl 16(%ebp),%ecx
rep movsb
popl %ebx
popl %edi
popl %esi
popl %ebp
retl
