.text
strlen:/*dhulbDoc-v300:function;u16 strlen(a16*u8) call16;*/
.globl strlen
/*Resultant legth does not include the null terminator*/
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
notw %cx
decw %cx
movw %cx,%ax
popw %di
popw %bp
retw
bcmp:/*dhulbDoc-v300:function;s8 bcmp(a16*u8, a16*u8, u16) call16*/
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
memcpy:/*dhulbDoc-v301:function;a16 memcpy(a16, a16, u16) call16*/
.globl memcpy
pushw %bp
movw %sp,%bp
pushw %si
pushw %di
movw 4(%bp),%di
movw %di,%ax
movw 6(%bp),%si
movw 8(%bp),%cx
rep movsb
popw %di
popw %si
popw %bp
retw
