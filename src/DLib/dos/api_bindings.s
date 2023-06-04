dos_console_input:/*dhulbDoc-v301:function;u8 dos_console_input() call16;*/
movb $0x01,%ah
int $0x21
xorb %ah,%ah#TODO does SystemV ABI for i386 require %ah to be zeroed when returning an 8-bit value that is of a type that would be passed on the stack to be passed as an argument?
retw
dos_console_output:/*dhulbDoc-v301:function;u8 dos_console_output(u8) call16;*/
pushw %bp
movw %sp,%bp
movb 4(%bp),%dl
movb $0x02,%ah
int $0x21
xorw %ax,%ax#TODO same question about %ah clearance as in dos_console_input
popw %bp
retw
dos_input_string:/*dhulbDoc-v301:function;u8 dos_input_string(a16) call16;*/
pushw %bp
movw %bp,%sp
movw 4(%bp),%dx
movb $0x0a,%ah
int $0x21
xorw %ax,%ax#TODO same
popw %bp
retw
