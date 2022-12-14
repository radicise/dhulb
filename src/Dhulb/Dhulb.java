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
	public static int mach = 0;//0: 8086; 1: 80386 32-bit mode, 2: AMD64 64-bit mode
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
	public static void main(String[] argv) throws IOException, InternalCompilerException, CompilationException {//TODO make some system for the compiler to know and manage needed register preservation
		mach = Integer.parseInt(argv[0]);
		if ((mach < 0) || (mach >= 3)) {
			throw new InternalCompilerException("Illegal target");
		}
		Compiler.rwdata.println(".data");
		Compiler.text.println(".text");
		if (mach == 0) {
			Compiler.text.println(".code16");
		}
		else if (mach == 1) {
			Compiler.text.println(".code32");
		}
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
					Type brog = Expression.from(';').bring();//TODO Avoid bringing the number if it is constant
					NoScopeVar thno = new NoScopeVar(s, typ);
					if (brog != typ) {
						brog.cast(typ);
					}
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
	static Literal getLit(String s) throws NumberFormatException {//TODO support manually defining the type
		return new Literal(Compiler.defSInt, Long.decode(s));//TODO support unsigned 64-bit literals above Long.MAX_VALUE
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
	void cast(Type toType) throws NotImplementedException {//All casts are valid
		switch (this) {
			case u16:
				if (!(toType.size == 16)) {//If both the original and casted-to types have a size of 16 bits, the binary data doesn't need to be changed
					throw new NotImplementedException();
				}
				break;
			case s16:
				if (!(toType.size == 16)) {
					throw new NotImplementedException();
				}
				break;
			case a16:
				if (!(toType.size == 16)) {
					throw new NotImplementedException();
				}
				break;
			default:
				throw new NotImplementedException();
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
	Type apply(Type typ) throws InternalCompilerException {//Unary; The value has already been brought
		if (!(unary)) {
			throw new InternalCompilerException("Not a unary operator: " + this.toString());
		}
		throw new NotImplementedException();
	}
	Type apply(Type LHO, Value RHO) throws CompilationException, InternalCompilerException {//Binary; The LHO has already been brought
		if (unary) {
			throw new InternalCompilerException("Not a binary operator: " + this.toString());
		}
		switch (id) {
			case ('+'):
				switch (LHO.size) {
					case (64):
						throw new NotImplementedException();
					case (32)://Remember that 32-bit push and pop aren't available in 64-bit mode
						throw new NotImplementedException();
					case (16)://u16, s16, or a16
						Type RHtyp = RHO.type;
						switch (RHtyp.size) {
							case (64):
								throw new NotImplementedException();
							case (32)://Remember that 32-bit push and pop aren't available in 64-bit mode
								throw new NotImplementedException();
							case (16)://u16, s16, or a16
								Compiler.text.println("movw %ax,%bx");//TODO prevent the need for this move by bring()-ing directly to %bx (unless it's significantly slower than using the accumulator %ax or it's impossible not to use %ax), in which cases the called function would either use %ax and then move it to %bx because it was told to leave the result in %bx or notify this function that it was left in %ax)
								RHO.bring();
								Compiler.text.println("addw %bx,%ax");//TODO add to %bx and then notify the caller that it was left in %bx, unless it's significantly slower than using the accumulator
								if ((LHO == Type.a16) || (RHtyp == Type.a16)) {
									return Type.a16;
								}
								else if ((LHO == Type.s16) || (RHtyp == Type.s16)) {
									return Type.s16;
								}
								else if ((LHO == Type.u16) && (RHtyp == Type.u16)) {
									return Type.u16;
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
	public Type bring() throws CompilationException, InternalCompilerException;
}
class NoScopeVar extends Value implements Storage {
	String name;
	NoScopeVar(String nam, Type typ) {
		name = nam;
		type = typ;
	}
	public String toString() {
		return (type + " " + name);
	}
	public void store() throws InternalCompilerException {
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
	public Type bring() throws NotImplementedException, InternalCompilerException {
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
class StackVar extends Value implements Storage {//Arguments passed in the SystemVi386CallingConvention-like way are stack variables, scoped to the entire function
	long pos;//Offset from base pointer of calling convention
	public Type bring() throws InternalCompilerException {
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
	public void store() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.size) {
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
	Type bring() throws CompilationException, InternalCompilerException {
		Type last;
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
					if (!(((Value) last).type.addressable)) {
						if ((((Value) last).type.size != 16) && (((Value) last).type.size != 32) && (((Value) last).type.size != 64)) {
							throw new CompilationException("Call to a non-addressable value: Cast the value to an addressable value first");//TODO implement casting
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
