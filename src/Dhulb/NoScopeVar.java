package Dhulb;

import Dhulb.Exceptions.InternalCompilerException;
import Dhulb.Exceptions.NotImplementedException;

class NoScopeVar extends Value implements Storage {
	String name;
	NoScopeVar(String nam, FullType typ) {
		name = nam;
		type = typ;
	}
	public String toString() {
		return (type + " " + name);
	}
	public void store() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.type.size) {
					case (64):
						Compiler.text.println("movw %dx," + name + "+6(,1)");
						Compiler.text.println("movw %cx," + name + "+4(,1)");
					case (32):
						Compiler.text.println("movw %bx," + name + "+2(,1)");
					case (16):
						Compiler.text.println("movw %ax," + name + "(,1)");
						break;
					case (8):
						Compiler.text.println("movb %al," + name + "(,1)");
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
	public FullType bring() throws NotImplementedException, InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.type.size) {
					case (64):
						Compiler.text.println("movw " + name + "+6(,1),%dx");
						Compiler.text.println("movw " + name + "+4(,1),%cx");
					case (32):
						Compiler.text.println("movw " + name + "+2(,1),%bx");
					case (16):
						Compiler.text.println("movw " + name + "(,1),%ax");
						break;
					case (8):
						Compiler.text.println("movb " + name + "(,1),%al");
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
}