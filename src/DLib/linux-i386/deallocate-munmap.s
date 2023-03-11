dealloc:/*dhulbDoc-v300:function;s32 dealloc(a32, u32) call32;*/
	pushl %ebx
	movl 8(%esp),%ebx
	movl 12(%esp),%ecx
	movl $91,%eax
	int $0x80
	popl %ebx
	cmpl $0xfffff000,%eax
	ja dealloc_err
	retl
	dealloc_err:
	xorl %eax,%eax
	decl %eax
	retl
