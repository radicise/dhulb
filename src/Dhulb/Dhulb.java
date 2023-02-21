package Dhulb;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
class Dhulb {
	public static void main(String[] argv) throws IOException, InternalCompilerException {
		if ((argv.length == 0) || argv[0].equals("--help")) {
				System.out.print("Invocation:\n"
						+ "dhulbc 16|32|64 -[t][N][B][G][T]\n"
						+ "\n"
						+ "\n"
						+ "Argument description:\n"
						+ "\n"
						+ "\n"
						+ "Argument 0: The instruction set used; \"16\" utilises that of the 8086 and generates code for it, \"32\" does the same for that of 80386 and and generates code for it, and \"64\" does the same for AMD64\n"
						+ "\n"
						+ "Argument 1: Each optional character used represents a flag being set. Usage is as follows:\n"
						+ "\tt\tPrints the compiler's stack trace upon compilation errors\n"
						+ "\tN\tEnables usage of platform-dependent type names\n"
						+ "\tB\tDisables error viewing\n"
						+ "\tG\tAutomatically makes declared global variables and functions global\n"
						+ "\tT\tCauses what are usually the data and text sections to instead have their contents be in one text section\n");
			return;
		}
		Compiler.mai(argv);//TODO prevent declaration of non-pointed structurally-typed variables
	}
}
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
	public static PushbackInputStream in = new PushbackInputStream(System.in, 64);//Do not `unread' too much
	public static int mach = 0;//0: 8086; 1: 80386 32-bit mode; 2: AMD64 64-bit mode
	public static final long FALSI = 0;
	public static final long VERIF = 1;
	public static boolean typeNicksApplicable = false;
	public static boolean allowTypeNicks = false;
	public static Type defUInt = Type.u16;
	public static Type defSInt = Type.s16;
	public static Type def754 = Type.f32;
	public static Type defAdr = Type.a16;
	public static int CALL_SIZE_BITS = 16;//default address size (for global variable references, global function calls, and default global function calling conventions); must be 16, 32, or 64
	public static boolean showCompilationErrorStackTrace = false;
	public static int warns = 0;
	public static long numericVersion = /*00_00_0*/2_01;//TODO bump when needed, should be bumped every time that stringVersion is bumped; do NOT remove this to-do marker
	public static String stringVersion = "0.0.2.1";//TODO bump when needed, should be bumped every time that numericVersion is bumped; do NOT remove this to-do marker
	public static TreeMap<String, NoScopeVar> HVars = new TreeMap<String, NoScopeVar>();
	public static TreeMap<String, Function> HFuncs = new TreeMap<String, Function>();
	public static Stack<Map<String, StackVar>> context = new Stack<Map<String, StackVar>>();
	public static TreeMap<String, Structure> structs = new TreeMap<String, Structure>();
	public static ArrayList<Compilable> program = new ArrayList<Compilable>();
	public static boolean noViewErrors = false;
	public static boolean autoGlobals = false;
	public static boolean oneText = false;
	public static void mai(String[] argv) throws IOException, InternalCompilerException {//TODO change operator output behaviour to match CPU instruction output sizes
		try {//TODO implement bulk memory movement syntax
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
				ma(argv);//TODO accept constant macros from the pre-processor
				prologue = proback;
				epilogue = epiback;
				rwdata = rwdatback;
				text = texback;
				//TODO epilogue
				finishStreams();
			}//TODO implement sizeof(type)
			catch (CompilationException | NotImplementedException exc) {//TODO prevent declaration of any variable named, "this"
				prologue = proback;
				epilogue = epiback;
				rwdata = rwdatback;
				text = texback;
				if (exc instanceof CompilationException) {
					System.err.println("Compilation error: " + exc.getMessage());
					if (showCompilationErrorStackTrace) {
						exc.printStackTrace(System.err);
					}
				}
				else if (exc instanceof NotImplementedException) {
					System.err.println("A used feature has not yet been implemented");
					System.err.println("The stack trace is as follows:");
					exc.printStackTrace(System.err);
				}
				else {
					throw new InternalCompilerException("This exceptional condition should not occur!");
				}
				if (!(noViewErrors)) {
					System.err.println();
					StringBuilder sb = new StringBuilder();
					int pos = Util.viewStart + 1;
					int where = 0;
					while (pos != Util.viewPos) {
						sb.appendCodePoint(Util.view[pos]);
						pos++;
						if (pos == Util.viewLen) {
							pos = 0;
						}
						where++;
					}
					try {
						while (pos < Util.viewEnd) {
							sb.appendCodePoint(Util.read());
							pos++;
							if (pos == Util.viewLen) {
								pos = 0;
							}
						}
						noViewErrors = true;
						for (pos = 0; pos < Util.viewLen; pos++) {
							sb.appendCodePoint(Util.read());
						}
					}
					catch (EOFException E) {
					}
					System.err.println(sb.toString().replace('\n', ' ').replace('\r', ' ').replace('\t', ' '));
					char[] spc = new char[where - 1];
					Arrays.fill(spc, ' ');
					System.err.print(spc);
					System.err.println('^');
					System.err.print(spc);
					System.err.println("here");
				}
				if (exc instanceof CompilationException) {
					epilogue.println(".err # Dhulb compilation error: " + exc.getMessage());
				}
				else if (exc instanceof NotImplementedException) {
					epilogue.println(".err # Dhulb compilation error: Feature not implemented");
				}
				else {
					throw new InternalCompilerException("This exceptional condition should not occur!");
				}
				//TODO epilogue
				finishStreams();
				System.exit(3);
			}
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
		if (argv[1].contains("t")) {
			showCompilationErrorStackTrace = true;
		}
		if (argv[1].contains("N")) {//the `defAdr' alias is needed even if platform-dependent type aliases are not enabled
			allowTypeNicks = true;
			typeNicksApplicable = true;
		}
		if (argv[1].contains("B")) {
			noViewErrors = true;
		}
		if (argv[1].contains("G")) {
			autoGlobals = true;
		}
		if (argv[1].contains("T")) {
			oneText = true;
		}//TODO flag for using .err # "text", using .error "text" instead of .err # text, putting warnings in comments with whitespace before the comments on the same line by themselves, and using .warning "text"
		if (oneText) {
			Compiler.prologue.println(".text");
		}
		else {
			Compiler.rwdata.println(".data");
			Compiler.text.println(".text");
		}
		int madec = Integer.parseInt(argv[0]);
		if (madec == 16) {
			Compiler.text.println(".code16");
			mach = 0;
			CALL_SIZE_BITS = 16;
			defUInt = Type.u16;
			defSInt = Type.s16;
			def754 = Type.f32;
			defAdr = Type.a16;
		}
		else if (madec == 32) {
			Compiler.text.println(".code32");
			mach = 1;
			CALL_SIZE_BITS = 32;
			defUInt = Type.u32;
			defSInt = Type.s32;
			def754 = Type.f32;
			defAdr = Type.a32;
		}
		else if (madec == 64) {
			Compiler.text.println(".code64");
			mach = 2;
			CALL_SIZE_BITS = 64;
			defUInt = Type.u64;
			defSInt = Type.s64;
			def754 = Type.f64;
			defAdr = Type.a64;
		}
		else {
			throw new InternalCompilerException("Unidentifiable target");
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
	static void getCompilable(ArrayList<Compilable> list, boolean inFunc, Stacked fn) throws CompilationException, InternalCompilerException, IOException {
		if (inFunc && (fn == null)) {
			throw new InternalCompilerException("Function not provided");
		}
		String s;
		FullType typ;
		int i;
		Util.skipWhite();
		i = Util.read();
		if (i == '}') {
			throw new BlockEndException("Unexpected end-of-block");//Unexpected by this call, the message only applies to the compilation as a whole if this is not within a block
		}
		else if (i == '{') {
			if (!(inFunc)) {
				throw new CompilationException("Scoped block outside of a function");
			}
			list.add(Block.from(fn));
			return;
		}
		else if (i == '/') {
			if ((i = Util.read()) == '%') {
				RawText r = new RawText(rwdatback);
				try {
					while (true) {
						i = Util.read();
						if (i == '\\') {
							r.write(Util.read());
						}
						else if (i == '%') {
							if ((i = Util.read()) == '/') {
								list.add(r);
								return;
							}
							r.write('%');
							r.write(i);
						}
						else {
							r.write(i);
						}
					}
				}
				catch (EOFException E) {
					throw new CompilationException("Data section assembly block not closed");
				}
			}
			else if (i == '&') {
				RawText r = new RawText(texback);
				try {
					while (true) {
						i = Util.read();
						if (i == '\\') {
							r.write(Util.read());
						}
						else if (i == '&') {
							if ((i = Util.read()) == '/') {
								list.add(r);
								return;
							}
							r.write('&');
							r.write(i);
						}
						else {
							r.write(i);
						}
					}
				}
				catch (EOFException E) {
					throw new CompilationException("Code section assembly block not closed");
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
					throw new CompilationException("Comment not closed");
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
			if (s.equals("class")) {
				Util.skipWhite();
				String naam = Util.phrase(0x3d);
				if (Compiler.structs.containsKey(naam)) {
					throw new CompilationException("Duplicate class name: " + naam);
				}
				Structure st = Structure.from(naam);
				Compiler.structs.put(naam, st);
				list.add(st);
			}
			else if (Util.legalIdent(s)) {
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
						String r = Util.phrase(0x3d);//identifier legality is not checked for, this is on purpose
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
					case ("return"):
						if (!(inFunc)) {
							throw new CompilationException("Keyword `return' is not allowed outside of a function");
						}
						Util.skipWhite();
						list.add(new Yield(Expression.from(';'), fn.retType(), fn.abiSize()));
						return;
					case ("if"):
						if (inFunc) {
							list.add(IfThen.from(fn));
						}
						else {
							throw new NotImplementedException();//TODO decide how variables declared inside of if statements which are outside of functions will work
						}
						return;
					default:
						throw new NotImplementedException("Unimplemented or illegal use of keyword: " + s);
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
		if (Util.nondefk.contains(s)) {
			throw new CompilationException("Reserved identifier: " + s);
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
				throw new CompilationException("Symbol collision: " + typ + " " + s + " conflicts with global variable " + coll.toString());
			}
			Function collf = HFuncs.get(s);
			if (collf != null) {
				throw new CompilationException("Symbol collision: " + typ + " " + s + " conflicts with global function " + collf.toString());
			}
		}
		Util.skipWhite();
		if ((i = Util.read()) == '=') {
			if (inFunc) {
				StackVar sav = new StackVar(fn.adjust(-((typ.type.size() / 8) + (((typ.type.size() % 8) == 0) ? 0 : 1))), typ);
				context.peek().put(s, sav);
				list.add(new Assignment(sav, Expression.from(';')));
			}
			else {
				list.add(new GlobalVarDecl(s, typ));
				NoScopeVar thno = new NoScopeVar(s, typ);
				HVars.put(s, thno);//Before the assignment so that the assignment can refer the the variable itself, which should contain the default value when dealing with a global variable and whatever data was already there when dealing with a stack variable
				list.add(new Assignment(thno, Expression.from(';')));//TODO Avoid eventually bringing the number if it is constant
			}
		}
		else if (i == ';') {
			if (inFunc) {
				context.peek().put(s, new StackVar(fn.adjust(-((typ.type.size() / 8) + (((typ.type.size() % 8) == 0) ? 0 : 1))), typ));
				Util.warn("Implicit interpretation of undefined data");
			}
			else {
				list.add(new GlobalVarDecl(s, typ));
				HVars.put(s, new NoScopeVar(s, typ));
				Util.warn("Implicit interpretation of zeroed data");
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
	public static final long ubid = (new SecureRandom()).nextLong();
	static ArrayList<Integer> brack = new ArrayList<Integer>();
	static long sen = 0;
	static int[] brace = new int[]{'(', ')', '[', ']', '{', '}', '<', '>'};
	static ArrayList<String> keywork = new ArrayList<String>();
	static String[] keywore = new String[]{"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "goto", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "u8", "s8", "u16", "s16", "u32", "s32", "u64", "s64", "f32", "f64", "a16", "a32", "a64", "uint", "sint", "addr", "imply", "as", "to", "byref", "byval"};
	static ArrayList<String> accesk = new ArrayList<String>();
	static String[] accese = new String[]{};
	static ArrayList<String> boolitk = new ArrayList<String>();
	static String[] boolite = new String[]{"false", "true"};
	static ArrayList<String> nulitk = new ArrayList<String>();
	static String[] nulite = new String[]{"null", "NULL"};
	static ArrayList<String> primsk = new ArrayList<String>();
	static String[] primse = new String[]{"u8", "s8", "u16", "s16", "u32", "s32", "u64", "s64", "f32", "f64", "a16", "a32", "a64", "uint", "sint", "float", "addr", "int", "sizeof"};//TODO maybe but probably not: remove the platform-dependent aliases, use a plug-in for them instead
	static ArrayList<Integer> inpork = new ArrayList<Integer>();
	static int[] inpore = new int[]{'+', '-', '=', '/', '<', '>', '*', '%', ',', '$', '!', '~', '@'};
	static ArrayList<String> nondefk = new ArrayList<String>();
	static String[] nondefe = new String[]{"this"};
	static ArrayList<Integer> idesk = new ArrayList<Integer>();
	static int[] idese = new int[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '_'};
	static ArrayList<Integer> idepk = new ArrayList<Integer>();
	static int[] idepe = new int[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '_', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
	static ArrayList<Integer> alphak = new ArrayList<Integer>();
	static int[] alphae = new int[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
	public static int viewLen = 32;
	public static int[] view = new int[viewLen];
	public static int viewStart = 0;//first valid
	public static int viewEnd = 1;//first invalid
	public static int viewPos = 1;
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
		for (String s : nondefe) {
			nondefk.add(s);
		}
		for (int c : idese) {
			idesk.add(c);
		}
		for (int c : idepe) {
			idepk.add(c);
		}
		for (int c : alphae) {
			alphak.add(c);
		}
		view[0] = '!';//this '!' should not ever be seen
	}
	private Util() {
	}
//	static char suffixFor(int mch) throws InternalCompilerException {
//		switch(mch) {
//			case (0):
//				return 'w';
//			case (1):
//				return 'l';
//			case (2):
//				return 'q';
//			case (8):
//				return 'b';
//			case (16):
//				return 'w';
//			case (32):
//				return 'l';
//			case (64):
//				return 'q';
//			default:
//				throw new InternalCompilerException("Illegal operand size");
//		}
//	}
	static String reserve() {
		return "___dhulbres__" + Long.toHexString(ubid) + "__" + Long.toString(sen++);
	}
	static void unread(int[] ii) throws InternalCompilerException, IOException {//un-reading of phrases up to only 8 characters is supported
		for (int t = ii.length - 1; t >= 0; t--) {
			Util.unread(ii[t]);
		}
	}
	static boolean confirm(int[] ii) throws InternalCompilerException, IOException {//checking of phrases up to only 8 characters is supported; remember to use include ending delimiters when needed
		if (ii.length > 8) {
			throw new InternalCompilerException();
		}
		int i = 0;
		try {
			for (; i < ii.length; i++) {
				int r = Util.read();
				if (r != ii[i]) {
					Util.unread(r);
					for (int t = i - 1; t >= 0; t--) {
						Util.unread(ii[t]);
					}
					return false;
				}
			}
		}
		catch (EOFException E) {
			for (int t = i - 1; t >= 0; t--) {
				Util.unread(ii[t]);
			}
		}
		return true;
	}
	static String escape(String term) {
		StringBuilder sb = new StringBuilder();
		term.codePoints().iterator().forEachRemaining(new IntConsumer() {
			public void accept(int c) {
				if (Util.alphak.contains(c)) {
					sb.appendCodePoint(c);
				}
				else {
					if (c < 0x80) {
						sb.append('_');
						sb.appendCodePoint(alphae[c & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 4) & 0x0f]);
					}
					else if (c < 0x10000) {
						sb.append('_');
						sb.append('u');
						sb.appendCodePoint(alphae[c & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 4) & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 8) & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 12) & 0x0f]);
					}
					else {
						sb.append('_');
						sb.append('v');
						sb.appendCodePoint(alphae[c & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 4) & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 8) & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 12) & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 16) & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 20) & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 24) & 0x0f]);
						sb.appendCodePoint(alphae[(c >>> 28) & 0x0f]);
					}
				}
			}
		});
		return sb.toString();
	}
	static String escape(String[] terms) throws InternalCompilerException {
		if (terms.length == 0) {
			throw new InternalCompilerException("No strings to escape");
		}
		StringBuilder sb = new StringBuilder();
		sb.append(escape(terms[0]));
		int i = 1;
		while (i < terms.length) {
			sb.append("__");
			sb.append(escape(terms[i]));
			i++;
		}
		return sb.toString();
	}
	static FullType fromAddr() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				Compiler.text.println("movw %bx,%ax");
				return FullType.a16;
			case (1):
				return FullType.a32;
			case (2):
				return FullType.a64;
			default:
				throw new InternalCompilerException("Unidentifiable target");
		}
	}
	static String signedRestrict(long val, int size) throws SizeNotFitException, InternalCompilerException {
		switch (size) {
			case (8):
				if ((val > 0x7f) || (val < (-(0x80)))) {
					throw new SizeNotFitException();//TODO add error message
				}
				return herxb(val);
			case (16):
				if ((val > 0x7f) || (val < (-(0x80)))) {
					if ((val > 0x7fff) || (val < (-(0x8000)))) {
						throw new SizeNotFitException();//TODO add error message
					}
					return herxs(val);
				}
				return herxb(val);
			case (32):
				if ((val > 0x7f) || (val < (-(0x80)))) {
					if ((val > 0x7fff) || (val < (-(0x8000)))) {
						if ((val > 0x7fffffff) || (val < (-(0x80000000)))) {
							throw new SizeNotFitException();//TODO add error message
						}
						return herxi(val);
					}
					return herxs(val);
				}
				return herxb(val);
			case (33)://like 32 but no 16-bit
				if ((val > 0x7f) || (val < (-(0x80)))) {
					if ((val > 0x7fffffff) || (val < (-(0x80000000)))) {
						throw new SizeNotFitException();//TODO add error message
					}
					return herxi(val);
				}
				return herxb(val);
			case (64):
				if ((val > 0x7f) || (val < (-(0x80)))) {
					if ((val > 0x7fff) || (val < (-(0x8000)))) {
						if ((val > 0x7fffffff) || (val < (-(0x80000000L)))) {
							if ((val > 0x7fffffffffffffffL) || (val < (-(0x8000000000000000L)))) {
								throw new SizeNotFitException();//TODO add error message
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
	static int read() throws InternalCompilerException, IOException {
		if (Compiler.noViewErrors) {
			return tread();
		}
		int i = tread();
		if (viewPos == viewEnd) {
			view[viewPos] = i;
			if (viewEnd == viewStart) {
				viewEnd++;
				if (viewEnd == viewLen) {
					viewEnd = 0;
				}
				viewPos = viewEnd;
				viewStart = viewEnd;
			}
			else {
				viewEnd++;
				if (viewEnd == viewLen) {
					viewEnd = 0;
				}
				viewPos = viewEnd;
			}
		}
		else {
			if (i != view[viewPos]) {
				throw new InternalCompilerException("Pushback-based re-reading inconsistency");
			}
			viewPos++;
			if (viewPos == viewLen) {
				viewPos = 0;
			}
		}
		return i;
	}
	static void unread(int f) throws InternalCompilerException, IOException {
		tunread(f);
		if (Compiler.noViewErrors) {
			return;
		}
		if (viewPos == (viewStart + 1)) {
			throw new InternalCompilerException("Reading position was pushed back to before the earliest part of the view buffer");
		}
		if (viewPos == 0) {
			viewPos = viewLen - 1;
		}
		else {
			viewPos--;
		}
		if (view[viewPos] != f) {
			System.out.println((char) view[viewPos]);
			System.out.println((char) f);
			throw new InternalCompilerException("Pushback-based un-reading inconsistency");
		}
	}
	static Literal getLit(String s) throws CompilationException, NumberFormatException, InternalCompilerException {//TODO support manually defining the type
		long l = Long.decode(s);
		if (!(Compiler.defSInt.fits(l, true))) {
			throw new CompilationException("Datum type of literal cannot hold its specified value");
		}
		return new Literal(FullType.of(Compiler.defSInt), l);//TODO support unsigned 64-bit literals above Long.MAX_VALUE
	}
	private static int tread() throws IOException {// Use this for UTF-8 instead of anything relying on StreamDecoder that caches characters
		int g;
		if ((g = Compiler.in.read()) == (-1)) {
			throw new EOFException();
		}
		if (g < 0x80) {
			return g;
		}
		throw new UnsupportedOperationException();// TODO full support for UTF-8
	}
//	private static int readByte() throws IOException {//returns an int where the 24 high-order bits are zero
//		int i = Compiler.in.read();
//		if (i == (-(1))) {
//			throw new EOFException();
//		}
//		return i;
//	}
//	static void unread(String s) throws IOException {
//		byte[] bys = s.getBytes(StandardCharsets.UTF_8);
//		for (int i = bys.length - 1; i >= 0; i--) {
//			Compiler.in.unread(bys[i]);
//		}
//	}
	private static void tunread(int f) throws IOException {
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
	static boolean legalIdent(String s) throws IOException {//dollar signs are not allowed in names
		if (s.length() == 0) {
			return false;
		}
		if ((keywork.contains(s)) || (boolitk.contains(s)) || (nulitk.contains(s))) {
			return false;
		}
		int c = 0;
		int h = s.codePointAt(c);
		if (!(idesk.contains(h))) {
			return false;
		}
		if (h > 0xffff) {
			c++;
		}
		c++;
		try {
			while (true) {
				if (!(idepk.contains(h = s.codePointAt(c)))) {
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
	static void bring8(char reg, byte val, boolean high) {
		if (val == 0) {
			Compiler.text.println("xorb %" + reg + "l,%" + reg + (high ? "h" : "l"));
		}
		else {
			Compiler.text.println("movb $" + Util.hexb(val) + ",%" + reg + (high ? "h" : "l"));
		}
	}
	static void bring16(char reg, short val) {
		bring16(reg, val, false);
	}
	static void bring16(char reg, short val, boolean assumeZeroed) {
		if (((val & 0xff00) != 0) && ((val & 0x00ff) != 0)) {
			Compiler.text.println("movw $0x" + Util.hexs(val) + ",%" + reg + "x");
		}
		else if ((val & 0x00ff) != 0) {
			if (!(assumeZeroed)) {
				Compiler.text.println("xorb %" + reg + "h,%" + reg + "h");
			}
			Compiler.text.println("movb $0x" + Util.hexb(val) + ",%" + reg + "l");
		}
		else if ((val & 0xff00) != 0) {
			if (!(assumeZeroed)) {
				Compiler.text.println("xorb %" + reg + "l,%" + reg + "l");
			}
			Compiler.text.println("movb $0x" + Util.hexb(val >>> 8) + ",%" + reg + "h");
		}
		else {
			if (assumeZeroed) {
				return;
			}
			Compiler.text.println("xorw %" + reg + "x,%" + reg + "x");
		}
	}
	static void bring32(char reg, int val) {
		bring32(reg, val, false);
	}
	static void bring32(char reg, int val, boolean assumeZeroed) {
		if (val == 0) {
			if (assumeZeroed) {
				return;
			}
			Compiler.text.println("xorl %e" + reg + "x,%e" + reg + "x");
		}
		else if (((val & 0xffff0000) == 0) && ((val & 0x0000ffff) != 0)) {
			if (!(assumeZeroed)) {
				Compiler.text.println("xorl %e" + reg + "x,%e" + reg + "x");
			}
			bring16(reg, (short) (val & 0xffff), true);
		}
		else {
			Compiler.text.println("movl $0x" + Util.hexl(val) + ",%e" + reg + "x");
		}
	}
}
class Yield implements Doable {//use the function's ABI
	final Value val;
	final FullType retType;
	final int abiSize;
	Yield(Value v, FullType ft, int abi) {
		val = v;
		retType = ft;
		abiSize = abi;
	}
	public void compile() throws CompilationException, InternalCompilerException {
		FullType ft = val.bring();
		if (!(ft.provides(retType))) {
			ft.cast(retType);
		}
		int cab;
		switch (Compiler.mach) {
			case (0):
				cab = 16;
				break;
			case (1):
				cab = 32;
				break;
			case (2):
				cab = 64;
				break;
			default:
				throw new InternalCompilerException("This exceptional condition should not occur!");
		}
		abiChange(cab, abiSize, retType.type.size());
		ret(abiSize);
	}
	static void abiChange(int from, int to, int size) throws NotImplementedException {
		if (from == to) {
			return;
		}
		throw new NotImplementedException();
	}
	static void ret(int abi) throws InternalCompilerException {
		switch (abi) {
			case (16):
				Compiler.text.println("movw %bp,%sp");
				Compiler.text.println("popw %bp");
				Compiler.text.println("retw");
				break;
			case (32):
				Compiler.text.println("movl %ebp,%esp");
				Compiler.text.println("popl %ebp");
				Compiler.text.println("retl");
				break;
			case (64):
				Compiler.text.println("movq %rbp,%rsp");
				Compiler.text.println("popq %rbp");
				Compiler.text.println("retq");
				break;
			default:
				throw new InternalCompilerException("Unidentifiable target");
		}
	}
}
class Condition {
	Value based;
	Condition(Value b) {
		based = b;
	}
	void toZero() throws CompilationException, InternalCompilerException {//clears ZF upon truth; sets it otherwise
		int siz = based.bring().type.size();
		switch (Compiler.mach) {
			case (0):
				switch (siz) {
					case (8):
						Compiler.text.println("testb %al,%al");
						break;
					case (16):
						Compiler.text.println("testw %ax,%ax");
						break;
					case 32:
						throw new NotImplementedException();
					case 64:
						throw new NotImplementedException();
					default:
						throw new InternalCompilerException("Illegal datum size");
				}
				break;
			case (1):
				switch (siz) {
					case (8):
						Compiler.text.println("testb %al,%al");
						break;
					case (16):
						Compiler.text.println("testw %ax,%ax");
						break;
					case 32:
						Compiler.text.println("testl %eax,%eax");
						break;
					case 64:
						throw new NotImplementedException();
					default:
						throw new InternalCompilerException("Illegal datum size");
				}
				break;
			case (2):
				switch (siz) {
					case (8):
						Compiler.text.println("testb %al,%al");
						break;
					case (16):
						Compiler.text.println("testw %ax,%ax");
						break;
					case 32:
						Compiler.text.println("testl %eax,%eax");
						break;
					case 64:
						Compiler.text.println("testl %eax,%eax");
						break;
					default:
						throw new InternalCompilerException("Illegal datum size");
				}
				break;
			default:
				throw new InternalCompilerException("Unidentifiable target");
		}
	}
}
abstract class Conditional implements Doable {//if(X), else(X), else, while(X), for(X;X;X), dowhile(X)
	static int[] els = new int[] {'e', 'l', 's', 'e'};
	final Stacked parent;
	protected Conditional(Stacked p) {
		parent = p;
	}
}//TODO allow do{X}while(X) and do while(X) {X}
class IfThen extends Conditional {
	final ArrayList<Condition> ifs;//must have the same size as `poss' and `thens'
	final ArrayList<Boolean> poss;//must have the same size as `ifs' and `thens'
	final ArrayList<Block> thens;//must have the same size as `ifs' and `poss'
	final Block ending;//null if no else{X}
	private IfThen(ArrayList<Condition> i, ArrayList<Block> t, ArrayList<Boolean> po, Block e, Stacked p) {
		super(p);
		ifs = i;
		poss = po;
		thens = t;
		ending = e;
	}
	static IfThen from(Stacked par) throws CompilationException, InternalCompilerException, IOException {//starts reading from after the "if"
		ArrayList<Condition> conds = new ArrayList<Condition>();
		ArrayList<Block> execs = new ArrayList<Block>();
		ArrayList<Boolean> pos = new ArrayList<Boolean>();
		Util.skipWhite();
		int i = Util.read();
		while (true) {
			if (i == '!') {
				pos.add(false);
				Util.skipWhite();
				i = Util.read();
			}
			else {
				pos.add(true);
			}
			if (i != '(') {
				throw new CompilationException("Illegal operator: " + new String(new int[]{i}, 0, 1));
			}
			conds.add(new Condition(Expression.from(')')));
			Util.skipWhite();
			if ((i = Util.read()) != '{') {
				throw new CompilationException("Illegal operator: " + new String(new int[]{i}, 0, 1));
			}
			execs.add(Block.from(par));
			Util.skipWhite();
			if (!(Util.confirm(els))) {
				return new IfThen(conds, execs, pos, null, par);
			}
			i = Util.read();
			if (!((Character.isWhitespace(i)) || (i == '{') || (i == '(') || (i == '!'))) {
				Util.unread(i);
				Util.unread(els);
				return new IfThen(conds, execs, pos, null, par);
			}
			if (Character.isWhitespace(i)) {
				Util.skipWhite();
				i = Util.read();
				if (!((i == '(') || (i == '{') || (i == '!'))) {
					throw new CompilationException("Illegal operator: " + new String(new int[]{i}, 0, 1));
				}
			}
			if (i == '{') {
				return new IfThen(conds, execs, pos, Block.from(par), par);
			}
		}
	}
	public void compile() throws CompilationException, InternalCompilerException, IOException {
		String esymb = Util.reserve();
		String nsymb = null;
		int j = ifs.size() - 1;
		for (int i = 0; i <= j; i++) {
			ifs.get(i).toZero();
			if (i != j) {
				nsymb = Util.reserve();
				if (poss.get(i)) {
					Compiler.text.println("jnz " + nsymb);
				}
				else {
					Compiler.text.println("jz " + nsymb);
				}
				thens.get(i).compile();
				Compiler.text.println("jmp " + esymb);
				Compiler.text.print(nsymb);
				Compiler.text.println(":");
			}
			else {
				if (ending == null) {
					if (poss.get(i)) {
						Compiler.text.println("jz " + esymb);
					}
					else {
						Compiler.text.println("jnz " + esymb);
					}
					thens.get(i).compile();
					Compiler.text.print(esymb);
					Compiler.text.println(":");
				}
				else {
					nsymb = Util.reserve();
					if (poss.get(i)) {
						Compiler.text.println("jz " + nsymb);
					}
					else {
						Compiler.text.println("jnz " + nsymb);
					}
					thens.get(i).compile();
					Compiler.text.println("jmp " + esymb);
					Compiler.text.print(nsymb);
					Compiler.text.println(":");
					ending.compile();
					Compiler.text.print(esymb);
					Compiler.text.println(":");
				}
				return;
			}
		}
	}
}
class Structure implements Compilable {//TODO allow use of the structured byte before field definitions are complete
	StructuredType type;//do not change field values of this; equality operator should not be used for equality checks
	FullType fulltype;//do not change field values of this; equality operator should not be used for equality checks
	private long id;//0 if no ID was specified
	long length;//if fieldsFinalised, holds the total length, including tail padding; else, holds the length using the current fields minus tail padding
	long lenUsed;//if fieldsFinalised, holds the length minus the amount of tail padding
	int alignmentBytes;//byte alignment needed
	private transient boolean fieldsFinalised;
	String name;
	LinkedHashMap<String, StructEntry> fields;
	LinkedHashMap<String, Function> funcs;//each function has `this' set appropriately for the function, it being the leftmost parameter (hidden), when passing by reference, or a stack variable which holds the logical address offset value (hidden), when passing by value. When passing by value, the field names themselves can also be used because they are declared as fields in the function
	private Structure() {//TODO alert the user if there is nothing between a dot operator and the next appropriate delimiter, not including whitespace
		length = 0;
		alignmentBytes = 1;
		fields = new LinkedHashMap<String, StructEntry>();
		funcs = new LinkedHashMap<String, Function>();
	}
	private Structure(Structure parent) throws InternalCompilerException {
		if (!(parent.fieldsFinalised)) {
			throw new InternalCompilerException("Extension of a non-field-finalised class");
		}
		length = parent.lenUsed;
		alignmentBytes = parent.alignmentBytes;
		parent.fields.forEach(new BiConsumer<String, StructEntry>() {
			public void accept(String s, StructEntry se) {
				fields.put(s, new StructEntry(se.offset, se.type));
			}
		});
		funcs = new LinkedHashMap<String, Function>();
	}
	boolean fieldsFinalised() {
		return fieldsFinalised;
	}
	long addField(String nam, FullType t) throws InternalCompilerException {
		if (fieldsFinalised) {
			throw new InternalCompilerException("Addition of fields to a field-finalised class");
		}
		long l = length + (((length % ((long) (t.type.size() / 8))) == 0) ? 0 : (((long) (t.type.size() / 8)) - (length % ((long) (t.type.size() / 8)))));
		fields.put(nam, new StructEntry(l, t));
		if (alignmentBytes < (t.type.size() / 8)) {
			alignmentBytes = (t.type.size() / 8);
		}
		length = l + (t.type.size() / 8);
		return l;
	}
	void finishFields() throws NotImplementedException {
		fieldsFinalised = true;
		lenUsed = length;
		length += (((length % ((long) alignmentBytes)) == 0) ? 0 : (((long) alignmentBytes) - (length % ((long) alignmentBytes))));
	}
	long getID() {
		return id;
	}
	static Structure from(String name) throws CompilationException, InternalCompilerException, IOException {//TODO decide whether the functions are referenced through a function that has the escaped name (using `Compiler.HFuncs') or the function having having the name as a key in `funcs' (doing the former would allow instance methods to be declared solely by using the escaped name)
		Util.skipWhite();
		int i = Util.read();
		Structure st = null;
		long id = 0;
		if (i != '{') {
			Util.unread(i);
			String n = Util.phrase(0x21);
			if (n.equals("extends")) {
				Util.skipWhite();
				String nam = Util.phrase(0x21);
				st = Compiler.structs.get(nam);
				if (st == null) {
					throw new CompilationException("Class not found: " + nam);
				}
				Util.skipWhite();
				i = Util.read();
				if (i != '{') {
					Util.unread(i);
					if (!(Util.phrase(0x21).equals("id"))) {
						throw new CompilationException("Illegal or unidentifiable keyword: " + n);
					}
					Util.skipWhite();
					id = Long.decode(Util.phrase(0x21));
					Util.skipWhite();
					if ((i = Util.read()) != '{') {
						throw new CompilationException("Unexpected operator at start of class body: " + new String(new int[]{i}, 0, 1));
					}
				}
			}
			else if (n.equals("id")) {
				Util.skipWhite();
				id = Long.decode(Util.phrase(0x21));
				Util.skipWhite();
				if ((i = Util.read()) != '{') {
					throw new CompilationException("Unexpected operator at start of class body: " + new String(new int[]{i}, 0, 1));
				}
			}
			else {
				throw new CompilationException("Unexpected operator at start of class body: " + new String(new int[]{i}, 0, 1));
			}
		}
		if (st == null) {
			st = new Structure();
		}
		else {
			st = new Structure(st);
		}
		st.name = name;
		st.id = id;
		boolean mif = true;
		String sr = null;
		FullType ft = null;
		boolean nof = true;
		boolean iaf = false;
		boolean byref = true;
		Util.skipWhite();
		i = Util.read();
		if (i == '}') {
			st.finishFields();
			return st;
		}
		Util.unread(i);
		while (true) {//TODO implement specificity
			Util.skipWhite();
			i = Util.read();
			if (i == ';') {
				if (mif) {
					throw new CompilationException("Unexpected operator: ;");
				}
				st.finishFields();
				break;
			}
			Util.unread(i);
			ft = FullType.from();
			Util.skipWhite();
			sr = Util.phrase(0x3d);
			Util.skipWhite();
			if (nof) {
				if (sr.equals("byref") || sr.equals("byval")) {
					st.finishFields();
					iaf = true;
					byref = sr.equals("byref");
					sr = Util.phrase(0x3d);
					if (!(Util.legalIdent(sr))) {
						throw new CompilationException("Illegal identifier: " + sr);
					}
					if (Util.nondefk.contains(sr)) {
						throw new CompilationException("Reserved identifier: " + sr);
					}
					i = Util.read();
					if (i != '(') {
						throw new CompilationException("Unexpected operator: " + new String(new int[]{i}, 0, 1));
					}
					break;
				}
			}
			if (!(Util.legalIdent(sr))) {
				throw new CompilationException("Illegal identifier: " + sr);//could be for a field or, when there are no fields, the first function
			}
			if (Util.nondefk.contains(sr)) {
				throw new CompilationException("Reserved identifier: " + sr);
			}
			if (nof) {
				i = Util.read();
				if (i == '(') {
					st.finishFields();
					iaf = true;
					break;
				}
				Util.unread(i);
			}
			if (st.fields.containsKey(sr)) {
				throw new CompilationException("Duplicate structural type field name: " + sr);
			}
			st.addField(sr, ft);
			mif = false;
			nof = false;
			i = Util.read();
			if (i == ',') {
				mif = true;
			}
			else if (i == ';') {
				Util.unread(i);
			}
			else {
				throw new CompilationException("Unexpected operator: " + new String(new int[]{i}, 0, 1));
			}
		}
		st.fulltype = FullType.of(st.type = new StructuredType(st));
		if (iaf) {//starts after the opening parenthesis
			if (st.fields.containsKey(sr)) {
				throw new CompilationException("Class method has the same name as a field of the instance's structural type");
			}
			aiherghre(st, sr, ft, byref);
		}
		while (true) {
			Util.skipWhite();
			i = Util.read();
			if (i == '}') {
				break;
			}
			Util.unread(i);
			ft = FullType.from();
			Util.skipWhite();
			sr = Util.phrase(0x3d);
			if ((sr.equals("byref")) || (sr.equals("byval"))) {
				byref = sr.equals("byref");
				Util.skipWhite();
				sr = Util.phrase(0x3d);
			}
			else {
				byref = true;
			}
			if (Util.nondefk.contains(sr)) {
				throw new CompilationException("Reserved identifier: " + sr);
			}
			if (!(Util.legalIdent(sr))) {
				throw new CompilationException("Illegal identifier for class method: " + sr);
			}
			if (st.fields.containsKey(sr)) {
				throw new CompilationException("Class method has the same name as a field of the instance's structural type");
			}
			Util.skipWhite();
			i = Util.read();
			if (i != '(') {
				throw new CompilationException("Unexpected operator: " + new String(new int[]{i}, 0, 1));
			}
			aiherghre(st, sr, ft, byref);
		}
		return st;
	}
	public String toString() {
		StringJoiner sj = new StringJoiner(",", name, "");
		fields.forEach(new BiConsumer<String, StructEntry>() {
			public void accept(String s, StructEntry se) {
				sj.add(" " + se.type + " " + s);
			}
		});
		return sj.toString();
	}
	public void compile() throws CompilationException, InternalCompilerException, IOException {
		Compiler.text.println("/*dhulbDoc-v" + Compiler.numericVersion + ":structure;" + toString() + ";*/");
		for (Map.Entry<String, Function>  sf: funcs.entrySet()) {
			sf.getValue().compile();
		}
	}
	private static void aiherghre(Structure st, String sr, FullType ft, boolean byref) throws CompilationException, InternalCompilerException, IOException {
		if (byref) {
			LinkedHashMap<String, FullType> sf = new LinkedHashMap<String, FullType>();
			sf.put("this", new FullType(Compiler.defAdr, st.fulltype));
			String nn = "___structfunc__" + Util.escape(new String[]{st.name, sr});
			if (Compiler.HVars.containsKey(nn) || Compiler.HFuncs.containsKey(nn)) {
				throw new CompilationException("Symbol is already defined: " + nn);
			}
			Function stf = Function.from(ft, nn, st.funcs, sf);
			Compiler.HFuncs.put(nn, new Function(ft, stf.dargs, null, nn));
		}
		else {
			throw new NotImplementedException();
		}
	}
}
class StructEntry implements Comparable<StructEntry> {
	final long offset;
	final FullType type;
	StructEntry(long off, FullType typ) {
		offset = off;
		type = typ;
	}
	public boolean equals(Object obj) {
		if (obj instanceof StructEntry) {
			return offset == ((StructEntry) obj).offset;
		}
		return this == obj;
	}
	public int compareTo(StructEntry to) {
		long diff = offset - to.offset;
		return (diff == 0L) ? 0 : ((diff > 0L) ? 1 : 0);
	}
}
class Function implements Stacked, Compilable {//TODO maybe warn when the code may reach the end of the function without returning
	FullType retType;
	String name;//symbol name
	FullType[] dargs;
	Doable[] does;//null if the function represents a function declared outside of the file; non-null otherwise
	private transient long bpOff = 0;//offset from the base pointer 
	private long minOff = 0;//lowest value of the offset from the base pointer without processing blocks
	public final int abiSize;
	long spos = 0;
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
	public long adjust(long n) {
		bpOff += n;
		if (bpOff < minOff) {
			spos = minOff = bpOff;
		}
		return bpOff;
	}
	public void compile() throws CompilationException, InternalCompilerException, IOException {
		Compiler.text.println(name + ":/*dhulbDoc-v" + Compiler.numericVersion + ":function;" + this.toString() + " " + "call" + abiSize + ";*/");
		if (Compiler.autoGlobals) {
			Compiler.text.print(".globl ");
			Compiler.text.println(name);
		}
		switch (abiSize) {
			case (16):
				Compiler.text.println("pushw %bp");
				Compiler.text.println("movw %sp,%bp");
				if (minOff > 0) {
					throw new InternalCompilerException("This exceptional condition should not occur!");
				}
				else if (minOff < 0) {
					Compiler.text.println("subw $" + Util.signedRestrict(-minOff, 16) + ",%sp");
				}
				break;
			case (32):
				Compiler.text.println("pushl %ebp");
				Compiler.text.println("movl %esp,%ebp");
				if (minOff > 0) {
					throw new InternalCompilerException("This exceptional condition should not occur!");
				}
				else if (minOff < 0) {
					Compiler.text.println("subl $" + Util.signedRestrict(-minOff, 33) + ",%esp");
				}
				break;
			case (64):
				throw new NotImplementedException();
			default:
				throw new InternalCompilerException("Unidentifiable target");
		}
		for (Doable d : does) {
			d.compile();
		}//don't implicitly return at the end of the function body; the programmer needs to make sure that nothing reaches the end without a return statement unless they actually want the function to not exit properly in those cases
	}
	static Function from(FullType rett, String nam, TreeMap<String, Function> funcs) throws NotImplementedException, CompilationException, InternalCompilerException, IOException {
		return from(rett, nam, funcs, null);
	}
	static Function from(FullType rett, String nam, Map<String, Function> funcs, LinkedHashMap<String, FullType> ext) throws NotImplementedException, CompilationException, InternalCompilerException, IOException {//starts at the parameters, first thing is the first non-whitespace character after the opening parenthesis (whitespace before it allowed), consumes everything up to the closing curly brace, inclusive
		Function fn = new Function();
		fn.retType = rett;
		fn.name = nam;
		LinkedHashMap<String, StackVar> ar;
		if (ext == null) {
			ar = FullType.getArgs(')', fn.abiSize);
		}
		else {
			ar = FullType.getArgs(')', fn.abiSize, ext);
		}
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
	public FullType retType() {
		return retType;
	}
	public int abiSize() {
		return abiSize;
	}
	public long minOff() {
		return minOff;
	}
	public long spos() {
		return spos;
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
		if (!(t1.provides(t2))) {
			Util.warn("Implicit cast from " + t1.toString() + " to " + t2.toString() + " during assignment");
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
	final Typed type;
	final FullType[] runsWith;//non-null when there is a calling clause; null otherwise
	final FullType gives;//non-null when there is a pointing clause (specifies particular full type that must be pointed to) and also when there is a calling clause; null otherwise
	public boolean equals(Object o) {
		if (!(o instanceof FullType)) {
			return super.equals(o);
		}
		if (!(type.eq(((FullType) o).type))) {
			return false;
		}
		if (!(type.addressable())) {
			return true;
		}
		if ((gives == null) != (((FullType) o).gives == null)) {
			return false;
		}
		if (gives == null) {
			return true;
		}
		if (!(gives.equals(((FullType) o).gives))) {
			return false;
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
	public boolean provides(FullType typ) throws InternalCompilerException, NotImplementedException {//if this already serves as an instance of typ without warnings
		if (this.equals(typ)) {
			return true;
		}
		if (typ.type != this.type) {
			return false;
		}
		if ((!(typ.type.addressable())) || (!(this.type.addressable()))) {
			throw new InternalCompilerException("This exceptional condition should not occur!");
		}
		if (typ.gives == null) {
			return true;
		}
		if (this.gives == null) {
			return false;
		}
		if ((this.runsWith == null) != (typ.runsWith == null)) {
			return false;
		}
		if (this.runsWith == null) {
			return this.gives.provides(typ.gives);
		}
		else {
			if(!(this.gives.provides(typ.gives))) {
				return false;
			}
			if (this.runsWith.length != typ.runsWith.length) {
				return false;
			}
			for (int i = 0; i < this.runsWith.length; i++) {
				if (!(this.runsWith[i].provides(typ.runsWith[i]))) {
					return false;
				}
			}
			return true;
		}
	}
	FullType(Typed typ) {
		type = typ;
		runsWith = null;
		gives = null;
	}
	FullType(Typed typ, FullType giv, FullType[] run) throws InternalCompilerException {
		if (!(typ.addressable())) {
			throw new InternalCompilerException("Calling clause for non-addressable argument");
		}
		type = typ;
		runsWith = run;
		gives = giv;
	}
	FullType(Typed typ, FullType pointed) throws InternalCompilerException {
		if (!(typ.addressable())) {
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
		Typed typ;
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
			if (Compiler.structs.containsKey(s)) {
				typ = Compiler.structs.get(s).type;
			}
			else {
				throw new UnidentifiableTypeException(s);
			}
		}
		try {
			Util.skipWhite();
			int ci = Util.read();
			if (ci == '*') {
				if (!(typ.addressable())) {
					throw new CompilationException("Cannot impose pointing clause for non-addressable type");
				}
				Util.skipWhite();
				FullType given = from();
				return new FullType(typ, given);
			}
			else if (ci == '$') {
				if (!(typ.addressable())) {
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
			throw new CompilationException("Unidentifiable type: " + E.verbatim);//TODO set `E' as the cause
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
	static LinkedHashMap<String, StackVar> getArgs(int ending, long abiSize) throws UnidentifiableTypeException, CompilationException, InternalCompilerException, IOException {
		return getArgs(ending, abiSize, null);
	}
	static LinkedHashMap<String, StackVar> getArgs(int ending, long abiSize, LinkedHashMap<String, FullType> ext) throws UnidentifiableTypeException, CompilationException, InternalCompilerException, IOException {//gets list of comma-delimited fullType entries, ended with the ending character (there is no comma after the last entry) (0-length allowed) (whitespace allowed)
		LinkedHashMap<String, StackVar> fl = new LinkedHashMap<String, StackVar>();
		FullType ft;
		String nam;
		long from = 2L * ((abiSize / 8L) + (((abiSize % 8L) == 0) ? 0L : 1L));
		Util.skipWhite();
		int ci = Util.read();
		int l;
		if (ci == ')') {
			if (ext != null) {
				for (Map.Entry<String, FullType> et : ext.entrySet()) {
					nam = et.getKey();
					ft = et.getValue();
					if (fl.containsKey(nam)) {
						throw new CompilationException("Duplicate argument name in function declaration: " + nam);
					}
					fl.put(nam, new StackVar(from, ft));
					l = ft.type.size();
					from += (((l / abiSize) * (abiSize / 8L)) + (((l % abiSize) == 0) ? 0L : (abiSize / 8L)));
				}
			}
			return fl;
		}
		Util.unread(ci);
		while (true) {
			ft = from();
			Util.skipWhite();
			nam = Util.phrase(0x3d);
			if (!(Util.legalIdent(nam))) {
				throw new CompilationException("Illegal identifier: " + nam);
			}
			if (Util.nondefk.contains(nam)) {
				throw new CompilationException("Reserved identifier: " + nam);
			}
			if (fl.containsKey(nam)) {
				throw new CompilationException("Duplicate argument name in function declaration: " + nam);
			}
			fl.put(nam, new StackVar(from, ft));
			l = ft.type.size();
			from += (((l / abiSize) * (abiSize / 8L)) + (((l % abiSize) == 0) ? 0L : (abiSize / 8L)));
			Util.skipWhite();
			ci = Util.read();
			if (ci == ')') {
				if (ext != null) {
					for (Map.Entry<String, FullType> et : ext.entrySet()) {
						nam = et.getKey();//identifier legality is unchecked, this is on purpose
						ft = et.getValue();
						if (fl.containsKey(nam)) {
							throw new CompilationException("Duplicate argument name in function declaration: " + nam);
						}
						fl.put(nam, new StackVar(from, ft));
						l = ft.type.size();
						from += (((l / abiSize) * (abiSize / 8L)) + (((l % abiSize) == 0) ? 0L : (abiSize / 8L)));
					}
				}
				return fl;
			}
			else if (ci != ',') {
				throw new CompilationException("Unexpected statement");
			}
			Util.skipWhite();
		}
	}
	static FullType of(Typed bec) throws InternalCompilerException {
		if (bec instanceof Type) {
			switch ((Type) bec) {
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
		return new FullType(bec);
	}
	void cast(FullType toType) throws CompilationException, InternalCompilerException {//All casts are valid except between primitives and structural types and from a structural object to another which the one from does not provide
		if (this.provides(toType)) {
			return;
		}
		if ((type instanceof Type) != (toType.type instanceof Type)) {
			throw new CompilationException("Illegal cast: Casting between a primitive type and a structural type");
		}
		if (type instanceof Type) {
			switch ((Type) type) {
				case u16:
				case s16:
					if (toType.type.size() != 16) {//If both the original and casted-to types have a size of 16 bits, the binary data doesn't need to be changed
						throw new NotImplementedException();
					}
					if (toType.type.addressable()) {
						Util.warn("Cast from a non-addressable type to an addressable type");
					}
					break;
				case a16:
					if (toType.type.size() != 16) {
						throw new NotImplementedException();
					}
					if (toType.type.addressable()) {
						if (!(this.provides(toType))) {
							Util.warn("Non-provisional cast from " + this.toString() + " to " + toType.toString());
						}
					}
					break;
				case u32:
				case s32:
					if (toType.type.size() != 32) {
						throw new NotImplementedException();
					}
					if (toType.type.floating()) {
						throw new NotImplementedException();
					}
					if (toType.type.addressable()) {
						Util.warn("Cast from a non-addressable type to an addressable type");
					}
					break;
				case a32:
					if (toType.type.size() != 32) {
						throw new NotImplementedException();
					}
					if (toType.type.floating()) {
						throw new NotImplementedException();
					}
					if (toType.type.addressable()) {
						if (!(this.provides(toType))) {
							Util.warn("Non-provisional cast from " + this.toString() + " to " + toType.toString());
						}
					}
					break;
				
				default:
					throw new NotImplementedException();
			}
			return;
		}
		throw new NotImplementedException();
	}
	public static void addrToMain(Type getting) throws InternalCompilerException {
		switch (Compiler.mach + getting.size()) {
			case (8):
				Compiler.text.println("movb (%bx),%al");
				return;
			case (16):
				Compiler.text.println("movw (%bx),%ax");
				return;
			case (32):
				Compiler.text.println("movw (%bx),%ax");
				Compiler.text.println("movw 2(%bx),%dx");
				return;
			case (64):
				Compiler.text.println("movw (%bx),%ax");
				Compiler.text.println("movw 2(%bx),%dx");
				Compiler.text.println("movw 4(%bx),%cx");
				Compiler.text.println("movw 6(%bx),%bx");
				return;
			case (9):
				Compiler.text.println("movb (%eax),%al");
				return;
			case (17):
				Compiler.text.println("movw (%eax),%ax");
				return;
			case (33):
				Compiler.text.println("movl (%eax),%eax");
				return;
			case (65):
				Compiler.text.println("movl 4(%eax),%edx");
				Compiler.text.println("movl (%eax),%eax");
				return;
			case (10):
				Compiler.text.println("movb (%rax),%al");
				return;
			case (18):
				Compiler.text.println("movw (%rax),%ax");
				return;
			case (34):
				Compiler.text.println("movl (%rax),%eax");
				return;
			case (66):
				Compiler.text.println("movq (%rax),%rax");
				return;
			default:
				throw new InternalCompilerException("Unidentifiable or disallowed operand size and / or unidentifiable target");
		}
	}
}
class Call extends Value {//TODO make inter-address size calls have the caller save and restore registers which are part of the instruction set that corresponds with the calling convention of the caller and needs to be preserved but is not preserved by the callee
	final Value addr;//size of the type determines the ABI used
	final Value[] args;//array length is the same as the amount of values used in the call
	final long pushedBits;//total amount of bits used as arguments to the call, not necessarily the amount pushed to the stack as arguments to the call
	final long inStack;//when Compiler.mach equals 2, the value is defined as being the amount of bits pushed to the stack as arguments to the call
	Call(Value v, Value[] g) throws CompilationException, InternalCompilerException {
		args = g;
		addr = v;
		if (!(v.type.type.addressable())) {
			throw new CompilationException("Function address value is not of an addressable type");
		}
		if (v.type.runsWith == null) {
			throw new CompilationException("Function address value has no calling clause");
		}
		if (v.type.gives == null) {
			throw new InternalCompilerException("This exceptional condition should not occur!");
		}
		type = v.type.gives;
		long[] pInfo = warn(args, v.type.runsWith, addr.type.type.size());
		pushedBits = pInfo[0];
		inStack = pInfo[1];
		if ((pushedBits % 8L) != 0L) {
			throw new InternalCompilerException("Non-integral amount of bytes used for function call");
		}
	}
	static long[] warn(Value[] args, FullType[] dargs, int abiSize) throws InternalCompilerException {
		boolean dif = false;
		long totalFn = 0;
		long totalAr = 0;
		int i = -(1);
		long pur = 0;
		int l;
		for (FullType t : dargs) {
			i++;
			l = t.type.size();
			totalFn += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? abiSize : 0);
			if (args.length <= i) {
				dif = true;
			}
			else {
				l = args[i].type.type.size();
				if (i > 5) {
					pur += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? abiSize : 0);
				}
				totalAr += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? abiSize : 0);
				if (totalFn != totalAr) {
					dif = true;
				}
			}
		}
		for (i++; i < args.length; i++) {
			l = args[i].type.type.size();
			if (i > 5) {
				pur += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? abiSize : 0);
			}
			totalAr += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? abiSize : 0);
		}
		if (dif) {
			if (Compiler.mach == 2) {
				Util.warn("Provided arguments sizes of function call do not agree with those of the specified arguments of the function");
			}
			else {
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
		}
		else {
			i = -(1);
			for (FullType t : dargs) {
				i++;
				if (!(args[i].type.equals(t))) {
					if (args[i].type.type.size() != t.type.size()) {
						throw new InternalCompilerException("This exceptional condition should not occur!");
					}
					if (args[i].type.type != t.type) {
						Util.warn("Raw conversion from provided argument " + args[i].type.toString() + " to specified function argument " + t.toString());
					}
					else {
						if (!(t.type.addressable() && args[i].type.type.addressable())) {
							throw new InternalCompilerException("This exceptional condition should not occur!");
						}
						if (!(args[i].type.provides(t))) {
							if (t.gives != null) {
								if (t.runsWith == null) {
									Util.warn("Ignorance of specified pointing clause in argument provision for function call");
								}
								else {
									Util.warn("Ignorance of specified calling clause in argument provision for function call");
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
		return new long[]{totalAr, pur};
	}
	FullType bring() throws CompilationException, InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (addr.type.type.size()) {
					case (16):
						before16_16();
						args16_16(args.length, args);
						addr.bring().type.toAddr();
						call16();
						after16_16(pushedBits);
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
				switch (addr.type.type.size()) {
					case (16):
						throw new NotImplementedException();
					case (32):
						before32_32();
						args32_32(args.length, args);
						addr.bring().type.toAddr();
						call32();
						after32_32(pushedBits);
						break;
					case (64):
						throw new NotImplementedException();
					default:
						throw new InternalCompilerException("Illegal operand size");
				}
				break;
			case (2):
					switch (addr.type.type.size()) {
					case (16):
						throw new NotImplementedException();
					case (32):
						throw new NotImplementedException();
					case (64):
//						before64_64();
//						args64_64(args.length, args);
//						addr.bring().type.toAddr();
//						call64();
//						after64_64(pushedBits);
//						break;
						throw new NotImplementedException();
					default:
						throw new InternalCompilerException("Illegal operand size");
				}
//				break;
			default:
				throw new InternalCompilerException("Illegal target");
		}
		return type;
	}
	static Value[] from() throws CompilationException, InternalCompilerException, IOException {//reads starting from after the opening parenthesis of the arguments, consumes the closing parenthesis
		ArrayList<Value> args = new ArrayList<Value>();
		Util.skipWhite();
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
	static void before32_32() {
	}
	static void before64_64() {
	}
	static void after16_16(long pb) throws SizeNotFitException, InternalCompilerException {
		try {
			if (pb != 0) {
				Compiler.text.println("addw $" + Util.signedRestrict(pb / 8L, 16) + ",%sp");
			}
		}
		catch (SizeNotFitException e) {
			throw new SizeNotFitException("Stack pointer restoration after function call does not abide by addressing mode limitations", e);
		}
	}
	static void after32_32(long pb) throws SizeNotFitException, InternalCompilerException {
		try {
			if (pb != 0) {
				Compiler.text.println("addl $" + Util.signedRestrict(pb / 8L, 33) + ",%esp");
			}
		}
		catch (SizeNotFitException e) {
			throw new SizeNotFitException("Stack pointer restoration after function call does not abide by addressing mode limitations", e);
		}
	}
	static void args16_32(int amnt, Value[] vals) throws InternalCompilerException, CompilationException {
//		args16_16(amnt, vals);
		throw new NotImplementedException();
	}
	static void args16_16(int amnt, Value[] vals) throws InternalCompilerException, CompilationException {//argument pushing for calls from 16-bit code using the 16-bit ABI or the 32-bit ABI (the 32-bit ABI is the System V ABI for Intel386 and the 16-bit ABI is the same thing but with 16-bit calls instead of 32-bit calls and %ebx can be scratched in functions and data is returned from functions, from lowest to highest word, in %ax, %dx, %cx, and %bx)
		FullType typ;
		for (int i = (amnt - 1); i >= 0; i--) {//TODO allow more than Integer.MAX_VALUE arguments to be used
			typ = vals[i].bring();
			switch (typ.type.size()) {
				case (8):
					Compiler.text.println("xorb %ah,%ah");
					Compiler.text.println("pushw %ax");
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
	static void args32_32(int amnt, Value[] vals) throws InternalCompilerException, CompilationException {
		FullType typ;
		for (int i = (amnt - 1); i >= 0; i--) {
			typ = vals[i].bring();
			switch (typ.type.size()) {
				case (8):
					Compiler.text.println("subw $4,%esp");
					Compiler.text.println("movzbl %al,(%esp)");
					break;
				case (64):
					Compiler.text.println("pushl %edx");
				case (32):
					Compiler.text.println("pushl %eax");
					break;
				case (16):
					Compiler.text.println("subw $4,%sp");
					Compiler.text.println("movzwl %ax,(%esp)");
					break;
				default:
					throw new InternalCompilerException("Illegal datum size");
			}
		}
	}
	void call16() {//TODO obey the ABI
		Compiler.text.println("callw *%bx");
	}
	void call32() {//TODO save registers
		Compiler.text.println("calll *%eax");
	}
	void call64() {
		Compiler.text.println("callq *%rax");
	}
}
interface Typed {//EVERY Typed MUST have its `size()' result in a positive int that is evenly divisible by 8
	int size();
	boolean addressable();
	boolean floating() throws InternalCompilerException ;
	default boolean eq(Typed to) {
		if ((this instanceof Type) != (to instanceof Type)) {
			return false;
		}
		if (this instanceof Type) {
			return this == to;
		}
		return ((StructuredType) this).struct.name.equals(((StructuredType) to).struct.name);
	}
	void pushMain() throws InternalCompilerException;
	void popMain() throws InternalCompilerException;
	void popAddr() throws InternalCompilerException;
	void toAddr() throws CompilationException, InternalCompilerException;
}
class StructuredType implements Typed {
	Structure struct;
	public int size() {
		return (int) struct.length;//TODO maybe fix the need for casting; structs longer than the int max value are not supported in this manner
	}
	public boolean addressable() {
		return false;
	}
	StructuredType(Structure st) throws InternalCompilerException {
		if (!(st.fieldsFinalised())) {
			throw new InternalCompilerException("Creation of a structured type based on a non-field-finalised class");
		}
		struct = st;
	}
	public String toString() {
		return struct.name;
	}
	public void pushMain() throws InternalCompilerException {
		throw new InternalCompilerException("Attempt to push to the stack the value of a structural type");
	}
	public void popMain() throws InternalCompilerException {
		throw new InternalCompilerException("Attempt to pop from the stack the value of a structural type");
	}
	public void popAddr() throws InternalCompilerException {
		throw new InternalCompilerException("Attempt to pop from the stack to an address the value of a structural type");
	}
	public void toAddr() throws InternalCompilerException {
		throw new InternalCompilerException("Attempt to use the value of a structural type as an address");
	}
	public boolean floating() throws InternalCompilerException {
		throw new InternalCompilerException("Requested boolean information is not applicable for a structured type");
	}
}
enum Type implements Typed {//ONLY sizes of 8, 16, 32, and 64 are allowed
	u8 (8, false, false),
	s8 (8, false, true),
	u16 (16, false, false),
	s16 (16, false, true),
	u32 (32, false, false),
	s32 (32, false, true),
	u64 (64, false, false),
	s64 (64, false, true),
	f32 (32, true, true),
	f64 (64, true, true),
	a16 (16, true, false, false),
	a32 (32, true, false, false),
	a64 (64, true, false, false);
	final int size;
	final boolean addressable;
	final boolean floating;
	final boolean signed;
	Type(int s, boolean f, boolean signd) {
		size = s;
		addressable = false;
		floating = f;
		signed = signd;
	}
	Type(int s, boolean addrsable, boolean f, boolean signd) {
		size = s;
		addressable = addrsable;
		floating = f;
		signed = signd;
	}
	public int size() {
		return size;
	}
	public boolean addressable() {
		return addressable;
	}
	public boolean floating() {
		return floating;
	}
	boolean fits(long l, boolean s) throws NotImplementedException {//the boolean expresses if the passed long's bits should be interpreted as signed
		if (floating) {
			throw new NotImplementedException();
		}
		if (s) {
			if (signed) {
				if (size == 64) {
					return true;
				}
				return (((0x01L << (size - 1)) > l) && ((-(0x01L << (size - 1))) <= l));
			}
			else {
				if (size == 64) {
					return (l >= 0);
				}
				return ((l >= 0) && ((0x01L << size) > l));
			}
		}
		else {
			if (signed) {
				if (size == 64) {
					return (l >= 0);
				}
				return ((l >= 0) && (((0x8000000000000000L >> (64 - size)) & l) == 0L));
			}
			else {
				if (size == 64) {
					return true;
				}
				return (((0x8000000000000000L >> (64 - size - 1)) & l) == 0L);
			}
		}
	}
	public void pushMain() throws InternalCompilerException {
		switch (size + Compiler.mach) {
			case (8):
				Compiler.text.println("movb %al,(%sp)");
				Compiler.text.println("subw $1,%sp");
				return;
			case (9):
				Compiler.text.println("movb %al,(%esp)");
				Compiler.text.println("subl $1,%esp");
				return;
			case (10):
				Compiler.text.println("movb %al,(%rsp)");
				Compiler.text.println("subq $1,%rsp");
				return;
			case (16):
			case (17):
			case (18):
				Compiler.text.println("pushw %ax");
				return;
			case (32):
				Compiler.text.println("pushw %dx");
				Compiler.text.println("pushw %ax");
				return;
			case (33):
				Compiler.text.println("pushl %eax");
				return;
			case (34):
				Compiler.text.println("movl %eax,(%rsp)");
				Compiler.text.println("subq $4,%rsp");
				return;
			case (64):
				Compiler.text.println("pushw %bx");
				Compiler.text.println("pushw %cx");
				Compiler.text.println("pushw %dx");
				Compiler.text.println("pushw %ax");
				return;
			case (65):
				Compiler.text.println("pushl %edx");
				Compiler.text.println("pushl %eax");
				return;
			case (66):
				Compiler.text.println("pushq %rax");
			default:
				throw new InternalCompilerException("Unidentifiable or disallowed operand size and / or unidentifiable target");
		}
	}
	public void popMain() throws InternalCompilerException {
		switch (size + Compiler.mach) {
			case (8):
				Compiler.text.println("addw $1,%sp");
				Compiler.text.println("movb (%sp),%al");
				return;
			case (9):
				Compiler.text.println("addl $1,%esp");
				Compiler.text.println("movb (%esp),%al");
				return;
			case (10):
				Compiler.text.println("addq $1,%rsp");
				Compiler.text.println("movb (%rsp),%al");
				return;
			case (16):
			case (17):
			case (18):
				Compiler.text.println("popw %ax");
				return;
			case (32):
				Compiler.text.println("popw %ax");
				Compiler.text.println("popw %dx");
				return;
			case (33):
				Compiler.text.println("popl %eax");
				return;
			case (34):
				Compiler.text.println("addq $4,%rsp");
				Compiler.text.println("movl (%rsp),%eax");
				return;
			case (64):
				Compiler.text.println("popw %ax");
				Compiler.text.println("popw %dx");
				Compiler.text.println("popw %cx");
				Compiler.text.println("popw %bx");
				return;
			case (65):
				Compiler.text.println("popl %eax");
				Compiler.text.println("popl %edx");
				return;
			case (66):
				Compiler.text.println("popq %rax");
			default:
				throw new InternalCompilerException("Unidentifiable or disallowed operand size and / or unidentifiable target");
		}
	}
	public void popAddr() throws InternalCompilerException {
		switch (size + Compiler.mach) {
			case (8):
				Compiler.text.println("addw $1,%sp");
				Compiler.text.println("movb (%sp),%al");
				Compiler.text.println("movb %al,(%bx)");
				return;
			case (9):
				Compiler.text.println("addl $1,%esp");
				Compiler.text.println("movb (%esp),%bl");
				Compiler.text.println("movb %bl,(%eax)");
				return;
			case (10):
				Compiler.text.println("addq $1,%rsp");
				Compiler.text.println("movb (%rsp),%bl");
				Compiler.text.println("movb %bl,(%rax)");
				return;
			case (16):
				Compiler.text.println("popw (%bx)");
				return;
			case (17):
				Compiler.text.println("popw (%eax)");
				return;
			case (18):
				Compiler.text.println("popw (%rax)");
				return;
			case (32):
				Compiler.text.println("popw (%bx)");
				Compiler.text.println("popw 2(%bx)");
				return;
			case (33):
				Compiler.text.println("popl (%eax)");
				return;
			case (34):
				Compiler.text.println("popw (%rax)");
				Compiler.text.println("popw 2(%rax)");
				return;
			case (64):
				Compiler.text.println("popw (%bx)");
				Compiler.text.println("popw 2(%bx)");
				Compiler.text.println("popw 4(%bx)");
				Compiler.text.println("popw 6(%bx)");
				return;
			case (65):
				Compiler.text.println("popl (%eax)");
				Compiler.text.println("popl 4(%eax)");
				return;
			case (66):
				Compiler.text.println("popq (%rax)");
			default:
				throw new InternalCompilerException("Unidentifiable or disallowed operand size and / or unidentifiable target");
		}
	}
	public void toAddr() throws CompilationException, InternalCompilerException {
		if (!(addressable())) {
			throw new InternalCompilerException("Attempt to use the value of a non-addressable type as an address");
		}
		switch (Compiler.mach + size) {
			case (16):
				Compiler.text.println("movw %ax,%bx");
				return;
			case (32):
			case (64):
				throw new CompilationException("Direct usage of an address size that is not supported by the target");
			case (17):
				Compiler.text.println("movzwl %ax,%eax");
			case (33):
				return;
			case (65):
				throw new CompilationException("Direct usage of an address size that is not supported by the target");
			case (18):
				Compiler.text.println("movzwl %ax,%eax");
			case (34):
				Compiler.text.println("and %eax,%eax");
			case (66):
				return;
			default:
				throw new InternalCompilerException("Unidentifiable or disallowed operand size and / or unidentifiable target");
		}
	}
}
abstract class Item {
}
interface Compilable {
	public void compile() throws CompilationException, InternalCompilerException, IOException;
}
interface Doable extends Compilable {
}
interface Stacked {
	int abiSize();
	FullType retType();
	long adjust(long n);
	long minOff();
	long spos();
}
class StrDecl implements Compilable {
	final String text;
	final String symb;
	StrDecl(String tex, String sym) {
		text = tex;
		symb = sym;
	}
	public void compile() {
		Compiler.rwdata.print(symb);
		Compiler.rwdata.println(':');
		Compiler.rwdata.print(".asciz \"");
		Compiler.rwdata.print(text);
		Compiler.rwdata.println('\"');
	}
}
class Str extends Value {
	final String text;
	final String symb;
	final StrDecl assoc;
	private Str(String tex) throws InternalCompilerException {//TODO avoid multiple identical string declarations being put out for different string usages; decide if string texts which mean the same thing but have different representations, such as by using different escapes, are identical
		text = tex;
		symb = Util.reserve();
		type = new FullType(Compiler.defAdr, FullType.u8);
		assoc = new StrDecl(tex, symb);
	}
	FullType bring() throws InternalCompilerException {
		if (type.type instanceof StructuredType) {
			throw new InternalCompilerException("String backs as a structural type");
		}
		switch ((Type) type.type) {
			case a16:
				Compiler.text.println("movw $" + symb + ",%ax");
				return type;
			case a32:
				Compiler.text.println("movl $" + symb + ",%eax");
				return type;
			case a64:
				Compiler.text.println("movq $" + symb + ",%rax");
				return type;
			default:
			throw new InternalCompilerException("String backs as a non-addressable type");
		}
	}
	static Str from() throws InternalCompilerException, IOException {//starts reading from directly after the opening doublequote
		int i;
		boolean sl = false;
		StringBuilder sb = new StringBuilder();
		while (true) {
			i = Util.read();
			if ((i == '\"') && (!(sl))) {
				return new Str(sb.toString());
			}
			sl = i == '\\';
			sb.appendCodePoint(i);
		}
	}
}
class Block implements Doable, Stacked {
	Doable[] comps;
	private transient long bpOff = 0;//offset from the base pointer 
	private long minOff = 0;//lowest value of the offset from the base pointer without processing blocks
	long spos;
	Stacked parent;
	Function assoc;
	Block(Stacked p, Function a) {
		parent = p;
		assoc = a;
		spos = minOff = bpOff = p.minOff();
	}
	static Block from(Stacked f) throws CompilationException, InternalCompilerException, IOException {
		ArrayList<Compilable> c = new ArrayList<Compilable>();
		Compiler.context.push(new TreeMap<String, StackVar>());
		Block b = new Block(f, (f instanceof Function) ? ((Function) f) : ((Block) f).assoc);
		try {
			while (true) {
				Compiler.getCompilable(c, true, b);
			}
		}
		catch (BlockEndException e) {
			b.comps = c.toArray(new Doable[0]);
			Compiler.context.pop();
			return b;
		}
	}
	public void compile() throws CompilationException, InternalCompilerException, IOException {
		long mo;
		if ((mo = parent.spos()) <= minOff) {
			spos = mo;
			mo = 0;
		}
		else {
			mo = minOff - parent.spos();
			spos = minOff;
		}
		if (mo > 0) {
			throw new InternalCompilerException("This exceptional condition should not occur!");
		}
		else if (mo < 0) {
			switch (assoc.abiSize) {
				case (16):
					Compiler.text.println("subw $" + Util.signedRestrict(-mo, 16) + ",%sp");
					break;
				case (32):
					Compiler.text.println("subl $" + Util.signedRestrict(-mo, 33) + ",%esp");
					break;
				case (64):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException("Unidentifiable target");
			}
		}
		for (Doable cpl : comps) {
			cpl.compile();
		}
		if (mo < 0) {
			switch (assoc.abiSize) {
				case (16):
					Compiler.text.println("addw $" + Util.signedRestrict(-mo, 16) + ",%sp");
					break;
				case (32):
					Compiler.text.println("addl $" + Util.signedRestrict(-mo, 33) + ",%esp");
					break;
				case (64):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException("Unidentifiable target");
			}
		}
	}
	public FullType retType() {
		return assoc.retType;
	}
	public int abiSize() {
		return assoc.abiSize;
	}
	public long adjust(long n) {
		bpOff += n;
		if (bpOff < minOff) {
			spos = minOff = bpOff;
		}
		return bpOff;
	}
	public long minOff() {
		return minOff;
	}
	public long spos() {
		return spos;
	}
}
abstract class Value extends Item implements Doable {
	FullType type;
	abstract FullType bring() throws CompilationException, InternalCompilerException;
	public void compile() throws CompilationException, InternalCompilerException {
		bring();
	}
}
class GlobalVarDecl implements Compilable {
	final FullType type;
	final String name;
	GlobalVarDecl(String s, FullType typ) {
		type = typ;
		name = s;
	}
	public void compile() throws InternalCompilerException {
		Compiler.rwdata.println(name + ":/*dhulbDoc-v" + Compiler.numericVersion + ":globalvar;" + type.toString() + " " + name + ";*/");//automatic documentation, the name should be the same as the symbol name
		if (Compiler.autoGlobals) {
			Compiler.rwdata.print(".globl ");
			Compiler.rwdata.println(name);
		}
		switch (type.type.size()) {
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
	public void write(int f) throws NotImplementedException, IOException {
		if ((f > 0x10ffff) || (f < 0)) {
			throw new IOException("Invalid Unicode character");
		}
		if (f < 0x80) {
			dat.put((byte) f);
		}
		else {
			throw new NotImplementedException();
		}
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
		val = (vlu & (0xffffffffffffffffL >>> (64 - type.type.size())));
	}
	FullType bring() throws InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				if (type.type.size() == 8) {
					Util.bring8('a', (byte) val, false);
				}
				else if (type.type.size() == 16) {
					Util.bring16('a', (short) val);
				}
				else if (type.type.size() == 32) {
					Util.bring16('a', ((short) val));
					Util.bring16('d', ((short) (val >>> 16)));
				}
				else if (type.type.size() == 64) {
					Util.bring16('a', ((short) (val & 0xffffL)));
					Util.bring16('d', ((short) (val >>> 16)));
					Util.bring16('c', ((short) (val >>> 32)));
					Util.bring16('b', ((short) (val >>> 48)));
				}
				else {
					throw new InternalCompilerException("Illegal datum size");
				}
				break;
			case (1):
				if (type.type.size() == 8) {
					Util.bring8('a', (byte) val, false);
				}
				else if (type.type.size() == 16) {
					Util.bring16('a',(short) val);
				}
				else if (type.type.size() == 32) {
					Util.bring32('a',(int) val);
				}
				else if (type.type.size() == 64) {
					Util.bring32('a', (int) (val & 0xffffffffL));
					Util.bring32('d', (int) (val >>> 32));
				}
				else {
					throw new InternalCompilerException("Illegal datum size");
				}
				break;
			case (2):
				throw new NotImplementedException();
			default:
				throw new InternalCompilerException("Illegal target");
		}
		return type;
	}
}
class Casting extends Operator {
	FullType from;
	FullType to;
	boolean raw;
	Casting(boolean r, FullType f, FullType t) {//'a', as, raw conversion; 't', to, cast
		super(true, r ? 'a' : 't');
		raw = r;
		from = f;
		to = t;
	}
	FullType apply(FullType typ) throws CompilationException, InternalCompilerException {
		if (raw) {
			if (from.type.size() < to.type.size()) {
				throw new NotImplementedException();
			}
		}
		else {
			from.cast(to);
		}
		return to;
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
	static final Operator STO = new Operator(false, 'S');//("->") Store to memory
	static final Operator GET = new Operator(true, '@');//("@") Get a value from memory
	final boolean unary;
	final int id;
	Operator(boolean un, int ident) {
		unary = un;
		id = ident;
	}
	FullType apply(FullType typ) throws CompilationException, InternalCompilerException {//Unary; The value has already been brought
		if (typ.type instanceof StructuredType) {
			throw new CompilationException("Usage of primitive operators with a structured type");
		}
		if (this instanceof Casting) {
			return ((Casting) this).apply(typ);
		}
		if (!(unary)) {
			throw new InternalCompilerException("Not a unary operator: " + this.toString());
		}
		switch (id) {
			case ('@'):
				if (!(typ.type.addressable())) {
					throw new CompilationException("Attempted dereferencing of a non-addressable type");
				}
				typ.type.toAddr();
				if ((typ.gives == null) || (typ.runsWith != null)) {
					Util.warn("Dereferencing of an address which has no pointing clause; dereferencing a value of the unsigned 8-bit type");
					FullType.addrToMain(Type.u8);
					return FullType.u8;
				}
				if (!(typ.gives.type instanceof Type)) {
					throw new CompilationException("Attempted dereferencing of a value of a structural type");
				}
				FullType.addrToMain((Type) typ.gives.type);
				return typ.gives;
			default:
				throw new NotImplementedException();
		}
	}
	FullType apply(FullType LHO, Value RHO) throws CompilationException, InternalCompilerException {//Binary; The LHO has already been brought
		if ((LHO.type instanceof StructuredType) || (RHO.type.type instanceof StructuredType)) {
			throw new CompilationException("Usage of primitive operators with a structured type");
		}
		if (unary) {
			throw new InternalCompilerException("Not a binary operator: " + this.toString());
		}
		FullType RHtyp = RHO.type;
		boolean alt = false;
		switch (id) {
			case ('-'):
				alt = true;
			case ('+'):
				switch (LHO.type.size()) {
					case (64):
						throw new NotImplementedException();
					case (32)://Remember that 32-bit push and pop aren't available in 64-bit mode
						switch (RHtyp.type.size()) {
							case (64):
								throw new NotImplementedException();
							case (32):
								switch (Compiler.mach) {
									case (0):
										throw new NotImplementedException();
									case (1):
										Compiler.text.println("pushl %eax");//TODO prevent the need for this move by bring()-ing directly to %bx and preserving %ax (unless it's significantly slower than using the accumulator %ax or it's impossible not to use %ax), in which cases the called function might warn this function that it would be left in %ax)
										RHO.bring();
										Compiler.text.println("popl %ebx");
										if (alt) {//TODO perform to %bx and then notify the caller that it was left in %bx, unless it's significantly slower than using the accumulator
											Compiler.text.println("subl %ebx,%eax");
										}
										else {
											Compiler.text.println("addl %ebx,%eax");
										}
										if ((LHO.type == Type.a32) || (RHtyp.type == Type.a32)) {
											return FullType.u32;
										}
										else if ((LHO.type == Type.s32) || (RHtyp.type == Type.s32)) {
											return FullType.s32;
										}
										else if ((LHO.type == Type.u32) && (RHtyp.type == Type.u32)) {
											return FullType.u32;
										}
										else {
											throw new InternalCompilerException("Resultant type of operation could not be resolved");
										}
									case (2):
										throw new NotImplementedException();
									default:
										throw new InternalCompilerException("Unidentifiable target");
								}
							case (16):
								throw new NotImplementedException();
							case (8):
								throw new NotImplementedException();
							default:
								throw new InternalCompilerException("Illegal datum size");
						}
					case (16)://u16, s16, or a16
						switch (RHtyp.type.size()) {
							case (64):
								throw new NotImplementedException();
							case (32)://Remember that 32-bit push and pop aren't available in 64-bit mode
								throw new NotImplementedException();
							case (16)://u16, s16, or a16
								Compiler.text.println("pushw %ax");//TODO prevent the need for this move by bring()-ing directly to %bx and preserving %ax (unless it's significantly slower than using the accumulator %ax or it's impossible not to use %ax), in which cases the called function might warn this function that it would be left in %ax)
								RHO.bring();
								Compiler.text.println("popw %bx");
								if (alt) {//TODO perform to %bx and then notify the caller that it was left in %bx, unless it's significantly slower than using the accumulator
									Compiler.text.println("subw %bx,%ax");
								}
								else {
									Compiler.text.println("addw %bx,%ax");
								}
								if ((LHO.type == Type.a16) || (RHtyp.type == Type.a16)) {
									return FullType.u16;
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
			case ('S'):
				if (!(RHtyp.type.addressable())) {
					throw new CompilationException("Attempted storage of a value to a non-addressable type");
				}
				if ((RHtyp.gives == null) || (RHtyp.runsWith != null)) {
					Util.warn("Storage to an address which does not have a pointing clause");
				}
				else if (!(LHO.provides(RHtyp.gives))) {
					Util.warn("Non-provisional storage");
				}
				LHO.type.pushMain();
				RHO.bring().type.toAddr();
				LHO.type.popAddr();
				return Util.fromAddr();
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
				switch (type.type.size()) {
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
				switch (type.type.size()) {
					case (64):
						Compiler.text.println("movl %edx," + name + "+4(,1)");
					case (32):
						Compiler.text.println("movw %eax," + name + "(,1)");
						break;
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
			case (2):
				throw new NotImplementedException();
			default:
				throw new InternalCompilerException("Illegal target");
		}
	}
	public FullType bring() throws NotImplementedException, InternalCompilerException {
		switch (Compiler.mach) {
			case (0):
				switch (type.type.size()) {
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
				switch (type.type.size()) {
					case (64):
						Compiler.text.println("movl " + name + "+4(,1),%edx");
					case (32):
						Compiler.text.println("movl " + name + "(,1),%eax");
						break;
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
					switch (type.type.size()) {
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
				try {
					switch (type.type.size()) {
						case (64):
							Compiler.text.println("movl " + Util.signedRestrict(pos + 4, 33) + "(%ebp),%edx");
						case (32):
							Compiler.text.println("movl " + Util.signedRestrict(pos, 33) + "(%ebp),%eax");
							break;
						case (16):
							Compiler.text.println("movw " + Util.signedRestrict(pos, 33) + "(%ebp),%ax");
							break;
						case (8):
							Compiler.text.println("movb " + Util.signedRestrict(pos, 33) + "(%ebp),%al");
							break;
						default:
							throw new InternalCompilerException("Illegal datum size");
					}
				}
				catch (SizeNotFitException E) {
					throw new SizeNotFitException("Access of stack variable at offset " + pos + " from the base pointer does not abide by addressing mode limitations");
				}
				break;
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
					switch (type.type.size()) {
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
				try {
					switch (type.type.size()) {
						case (64):
							Compiler.text.println("movl %edx," + Util.signedRestrict(pos + 4, 33) + "(%ebp)");
						case (32):
							Compiler.text.println("movl %eax," + Util.signedRestrict(pos, 33) + "(%ebp)");
							break;
						case (16):
							Compiler.text.println("movw %ax," + Util.signedRestrict(pos, 33) + "(%ebp)");
							break;
						case (8):
							Compiler.text.println("movb %al," + Util.signedRestrict(pos, 33) + "(%ebp)");
							break;
						default:
							throw new InternalCompilerException("Illegal datum size");
					}
				}
				catch (SizeNotFitException E) {
					throw new SizeNotFitException("Access of stack variable at offset " + pos + " from the base pointer does not abide by addressing mode limitations");
				}
				break;
			case (2):
				throw new NotImplementedException();
			default:
				throw new InternalCompilerException("Illegal target");
		}
	}
}
class FunctionAddr extends Value {//size of type is the ABI size
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
				switch (type.type.size()) {
					case (16):
						Compiler.text.println("movw $" + name + ",%ax");
						break;
					case (32):
						throw new NotImplementedException();//TODO attempt to figure out how to distribute the relocatable symbol between the multiple registers without using more than the 16-bit registers; this may not be possible, so maybe just use the higher instructions
					case (64):
						throw new NotImplementedException();//TODO attempt to figure out how to distribute the relocatable symbol between the multiple registers without using more than the 16-bit registers; this may not be possible, so maybe just use the higher instructions
					default:
						throw new InternalCompilerException("Unidentifiable address size for a code symbol");
				}
				break;
			case (1):
				switch (type.type.size()) {
					case (16):
						Compiler.text.println("movw $" + name + ",%ax");
						break;
					case (32):
						Compiler.text.println("movl $" + name + ",%eax");
						break;
					case (64):
						throw new NotImplementedException();
					default:
						throw new InternalCompilerException("Unidentifiable address size for a code symbol");
				}
				break;
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
	private transient boolean finalised;
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
		return last;
	}
	static Expression from(int ending) throws InternalCompilerException, IOException, CompilationException {
		return from(ending, ending, null);
	}
	static Expression from(int ending, Item pri) throws InternalCompilerException, IOException, CompilationException {
		return from(ending, ending, pri);
	}
	static Expression from(int ending, int ending2) throws InternalCompilerException, IOException, CompilationException {
		return from(ending, ending2, null);
	}
	static Expression from(int ending, int ending2, Item pri) throws InternalCompilerException, IOException, CompilationException {//Consumes the ending character
		Expression ex = new Expression();
		Item last = null;
		if (pri != null) {
			ex.add(last = pri);
		}
		while (true) {
			Util.skipWhite();
			int tg;
			tg = Util.read();
			if (tg == '(') {
				if (last instanceof Value) {
					if (!(((Value) last).type.type.addressable())) {
						if ((((Value) last).type.type.size() != 16) && (((Value) last).type.type.size() != 32) && (((Value) last).type.type.size() != 64)) {
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
			if (tg == '\"') {
				ex.add(last = Str.from());
				((Str) last).assoc.compile();
				continue;
			}
			else if ((tg == ending) || (tg == ending2)) {
				if (ex.items.isEmpty()) {
					throw new CompilationException("Empty expression");
				}
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
				String s = Util.phrase(0x2f);//do NOT change from 0x2f without updating the delimiter likewise for the cast / conversion chain checking for when performing a cast / conversion
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
					if ((s.equals("as")) || (s.equals("to"))) {
						boolean raw = s.equals("as");
						if (raw) {
							Util.warn("Raw conversion");
						}
						if (!(last instanceof Value)) {
							throw new CompilationException(raw ? "Raw conversion of a non-value" : "Casting of a non-value");
						}
						Util.skipWhite();
						FullType fto = FullType.from();
						Util.skipWhite();
						int ir = Util.read();
						if (ir == '(') {
							Util.unread(ir);
							Value ra = (Value) last;
							ex.skim(ra);
							Expression en = new Expression();
							en.add(ra);
							en.add(new Casting(raw, ra.type, fto));
							en.finalised = true;
							en.type = fto;
							ex.add(last = en);
						}
						else {
							boolean de = true;
							int a0 = 0;
							int a1 = 0;
							try {
								a0 = Util.read();
							}
							catch (EOFException E) {
								Util.unread(ir);
								de = false;
							}
							if (de) {
								try {
									a1 = Util.read();
								}
								catch (EOFException E) {
									Util.unread(a0);
									Util.unread(ir);
									de = false;
								}
								if (de) {
									if ((((ir == 'a') && (a0 == 's')) || ((ir == 't') && (a0 == 'o'))) && (Character.isWhitespace(a1) || Util.brack.contains(a1) || (a1 == ',') || (a1 == ';') || Util.inpork.contains(a1))) {
										Util.unread(a1);
										Util.unread(a0);
										Util.unread(ir);
										Value ra = (Value) last;
										ex.skim(ra);
										Expression en = new Expression();
										en.add(ra);
										en.add(new Casting(raw, ra.type, fto));
										en.finalised = true;
										en.type = fto;
										ex.add(last = en);
									}
									else {
										Util.unread(a1);
										Util.unread(a0);
										Util.unread(ir);
										ex.add(last = new Casting(raw, ((Value) last).type, fto));
									}
								}
								else {
									ex.add(last = new Casting(raw, ((Value) last).type, fto));
								}
							}
							else {
								ex.add(last = new Casting(raw, ((Value) last).type, fto));
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
									if ((i = Util.read()) == '>') {
										ex.add(last = Operator.STO);
									}
									else {
										Util.unread(i);
										ex.add(last = Operator.SUB);
									}
									break;
								case ('*'):
									ex.add(last = Operator.MUL);
									break;
								case ('/'):
									ex.add(last = Operator.DIV);
									break;
								case ('@'):
									ex.add(last = Operator.GET);
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
	CompilationException(String reas, Throwable t) {
		super(reas, t);
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
	BlockEndException(String reas) {
		super(reas);
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
	SizeNotFitException(String reas, Throwable t) {
		super(reas, t);
	}
}
