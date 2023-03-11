exit:/*dhulbDoc-v300:function;s32 exit(s32) call32;*/
movl %eax,%ebx
xorl %eax,%eax
incl %eax
int $0x80
read:/*dhulbDoc-v300:function;s32 read(s32, a32, u32) call32;*/
pushl %ebx
movl 8(%ebp),%ebx
movl 12(%ebp),%ecx
movl 16(%ebp),%edx
movl $0x03,%eax
int $0x80
popl %ebx
retl
write:/*dhulbDoc-v300:function;s32 write(s32, a32, u32) call32;*/
pushl %ebx
movl 8(%ebp),%ebx
movl 12(%ebp),%ecx
movl 16(%ebp),%edx
movl $0x04,%eax
int $0x80
popl %ebx
retl
