/*
 * Linux "long syscall(long, ...)" implementation for single-threaded applications
 * Defines errno as a global variable "int errno"
 */
.data
errno:/*dhulbDoc-v301:globalVar;s32 errno;*/
.globl errno
.byte 0x00
.byte 0x00
.byte 0x00
.byte 0x00
.text
syscall:/*dhulbDoc-v301:function;s32 syscall(VARARGS) call32;*/
.globl syscall
pushl %ebx
pushl %esi
pushl %edi
pushl %ebp
movl %esp,%ebp
movl 20(%ebp),%eax
movl 24(%ebp),%ebx
movl 28(%ebp),%ecx
movl 32(%ebp),%edx
movl 36(%ebp),%esi
movl 40(%ebp),%edi
movl 44(%ebp),%ebp
int $0x80
cmpl $0xfffff000,%eax
jna syscall__noerr
notl %eax
incl %eax
movl %eax,errno(,1)
xorl %eax,%eax
notl %eax
syscall__noerr:
popl %ebp
popl %edi
popl %esi
popl %ebx
retl
