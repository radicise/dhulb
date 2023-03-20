/*
 * mem.s
 * Created as sysmem.dhulb by root on UTC 2023-01-18_01:02:06.810019082
 */
alloc:/*dhulbDoc-v300:function;a32 alloc(u32) call32;*/
	pushl %ebp
	movl %esp,%ebp
	pushl %ebx
	pushl $0x00000000
	pushl $0xffffffff
	pushl $0x00000021
	pushl $0x00000003
	pushl 8(%ebp)
	pushl $0x00000000
	movl %esp,%ebx
	movl $90,%eax
	int $0x80
	cmpl $0xfffff000,%eax
	ja allocate_lin32_err
	addl $0x18,%esp
	popl %ebx
	movl %ebp,%esp # not needed if %ebx was the first thing in stack space after the base pointer
	popl %ebp
	ret
	allocate_lin32_err:
	addl $0x18,%esp
	popl %ebx
	movl %ebp,%esp
	popl %ebp
	xorl %eax,%eax
	ret
