exit:/*dhulbDoc-v300:function;s32 exit(s32) call32;*/
.globl exit
movl 4(%esp),%ebx
xorl %eax,%eax
incl %eax
int $0x80
read:/*dhulbDoc-v300:function;s32 read(s32, a32, u32) call32;*/
.globl read
pushl %ebx
movl 8(%esp),%ebx
movl 12(%esp),%ecx
movl 16(%esp),%edx
movl $0x03,%eax
int $0x80
popl %ebx
cmpl $0xfffff000,%eax
ja read_err
retl
write:/*dhulbDoc-v300:function;s32 write(s32, a32, u32) call32;*/
.globl write
pushl %ebx
movl 8(%esp),%ebx
movl 12(%esp),%ecx
movl 16(%esp),%edx
movl $0x04,%eax
int $0x80
popl %ebx
cmpl $0xfffff000,%eax
ja write_err
retl
read_err:
write_err:
xorl %eax,%eax
decl %eax
retl
