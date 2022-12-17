package Dhulb;

import Dhulb.Exceptions.InternalCompilerException;
import Dhulb.Exceptions.NotImplementedException;

class Literal extends Value {
	final long val;//Non-conforming places must hold zero
	Literal(FullType typ, long vlu) {
		type = typ;
		val = (vlu & (0xffffffffffffffffL >>> (64 - type.type.size)));
	}
	FullType bring() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				if (type.type.size == 8) {
					if (val == 0) {
						Compiler.text.println("xorb %al, %al");
					}
					else {
						Compiler.text.println("movb $" + Util.hexb(val) + ", %al");
					}
				}
				else if (type.type.size == 16) {
					Util.bring16('a', (short) val);
				}
				else if (type.type.size == 32) {
					Util.bring16('a', ((short) val));
					Util.bring16('b', ((short) (val >>> 16)));
				}
				else if (type.type.size == 64) {
					Util.bring16('a', ((short) val));
					Util.bring16('b', ((short) (val >>> 16)));
					Util.bring16('c', ((short) (val >>> 32)));
					Util.bring16('d', ((short) (val >>> 48)));
				}
				else {
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