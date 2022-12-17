package Dhulb;

import java.io.IOException;
import java.util.ArrayList;

import Dhulb.Exceptions.CompilationException;
import Dhulb.Exceptions.InternalCompilerException;
import Dhulb.Exceptions.NotImplementedException;
import Dhulb.Exceptions.UnidentifiableTypeText;

class FullType {//Like Type but with possible pointing or running clauses
	static final FullType u8 = new FullType(Type.u8);
	static final FullType s8 = new FullType(Type.s8);
	static final FullType u16 = new FullType(Type.u16);
	static final FullType s16 = new FullType(Type.s16);
	static final FullType u32 = new FullType(Type.u32);
	static final FullType s32 = new FullType(Type.s32);
	static final FullType u64 = new FullType(Type.u64);
	static final FullType s64 = new FullType(Type.s64);
	static final FullType f32 = new FullType(Type.f32);
	static final FullType f64 = new FullType(Type.f64);
	static final FullType a16 = new FullType(Type.a16);
	static final FullType a32 = new FullType(Type.a32);
	static final FullType a64 = new FullType(Type.a64);//Do not depend on pointers to any of these matching with anything; these are to prevent the need of repeatedly making FullType instances
	final Type type;
	final FullType[] runsWith;//non-null when there is a calling clause; null otherwise
	final FullType gives;//non-null when there is a pointing clause (specifies particular full type that must be pointed to) and also when there is a calling clause; null otherwise
	FullType(Type typ) {
		type = typ;
		runsWith = null;
		gives = null;
	}
	FullType(Type typ, FullType giv, FullType[] run) throws InternalCompilerException {
		if (!(typ.addressable)) {
			throw new InternalCompilerException("Calling clause for non-addressable argument");
		}
		type = typ;
		runsWith = run;
		gives = giv;
	}
	FullType(Type typ, FullType pointed) throws InternalCompilerException {
		if (!(typ.addressable)) {
			throw new InternalCompilerException("Pointing clause for non-addressable argument");
		}
		type = typ;
		runsWith = null;
		gives = pointed;
	}
    //Throws UnidentifiableTypeText when the phrase(0x29) call yields a string which does not correspond to any valid type or any valid type shorthand notation
	static FullType from() throws UnidentifiableTypeText, CompilationException, InternalCompilerException, IOException {
		String s = Util.phrase(0x29);
		Type typ;
		if (Util.primsk.contains(s)) {
			try {
				typ = Type.valueOf(s);
			}
			catch (IllegalArgumentException E) {
				if (s.equals("uint")) {
					typ = Compiler.defUInt;
				}
				else if (s.equals("sint")) {
					typ = Compiler.defSInt;
				}
				else if (s.equals("float")) {
					typ = Compiler.def754;
				}
				else if (s.equals("addr")) {
					typ = Compiler.defAdr;
				}
				else if (s.equals("int")) {
					typ = Compiler.defSInt;
				}
				else {
					throw new InternalCompilerException("Type cannot be found: " + s);
				}
			}
		}
		else {
			throw new UnidentifiableTypeText(s);
		}
		Util.skipWhite();
		int ci = Util.read();
		if (ci == '*') {
			if (!(typ.addressable)) {
				throw new CompilationException("Cannot add pointing or calling clause for non-addressable type");
			}
			Util.skipWhite();
			FullType given = from();
			Util.skipWhite();
			ci = Util.read();
			if (ci == '(') {
				ArrayList<FullType> fl = new ArrayList<FullType>();
				Util.skipWhite();
				ci = Util.read();
				if (ci == ')') {
					return new FullType(typ, given, new FullType[0]);
				}
				Util.unread(ci);
				while (true) {
					fl.add(from());
					Util.skipWhite();
					ci = Util.read();
					if (ci == ')') {
						return new FullType(typ, given, fl.toArray(new FullType[0]));
					}
					else if (ci != ',') {
						throw new CompilationException("Unexpected statement");
					}
					Util.skipWhite();
				}
			}
			else {
				Util.unread(ci);
				return new FullType(typ, given);
			}
		}
		else {
			Util.unread(ci);
			return of(typ);
		}
	}
	static FullType of(Type bec) throws InternalCompilerException {
		switch (bec) {
			case u8:
				return u8;
			case s8:
				return s8;
			case u16:
				return u16;
			case s16:
				return s16;
			case u32:
				return u32;
			case s32:
				return s32;
			case u64:
				return u64;
			case s64:
				return s64;
			case f32:
				return f32;
			case f64:
				return f64;
			case a16:
				return a16;
			case a32:
				return a32;
			case a64:
				return a64;
			default:
				throw new InternalCompilerException("Unidentifiable type");
		}
	}
	void cast(FullType toType) throws NotImplementedException {//All casts are valid
		switch (type) {
			case u16:
				if (!(toType.type.size == 16)) {//If both the original and casted-to types have a size of 16 bits, the binary data doesn't need to be changed
					throw new NotImplementedException();
				}
				if (toType.type.addressable) {
					Util.warn("Casting from s16 to a16", 2);
				}
				break;
			case s16:
				if (!(toType.type.size == 16)) {
					throw new NotImplementedException();
				}
				if (toType.type.addressable) {
					Util.warn("Casting from s16 to a16", 2);
				}
				break;
			case a16:
				if (!(toType.type.size == 16)) {
					throw new NotImplementedException();
				}
				break;
			default:
				throw new NotImplementedException();
		}
	}
}