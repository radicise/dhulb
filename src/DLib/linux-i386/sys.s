exit:/*dhulbDoc-v300:function;s32 exit(s32) call32;*/
.globl exit
movl 4(%esp),%ebx
xorl %eax,%eax
incl %eax
int $0x80
read:/*dhulbDoc-v300:function;s32 read(s32, a32, u32) call32;*/
.globl read
xchgl %ebx,4(%esp)
movl 8(%esp),%ecx
movl 12(%esp),%edx
movl $0x03,%eax
int $0x80
xchgl %ebx,4(%esp)
cmpl $0xfffff000,%eax
ja read__err
retl
write:/*dhulbDoc-v300:function;s32 write(s32, a32, u32) call32;*/
.globl write
xchgl %ebx,4(%esp)
movl 8(%esp),%ecx
movl 12(%esp),%edx
movl $0x04,%eax
int $0x80
xchgl %ebx,4(%esp)
cmpl $0xfffff000,%eax
ja write__err
retl
bread:/*dhulbDoc-v300:function;s32 bread(s32) call32*/
.globl bread
xchgl %ebx,4(%esp)
xorl %eax,%eax
pushl %eax
movl %esp,%ecx
xorl %edx,%edx
incl %edx
movl $0x03,%eax
int $0x80
cmpl $0xfffff000,%eax
popl %eax
xchgl %ebx,4(%esp)
ja bread__err
retl
bwrite:/*dhulbDoc-v300:function;s32 bwrite(s32, u8) call32*/
.globl bwrite
xchgl %ebx,4(%esp)
movl %esp,%ecx
addl $0x08,%ecx
xorl %edx,%edx
incl %edx
movl $0x04,%eax
int $0x80
cmpl $0xfffff000,%eax
xchgl %ebx,4(%esp)
ja bwrite__err
retl
read__err:
write__err:
bread__err:
bwrite__err:
xorl %eax,%eax
notl %eax
retl
