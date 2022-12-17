package Dhulb;

import Dhulb.Exceptions.InternalCompilerException;
import Dhulb.Exceptions.NotImplementedException;

class StackVar extends Value implements Storage {//Arguments passed in the SystemVi386CallingConvention-like way are stack variables, scoped to the entire function
	long pos;//Offset from base pointer of calling convention
	public FullType bring() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.type.size) {
					case (64):
						Compiler.text.println("movw " + (pos + 6) + "(%bp),%dx");
						Compiler.text.println("movw " + (pos + 4) + "(%bp),%cx");
					case (32):
						Compiler.text.println("movw " + (pos + 2) + "(%bp),%bx");
					case (16):
						Compiler.text.println("movw " + pos + "(%bp),%ax");
						break;
					case (8):
						Compiler.text.println("movb " + pos + "(%bp),%al");
						break;
					default:
						throw new InternalCompilerException("Illegal datum size");
				}
				break;
			case (1):
				throw new NotImplementedException();
			case (2):
				throw new NotImplementedException();
			default:
				throw new InternalCompilerException("Illegal target");
		}
		return type;
	}
	public void store() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.type.size) {
					case (64):
						Compiler.text.println("movw %dx," + (pos + 6) + "(%bp)");
						Compiler.text.println("movw %cx," + (pos + 4) + "(%bp)");
					case (32):
						Compiler.text.println("movw %bx," + (pos + 2) + "(%bp)");
					case (16):
						Compiler.text.println("movw %ax" + pos + "(%bp)");
						break;
					case (8):
						Compiler.text.println("movb %al," + pos + "(%bp)");
						break;
					default:
						throw new InternalCompilerException("Illegal datum size");
				}
				break;
			case (1):
				throw new NotImplementedException();
			case (2):
				throw new NotImplementedException();
			default:
				throw new InternalCompilerException("Illegal target");
		}
	}
}