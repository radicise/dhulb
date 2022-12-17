package Dhulb;

import Dhulb.Exceptions.CompilationException;
import Dhulb.Exceptions.InternalCompilerException;

class Dhulb {
	private Dhulb() {
	}
	void args1616(int amnt, Value[] vals, String fname) throws InternalCompilerException, CompilationException {
		FullType typ;
		for (int i = (amnt - 1); i >= 0; i--) {
			typ = vals[i].bring();
			switch (typ.type.size) {
				case (8):
					Compiler.text.println("decw %sp");
					Compiler.text.println("movb %al,(%sp)");
					break;
				case (64):
					Compiler.text.println("pushw %dx");
					Compiler.text.println("pushw %cx");
				case (32):
					Compiler.text.println("pushw %bx");
				case (16):
					Compiler.text.println("pushw %ax");
					break;
				default:
					throw new InternalCompilerException("Illegal datum size");
			}
		}
	}
	void call1616(String fname) {
		Compiler.text.println("callw" + fname);
	}
}
