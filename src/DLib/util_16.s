.text
strlen:/*dhulbDoc-v300:function;u16 strlen(a16*u8) call16;*/
.globl strlen
pushw %bp
movw %sp,%bp
pushw %di
movw 4(%bp),%di
xorw %cx,%cx
notw %cx
movw %ds,%ax
movw %ax,%es
xorb %al,%al
repnz
scasb
notw %ecx
decw %ecx
movw %cx,%ax
popw %di
popw %bp
retw
bcmp:/*dhulbDoc-v300:function;s8 bcmp(a32*u8, a32*u8, u32) call32*/
.globl bcmp
movw %ds,%ax
movw %ax,%es
pushw %bp
movw %sp,%bp
movw 4(%bp),%si
movw 6(%bp),%di
movw 8(%bp),%cx
repz
cmpsb
setb %al
rorb $1,%al
cbtw
setnz %al
orb %ah,%al
popw %bp
retw
