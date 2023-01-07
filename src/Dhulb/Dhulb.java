package Dhulb;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.TreeMap;
class Compiler {//TODO keywords: "imply" (like extern, also allows illegal names to be implied so that they can be accessed using the syntax which allows global variables with illegal names as well as global function with illegal names to be accessed, which has not yet been implemented), "linkable" (like globl), "assert" (change stack variable's full type to any full type (different than just casting, since the amount of data read can change based on the size of the type (this is necessary for stack variables but not for global variables since global variables can just referenced and then the pointing clause or dereferencing method of that reference changed, while doing that for stack variables would produce a stack segment-relative address whether the LEA instruction is used or the base pointer offset for the stack variable is used and this is not always good, since it would not work when the base of the data segment is not the same as the base of the stack segment or when the data segment's limit does not encompass the entirety of the data, given that addresses are specified to be logical address offset values for the data segment, though this does not happen with many modern user-space program loaders))) (or some other names like those)
	public static PrintStream nowhere;//Never be modified after initial setting
	public static PrintStream prologue;
	public static PrintStream epilogue;
	public static PrintStream rwdata;
	public static PrintStream text;
	public static PrintStream proback;
	public static PrintStream epiback;
	public static PrintStream rwdatback;
	public static PrintStream texback;
	public static PushbackInputStream in = new PushbackInputStream(System.in, 1536);//Do not unread too much
	public static int mach = 0;//0: 8086; 1: 80386 32-bit mode; 2: AMD64 64-bit mode
	public static final long FALSI = 1;
	public static final long VERIF = 0;
	public static boolean typeNicksApplicable = false;
	public static boolean allowTypeNicks = false;
	public static Type defUInt = Type.u16;
	public static Type defSInt = Type.s16;
	public static Type def754 = Type.f32;
	public static Type defAdr = Type.a16;
	public static int CALL_SIZE_BITS = 16;//default address size (for global variable references, global function calls, and default global function calling conventions); must be 16, 32, or 64
	public static boolean showCompilationErrorStackTrace = false;
	public static int warns = 0;
	public static long numericVersion = 3;//TODO bump when needed, should be bumped every time that stringVersion is bumped; do NOT remove this to-do marker
	public static String stringVersion = "0.0.0.3";//TODO bump when needed, should be bumped every time that numericVersion is bumped; do NOT remove this to-do marker
	public static TreeMap<String, NoScopeVar> HVars = new TreeMap<String, NoScopeVar>();
	public static TreeMap<String, Function> HFuncs = new TreeMap<String, Function>();
	public static Stack<Map<String, StackVar>> context = new Stack<Map<String, StackVar>>();
	public static ArrayList<Compilable> program = new ArrayList<Compilable>();
	public static void main(String[] argv) throws IOException, InternalCompilerException {//TODO change operator output behaviour to match CPU instruction output sizes
		try {//TODO create a way for an address to be gotten from a named global function
			nowhere = new PrintStream(new NullOutputStream());
			prologue = new PrintStream(new BufferedOutputStream(System.out));
			proback = prologue;
			epilogue = new PrintStream(new BufferedOutputStream(System.out));
			epiback = epilogue;
			rwdata = new PrintStream(new BufferedOutputStream(System.out));
			rwdatback = rwdata;
			text = new PrintStream(new BufferedOutputStream(System.out));
			texback = text;
			//TODO prologue
			ma(argv);
			prologue = proback;
			epilogue = epiback;
			rwdata = rwdatback;
			text = texback;
			//TODO epilogue
			finishStreams();
		}
		catch (CompilationException exc) {
			prologue = proback;
			epilogue = epiback;
			rwdata = rwdatback;
			text = texback;
			System.err.println("Compilation error: " + exc.getMessage());
			if (showCompilationErrorStackTrace) {
				exc.printStackTrace(System.err);
			}
			epilogue.println(".err # Dhulb compilation error: " + exc.getMessage());
			//TODO epilogue
			finishStreams();
			System.exit(3);
		}
		catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(4);
		}
	}
	static void finishStreams() {
		prologue.flush();
		rwdata.flush();
		text.flush();
		epilogue.flush();
	}
	public static void ma(String[] argv) throws CompilationException, InternalCompilerException, IOException {//TODO make some system for the compiler to know and manage needed register preservation
		mach = Integer.parseInt(argv[0]);
		if ((mach != 16) && (mach != 32) && (mach != 64)) {
			throw new CompilationException("Illegal target");
		}
		Compiler.rwdata.println(".data");
		Compiler.text.println(".text");
		if (mach == 16) {
			Compiler.text.println(".code16");
			CALL_SIZE_BITS = 16;
			defUInt = Type.u16;
			defSInt = Type.s16;
			def754 = Type.f32;
			defAdr = Type.a16;
		}
		else if (mach == 32) {
			Compiler.text.println(".code32");
			CALL_SIZE_BITS = 32;
			defUInt = Type.u32;
			defSInt = Type.s32;
			def754 = Type.f32;
			defAdr = Type.a32;
		}
		else if (mach == 64) {
			Compiler.text.println(".code64");
			CALL_SIZE_BITS = 64;
			defUInt = Type.u64;
			defSInt = Type.s64;
			def754 = Type.f64;
			defAdr = Type.a64;
		}
		else {
			throw new InternalCompilerException("Unidentifiable target");
		}
		if (argv[1].contains("t")) {
			showCompilationErrorStackTrace = true;
		}
		if (argv[1].contains("N")) {
			allowTypeNicks = true;
			typeNicksApplicable = true;
		}
		try {
			while (true) {
				getCompilable(program, false, null);
			}
		}
		catch (EOFException e) {
			for (Compilable c : program) {
				c.compile();
			}
		}
	}
	static void getCompilable(ArrayList<Compilable> list, boolean inFunc, Function fn) throws CompilationException, InternalCompilerException, IOException {
		if (inFunc && (fn == null)) {
			throw new InternalCompilerException("Function not provided");
		}
		String s;
		FullType typ;
		int i;
		Util.skipWhite();
		i = Util.read();
		if (i == '}') {
			throw new BlockEndException();
		}
		else if (i == '{') {
			throw new NotImplementedException();//TODO support for blocks (saves stack space)
		}
		else if (i == '/') {
			if ((i = Util.read()) == '%') {
				RawText r = new RawText(rwdatback);
				try {
					while (true) {
						i = Util.readByte();
						if (i == '\\') {
							r.dat.put((byte) Util.readByte());
						}
						else if (i == '%') {
							if ((i = Util.readByte()) == '/') {
								list.add(r);
								return;
							}
							r.dat.put((byte) '%');
							r.dat.put((byte) i);
						}
						else {
							r.dat.put((byte) i);
						}
					}
				}
				catch (EOFException E) {
					throw new CompilationException("Un-closed comment");
				}
			}
			else if (i == '&') {
				RawText w = new RawText(texback);
				try {
					while (true) {
						i = Util.readByte();
						if (i == '\\') {
							w.dat.put((byte) Util.readByte());
						}
						else if (i == '&') {
							if ((i = Util.readByte()) == '/') {
								list.add(w);
								return;
							}
							w.dat.put((byte) '&');
							w.dat.put((byte) i);
						}
						else {
							w.dat.put((byte) i);
						}
					}
				}
				catch (EOFException E) {
					throw new CompilationException("Un-closed comment");
				}
			}
			else if (i == '*') {
				try {
					while (true) {
						if (Util.read() == '*') {
							if ((i = Util.read()) == '/') {
								return;
							}
							else {
								Util.unread(i);
							}
						}
					}
				}
				catch (EOFException E) {
					throw new CompilationException("Un-closed comment");
				}
			}
			else {
				Util.unread(i);
			}
			i = '/';
		}
		Util.unread(i);
		try {
			typ = FullType.from();
		}
		catch (UnidentifiableTypeException utt) {
			s = utt.verbatim;//s = phrase(0x2d)
			if (Util.legalIdent(s)) {
				Storage st = null;
				if (inFunc) {
					for (Map<String, StackVar> cn : context) {
						if (cn.containsKey(s)) {
							st = cn.get(s);
							break;
						}
					}
				}
				if (st == null) {
					st = HVars.get(s);
				}
				if (st == null) {
					if (HFuncs.get(s) != null) {
						list.add(Expression.from(';', new FunctionAddr(s)));
					}
					else {
						throw new NondefinitionException(s + " is not defined");
					}
				}
				else {
					Util.skipWhite();
					i = Util.read();
					if (i == '=') {
						list.add(new Assignment(st, Expression.from(';')));
					}
					else {
						Util.unread(i);
						list.add(Expression.from(';', (Item) st));
					}
				}
			}
			else if (Util.keywork.contains(s)) {
				switch (s) {
					case ("imply"):
						typeNicksApplicable = false;
						Util.skipWhite();
						FullType ft = FullType.from();
						Util.skipWhite();
						String r = Util.phrase(0x3d);
						Util.skipWhite();
						int ne = Util.read();
						if (ne == '(') {
							FullType[] fts = FullType.getList(')');
							Util.skipWhite();
							ne = Util.read();
							if (ne == ';') {
								HFuncs.put(r, new Function(ft, fts, null, r));
							}
							else {
								Util.unread(ne);
								String j = Util.phrase(0x24);
								Util.skipWhite();
								ne = Util.read();
								if (ne != ';') {
									throw new CompilationException("Unexpected operator: " + new String(new int[]{ne}, 0, 1));
								}
								switch (j) {
									case ("call16"):
										HFuncs.put(r, new Function(ft, fts, null, r, 16));
										break;
									case ("call32"):
										HFuncs.put(r, new Function(ft, fts, null, r, 32));
										break;
									case ("call64"):
										HFuncs.put(r, new Function(ft, fts, null, r, 64));
										break;
									default:
										throw new CompilationException("Invalid qualifier: " + j);
								}
							}
						}
						else if (ne != ';') {
							throw new CompilationException("Unexpected operator: " + new String(new int[]{ne}, 0, 1));
						}
						else {
							HVars.put(r, new NoScopeVar(r, ft));
						}
						typeNicksApplicable = allowTypeNicks;
						return;
					default:
						throw new NotImplementedException("Not implemented or invalid syntax");
				}
			}
			else {
				if (s.length() == 0) {
					i = Util.read();
					if (i == ';') {
						throw new CompilationException("Empty expression");
					}
					Util.unread(i);
					list.add(Expression.from(';'));
				}
				else {
					Literal j;
					try {
						j = Util.getLit(s);
					}
					catch (NumberFormatException E) {
						throw new CompilationException("Invalid literal notation: " + s);
					}
					list.add(Expression.from(';', j));
				}
			}
			return;
		}
		Util.skipWhite();
		s = Util.phrase(0x3d);
		if (!(Util.legalIdent(s))) {
			throw new CompilationException("Illegal identifier: " + s);
		}
		if (inFunc) {
			for (Map<String, StackVar> cn : context) {
				if (cn.containsKey(s)) {
					throw new CompilationException("Stack variable naming collision: " + s + " is defined again in the same scope or an enclosing scope");
				}
			}
		}
		else {
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
			if (inFunc) {
				StackVar sav = new StackVar(fn.adjust(-((typ.type.size / 8) + (((typ.type.size % 8) == 0) ? 0 : 1))), typ);
				context.peek().put(s, sav);
				list.add(new Assignment(sav, Expression.from(';')));
			}
			else {
				list.add(new globalVarDecl(s, typ));
				NoScopeVar thno = new NoScopeVar(s, typ);
				HVars.put(s, thno);//Before the assignment so that the assignment can refer the the variable itself, which should contain the default value when dealing with a global variable and whatever data was already there when dealing with a stack variable
				list.add(new Assignment(thno, Expression.from(';')));//TODO Avoid eventually bringing the number if it is constant
			}
		}
		else if (i == ';') {
			if (inFunc) {
				context.peek().put(s, new StackVar(fn.adjust(-((typ.type.size / 8) + (((typ.type.size % 8) == 0) ? 0 : 1))), typ));
			}
			else {
				list.add(new globalVarDecl(s, typ));
				HVars.put(s, new NoScopeVar(s, typ));
			}
		}
		else if (i == '(') {
			if (inFunc) {
				throw new CompilationException("Function declaration not allowed within a function");//TODO maybe implement nested functions: it would be kind of easy, to take advantage of the already-existing stack of function-scoped values
			}
			list.add(Function.from(typ, s, HFuncs));
		}
		else {
			throw new CompilationException("Illegal operator: " + new String(new int[]{i}, 0, 1));
		}
	}
}
class Util {
	static ArrayList<Integer> brack = new ArrayList<Integer>();
	static int[] brace = new int[]{'(', ')', '[', ']', '{', '}', '<', '>'};
	static ArrayList<String> keywork = new ArrayList<String>();
	static String[] keywore = new String[]{"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "goto", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "u8", "s8", "u16", "s16", "u32", "s32", "u64", "s64", "f32", "f64", "a16", "a32", "a64", "uint", "sint", "addr", "imply"};
	static ArrayList<String> accesk = new ArrayList<String>();
	static String[] accese = new String[]{"private", "protected", "public"};
	static ArrayList<String> boolitk = new ArrayList<String>();
	static String[] boolite = new String[]{"false", "true"};
	static ArrayList<String> nulitk = new ArrayList<String>();
	static String[] nulite = new String[]{"null"};
	static ArrayList<String> primsk = new ArrayList<String>();
	static String[] primse = new String[]{"u8", "s8", "u16", "s16", "u32", "s32", "u64", "s64", "f32", "f64", "a16", "a32", "a64", "uint", "sint", "float", "addr", "int"};//TODO remove the platform-dependent aliases, use a plug-in for them instead
	static ArrayList<Integer> inpork = new ArrayList<Integer>();
	static int[] inpore = new int[]{'+', '-', '=', '/', '<', '>', '*', '%', ',', '$'};
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
	static String signedRestrict(long val, int size) throws SizeNotFitException, InternalCompilerException {
		switch (size) {
			case (8):
				if ((val > 0x7f) || (val < (-(0x80)))) {
					throw new SizeNotFitException();
				}
				return herxb(val);
			case (16):
				if ((val > 0x7f) || (val < (-(0x80)))) {
					if ((val > 0x7fff) || (val < (-(0x8000)))) {
						throw new SizeNotFitException();
					}
					return herxs(val);
				}
				return herxb(val);
			case (32):
				if ((val > 0x7f) || (val < (-(0x80)))) {
					if ((val > 0x7fff) || (val < (-(0x8000)))) {
						if ((val > 0x7fffffff) || (val < (-(0x80000000)))) {
							throw new SizeNotFitException();
						}
						return herxi(val);
					}
					return herxs(val);
				}
				return herxb(val);
			case (64):
				if ((val > 0x7f) || (val < (-(0x80)))) {
					if ((val > 0x7fff) || (val < (-(0x8000)))) {
						if ((val > 0x7fffffff) || (val < (-(0x80000000L)))) {
							if ((val > 0x7fffffffffffffffL) || (val < (-(0x8000000000000000L)))) {
								throw new SizeNotFitException();
							}
							return herxl(val);
						}
						return herxi(val);
					}
					return herxs(val);
				}
				return herxb(val);
			default:
				throw new InternalCompilerException("Invalid value restriction");
		}
	}
	static void warn(String s) {
		Compiler.warns++;
		System.err.println("Warning: " + s);
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
	static int readByte() throws IOException {//returns an int where the 24 high-order bits are zero
		int i = Compiler.in.read();
		if (i == (-(1))) {
			throw new EOFException();
		}
		return i;
	}
	static void unread(String s) throws IOException {
		byte[] bys = s.getBytes(StandardCharsets.UTF_8);
		for (int i = bys.length - 1; i >= 0; i--) {
			Compiler.in.unread(bys[i]);
		}
	}
	static void unread(int f) throws IOException {
		if ((f > 0x10ffff) || (f < 0)) {
			throw new IOException("Invalid Unicode character");
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
		if (!(Character.isJavaIdentifierStart(h))) {//TODO don't depend on Java specification
			return false;
		}
		if (h > 0xffff) {
			c++;
		}
		c++;
		try {
			while (true) {
				if (!(Character.isJavaIdentifierPart(h = s.codePointAt(c)))) {//TODO don't depend on Java specification
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
	static String herxb(long b) {
		if (b >= 0) {
			return "0x" + hexb(b);
		}
		return "-0x" + hexb(-(b));
	}
	static String herxs(long w) {
		if (w >= 0) {
			return "0x" + hexs(w);
		}
		return "-0x" + hexs(-(w));
	}
	static String herxi(long i) {
		if (i >= 0) {
			return "0x" + hexi(i);
		}
		return "-0x" + hexi(-(i));
	}
	static String herxl(long l) {
		if (l >= 0) {
			return "0x" + hexl(l);
		}
		return "-0x" + hexl(-(l));
	}
	static void bring16(char reg, short val) {
		if (((val & 0xff00) != 0) && ((val & 0x00ff) != 0)) {
			Compiler.text.println("movw $0x" + Util.hexs(val) + ",%" + reg + "x");
		}
		else if ((val & 0x00ff) != 0) {
			Compiler.text.println("xorb %" + reg + "h,%" + reg + "h");
			Compiler.text.println("movb $0x" + Util.hexb(val) + ",%" + reg + "l");
		}
		else if ((val & 0xff00) != 0) {
			Compiler.text.println("xorb %" + reg + "l,%" + reg + "l");
			Compiler.text.println("movb $0x" + Util.hexb(val >>> 8) + ",%" + reg + "h");
		}
		else {
			Compiler.text.println("xorw %" + reg + "x,%" + reg + "x");
		}
	}
}
class Function implements Compilable {
	FullType retType;
	String name;//symbol name
	FullType[] dargs;
	Doable[] does;//null if the function represents a function declared outside of the file; non-null otherwise
	private transient long bpOff = 0;//offset from the base pointer 
	private long minOff = 0;//lowest value of the offset from the base pointer without processing blocks
	public final int abiSize;
	private Function() {
		abiSize = Compiler.CALL_SIZE_BITS;
	}
	Function(FullType rett, FullType[] ar, Doable[] doz, String nam) {
		retType = rett;
		dargs = ar;
		does = doz;
		name = nam;
		abiSize = Compiler.CALL_SIZE_BITS;
	}
	Function(FullType rett, FullType[] ar, Doable[] doz, String nam, int siz) throws CompilationException {
		retType = rett;
		dargs = ar;
		does = doz;
		name = nam;
		if ((siz != 16) && (siz != 32) && (siz != 64)) {
			throw new CompilationException("Invalid address size");
		}
		abiSize = siz;
	}
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(retType.toString());
		sb.append(" ");
		sb.append(name);
		sb.append("(");
		Iterator<FullType> svs = Arrays.stream(dargs).iterator();
		FullType sv;
		while (svs.hasNext()) {
			sv = svs.next();
			sb.append(sv.toString());
			if (svs.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}
	long adjust(long n) {//TODO add support for blocks
		bpOff += n;
		if (bpOff < minOff) {
			minOff = bpOff;
		}
		return bpOff;
	}
	public void compile() throws CompilationException, InternalCompilerException, IOException {
		Compiler.text.println(name + ": #dhulbDoc-v" + Compiler.numericVersion + ":" + this.toString() + " " + "call" + abiSize);
		switch (Compiler.mach) {
			case (0):
				Compiler.text.println("pushw %bp");
				Compiler.text.println("movw %sp,%bp");
				if (minOff > 0) {
					throw new InternalCompilerException("This exceptional condition should not occur!");
				}
				if (minOff < 0) {//not an else if since the above if statement is for testing purposes
					Compiler.text.println("subw $" + Util.signedRestrict(bpOff, 16) + ",%sp");
				}
				break;
			case (1):
				Compiler.text.println("pushl %ebp");
				Compiler.text.println("movl %esp,%ebp");
			case (2):
				throw new NotImplementedException();
			default:
				throw new InternalCompilerException("Unidentifiable target");
		}
		for (Doable d : does) {
			d.compile();
		}
		//don't implicitly return at the end of the function body; force the programmer to make sure that nothing reaches the end without a return statement unless they actually want the function to not exit properly in those cases
	}
	static Function from(FullType rett, String nam, TreeMap<String, Function> funcs) throws NotImplementedException, CompilationException, InternalCompilerException, IOException {//starts at the parameters, first thing is the first non-whitespace character after the opening parenthesis (whitespace before it allowed), consumes everything up to the closing curly brace, inclusive
		Function fn = new Function();
		fn.retType = rett;
		fn.name = nam;
		LinkedHashMap<String, StackVar> ar = FullType.getArgs(')', fn.abiSize);
		funcs.put(nam, fn);
		Util.skipWhite();
		int i = Util.read();
		if (i != '{') {
			throw new CompilationException("Unexpected operator at start of function body: " + new String(new int[]{i}, 0, 1));
		}
		ArrayList<Compilable> comps = new ArrayList<Compilable>();
		Iterator<Entry<String, StackVar>> svs = ar.entrySet().iterator();
		FullType[] ad = new FullType[ar.size()];
		Entry<String, StackVar> sv;
		int c = 0;
		while (svs.hasNext()) {
			sv = svs.next();
			ad[c] = sv.getValue().type;
			c++;
		}
		fn.dargs = ad;
		Compiler.context.push(ar);
		try {
			while (true) {
				Compiler.getCompilable(comps, true, fn);
			}
		}
		catch (BlockEndException e) {
			fn.does = comps.toArray(new Doable[0]);
			Compiler.context.pop();
			return fn;
		}
	}
}
class Assignment implements Doable {
	final Storage place;
	final Value assignee;
	Assignment(Storage s, Value asgn) {
		place = s;
		assignee = asgn;
	}
	public void compile() throws CompilationException, InternalCompilerException {
		assignee.bring();
		FullType t1 = assignee.type;
		FullType t2 = place.type();
		if (!(t1.equals(t2))) {
			t1.cast(t2);
		}
		place.store();
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
	static final FullType a64 = new FullType(Type.a64);//Do not depend on pointers to any of these matching with anything; these are only to negate the need of repeatedly making FullType instances
	final Type type;
	final FullType[] runsWith;//non-null when there is a calling clause; null otherwise
	final FullType gives;//non-null when there is a pointing clause (specifies particular full type that must be pointed to) and also when there is a calling clause; null otherwise
	public boolean equals(Object o) {
		if (!(o instanceof FullType)) {
			return super.equals(o);
		}
		if (type != ((FullType) o).type) {
			return false;
		}
		if (gives != ((FullType) o).gives) {
			return false;
		}
		if (gives == null) {
			return true;
		}
		if ((runsWith == null) != (((FullType) o).runsWith == null)) {
			return false;
		}
		if (runsWith == null) {
			return true;
		}
		if (runsWith.length != ((FullType) o).runsWith.length) {
			return false;
		}
		for (int i = 0; i < runsWith.length; i++) {
			if (!(runsWith[i].equals(((FullType) o).runsWith[i]))) {
				return false;
			}
		}
		return true;
	}
	public boolean provides(FullType typ) throws NotImplementedException {//if this already serves as an instance of typ without warnings
		if (typ.type != this.type) {
			return false;
		}
		throw new NotImplementedException();//TODO finish writing this
	}
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
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(type);
		if (gives != null) {
			if (runsWith != null) {
				sb.append("$");
				sb.append(gives);
				sb.append("(");
				for (int i = 0; i < runsWith.length; i++) {
					sb.append(runsWith[i].toString());
					if (i != (runsWith.length - 1)) {
						sb.append(", ");
					}
				}
				sb.append(")");
			}
			else {
				sb.append("*");
				sb.append(gives);
			}
		}
		return sb.toString();
	}
	static FullType from() throws UnidentifiableTypeException, CompilationException, InternalCompilerException, IOException {//Throws UnidentifiableTypeText when the phrase(0x29) call yields a string which does not correspond to any valid type or any valid type shorthand notation
		String s = Util.phrase(0x2d);
		Type typ;
		if (Util.primsk.contains(s)) {
			try {
				typ = Type.valueOf(s);
			}
			catch (IllegalArgumentException E) {
				if (s.equals("uint")) {
					if (!(Compiler.typeNicksApplicable)) {
						Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
						throw new CompilationException("Illegal use of a platform-dependent type name");
					}
					typ = Compiler.defUInt;
				}
				else if (s.equals("sint") || s.equals("int")) {
					if (!(Compiler.typeNicksApplicable)) {
						Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
						throw new CompilationException("Illegal use of a platform-dependent type name");
					}
					typ = Compiler.defSInt;
				}
				else if (s.equals("float")) {
					if (!(Compiler.typeNicksApplicable)) {
						Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
						throw new CompilationException("Illegal use of a platform-dependent type name");
					}
					typ = Compiler.def754;
				}
				else if (s.equals("addr")) {
					if (!(Compiler.typeNicksApplicable)) {
						Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
						throw new CompilationException("Illegal use of a platform-dependent type name");
					}
					typ = Compiler.defAdr;
				}
				else {
					throw new InternalCompilerException("Type cannot be found: " + s);
				}
			}
		}
		else {
			throw new UnidentifiableTypeException(s);
		}
		try {
			Util.skipWhite();
			int ci = Util.read();
			if (ci == '*') {
				if (!(typ.addressable)) {
					throw new CompilationException("Cannot impose pointing clause for non-addressable type");
				}
				Util.skipWhite();
				FullType given = from();
				return new FullType(typ, given);
			}
			else if (ci == '$') {
				if (!(typ.addressable)) {
					throw new CompilationException("Cannot impose calling clause for non-addressable type");
				}
				Util.skipWhite();
				FullType given = from();
				Util.skipWhite();
				ci = Util.read();
				if (ci != '(') {
					throw new CompilationException("Calling clause is missing arguments");
				}
				return new FullType(typ, given, getList(')'));
			}
			else {
				Util.unread(ci);
				return of(typ);
			}
		}
		catch (UnidentifiableTypeException E) {
			throw new CompilationException("Unidentifiable type: " + E.verbatim);
		}
	}
	static FullType[] getList(int ending) throws UnidentifiableTypeException, CompilationException, InternalCompilerException, IOException {//gets list of comma-delimited fullType entries, ended with the ending character (there is no comma after the last entry) (0-length allowed) (whitespace allowed)
		ArrayList<FullType> fl = new ArrayList<FullType>();
		Util.skipWhite();
		int ci = Util.read();
		if (ci == ')') {
			return new FullType[0];
		}
		Util.unread(ci);
		while (true) {
			fl.add(from());
			Util.skipWhite();
			ci = Util.read();
			if (ci == ')') {
				return fl.toArray(new FullType[0]);
			}
			else if (ci != ',') {
				throw new CompilationException("Unexpected statement");
			}
			Util.skipWhite();
		}
	}
	static LinkedHashMap<String, StackVar> getArgs(int ending, long abiSize) throws UnidentifiableTypeException, CompilationException, InternalCompilerException, IOException {//gets list of comma-delimited fullType entries, ended with the ending character (there is no comma after the last entry) (0-length allowed) (whitespace allowed)
		LinkedHashMap<String, StackVar> fl = new LinkedHashMap<String, StackVar>();
		FullType ft;
		String nam;
		long from = 2L * ((abiSize / 8L) + (((abiSize % 8L) == 0) ? 0L : 1L));
		Util.skipWhite();
		int ci = Util.read();
		if (ci == ')') {
			return fl;
		}
		Util.unread(ci);
		while (true) {
			ft = from();
			Util.skipWhite();
			nam = Util.phrase(0x3d);
			if (!Util.legalIdent(nam)) {
				throw new CompilationException("Illegal identifier: " + nam);
			}
			if (fl.containsKey(nam)) {
				throw new CompilationException("Duplicate argument name in function declaration: " + nam);
			}
			fl.put(nam, new StackVar(from, ft));
			from += (((ft.type.size) / 8L) + (((ft.type.size % 8L) == 0) ? 0L : 1L));
			Util.skipWhite();
			ci = Util.read();
			if (ci == ')') {
				return fl;
			}
			else if (ci != ',') {
				throw new CompilationException("Unexpected statement");
			}
			Util.skipWhite();
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
					Util.warn("Cast from u16 to a16");
				}
				break;
			case s16:
				if (!(toType.type.size == 16)) {
					throw new NotImplementedException();
				}
				if (toType.type.addressable) {
					Util.warn("Cast from s16 to a16");
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
class Call extends Value {
	final Value addr;//size of the type determines the ABI used
	final Value[] args;//array length is the same as the amount of values used in the call
	final long pushedBits;
	Call(Value v, Value[] g) throws CompilationException, InternalCompilerException {
		args = g;
		addr = v;
		if (!(v.type.type.addressable)) {
			throw new CompilationException("Function address value is not of an addressable type");
		}
		if (v.type.runsWith == null) {
			throw new CompilationException("Function address value has no calling clause");
		}
		if (v.type.gives == null) {
			throw new InternalCompilerException("This exceptional condition should not occur!");
		}
		type = v.type.gives;
		pushedBits = warn(args, v.type.runsWith);
	}
	static long warn(Value[] args, FullType[] dargs) throws InternalCompilerException {
		boolean dif = false;
		long totalFn = 0;
		long totalAr = 0;
		int i = -(1);
		for (FullType t : dargs) {
			i++;
			totalFn += t.type.size;
			if (args.length <= i) {
				dif = true;
			}
			else {
				totalAr += args[i].type.type.size;
				if (totalFn != totalAr) {
					dif = true;
				}
			}
		}
		for (i++; i < args.length; i++) {
			totalAr += args[i].type.type.size;
		}
		if (dif) {
			if (totalFn > totalAr) {
				Util.warn("Function call provides less bits of data than specified by the function argument(s)");
			}
			else if (totalAr > totalFn) {
				throw new NotImplementedException("This error message has not yet been written!");//TODO let compiler warn that this is a raw conversion, even noting this in ways which allow the last argument to be noted to be partially-used (the amount used is noted) when it is
			}
			else {
				StringBuilder sb = new StringBuilder();
				sb.append("Raw conversion from provided argument(s) (");
				for (i = 0; i < args.length; i++) {
					sb.append(args[i].type.toString());
					if (i < (args.length - 1)) {
						sb.append(", ");
					}
				}
				sb.append(") to specified argument(s) (");
				for (i = 0; i < dargs.length; i++) {
					sb.append(dargs[i].toString());
					if (i < (dargs.length - 1)) {
						sb.append(", ");
					}
				}
				sb.append(")");
				Util.warn(sb.toString());
			}
		}
		else {
			i = -(1);
			for (FullType t : dargs) {
				i++;
				if (!(args[i].type.equals(t))) {
					if (args[i].type.type.size != t.type.size) {
						throw new InternalCompilerException("This exceptional condition should not occur!");
					}
					if (args[i].type.type != t.type) {
						Util.warn("Raw conversion from provided argument " + args[i].type.toString() + " to specified function argument " + t.toString());
					}
					else {
						if (!(t.type.addressable && args[i].type.type.addressable)) {
							throw new InternalCompilerException("This exceptional condition should not occur!");
						}
						if (!(args[i].type.provides(t))) {//TODO implement the instance method FullType.provides(FullType), then change the 2 next error messages to alert to a definite ignorance; this will satisfy the to-do statement on the same line of each, for each
							if (t.gives != null) {
								if (t.runsWith == null) {
									Util.warn("Possible ignorance of specified pointing clause in argument provision for function call");//TODO instead of mentioning the possibility, actually check if the provision is still viable despite inequality (ex. gr. a32*a32$s32(a32) can be provided when a32*a32$s32(a32*u16) is expected) and tell of the ignorance, only if it is not viable
								}
								else {
									Util.warn("Possible ignorance of specified calling clause in argument provision for function call");//TODO instead of mentioning the possibility, actually check if the provision is still viable despite inequality (ex. gr. a32$s32(a32) can be provided when a32$s32(a32*u16) is expected) and tell of the ignorance, only if it is not viable
								}
							}
							else {
								throw new InternalCompilerException("This exceptional condition should not occur!");
							}
						}
					}
				}
			}
			if (totalAr > totalFn) {
				Util.warn("Function call provides extra data");
			}
			if (totalFn > totalAr) {
				throw new InternalCompilerException("This exceptional condition should not occur!");//reaching this line of code should not be possible
			}
		}
		return totalAr;
	}
	FullType bring() throws CompilationException, InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (addr.type.type.size) {
					case (16):
						before16_16();
						args16_16(args.length, args);
						addr.bring();
						addr.type.cast(FullType.a16);
						call16();
						after16_16();
						break;
					case (32):
						throw new NotImplementedException();
					case (64):
						throw new NotImplementedException();
					default:
						throw new InternalCompilerException("Illegal operand size");
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
	static Value[] from() throws CompilationException, InternalCompilerException, IOException {//reads starting from after the opening parenthesis of the arguments, consumes the closing parenthesis
		ArrayList<Value> args = new ArrayList<Value>();
		int i = Util.read();
		if (i == ')') {
			return new Value[0];
		}
		else {
			Util.unread(i);
			Expression ex;
			do {
				args.add(ex = Expression.from(',', ')'));
			}
			while (!(ex.auxEnding));
			return args.toArray(new Value[0]);
		}
	}
	static void before16_16() {
	}
	static void after16_16() {
	}
	static void args16_32(int amnt, Value[] vals) throws InternalCompilerException, CompilationException {
		args16_16(amnt, vals);
	}
	static void args16_16(int amnt, Value[] vals) throws InternalCompilerException, CompilationException {//argument pushing for calls from 16-bit code using the 16-bit ABI or the 32-bit ABI (the 32-bit ABI is the System V ABI for Intel386 and the 16-bit ABI is the same thing but with 16-bit calls instead of 32-bit calls and %ebx can be scratched in functions and data is returned from functions, from lowest to highest word, in %ax, %dx, %cx, and %bx)
		FullType typ;
		for (int i = (amnt - 1); i >= 0; i--) {//TODO allow more than Integer.MAX_VALUE arguments to be used
			typ = vals[i].bring();
			switch (typ.type.size) {
				case (8):
					Compiler.text.println("decw %sp");
					Compiler.text.println("movb %al,(%sp)");
					break;
				case (64):
					Compiler.text.println("pushw %bx");
					Compiler.text.println("pushw %cx");
				case (32):
					Compiler.text.println("pushw %dx");
				case (16):
					Compiler.text.println("pushw %ax");
					break;
				default:
					throw new InternalCompilerException("Illegal datum size");
			}
		}
	}
	void call16() {//TODO obey the ABI 
		Compiler.text.println("movw %ax,%bx");
		Compiler.text.println("callw (%bx)");
	}
	void call32() {
		Compiler.text.println("calll (%eax)");
	}
	void call64() {
		Compiler.text.println("callq (%rax)");
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
interface Compilable {
	public void compile() throws CompilationException, InternalCompilerException, IOException;
}
interface Doable extends Compilable {
}
class Block implements Doable {
	ArrayList<Compilable> comps;
	Block(ArrayList<Compilable> c) {
		comps = c;
	}
	Block from(Function f) throws CompilationException, InternalCompilerException, IOException {
		ArrayList<Compilable> c = new ArrayList<Compilable>();
		Compiler.context.push(new TreeMap<String, StackVar>());
		try {
			while (true) {
				Compiler.getCompilable(c, true, f);
			}
		}
		catch (BlockEndException e) {
			Compiler.context.pop();
			return new Block(c);
		}
	}
	public void compile() throws CompilationException, InternalCompilerException, IOException {
		for (Compilable cpl : comps) {
			cpl.compile();
		}
	}
}
abstract class Value extends Item implements Doable {
	FullType type;
	abstract FullType bring() throws CompilationException, InternalCompilerException;
	public void compile() throws CompilationException, InternalCompilerException {
		bring();
	}
}
class globalVarDecl implements Compilable {
	final FullType type;
	final String name;
	globalVarDecl(String s, FullType typ) {
		type = typ;
		name = s;
	}
	public void compile() throws InternalCompilerException {
		Compiler.rwdata.println(name + ": #dhulbDoc-v" + Compiler.numericVersion + ":" + type.toString() + " " + name);//automatic documentation, the name should be the same as the symbol name
		switch (type.type.size) {
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
	}
}
class RawText implements Doable {
	final ByteBuffer dat;
	boolean tex;
	RawText(OutputStream os) throws InternalCompilerException {
		if ((os != Compiler.rwdatback) && (os != Compiler.texback)) {
			throw new InternalCompilerException("Illegal byte stream for raw text output");
		}
		tex = os == Compiler.texback;
		dat = ByteBuffer.allocate(8192);
	}
	public void compile() throws IOException {
		int i;
		if (tex) {
			Compiler.texback.write(dat.array(), i = dat.arrayOffset(), i + dat.position());
			dat.position();
		}
		else {
			Compiler.rwdatback.write(dat.array(), i = dat.arrayOffset(), i + dat.position());
			dat.position();
		}
	}
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
						Compiler.text.println("xorb %al,%al");
					}
					else {
						Compiler.text.println("movb $" + Util.hexb(val) + ",%al");
					}
				}
				else if (type.type.size == 16) {
					Util.bring16('a', (short) val);
				}
				else if (type.type.size == 32) {
					Util.bring16('a', ((short) val));
					Util.bring16('d', ((short) (val >>> 16)));
				}
				else if (type.type.size == 64) {
					Util.bring16('a', ((short) val));
					Util.bring16('d', ((short) (val >>> 16)));
					Util.bring16('c', ((short) (val >>> 32)));
					Util.bring16('b', ((short) (val >>> 48)));
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
	static final Operator XOR = new Operator(false, '^');//("^") Bit-wise exclusive or
	static final Operator OR = new Operator(false, '|');//("|") Bit-wise or
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
	public FullType type();
	public void store() throws CompilationException, InternalCompilerException;
	public FullType bring() throws CompilationException, InternalCompilerException;
}
class NoScopeVar extends Value implements Storage {
	String name;//symbol name
	NoScopeVar(String nam, FullType typ) {
		name = nam;
		type = typ;
	}
	public FullType type() {
		return type;
	}
	public String toString() {
		return (type + " " + name);
	}
	public void store() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.type.size) {
					case (64):
						Compiler.text.println("movw %bx," + name + "+6(,1)");
						Compiler.text.println("movw %cx," + name + "+4(,1)");
					case (32):
						Compiler.text.println("movw %dx," + name + "+2(,1)");
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
						Compiler.text.println("movw " + name + "+6(,1),%bx");
						Compiler.text.println("movw " + name + "+4(,1),%cx");
					case (32):
						Compiler.text.println("movw " + name + "+2(,1),%dx");
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
class StackVar extends Value implements Storage {//Arguments passed in the SystemVi386CallingConvention-like way are stack variables scoped to the entire function
	long pos;//Offset from base pointer of calling convention
	StackVar(long p, FullType typ) {
		pos = p;
		type = typ;
	}
	public FullType type() {
		return type;
	}
	public FullType bring() throws InternalCompilerException, SizeNotFitException {
		switch (Compiler.mach) {
			case (0):
				try {
					switch (type.type.size) {
						case (64):
							Compiler.text.println("movw " + Util.signedRestrict(pos + 6, 16) + "(%bp),%bx");
							Compiler.text.println("movw " + Util.signedRestrict(pos + 4, 16) + "(%bp),%cx");
						case (32):
							Compiler.text.println("movw " + Util.signedRestrict(pos + 2, 16) + "(%bp),%dx");
						case (16):
							Compiler.text.println("movw " + Util.signedRestrict(pos, 16) + "(%bp),%ax");
							break;
						case (8):
							Compiler.text.println("movb " + Util.signedRestrict(pos, 16) + "(%bp),%al");
							break;
						default:
							throw new InternalCompilerException("Illegal datum size");
					}
				}
				catch (SizeNotFitException E) {
					throw new SizeNotFitException("Access of stack variable at offset " + pos + " from the base pointer does not abide by addressing mode limitations");
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
	public void store() throws InternalCompilerException, SizeNotFitException {
		switch (Compiler.mach) {
			case (0):
				try {
					switch (type.type.size) {
						case (64):
							Compiler.text.println("movw %bx," + Util.signedRestrict(pos + 6, 16) + "(%bp)");
							Compiler.text.println("movw %cx," + Util.signedRestrict(pos + 4, 16) + "(%bp)");
						case (32):
							Compiler.text.println("movw %dx," + Util.signedRestrict(pos + 2, 16) + "(%bp)");
						case (16):
							Compiler.text.println("movw %ax," + Util.signedRestrict(pos, 16) + "(%bp)");
							break;
						case (8):
							Compiler.text.println("movb %al," + Util.signedRestrict(pos, 16) + "(%bp)");
							break;
						default:
							throw new InternalCompilerException("Illegal datum size");
					}
				}
				catch (SizeNotFitException E) {
					throw new SizeNotFitException("Access of stack variable at offset " + pos + " from the base pointer does not abide by addressing mode limitations");
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
class FunctionAddr extends Value {
	String name;
	FunctionAddr(String nam) throws CompilationException, InternalCompilerException {
		name = nam;
		Function f = Compiler.HFuncs.get(nam);
		if (f == null) {
			throw new NondefinitionException("Undefined code symbol: " + nam);
		}
		Type t;
		switch (f.abiSize) {
			case (16):
				t = Type.a16;
				break;
			case (32):
				t = Type.a32;
				break;
			case (64):
				t = Type.a64;
				break;
			default:
				throw new InternalCompilerException("Illegal address size");
		}
		type = new FullType(t, f.retType, f.dargs);
	}
	FullType bring() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.type.size) {
					case (16):
						Compiler.text.println("movw $" + name + ",%ax");
						break;
					case (32):
						throw new NotImplementedException();//TODO attempt to figure out how to distribute the relocatable symbol between the multiple registers without using more than the 16-bit registers; this may not be possible, so maybe just use the higher instructions
					case (64):
						throw new NotImplementedException();//TODO attempt to figure out how to distribute the relocatable symbol between the multiple registers without using more than the 16-bit registers; this may not be possible, so maybe just use the higher instructions
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
	transient boolean auxEnding;
	private ArrayList<Item> items;
	private boolean finalised;
	private Expression() {
		items = new ArrayList<Item>();
		finalised = false;
	}
	void add(Item i) throws InternalCompilerException {//Should not throw NumberFormatException
		if (finalised) {
			throw new InternalCompilerException("Modification of finalised expression");
		}
		else {
			items.add(i);
		}
	}
	void skim(Item ite) throws InternalCompilerException {
		if (finalised) {
			throw new InternalCompilerException("Modification of finalised expression");
		}
		else {
			int i = items.lastIndexOf(ite);
			if (i == (-(1))) {
				throw new InternalCompilerException("Expression item does not exist");
			}
			items.remove(i);
		}
	}
	public String toString() {
		StringBuilder sb = new StringBuilder();
		asString(sb);
		return sb.toString();
	}
	void asString(StringBuilder sb) {
		int siz = items.size();
		Item ite;
		for (int i = 0; i < siz; i++) {
			if ((ite = items.get(i)) instanceof Expression) {
				sb.append("(");
				sb.append(ite);
				sb.append(")");
			}
			else {
				sb.append(ite);
			}
			if (i != (siz - 1)) {
				sb.append(" ");
			}
		}
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
	static Expression from(int ending) throws InternalCompilerException, IOException, CompilationException {
		return from(ending, null);
	}static Expression from(int ending, Item pri) throws InternalCompilerException, IOException, CompilationException {
		return from(ending, ending, pri);
	}
	static Expression from(int ending, int ending2) throws InternalCompilerException, IOException, CompilationException {
		return from(ending, ending2, null);
	}
	static Expression from(int ending, int ending2, Item pri) throws InternalCompilerException, IOException, CompilationException {//Consumes the ending character
		Expression ex = new Expression();
		Item last = null;
		if (pri != null) {
			ex.add(pri);
			last = pri;
		}
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
						throw new CompilationException("Call to a non-addressable value: Cast the value to an addressable value first or use the raw conversion operator");//TODO implement raw conversion operator (used for raw conversions and to override function parameter bit sizes (placed differently for overriding function parameter bit sizes and for raw conversion of a value to an address for function calling))
					}
					ex.skim(last);
					ex.add(last = new Call((Value) last, Call.from()));
				}
				else {
					ex.add(last = from(')'));
				}
				continue;
			}
			else if ((tg == ending) || (tg == ending2)) {
				ex.finalised = true;
				PrintStream pstemp = Compiler.text;
				Compiler.text = Compiler.nowhere;
				ex.type = ex.bring();//TODO maybe un-bodge
				Compiler.text = pstemp;
				ex.auxEnding = tg != ending;
				return ex;
			}
			else {
				Util.unread(tg);
				String s = Util.phrase(0x2f);
				Literal lit;
				if (Util.legalIdent(s)) {
					Util.skipWhite();
					StackVar sv = null;
					for (Map<String, StackVar> cn : Compiler.context) {
						if (cn.containsKey(s)) {
							sv = cn.get(s);
							if (sv != null) {
								ex.add(last = sv);
								break;
							}
						}
					}
					if (sv == null) {
						NoScopeVar hv = Compiler.HVars.get(s);
						if (hv == null) {
							Function r = Compiler.HFuncs.get(s);
							if (r == null) {
								throw new NondefinitionException(s + " is not defined");
							}
							else {
								ex.add(last = new FunctionAddr(s));
							}
						}
						else {
							ex.add(last = hv);
						}
					}
				}
				else {
					try {
						lit = Util.getLit(s);
						ex.add(last = lit);
					}
					catch (NumberFormatException e) {
						if (s.length() != 0) {
							throw new CompilationException("Invalid statement: " + s);
						}//Not an expression, function call, symbol, or literal
						int i = Util.read();
						switch (i) {
							case ('+'):
								ex.add(last = Operator.ADD);
								break;
							case ('-'):
								ex.add(last = Operator.SUB);
								break;
							case ('*'):
								ex.add(last = Operator.MUL);
								break;
							case ('/'):
								ex.add(last = Operator.DIV);
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
	CompilationException() {
		super();
	}
	CompilationException(String reas) {
		super(reas);
	}
}
@SuppressWarnings("serial")
class NondefinitionException extends CompilationException {
	NondefinitionException(String reas) {
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
class UnidentifiableTypeException extends CompilationException {
	final String verbatim;
	UnidentifiableTypeException(String s) {
		super("Unidentifiable type: " + s);
		verbatim = s;
	}
}
@SuppressWarnings("serial")
class BlockEndException extends CompilationException {
	BlockEndException() {
		super();
	}
}
@SuppressWarnings("serial")
class SizeNotFitException extends CompilationException {
	SizeNotFitException() {
		super();
	}
	SizeNotFitException(String reas) {
		super(reas);
	}
}
