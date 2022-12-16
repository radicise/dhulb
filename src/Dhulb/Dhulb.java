package Dhulb;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TreeMap;
class Compiler {//TODO keywords: "imply" (like extern), "linkable" (like globl) (or some other names like those)
	public static PrintStream nowhere;//Never be modified after initial setting
	public static PrintStream prologue;
	public static PrintStream epilogue;
	public static PrintStream rwdata;
	public static PrintStream text;
	public static PushbackInputStream in = new PushbackInputStream(System.in, 64);//Do not unread too much
	public static int mach = 0;//0: 8086; 1: 80386 32-bit mode, 2: AMD64 64-bit mode
	public static long pos = 0;//bytes from the beginning of the stack frame of the function, 0 bring the lowest-address reserved memory address
	static final long FALSI = 1;
	static final long VERIF = 0;
	static Type defUInt = Type.u16;
	static Type defSInt = Type.s16;
	static Type def754 = Type.f32;
	static Type defAdr = Type.a16;
	public static int warns = 0;
	static TreeMap<String, NoScopeVar> HVars = new TreeMap<String, NoScopeVar>();
	static TreeMap<String, Function> HFuncs = new TreeMap<String, Function>();
	static ArrayList<TreeMap<String, StackVar>> context = new ArrayList<TreeMap<String, StackVar>>();
	public static void main(String[] argv) throws IOException, InternalCompilerException {
		PrintStream proback = null;
		PrintStream epiback = null;
		PrintStream rwdatback = null;
		PrintStream texback = null;
		try {
			nowhere = new PrintStream(new File("/dev/null"));//Support for non-POSIX systems
			prologue = System.out;
			proback = prologue;
			epilogue = System.out;
			epiback = epilogue;
			rwdata = System.out;
			rwdatback = rwdata;
			text = System.out;//TODO different streams and then concatenate
			texback = text;
			//TODO prologue
			ma(argv);
			prologue = proback;
			epilogue = epiback;
			rwdata = rwdatback;
			text = texback;
			finishStreams();
			//TODO epilogue
		}
		catch (CompilationException exc) {
			prologue = proback;
			epilogue = epiback;
			rwdata = rwdatback;
			text = texback;
			System.out.println("Compilation error: " + exc.getMessage());
			//TODO epilogue
			finishStreams();
		}
		
	}
	static void finishStreams() {//TODO concatenate the files, add a new line 
	}
	public static void ma(String[] argv) throws IOException, InternalCompilerException, CompilationException {//TODO make some system for the compiler to know and manage needed register preservation
		mach = Integer.parseInt(argv[0]);
		if ((mach < 0) || (mach >= 3)) {
			throw new CompilationException("Illegal target");
		}
		Compiler.rwdata.println(".data");
		Compiler.text.println(".text");
		if (mach == 0) {
			Compiler.text.println(".code16");
			defUInt = Type.u16;
			defSInt = Type.s16;
			def754 = Type.f32;
			defAdr = Type.a16;
		}
		else if (mach == 1) {
			Compiler.text.println(".code32");
			defUInt = Type.u32;
			defSInt = Type.s32;
			def754 = Type.f32;
			defAdr = Type.a32;
		}
		else if (mach == 2) {
			defUInt = Type.u64;
			defSInt = Type.s64;
			def754 = Type.f64;
			defAdr = Type.a64;
		}
		else {
			throw new InternalCompilerException("Unidentifiable target");
		}
		String s;
		FullType typ;
		int i;
		while (true) {
			try {
				typ = FullType.from();
				s = Util.phrase(0x35);
				if (!(Util.legalIdent(s))) {
					throw new CompilationException("Illegal identifier: " + s);
				}
				{
					NoScopeVar coll = HVars.get(s);
					if (coll != null) {
						throw new CompilationException("Symbol collision: " + typ + " " + s + " conflicts with " + coll.toString());
					}
					Function collf = HFuncs.get(s);
					if (collf != null) {
						throw new CompilationException("Symbol collision: " + typ + " " + s + " conflicts with " + collf.toString());
					}
				}
				Util.skipWhite();
				if ((i = Util.read()) == '=') {
					Compiler.rwdata.println(s + ":");
					switch (typ.type.size) {
						case (8):
							Compiler.rwdata.println(".byte 0x00");
							break;
						case (16):
							Compiler.rwdata.println(".byte 0x00, 0x00");
							break;
						case (32):
							Compiler.rwdata.println(".byte 0x00, 0x00, 0x00, 0x00");
							break;
						case (64):
							Compiler.rwdata.println(".byte 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00");
							break;
						default:
							throw new InternalCompilerException("Illegal datum size");
					}
					//System.out.println(Expression.from(';'));
					//FullType brog = null;
					FullType brog = Expression.from(';').bring();//TODO Avoid bringing the number if it is constant
					NoScopeVar thno = new NoScopeVar(s, typ);
					if (brog != typ) {
						brog.cast(typ);
					}
					thno.store();
					HVars.put(s, thno);
				}
				else if (i == ';') {
					Compiler.rwdata.println(s + ":");
					switch (typ.type.size) {
						case (8):
							Compiler.rwdata.println(".byte 0x00");
							break;
						case (16):
							Compiler.rwdata.println(".byte 0x00, 0x00");
							break;
						case (32):
							Compiler.rwdata.println(".byte 0x00, 0x00, 0x00, 0x00");
							break;
						case (64):
							Compiler.rwdata.println(".byte 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00");
							break;
						default:
							throw new InternalCompilerException("Illegal datum size");
					}
					HVars.put(s, new NoScopeVar(s, typ));
				}
				else if (i == '(') {
					throw new NotImplementedException();//TODO implement functions
				}
				else {
					throw new CompilationException("Illegal operator");
				}
			}
			catch (UnidentifiableTypeText utt) {//TODO do
				s = utt.verbatim;
			}
		}
	}
	
}
class Util {
	static ArrayList<Integer> brack = new ArrayList<Integer>();
	static int[] brace = new int[]{'(', ')', '[', ']', '{', '}', '<', '>'};
	static ArrayList<String> keywork = new ArrayList<String>();
	static String[] keywore = new String[]{"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "goto", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "u8", "s8", "u16", "s16", "u32", "s32", "u64", "s64", "f32", "f64", "a16", "a32", "a64", "uint", "sint", "addr"};
	static ArrayList<String> accesk = new ArrayList<String>();
	static String[] accese = new String[]{"private", "protected", "public"};
	static ArrayList<String> boolitk = new ArrayList<String>();
	static String[] boolite = new String[]{"false", "true"};
	static ArrayList<String> nulitk = new ArrayList<String>();
	static String[] nulite = new String[]{"null"};
	static ArrayList<String> primsk = new ArrayList<String>();
	static String[] primse = new String[]{"u8", "s8", "u16", "s16", "u32", "s32", "u64", "s64", "f32", "f64", "a16", "a32", "a64", "uint", "sint", "float", "addr", "int"};
	static ArrayList<Integer> inpork = new ArrayList<Integer>();
	static int[] inpore = new int[]{'+', '-', '=', '/', '<', '>', '*', '%'};
	static {
		for (int c : brace) {
			brack.add(c);
		}
		for (String s : keywore) {
			keywork.add(s);
		}
		for (String s : accese) {
			accesk.add(s);
		}
		for (String s : boolite) {
			boolitk.add(s);
		}
		for (String s : nulite) {
			nulitk.add(s);
		}
		for (String s : primse) {
			primsk.add(s);
		}
		for (int c : inpore) {
			inpork.add(c);
		}
	}
	private Util() {
	}
	static void warn(String s, int strm) {
		Compiler.warns++;
		switch (strm) {
			case (1):
				Compiler.rwdata.println("# Warning: " + s);
				break;
			case (2):
				Compiler.text.println("# Warning: " + s);
				break;
		}
	}
	static Literal getLit(String s) throws NumberFormatException, InternalCompilerException {//TODO support manually defining the type
		return new Literal(FullType.of(Compiler.defSInt), Long.decode(s));//TODO support unsigned 64-bit literals above Long.MAX_VALUE
	}
	static int read() throws IOException {// Use this for UTF-8 instead of anything relying on StreamDecoder that caches characters
		int g;
		if ((g = Compiler.in.read()) == (-1)) {
			throw new EOFException();
		}
		if (g < 0x80) {
			return g;
		}
		throw new UnsupportedOperationException();// TODO full support for UTF-8
	}
	static void unread(String s) throws IOException {
		byte[] bys = s.getBytes(StandardCharsets.UTF_8);
		for (int i = bys.length - 1; i >= 0; i--) {
			Compiler.in.unread(bys[i]);
		}
	}
	static void unread(int f) throws IOException, InternalCompilerException {
		if ((f > 0x10ffff) || (f < 0)) {
			throw new InternalCompilerException("invalid Unicode character");
		}
		if (f < 0x80) {
			Compiler.in.unread(f);
		}
		else if (f < 0x800) {
			Compiler.in.unread(0x80 | (f & 0x3f));
			f >>>= 6;
			Compiler.in.unread(0xc0 | (f & 0x1f));
		}
		else if (f < 0x10000) {
			Compiler.in.unread(0x80 | (f & 0x3f));
			f >>>= 6;
			Compiler.in.unread(0x80 | (f & 0x3f));
			f >>>= 6;
			Compiler.in.unread(0xe0 | (f & 0x0f));
		}
		else {
			Compiler.in.unread(0x80 | (f & 0x3f));
			f >>>= 6;
			Compiler.in.unread(0x80 | (f & 0x3f));
			f >>>= 6;
			Compiler.in.unread(0x80 | (f & 0x3f));
			f >>>= 6;
			Compiler.in.unread(0xf0 | (f & 0x07));
		}
	}
	static boolean skipWhite() throws IOException, InternalCompilerException {
		int g = 0;
		boolean kre = false;
		while (true) {
			g = read();
			if (!(Character.isWhitespace(g))) {
				unread(g);
				break;
			}
			kre = true;
		}
		return kre;
	}
	static String phrase(long sbm) throws IOException, InternalCompilerException {
		skipWhite();
		StringWriter sr = new StringWriter();
		int h;
		while (true) {
			h = read();
			if (((sbm & 0x20L) != 0) && Character.isWhitespace(h)) {
				unread(h);
				sr.close();
				return sr.toString();
			}
			else if (((sbm & 0x01L) != 0) && (brack.contains(h))) {
				unread(h);
				sr.close();
				return sr.toString();
			}
			else if (((sbm & 0x02L) != 0) && (h == ',')) {
				unread(h);
				sr.close();
				return sr.toString();
			}
			else if (((sbm & 0x04L) != 0) && (h == ';')) {
				unread(h);
				sr.close();
				return sr.toString();
			}
			else if (((sbm & 0x08L) != 0) && (inpork.contains(h))) {
				unread(h);
				sr.close();
				return sr.toString();
			}
			else if (((sbm & 0x10L) != 0) && (h == '=')) {
				unread(h);
				sr.close();
				return sr.toString();
			}
			sr.write(h);
		}
	}
	static boolean legalIdent(String s) throws IOException {
		if (s.length() == 0) {
			return false;
		}
		if ((keywork.contains(s)) || (boolitk.contains(s)) || (nulitk.contains(s))) {
			return false;
		}
		int c = 0;
		int h = s.codePointAt(c);
		if (!(Character.isJavaIdentifierStart(h))) {
			return false;
		}
		if (h > 0xffff) {
			c++;
		}
		c++;
		try {
			while (true) {
				if (!(Character.isJavaIdentifierPart(h = s.codePointAt(c)))) {
					return false;
				}
				if (h > 0xffff) {
					c++;
				}
				c++;
			}
		}
		catch (IndexOutOfBoundsException e) {
		}
		return true;
	}
	static String hexb(long b) {
		String s = Long.toHexString(b);
		s = ("0000000000000000".substring(s.length())) + s;
		return s.substring(14);
	}
	static String hexs(long w) {
		String s = Long.toHexString(w);
		s = ("0000000000000000".substring(s.length())) + s;
		return s.substring(12);
	}
	static String hexi(long i) {
		String s = Long.toHexString(i);
		s = ("0000000000000000".substring(s.length())) + s;
		return s.substring(8);
	}
	static String hexl(long l) {
		String s = Long.toHexString(l);
		s = ("0000000000000000".substring(s.length())) + s;
		return s;
	}
	static void bring16(char reg, short val) {
		if (((val & 0xff00) != 0) && ((val & 0x00ff) != 0)) {
			Compiler.text.println("movw $0x" + Util.hexs(val) + ",%" + reg + "x");
		}
		else if ((val & 0x00ff) != 0) {
			Compiler.text.println("xorb %" + reg + "h, %" + reg + "h");
			Compiler.text.println("movb $0x" + Util.hexb(val) + ",%" + reg + "l");
		}
		else if ((val & 0xff00) != 0) {
			Compiler.text.println("xorb %" + reg + "l, %" + reg + "l");
			Compiler.text.println("movb $0x" + Util.hexb(val >>> 8) + ",%" + reg + "h");
		}
		else {
			Compiler.text.println("xorw %" + reg + "x,%" + reg + "x");
		}
	}
}
class Function {
	FullType retType;
	String name;
	FullType[] args;
	static Function from() throws NotImplementedException {//starts before the function definition (whitespace before it allowed), consumes everything up to the closing curly brace, inclusive
		throw new NotImplementedException();
	}
	
	
}
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
	static FullType from() throws UnidentifiableTypeText, CompilationException, InternalCompilerException, IOException {//Throws UnidentifiableTypeText when the phrase(0x29) call yields a string which does not correspond to any valid type or any valid type shorthand notation
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
enum Type {
	u8 (8),
	s8 (8),
	u16 (16),
	s16 (16),
	u32 (32),
	s32 (32),
	u64 (64),
	s64 (64),
	f32 (32),
	f64 (64),
	a16 (16, true),
	a32 (32, true),
	a64 (64, true);
	final int size;
	final boolean addressable;
	Type(int s) {
		size = s;
		addressable = false;
	}
	Type(int s, boolean addrsable) {
		size = s;
		addressable = addrsable;
	}
}
abstract class Item {
	
}
abstract class Value extends Item {
	FullType type;
	abstract FullType bring() throws CompilationException, InternalCompilerException;
}
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
class Operator extends Item {
	static final Operator ADD = new Operator(false, '+');//("+") Arithmetic addition
	static final Operator SUB = new Operator(false, '-');//("-") Arithmetic subtraction
	static final Operator MUL = new Operator(false, '*');//("*") Arithmetic multiplication
	static final Operator DIV = new Operator(false, '/');//("/") Arithmetic division ("/")
	static final Operator SHR = new Operator(false, ']');//(">>") Bit-wise zero-filling right shift
	static final Operator MSHR = new Operator(false, ')');//(">>|") Bit-wise MSB-duplicating right shift
	static final Operator SHL = new Operator(false, '[');//("<<") Bit-wise zero-filling left shift
	static final Operator ROR = new Operator(false, '}');//(">>>") Bit-wise right roll
	static final Operator ROL = new Operator(false, '{');//("<<<") Bit-wise left roll
	static final Operator AND = new Operator(false, '&');//("&") Bit-wise and
	static final Operator BNEG = new Operator(true, '~');//("~") Bit-wise negation
	static final Operator LNEG = new Operator(true, '!');//("!") Logical negation: 0 -> 1, else -> 0
	static final Operator XOR = new Operator(false, '^');//("^") Bit-wise exclusive or
	static final Operator OR = new Operator(false, '|');//("|") Bit-wise or
	static final Operator SCAND = new Operator(false, 'a');//("&&") Short-circuit and (0: TRUE; else: FALSE; TRUE: 0; FALSE: 1)
	static final Operator SCOR = new Operator(false, 'o');//("||") Short-circuit or (0: TRUE; else: FALSE; TRUE: 0; FALSE: 1)
	static final Operator GT = new Operator(false, '>');//(">") Greater than
	static final Operator LT = new Operator(false, '<');//("<") Less than
	final boolean unary;
	final int id;
	Operator(boolean un, int ident) {
		unary = un;
		id = ident;
	}
	FullType apply(FullType typ) throws InternalCompilerException {//Unary; The value has already been brought
		if (!(unary)) {
			throw new InternalCompilerException("Not a unary operator: " + this.toString());
		}
		throw new NotImplementedException();
	}
	FullType apply(FullType LHO, Value RHO) throws CompilationException, InternalCompilerException {//Binary; The LHO has already been brought
		if (unary) {
			throw new InternalCompilerException("Not a binary operator: " + this.toString());
		}
		switch (id) {
			case ('+'):
				switch (LHO.type.size) {
					case (64):
						throw new NotImplementedException();
					case (32)://Remember that 32-bit push and pop aren't available in 64-bit mode
						throw new NotImplementedException();
					case (16)://u16, s16, or a16
						FullType RHtyp = RHO.type;
						switch (RHtyp.type.size) {
							case (64):
								throw new NotImplementedException();
							case (32)://Remember that 32-bit push and pop aren't available in 64-bit mode
								throw new NotImplementedException();
							case (16)://u16, s16, or a16
								Compiler.text.println("pushw %ax");//TODO prevent the need for this move by bring()-ing directly to %bx and preserving %ax (unless it's significantly slower than using the accumulator %ax or it's impossible not to use %ax), in which cases the called function might warn this function that it would be left in %ax)
								RHO.bring();
								Compiler.text.println("popw %bx");
								Compiler.text.println("addw %bx,%ax");//TODO add to %bx and then notify the caller that it was left in %bx, unless it's significantly slower than using the accumulator
								if ((LHO.type == Type.a16) || (RHtyp.type == Type.a16)) {
									return FullType.a16;//Gets rid of calling and pointing clauses
								}
								else if ((LHO.type == Type.s16) || (RHtyp.type == Type.s16)) {
									return FullType.s16;
								}
								else if ((LHO.type == Type.u16) && (RHtyp.type == Type.u16)) {
									return FullType.u16;
								}
								else {
									throw new InternalCompilerException("Resultant type of operation could not be resolved");
								}
							case (8)://u8 or s8
								throw new NotImplementedException();
							default:
								throw new InternalCompilerException("Illegal datum size");
						}
					case (8)://u8 or s8
						throw new NotImplementedException();
					default:
						throw new InternalCompilerException("Illegal datum size");
				}
			default:
				throw new NotImplementedException();
		}
	}
}
interface Storage {
	public void store() throws CompilationException, InternalCompilerException;
	public FullType bring() throws CompilationException, InternalCompilerException;
}
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
class Expression extends Value {
	private ArrayList<Item> items;
	private boolean finalised;
	private Expression() {
		items = new ArrayList<Item>();
		finalised = false;
	}
	void add(Item i) throws InternalCompilerException {//Should not throw NumberFormatException
		if (finalised) {
			throw new InternalCompilerException("Modification of finalized expression");
		}
		else {
			items.add(i);
		}
	}
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Expression: ");
		asString(sb);
		return sb.toString();
	}
	void asString(StringBuilder sb) {
		sb.append("(");
		for (Item ite : items) {
			sb.append(ite);
			sb.append(" ");
		}
		sb.append(")");
	}
	FullType bring() throws CompilationException, InternalCompilerException {
		FullType last;
		int size = items.size();
		int i = 0;
		Item ite = items.get(i);
		Value vail;
		if (ite instanceof Operator) {
			throw new CompilationException("Operator at start of expression: " + ite);
		}
		else if (ite instanceof Value) {
			last = ((Value) ite).bring();
		}
		else {
			throw new InternalCompilerException("Inappropriate expression item: " + ite);
		}
		Operator oprt;
		for (i = 1; i < size; i++) {
			ite = items.get(i);
			if (!(ite instanceof Operator)) {
				throw new CompilationException("Not an operator: " + ite);
			}
			oprt = (Operator) ite;
			if (oprt.unary) {
				last = oprt.apply(last);
			}
			else {
				i++;
				if (i >= size) {
					throw new CompilationException("Binary operator missing right-hand operand: " + oprt);
				}
				ite = items.get(i);
				if (!(ite instanceof Value)) {
					throw new CompilationException("Not a value: " + ite);
				}
				vail = (Value) ite;
				last = oprt.apply(last, vail);
			}
		}
		return ((Value) ite).type;
	}
	static Expression from(int ending) throws InternalCompilerException, IOException, CompilationException {//Consumes the ending character
		Expression ex = new Expression();
		Item last = null;
		while (true) {
			Util.skipWhite();
			int tg;
			tg = Util.read();
			if (tg == '(') {
				if (last instanceof Value) {
					if (!(((Value) last).type.type.addressable)) {
						if ((((Value) last).type.type.size != 16) && (((Value) last).type.type.size != 32) && (((Value) last).type.type.size != 64)) {
							throw new CompilationException("Call to a non-addressable value of improper size: Cast the value to an addressable value first");//TODO implement casting
						}
						throw new CompilationException("Call to a non-addressable value: Cast the value to an addressable value first or use the raw conversion operator \'!\'");//TODO implement raw conversion operator (used for raw conversions and to override function parameter bit sizes (placed differently for overriding function parameter bit sizes and for raw conversion of a value to an address for function calling))
					}
					//TODO implement function calls
				}
				else {
					ex.add(from(')'));
				}
				continue;
			}
			
			else if (tg == ending) {
				ex.finalised = true;
				PrintStream pstemp = Compiler.text;
				Compiler.text = Compiler.nowhere;
				ex.type = ex.bring();//TODO maybe un-bodge
				Compiler.text = pstemp;
				return ex;
			}
			else {
				Util.unread(tg);
				String s = Util.phrase(0x2f);
				Literal lit;
				if (Util.legalIdent(s)) {
					Util.skipWhite();
					StackVar sv = null;
					for (int i = Compiler.context.size() - 1; i >= 0; i--) {
						sv = Compiler.context.get(i).get(s);
						if (sv != null) {
							ex.add(sv);
							break;
						}
					}
					if (sv == null) {
						NoScopeVar hv = Compiler.HVars.get(s);
						if (hv == null) {
							throw new SymbolUndefinedException("Undefined symbol: " + s);
						}
						else {
							ex.add(hv);
						}
					}
				}
				else {
					try {
						lit = Util.getLit(s);
						ex.add(lit);
					}
					catch (NumberFormatException e) {
						if (s.length() != 0) {
							throw new CompilationException("Invalid statement: " + s);
						}//Not an expression, function call, symbol, or literal
						int i = Util.read();
						switch (i) {
							case ('+'):
								ex.add(Operator.ADD);
								break;
							case ('-'):
								ex.add(Operator.SUB);
								break;
							case ('*'):
								ex.add(Operator.MUL);
								break;
							case ('/'):
								ex.add(Operator.DIV);
								break;
							default:
								throw new NotImplementedException();
						}
					}
				}
			}
		}
	}
}
@SuppressWarnings("serial")
class InternalCompilerException extends Exception {
	InternalCompilerException() {
		super();
	}
	InternalCompilerException(String reas) {
		super(reas);
	}
}
@SuppressWarnings("serial")
class CompilationException extends Exception {
	CompilationException(String reas) {
		super(reas);
	}
}
@SuppressWarnings("serial")
class SymbolUndefinedException extends CompilationException {
	SymbolUndefinedException(String reas) {
		super(reas);
	}
}
@SuppressWarnings("serial")
class NotImplementedException extends InternalCompilerException {
	NotImplementedException() {
		super();
	}
	NotImplementedException(String reas) {
		super(reas);
	}
}
@SuppressWarnings("serial")
class UnidentifiableTypeText extends Throwable {
	final String verbatim;
	UnidentifiableTypeText(String s) {
		super();
		verbatim = s;
	}
}
