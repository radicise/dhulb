package Dhulb;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.TreeMap;


class Compiler {//TODO keywords: "imply" (like extern), "linkable" (like globl) (or some other names like those)
	public static PrintStream text = System.out;
	public static PrintStream rwdata = System.out;//TODO different stream and then concatenate
	public static PushbackInputStream in = new PushbackInputStream(System.in, 16);
	public static int mach = 0;//0: 8086; 1: 80386, 2: AMD64
	public static long pos = 0;//bytes from the beginning of the stack frame of the function, 0 bring the lowest-address reserved memory address
	static final byte FALSI = 1;
	static final byte VERIF = 0;
	static Type defUInt = Type.u16;
	static Type defSInt = Type.s16;
	static Type def754 = Type.f32;
	static Type defAdr = Type.a16;
	static TreeMap<String, NoScopeVar> HVars = new TreeMap<String, NoScopeVar>();
	static TreeMap<String, Function> HFuncs = new TreeMap<String, Function>();
	static ArrayList<TreeMap<String, StackVar>> context = new ArrayList<TreeMap<String, StackVar>>();
	public static void main(String[] argv) throws IOException, InternalCompilerException, CompilationException {
		mach = Integer.parseInt(argv[0]);
		if ((mach < 0) || (mach >= 3)) {
			throw new InternalCompilerException("Illegal target");
		}
		Compiler.rwdata.println(".data");
		Compiler.text.println(".text");
		String s;
		String u;
		Type typ;
		int i;
		while (true) {
			s = Util.phrase(0x29);
			if (Util.primsk.contains(s)) {
				try {
					typ = Type.valueOf(s);
				}
				catch (IllegalArgumentException E) {
					if (s.equals("uint")) {
						typ = defUInt;
					}
					else if (s.equals("sint")) {
						typ = defSInt;
					}
					else if (s.equals("float")) {
						typ = def754;
					}
					else if (s.equals("addr")) {
						typ = defAdr;
					}
					else if (s.equals("int")) {
						typ = defSInt;
					}
					else {
						throw new InternalCompilerException("Type cannot be found: " + s);
					}
				}
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
					switch (typ.size) {
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
					Expression.from(')').bring();
					NoScopeVar thno = new NoScopeVar(s, typ);
					thno.store();
					HVars.put(s, thno);
				}
				else if (i == ';') {
					Compiler.rwdata.println(s + ":");
					switch (typ.size) {
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
				else {
					throw new CompilationException("Illegal operator");
				}
			}
			else if (true) {//TODO do
				
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
	static Literal getLit(String s) throws NumberFormatException {//TODO support multiple types
		return new Literal(Type.s16, Long.decode(s));//TODO support unsigned 64-bit literals above Long.MAX_VALUE
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
	
}
class Dhulb {
	private Dhulb() {
	}
	void args1616(int amnt, Value[] vals, String fname) throws InternalCompilerException, CompilationException {
		Type typ;
		for (int i = (amnt - 1); i >= 0; i--) {
			typ = vals[i].bring();
			switch (typ.size) {
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
	a16 (16),
	a32 (32),
	a64 (64);
	final int size;
	Type(int s) {
		size = s;
	}
}
class Datum {
	Type type;
	Expression def;
	Datum(int i, Expression expr) throws InternalCompilerException {
		switch (i) {
			case (0):
				type = Type.u8;
				break;
			case (1):
				type = Type.s8;
				break;
			case (2):
				type = Type.u16;
				break;
			case (3):
				type = Type.s16;
				break;
			case (4):
				type = Type.u32;
				break;
			case (5):
				type = Type.s32;
				break;
			case (6):
				type = Type.u64;
				break;
			case (7):
				type = Type.s64;
				break;
			case (8):
				type = Type.f32;
				break;
			case (9):
				type = Type.f64;
				break;
			case (10):
				type = Type.a16;
				break;
			case (11):
				type = Type.a32;
				break;
			case (12):
				type = Type.a64;
				break;
			default:
				throw new InternalCompilerException("Illegal datum type");
		}
	}
}
abstract class Item {
	
}
abstract class Value extends Item {
	Type type;
	abstract Type bring() throws CompilationException, InternalCompilerException;
}
class Literal extends Value {
	final long val;//Non-conforming places must hold zero
	Literal(Type typ, long vlu) {
		type = typ;
		val = (vlu & (0xffffffffffffffffL >>> (64 - type.size)));
	}
	Type bring() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				if (type.size == 8) {
					if (val == 0) {
						Compiler.text.println("xorb %al, %al");
					}
					else {
						Compiler.text.println("movb $" + Util.hexb(val) + ", %al");
					}
				}
				else if (type.size == 16) {
					Util.bring16('a', (short) val);
				}
				else if (type.size == 32) {
					Util.bring16('a', ((short) val));
					Util.bring16('b', ((short) (val >>> 16)));
				}
				else if (type.size == 64) {
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
enum Operator {
	ADD,//("+") Arithmetic addition
	SUB,//("-") Arithmetic subtraction
	MUL,//("*") Arithmetic multiplication
	DIV,//("/") Arithmetic division ("/")
	SHR,//(">>") Bit-wise right shift: Signed-type LHO causes a sign-extending shift, unsigned-type LHO causes a zero-extending shift
	SHL,//("<<") Bit-wise zero-extending left shift
	ROR,//(">>>") Bit-wise right roll
	ROL,//("<<<") Bit-wise left roll
	AND,//("&") Bit-wise and
	BNEG,//("~") Bit-wise negation
	ANEG,//("-") Arithmetic negation TODO maybe don't do this(?)
	LNEG,//("!") Logical negation: 0 -> 1, else -> 0
	XOR,//("^") Bit-wise exclusive or
	OR,//("|") Bit-wise or
}
class Postfix extends Item {
	Operator op;
	void apply(Item ite) {
		
	}
}
class Infix extends Item {
	Operator op;
	void apply(Item ite) {
		
	}
}
class NoScopeVar extends Value {
	String name;
	NoScopeVar(String nam, Type typ) {
		name = nam;
		type = typ;
	}
	public String toString() {
		return (name + " " + type);
	}
	void store() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.size) {
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
	Type bring() throws NotImplementedException, InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.size) {
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
class StackVar extends Value {//Arguments passed in the SystemVi386CallingConvention-like way are stack variables, scoped to the entire function
	long pos;//Offset from base pointer of calling convention
	Type bring() throws NotImplementedException, InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.size) {
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
	Type bring() throws CompilationException, InternalCompilerException {
		int size = items.size();
		int i = 0;
		Item ite = items.get(i);
		if ((ite instanceof Infix) || (ite instanceof Postfix)) {
			throw new CompilationException("Illegal operator position");
		}
		if (ite instanceof Value) {
			((Value) ite).bring();
		}
		for (i = 1; i < size; i++) {
			ite = items.get(i);
			//TODO finish: "if (ite)"
		}
		return ((Value) ite).type;
	}
	static Expression from(int ending) throws InternalCompilerException, IOException, CompilationException {//Consumes the ending character
		Expression ex = new Expression();
		while (true) {
			Util.skipWhite();
			int tg;
			if ((tg = Util.read()) == '(') {
				ex.add(from(')'));
				continue;
			}
			else if (tg == ending) {
				ex.finalised = true;
				return ex;
			}
			else {
				Util.unread(tg);
				String s = Util.phrase(0x2f);
				Literal lit;
				if (Util.legalIdent(s)) {
					Util.skipWhite();
					if ((tg = Util.read()) == '(') {
						throw new NotImplementedException();//Implement function calls
					}
					else {
						Util.unread(tg);
						StackVar sv = null;
						for (int i = Compiler.context.size(); i >= 0; i--) {
							sv = Compiler.context.get(i).get(s);
							if (sv != null) {
								ex.add(sv);
								break;
							}
						}
						if (sv == null) {
							NoScopeVar hv = Compiler.HVars.get(s);
							if (hv == null) {
								throw new CompilationException("Undefined variable: " + s);
							}
							else {
								ex.add(hv);
							}
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
						}
						//Not an expression, function call, variable, or literal
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
class VariableUndefinedException extends CompilationException {
	VariableUndefinedException(String reas) {
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

