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
import java.util.HashMap;
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
	public static final String invocation = "Invocation:\n"
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
			+ "\tT\tCauses what are usually the data and text sections to instead have their contents be in one text section\n"
			+ "\n"
			+ "\n"
			+ "\n"
			+ "dhulbc --help|-help|help\n"
			+ "\n"
			+ "\n"
			+ "\n"
			+ "dhulbc --version|-version|version\n";//TODO use the system line separator
	public static void main(String[] argv) throws IOException, InternalCompilerException {
		if (argv.length < 1) {
			System.err.print(invocation);
			System.exit(5);
		}
		else if (argv[0].equals("--help") || argv[0].equals("-help") || argv[0].equals("help")) {
			System.out.print(invocation);
			System.exit(0);
		}	
		else if (argv[0].equals("--version") || argv[0].equals("-version") || argv[0].equals("version")) {
			System.out.println("dhulb compiler reference implementation (dhulbc Dhulb), version " + Compiler.stringVersion + " (version ID " + Long.toString(Compiler.numericVersion) + " (0x" + Long.toHexString(Compiler.numericVersion) + "))");
			System.exit(0);
		}
		else if (argv.length < 2) {
			System.err.print(invocation);
			System.exit(10);
		}
		else {
			Compiler.mai(argv);//TODO prevent declaration of non-pointed structurally-typed variables
			System.exit(0);
		}
	}
}
class NOStream extends OutputStream {
    NOStream() {
            super();
    }
    public void close() {
    }
    public void flush() {
    }
    public void write(byte[] bs) {
    }
    public void write(byte[] bs, int i, int j) {
    }
    public void write(int i) throws IOException {
    }
}
class Compiler {//TODO keywords: "imply" (like extern, also allows illegal names to be implied so that they can be accessed using the syntax which allows global variables with illegal names as well as global function with illegal names to be accessed, which has not yet been implemented), "linkable" (like globl), "assert" (change stack variable's full type to any full type (different than just casting, since the amount of data read can change based on the size of the type (this is necessary for stack variables but not for global variables since global variables can just referenced and then the pointing clause or dereferencing method of that reference changed, while doing that for stack variables would produce a stack segment-relative address whether the LEA instruction is used or the base pointer offset for the stack variable is used and this is not always good, since it would not work when the base of the data segment is not the same as the base of the stack segment or when the data segment's limit does not encompass the entirety of the data, given that addresses are specified to be logical address offset values for the data segment, though this does not happen with many modern user-space program loaders))) (or some other names like those), "require"
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
	public static Type defOff = Type.s16;
	public static int CALL_SIZE_BITS = 16;//default address size (for global variable references, global function calls, and default global function calling conventions); must be 16, 32, or 64
	public static boolean showCompilationErrorStackTrace = false;
	public static int warns = 0;
	public static final long numericVersion = /*00_00_0*/3_01;//TODO bump when needed, should be bumped in accord with every bump of stringVersion; do NOT remove this to-do marker
	public static final String stringVersion = "0.0.3.1";//TODO bump when needed, should be bumped in accord with every bump of numericVersion; do NOT remove this to-do marker
	public static TreeMap<String, NoScopeVar> HVars = new TreeMap<String, NoScopeVar>();
	public static TreeMap<String, Function> HFuncs = new TreeMap<String, Function>();
	public static Stack<Map<String, StackVar>> context = new Stack<Map<String, StackVar>>();
	public static TreeMap<String, Structure> structs = new TreeMap<String, Structure>();
	public static ArrayList<Compilable> program = new ArrayList<Compilable>();
	public static boolean noViewErrors = false;
	public static boolean autoGlobals = false;
	public static boolean oneText = false;
	public static long buildTime = 0;
	public static long ver_major = (numericVersion / 1000000L) % 100L;// do not make final
	public static long ver_minor = (numericVersion / 10000L) % 100L;// do not make final
	public static long ver_inframinor = (numericVersion / 100L) % 100L;// do not make final
	public static long ver_subinframinor = numericVersion % 100L;// do not make final
	public static Literal nul;
	public static TreeMap<String, Typed> aliasesTypes = new TreeMap<String, Typed>();
	public static TreeMap<String, FullType> aliasesFullTypes = new TreeMap<String, FullType>();
	public static void mai(String[] argv) throws IOException, InternalCompilerException {//TODO change operator output behaviour to match CPU instruction output sizes
		buildTime = System.currentTimeMillis() / 1000L;
		try {//TODO implement bulk memory movement syntax
			try {//TODO create a way for an address to be gotten from a named global function
				nowhere = new PrintStream(new NOStream());
				prologue = new PrintStream(new BufferedOutputStream(System.out, 0x01000000));
				proback = prologue;
				epilogue = new PrintStream(new BufferedOutputStream(System.out, 0x01000000));
				epiback = epilogue;
				rwdata = new PrintStream(new BufferedOutputStream(System.out, 0x01000000));
				rwdatback = rwdata;
				text = new PrintStream(new BufferedOutputStream(System.out, 0x01000000));
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
					if (pos == Util.viewLen) {
						pos = 0;
					}
					int where = 0;
					while (true) {
						pos++;
						where++;
						if (pos == Util.viewLen) {
							pos = 0;
						}
						if (pos == Util.viewPos) {
							break;
						}
						sb.appendCodePoint(Util.view[pos]);
					}
					try {
						while (pos < Util.viewEnd) {
							pos++;
							if (pos == Util.viewLen) {
								pos = 0;
							}
							sb.appendCodePoint(Util.read());
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
		int madec = 0;
		try {
			madec = Integer.parseInt(argv[0]);
		}
		catch (NumberFormatException E) {
			System.err.print(Dhulb.invocation);
			System.exit(8);
		}
		if (madec == 16) {
			Compiler.text.println(".code16");
			mach = 0;
			CALL_SIZE_BITS = 16;
			defUInt = Type.u16;
			defSInt = Type.s16;
			def754 = Type.f32;
			defAdr = Type.a16;
			defOff = Type.s16;
		}
		else if (madec == 32) {
			Compiler.text.println(".code32");
			mach = 1;
			CALL_SIZE_BITS = 32;
			defUInt = Type.u32;
			defSInt = Type.s32;
			def754 = Type.f32;
			defAdr = Type.a32;
			defOff = Type.s32;
		}
		else if (madec == 64) {
			Compiler.text.println(".code64");
			mach = 2;
			CALL_SIZE_BITS = 64;
			defUInt = Type.u64;
			defSInt = Type.s64;
			def754 = Type.f64;
			defAdr = Type.a64;
			defOff = Type.s64;
		}
		else {
			System.err.print(Dhulb.invocation);
			System.exit(9);
		}
		nul = new Literal(FullType.of(defAdr), 0L);
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
//		if (inFunc && (fn == null)) {
//			throw new InternalCompilerException("Function not provided");
//		}
		String s;
		FullType typ;
		int i;
		Util.skipWhite();
		i = Util.read();
		if (i == '}') {
			throw new BlockEndException("Unexpected end-of-block");//Unexpected by this call, the message only applies to the compilation as a whole if this is not within a block
		}
		else if (i == ':') {
			if (!(inFunc)) {
				throw new NotImplementedException();//TODO implement `goto' statements and labels outside of functions
			}
			Util.skipWhite();
			Label l;
			s = Util.phrase(0x3d);
			if (!(Util.legalIdent(s))) {
				throw new CompilationException("Illegal identifier: " + s);
			}
			if (Util.nondefk.contains(s)) {
				throw new CompilationException("Reserved identifier: " + s);
			}
			for (Map<String, StackVar> cn : context) {
				if (cn.containsKey(s)) {
					throw new CompilationException("Label naming collision: label " + s + " is defined again in the same scope or an enclosing scope");
				}
			}
			if (fn.assoc().labels.containsKey(s)) {
				throw new CompilationException("Duplicate label name in the same function: " + s);
			}
			fn.label(s, l = new Label(fn));
			list.add(l);
			Util.skipWhite();
			if ((i = Util.read()) != ';') {
				throw new CompilationException("Unexpected operator: " + new String(new int[]{i}, 0, 1));
			}
			return;
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
			if (s.equals("class") || s.equals("struct") || s.equals("structure")) {
				Util.skipWhite();
				String naam = Util.phrase(0x3d);
				if (!(Util.legalIdent(naam))) {
					throw new CompilationException("Illegal identifier for class: " + naam);
				}
				if (Util.nondefk.contains(naam)) {
					throw new CompilationException("Reserved identifier for class: " + naam);
				}
				Structure st = Structure.from(naam);
				list.add(st);
			}
			else if (s.equals("typealias") || s.equals("typefullalias")) {
				boolean full = s.equals("typefullalias");
				Util.skipWhite();
				s = Util.phrase(0x3d);
				if (!(Util.legalIdent(s))) {
					throw new CompilationException("Illegal identifier for type alias: " + s);
				}
				else if (Util.nondefk.contains(s)) {
					throw new CompilationException("Reserved identifier for type alias: " + s);
				}
				else if (Compiler.HVars.containsKey(s)) {
					throw new CompilationException("Illegal identifier for type alias, already exists as a global variable: " + s);
				}
				else if (Compiler.HVars.containsKey(s)) {
					throw new CompilationException("Illegal identifier for type alias, already exists as a global function: " + s);
				}
				Util.skipWhite();
				try {
					if (full) {
						Compiler.aliasesFullTypes.put(s, FullType.from());
					}
					else {
						Compiler.aliasesTypes.put(s, Util.typedOfName(Util.phrase(0x2d)));
					}
				}
				catch (UnidentifiableTypeException E) {
					throw new CompilationException("Unidentifiable type: " + E.verbatim);//TODO set E as the cause
				}
				Util.skipWhite();
				i = Util.read();
				if (i != ';') {
					throw new CompilationException("Unexpected operator: " + new String(new int[]{i}, 0, 1));
				}
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
						if (Compiler.HVars.containsKey(r) || Compiler.HFuncs.containsKey(r)) {
							throw new CompilationException("Implied symbol is already defined: " + r);
						}
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
							throw new CompilationException("Keyword `return' is not allowed outside of functions");
						}
						Util.skipWhite();
						list.add(new Yield(Expression.from(';'), fn.retType(), fn.abiSize()));
						return;
					case ("if"):
						if (inFunc) {
							list.add(IfThen.from(fn));
						}
						else {
							throw new NotImplementedException();//TODO decide if `if' statements are allowed outside of functions; if so, decide how variables declared inside of `if' statements which are outside of functions will work
						}
						return;
					case ("goto"):
						if (inFunc) {
							Util.skipWhite();
							s = Util.phrase(0x3d);
							list.add(new Jump(s, fn));
							Util.skipWhite();
							i = Util.read();
							if (i != ';') {
								throw new CompilationException("Unexpected operator: " + new String(new int[]{i}, 0, 1));
							}
						}
						else {
							throw new NotImplementedException();//TODO implement `goto' statements and labels outside of functions
						}
						return;
					case ("jump"):
						FullType e = Expression.from(';').bring();
						if (!(e.type.addressable())) {
							throw new CompilationException("Raw jump to a non-addressable type");
						}
						e.type.toAddr();
						switch (Compiler.CALL_SIZE_BITS) {
							case (16):
								throw new NotImplementedException();
							case (32):
								Compiler.text.println("jmpl *%eax");
								break;
							case (64):
								throw new NotImplementedException();
							default:
								throw new InternalCompilerException("Illegal call size");
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
					if (Util.nulitk.contains(s)) {
						j = Compiler.nul;
					}
					else {
						try {
							j = Util.getLit(s);
						}
						catch (NumberFormatException E) {
							throw new CompilationException("Invalid literal notation: " + s);
						}
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
					throw new CompilationException("Stack variable naming collision: stack variable " + s + " is defined again in the same scope or an enclosing scope");
				}
			}
			if (fn.assoc().labels.containsKey(s)) {
				throw new CompilationException("Stack variable naming collision: " + s + " is defined in the same scope or an enclosing scope, as a label");
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
	static String[] keywore = new String[]{"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "goto", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "transient", "try", "void", "volatile", "while", "u8", "s8", "u16", "s16", "u32", "s32", "u64", "s64", "f32", "f64", "a16", "a32", "a64", "uint", "sint", "addr", "imply", "as", "to", "byref", "byval", "jump", "struct", "typealias", "typefullalias", "structure"};
	static ArrayList<String> accesk = new ArrayList<String>();
	static String[] accese = new String[]{};
	static ArrayList<String> boolitk = new ArrayList<String>();
	static String[] boolite = new String[]{"false", "true"};
	static ArrayList<String> nulitk = new ArrayList<String>();
	static String[] nulite = new String[]{"null"};
	static ArrayList<String> primsk = new ArrayList<String>();
	static String[] primse = new String[]{"u8", "s8", "u16", "s16", "u32", "s32", "u64", "s64", "f32", "f64", "a16", "a32", "a64", "uint", "sint", "float", "addr", "int", "sizeof"};//TODO maybe but probably not: remove the platform-dependent aliases, use a plug-in for them instead
	static ArrayList<Integer> inpork = new ArrayList<Integer>();
	static int[] inpore = new int[]{'+', '-', '=', '/', '<', '>', '*', '%', ',', '$', '!', '~', '@', '.', '&', '|', '^'};
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
	public static String buildNam;
	static {
		buildNam = Long.toHexString(ubid) + "__" + Long.toHexString(Compiler.buildTime);
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
	static Typed typedOfName(String s) throws CompilationException, UnidentifiableTypeException, InternalCompilerException {
		if (Compiler.aliasesTypes.containsKey(s)) {
			return Compiler.aliasesTypes.get(s);
		}
		else if (Util.primsk.contains(s)) {
			try {
				return Type.valueOf(s);
			}
			catch (IllegalArgumentException E) {
				if (s.equals("uint")) {
					if (!(Compiler.typeNicksApplicable)) {
						Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
						throw new CompilationException("Illegal use of a platform-dependent type");
					}
					return Compiler.defUInt;
				}
				else if (s.equals("sint") || s.equals("int")) {
					if (!(Compiler.typeNicksApplicable)) {
						Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
						throw new CompilationException("Illegal use of a platform-dependent type");
					}
					return Compiler.defSInt;
				}
				else if (s.equals("float")) {
					if (!(Compiler.typeNicksApplicable)) {
						Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
						throw new CompilationException("Illegal use of a platform-dependent type");
					}
					return Compiler.def754;
				}
				else if (s.equals("addr")) {
					if (!(Compiler.typeNicksApplicable)) {
						Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
						throw new CompilationException("Illegal use of a platform-dependent type");
					}
					return Compiler.defAdr;
				}
				else {
					throw new InternalCompilerException("Type cannot be found: " + s);
				}
			}
		}
		else {
			if (Compiler.structs.containsKey(s)) {
				return Compiler.structs.get(s).type;
			}
			else {
				throw new UnidentifiableTypeException(s);
			}
		}
	}
	static String reserve() {
		return "___dhulbres__" + buildNam + "__" + Long.toString(sen++);
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
	static void sx(int from, int to) throws InternalCompilerException {
		switch (to) {
			case (16):
				Compiler.text.println("cbtw");
				break;
			case (32):
				switch (from) {
					case (16):
						if (Compiler.mach == 0) {
							Compiler.text.println("cwtd");
						}
						else {
							Compiler.text.println("cwtl");
						}
						break;
					case (8):
						if (Compiler.mach == 0) {
							Compiler.text.println("cbtw");
							Compiler.text.println("cwtd");
						}
						else {
							Compiler.text.println("cbtw");
							Compiler.text.println("cwtl");
						}
						break;
					default:
						throw new InternalCompilerException("Illegal size for bit-wise extension");
				}
				break;
			case (64):
				switch (from) {
					case (32):
						if (Compiler.mach == 0) {
							Compiler.text.println("movw %ax,%cx");
							Compiler.text.println("movw %dx,%ax");
							Compiler.text.println("cwtd");
							Compiler.text.println("xchgw %dx,%cx");
							Compiler.text.println("xchgw %ax,%dx");
							Compiler.text.println("movw %cx,%bx");
						}
						else if (Compiler.mach == 1) {
							Compiler.text.println("cltd");
						}
						else {
							Compiler.text.println("cltq");
						}
						break;
					case (16):
						if (Compiler.mach == 0) {
							Compiler.text.println("cwtd");
							Compiler.text.println("movw %dx,%cx");
							Compiler.text.println("movw %dx,%bx");
						}
						else if (Compiler.mach == 1) {
							Compiler.text.println("cwtl");
							Compiler.text.println("cltd");
						}
						else {
							Compiler.text.println("cwtl");
							Compiler.text.println("cltq");
						}
						break;
					case (8):
						if (Compiler.mach == 0) {
							Compiler.text.println("cbtw");
							Compiler.text.println("cwtd");
							Compiler.text.println("movw %dx,%cx");
							Compiler.text.println("movw %dx,%bx");
						}
						else if (Compiler.mach == 1) {
							Compiler.text.println("cbtw");
							Compiler.text.println("cwtl");
							Compiler.text.println("cltd");
						}
						else {
							Compiler.text.println("cbtw");
							Compiler.text.println("cwtl");
							Compiler.text.println("cltq");
						}
						break;
					default:
						throw new InternalCompilerException("Illegal size for bit-wise extension");
				}		
				break;
			default:
				throw new InternalCompilerException("Illegal size for bit-wise extension");
		}
	}
	static void zx(int from, int to) throws InternalCompilerException {
		switch (to) {
			case (16):
				if (Compiler.mach == 0) {
					Compiler.text.println("xorb %ah,%ah");
				}
				else {
					Compiler.text.println("movzbw %al,%ax");
				}
				break;
			case (32):
				switch (from) {
					case (16):
						if (Compiler.mach == 0) {
							Compiler.text.println("xorw %dx,%dx");
						}
						else {
							Compiler.text.println("movzwl %ax,%eax");
						}
						break;
					case (8):
						if (Compiler.mach == 0) {
							Compiler.text.println("xorb %ah,%ah");
							Compiler.text.println("xorw %dx,%dx");
						}
						else {
							Compiler.text.println("movzbl %al,%eax");
						}
						break;
					default:
						throw new InternalCompilerException("Illegal size for bit-wise extension");
				}
				break;
			case (64):
				switch (from) {
					case (32):
						if (Compiler.mach == 0) {
							Compiler.text.println("xorw %cx,%cx");
							Compiler.text.println("xorw %bx,%bx");
						}
						else if (Compiler.mach == 1) {
							Compiler.text.println("xorl %edx,%edx");
						}
						else {
							Compiler.text.println("movl %eax,%eax");
						}
						break;
					case (16):
						if (Compiler.mach == 0) {
							Compiler.text.println("xorw %dx,%dx");
							Compiler.text.println("xorw %cx,%cx");
							Compiler.text.println("xorw %bx,%bx");
						}
						else if (Compiler.mach == 1) {
							Compiler.text.println("movzwl %ax,%eax");
							Compiler.text.println("xorl %edx,%edx");
						}
						else {
							Compiler.text.println("movzwq %ax,%rax");
						}
						break;
					case (8):
						if (Compiler.mach == 0) {
							Compiler.text.println("xorb %ah,%ah");
							Compiler.text.println("xorw %dx,%dx");
							Compiler.text.println("xorw %cx,%cx");
							Compiler.text.println("xorw %bx,%bx");
						}
						else if (Compiler.mach == 1) {
							Compiler.text.println("movzbl %al,%eax");
							Compiler.text.println("xorl %edx,%edx");
						}
						else {
							Compiler.text.println("movzbq %al,%rax");
						}
						break;
					default:
						throw new InternalCompilerException("Illegal size for bit-wise extension");
				}		
				break;
			default:
				throw new InternalCompilerException("Illegal size for bit-wise extension");
		}
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
		if ((keywork.contains(s)) || (boolitk.contains(s)) || (nulitk.contains(s)) || (Compiler.structs.containsKey(s)) || (Compiler.aliasesFullTypes.containsKey(s)) || (Compiler.aliasesTypes.containsKey(s))) {
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
			Util.warn("Implicit cast from " + ft.toString() + " to " + retType.toString() + " during function yield");
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
				throw new InternalCompilerException("Unidentifiable target");
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
				Compiler.text.println("movl -4(%ebp),%ebx");
				Compiler.text.println("movl %ebp,%esp");
				Compiler.text.println("popl %ebp");
				Compiler.text.println("retl");
				break;
			case (64):
				Compiler.text.println("movq -8(%rbp),%rbx");
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
abstract class Conditional implements Doable {//if(X){}, else(X){}, else{}, while(X){}, for(X;X;X){}, dowhile(X){}, loop{}
	static int[] els = new int[] {'e', 'l', 's', 'e'};
	final Stacked parent;
	protected Conditional(Stacked p) {
		parent = p;
	}
}//TODO allow both of the notations do{X}while(X) and dowhile(X){X}
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
					Compiler.text.println("jz " + nsymb);
				}
				else {
					Compiler.text.println("jnz " + nsymb);
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
		fields = new LinkedHashMap<String, StructEntry>();
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
		long l = length + (((length % ((long) (t.type.alignmentBits() / 8))) == 0) ? 0 : (((long) (t.type.alignmentBits() / 8)) - (length % ((long) (t.type.alignmentBits() / 8)))));
		fields.put(nam, new StructEntry(l, t));
		if (alignmentBytes < (t.type.alignmentBits() / 8)) {
			alignmentBytes = (t.type.alignmentBits() / 8);
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
		Compiler.structs.put(name, st);
		st.fulltype = FullType.of(st.type = new StructuredType(st));
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
					throw new CompilationException("Function declaration not allowed within a structure");
					/*
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
					*/
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
					throw new CompilationException("Function declaration not allowed within a structure");
					/*
					st.finishFields();
					iaf = true;
					break;
					*/
				}
				Util.unread(i);
			}
			if (st.fields.containsKey(sr)) {
				throw new CompilationException("Duplicate structural type field name: " + sr);
			}
			if (ft.type == st.type) {
				throw new CompilationException("Structure contains itself: " + name);
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
				throw new CompilationException("Function declaration not allowed within a structure");
				/*
				byref = sr.equals("byref");
				Util.skipWhite();
				sr = Util.phrase(0x3d);
				*/
			}
			else {
				byref = true;
			}
			/*
			if (Util.nondefk.contains(sr)) {
				throw new CompilationException("Reserved identifier: " + sr);
			}
			if (!(Util.legalIdent(sr))) {
				throw new CompilationException("Illegal identifier for class method: " + sr);
			}
			if (st.fields.containsKey(sr)) {
				throw new CompilationException("Class method has the same name as a field of the instance's structural type");
			}
			*/
			Util.skipWhite();
			i = Util.read();
			if (i != '(') {
				throw new CompilationException("Unexpected operator: " + new String(new int[]{i}, 0, 1));
			}
			throw new CompilationException("Function declaration not allowed within a structure");
			/*
			aiherghre(st, sr, ft, byref);
			*/
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
		Compiler.rwdata.println("/*dhulbDoc-v" + Compiler.numericVersion + ":structure;" + toString() + ";*/");
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
	public int compareTo(StructEntry to) {//make sure that there is never a StructEntry with offset of the minimum long value
		long diff = offset - to.offset;
		return (diff == 0L) ? 0 : ((diff > 0L) ? 1 : (-1));
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
	transient long cOff;//current offset from the base pointer, taking blocks into consideration
	Map<String, Label> labels;
	private Function() {
		abiSize = Compiler.CALL_SIZE_BITS;
		cOff = 0;
		labels = new HashMap<String, Label>();
	}
	Function(FullType rett, FullType[] ar, Doable[] doz, String nam) {
		retType = rett;
		dargs = ar;
		does = doz;
		name = nam;
		abiSize = Compiler.CALL_SIZE_BITS;
		cOff = 0;
		labels = new HashMap<String, Label>();
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
		cOff = 0;
		labels = new HashMap<String, Label>();
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
//		if (minOff == (-1)) {
//			switch (abiSize) {
//				case (16):
//					Compiler.text.println("pushw %bp");
//					Compiler.text.println("movw %sp,%bp");
//					Compiler.text.println("decw %sp");
//					break;
//				case (32):
//					Compiler.text.println("pushl %ebp");
//					Compiler.text.println("movl %esp,%ebp");
//					Compiler.text.println("decl %esp");
//					break;
//				case (64):
//					throw new NotImplementedException();
//				default:
//					throw new InternalCompilerException("Unidentifiable target");
//			}
//		} else
		if (minOff < 0) {
			switch (abiSize) {
				case (16):
					Compiler.text.println("pushw %bp");
					Compiler.text.println("movw %sp,%bp");
					Compiler.text.println("subw $" + Util.signedRestrict(-minOff, 16) + ",%sp");
					break;
				case (32):
					Compiler.text.println("pushl %ebp");
					Compiler.text.println("movl %esp,%ebp");
					Compiler.text.println("subl $" + Util.signedRestrict(-minOff, 33) + ",%esp");
					Compiler.text.println("movl %ebx,-4(%ebp)");
					break;
				case (64):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException("Unidentifiable target");
			}
		}
		else if (minOff == 0) {
			switch (abiSize) {
				case (16):
					Compiler.text.println("pushw %bp");
					Compiler.text.println("movw %sp,%bp");
					break;
				case (32):
					throw new InternalCompilerException("This exceptional condition should not occur!");
				case (64):
					throw new InternalCompilerException("This exceptional condition should not occur!");
				default:
					throw new InternalCompilerException("Unidentifiable target");
			}
		}
		else {
			throw new InternalCompilerException("This exceptional condition should not occur!");
		}
		update(minOff);
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
		if (fn.abiSize != 16) {
			fn.adjust(-(fn.abiSize / 8));
		}
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
	public long curOff() {
		return cOff;
	}
	public void update(long n) {
		cOff += n;
	}
	public void label(String nam, Label lbl) {
		labels.put(nam, lbl);
	}
	public Function assoc() {
		return this;
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
				if (!(typ.runsWith[i].provides(this.runsWith[i]))) {
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
		int g = Util.read();
		if (g == '*') {
			if (!(Compiler.typeNicksApplicable)) {
				Compiler.typeNicksApplicable = Compiler.allowTypeNicks;
				throw new CompilationException("Illegal use of a platform-dependent type");
			}
			Util.skipWhite();
			try {
				return new FullType(Compiler.defAdr, from());
			}
			catch (UnidentifiableTypeException E) {
				throw new CompilationException("Unidentifiable type: " + E.verbatim);//TODO set `E' as the cause
			}
		}
		Util.unread(g);
		String s = Util.phrase(0x2d);
		if (Compiler.aliasesFullTypes.containsKey(s)) {
			return Compiler.aliasesFullTypes.get(s);
		}
		Typed typ = Util.typedOfName(s);
		try {
			Util.skipWhite();
			int ci = Util.read();
			if (ci == '*') {
				if (!(typ.addressable())) {
					throw new CompilationException("Cannot impose pointing clause for non-addressable type");
				}
				Util.skipWhite();
				return new FullType(typ, from());
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
				case u8:
				case s8:
				case u16:
				case s16:
				case u32:
				case s32:
					if (toType.type.floating()) {
						throw new NotImplementedException();
					}
					if (toType.type.size() > type.size()) {
						if (type.signed() && toType.type.signed()) {
							Util.sx(type.size(), toType.type.size());
						}
						else {
							Util.zx(type.size(), toType.type.size());
						}
					}
					if (toType.type.addressable()) {
						Util.warn("Cast from a non-addressable type to an addressable type");
					}
					break;
				case a16:
				case a32:
					if (toType.type.floating()) {
						throw new NotImplementedException();
					}
					if (toType.type.size() > type.size()) {
							Util.zx(type.size(), toType.type.size());
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
			totalFn += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? 0 : abiSize);
			if (args.length <= i) {
				dif = true;
			}
			else {
				l = args[i].type.type.size();
				if (i > 5) {
					pur += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? 0 : abiSize);
				}
				totalAr += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? 0 : abiSize);
				if (totalFn != totalAr) {
					dif = true;
				}
			}
		}
		for (i++; i < args.length; i++) {
			l = args[i].type.type.size();
			if (i > 5) {
				pur += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? 0 : abiSize);
			}
			totalAr += ((l / abiSize) * abiSize) + (((l % abiSize) == 0) ? 0 : abiSize);
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
					Util.warn("Function call provides more bits of data than specified by the function argument(s)");
				}
				else {//TODO don't display this when calling using the SystemV AMD64 ABI
					StringBuilder sb = new StringBuilder();
					sb.append("Raw conversion from provided argument type(s) (");
					for (i = 0; i < args.length; i++) {
						sb.append(args[i].type.toString());
						if (i < (args.length - 1)) {
							sb.append(", ");
						}
					}
					sb.append(") to specified argument type(s) (");
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
					if (args[i].type.type != t.type) {
						Util.warn("Raw conversion from provided argument type " + args[i].type.toString() + " to specified function argument type " + t.toString());
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
	static void args16_16(int amnt, Value[] vals) throws InternalCompilerException, CompilationException {//argument pushing for calls from 16-bit code using the 16-bit ABI or the 32-bit ABI (the 32-bit ABI is the System V ABI for Intel386 and the 16-bit ABI is the same thing but with 16-bit calls instead of 32-bit calls and %bx can be scratched in functions, the preservation of the upper half of %ebx, should it exist, is left undefined, and data is returned from functions, from lowest to highest word, in %ax, %dx, %cx, and %bx)
		FullType typ;
		for (int i = (amnt - 1); i >= 0; i--) {//TODO allow more than Integer.MAX_VALUE arguments to be used
			typ = vals[i].bring();
			switch (typ.type.size()) {
				case (8):
					Compiler.text.println("xorb %ah,%ah");//Not `movzbw %al,%ax' because movz doesn't exist on the 8086
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
interface Typed {//EVERY Typed MUST have its `size()' result in a positive int below 65536 that is evenly divisible by 8
	int size() throws InternalCompilerException;
	boolean addressable();
	boolean floating() throws InternalCompilerException;
	boolean signed() throws InternalCompilerException;//only applicable for integral types
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
	int alignmentBits() throws InternalCompilerException;
}
class StructuredType implements Typed {//TODO fix `size()' returning 0 when the struct has no items conflicting with the need or the result of the call to be positive
	Structure struct;
	public int size() throws InternalCompilerException {
		if (!(struct.fieldsFinalised())) {
			throw new InternalCompilerException("Usage of the length of the type of a non-field-finalised structure");
		}
		return (int) (struct.length * 8L);//TODO maybe fix the need for casting; structs longer than the int max value are not supported in this manner
	}
	public boolean addressable() {
		return false;
	}
	StructuredType(Structure st) throws InternalCompilerException {
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
	public boolean signed() throws InternalCompilerException {
		throw new InternalCompilerException("Requested boolean information is not applicable for a structured type");
	}
	public int alignmentBits() throws InternalCompilerException {
		if (!(struct.fieldsFinalised())) {
			throw new InternalCompilerException("Usage of the alignment of the type of a non-field-finalised structure");
		}
		return struct.alignmentBytes * 8;
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
	public boolean signed() {
		return signed;
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
				Compiler.text.println("movb %al,%ah");
				Compiler.text.println("pushw %ax");
				Compiler.text.println("incw %sp");
				return;
			case (9):
				Compiler.text.println("decl %esp");
				Compiler.text.println("movb %al,(%esp)");
				return;
			case (10):
				Compiler.text.println("decq %rsp");
				Compiler.text.println("movb %al,(%rsp)");
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
				Compiler.text.println("subq $4,%rsp");
				Compiler.text.println("movl %eax,(%rsp)");
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
			case (8)://TODO maybe don't leave garbage in %ah and change the message in the comment(s) which refer(s) to this behaviour
				Compiler.text.println("decw %sp");
				Compiler.text.println("popw %ax");
				Compiler.text.println("movb %ah,%al");
				return;
			case (9):
				Compiler.text.println("movb (%esp),%al");
				Compiler.text.println("incl %esp");
				return;
			case (10):
				Compiler.text.println("movb (%rsp),%al");
				Compiler.text.println("incq %rsp");
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
				Compiler.text.println("movl (%rsp),%eax");
				Compiler.text.println("addq $4,%rsp");
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
				Compiler.text.println("decw %sp");
				Compiler.text.println("popw %ax");
				Compiler.text.println("movb %ah,(%bx)");
				return;
			case (9):
				Compiler.text.println("movb (%esp),%bl");
				Compiler.text.println("incl %esp");
				Compiler.text.println("movb %bl,(%eax)");
				return;
			case (10):
				Compiler.text.println("movb (%rsp),%bl");
				Compiler.text.println("incq %rsp");
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
				Compiler.text.println("andl %eax,%eax");
			case (66):
				return;
			default:
				throw new InternalCompilerException("Unidentifiable or disallowed operand size and / or unidentifiable target");
		}
	}
	public int alignmentBits() {
		return size();
	}
}
abstract class Item {
}
interface Compilable {
	public void compile() throws CompilationException, InternalCompilerException, IOException;
}
interface Doable extends Compilable {
}
interface Stacked {//TODO have blocks have references to their functions
	int abiSize();
	FullType retType();
	long adjust(long n);
	long minOff();
	long spos();
	long curOff();
	void update(long n);
	void label(String nam, Label lbl);
	Function assoc();
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
		assoc.compile();
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
	static Str from() throws CompilationException, InternalCompilerException, IOException {//starts reading from directly after the opening doublequote
		int i;
		boolean sl = false;
		StringBuilder sb = new StringBuilder();
		while (true) {
			i = Util.read();
			if ((i == '\"') && (!(sl))) {
				return new Str(sb.toString());
			}
			sl = i == '\\';
			if ((i == '\n') || (i == '\r')) {
				throw new CompilationException("Unescaped newline characters in string literal notation");
			}
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
	final boolean looped;
	Block(Stacked p, Function a, boolean loop) {
		parent = p;
		assoc = a;
		spos = minOff = bpOff = p.minOff();
		looped = loop;
	}
	static Block from(Stacked f) throws CompilationException, InternalCompilerException, IOException {
		return from(f, false);
	}
	static Block from(Stacked f, boolean loop) throws CompilationException, InternalCompilerException, IOException {
		ArrayList<Compilable> c = new ArrayList<Compilable>();
		Compiler.context.push(new TreeMap<String, StackVar>());
		Block b = new Block(f, (f instanceof Function) ? ((Function) f) : ((Block) f).assoc, loop);
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
		else if (mo == (-1)) {
			switch (assoc.abiSize) {
				case (16):
					Compiler.text.println("decw %sp");
					break;
				case (32):
					Compiler.text.println("decl %esp");
					break;
				case (64):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException("Unidentifiable target");
			}
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
		update(mo);
		for (Doable cpl : comps) {
			cpl.compile();
		}
		update(-mo);
		if (mo == (-1)) {
			switch (assoc.abiSize) {
				case (16):
					Compiler.text.println("incw %sp");
					break;
				case (32):
					Compiler.text.println("incl %esp");
					break;
				case (64):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException("Unidentifiable target");
			}
		}
		else if (mo < 0) {
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
	public long curOff() {
		return assoc.curOff();
	}
	public void update(long l) {
		assoc.update(l);
	}
	public void label(String nam, Label lbl) {
		assoc.label(nam, lbl);
	}
	public Function assoc() {
		return assoc;
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
		if ((type.type.size() % 8L) != 0) {
			throw new InternalCompilerException("Non-byte-aligned size");
		}
		long b = type.type.size() / 8L;
		if (b != 0) {
			Compiler.rwdata.print(".byte");
			for (; b != 0; b--) {
				Compiler.rwdata.print(" 0x00");
				if (b != 1) {
					Compiler.rwdata.print(" ,");
				}
			}
			Compiler.rwdata.println();
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
		}
		else {
			Compiler.rwdatback.write(dat.array(), i = dat.arrayOffset(), i + dat.position());
		}
	}
}
class Literal extends Value {
	final long val;//Non-conforming places must hold zero
	Literal(FullType typ, long vlu) throws InternalCompilerException {
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
				Util.zx(from.type.size(), to.type.size());
			}
		}
		else {
			from.cast(to);
		}
		return to;
	}
}
class DotOperator extends Operator {
	int structAlign;//in bytes
	FullType field;//null if a function
	long offset;
	String funcName;
	DotOperator(int stAlgn, FullType fld, long off, String fn) {
		super(true, '.');
		structAlign = stAlgn;
		field = fld;
		offset = off;
		funcName = fn;
	}
	static DotOperator from(Structure st) throws CompilationException, InternalCompilerException, IOException {
		String s = Util.phrase(0x3d);
		StructEntry g = st.fields.get(s);
		if (g == null) {
			Function f = st.funcs.get(s);
			if (f == null) {
				throw new CompilationException("Unknown field or function name following dot operator for class " + st.name + ": " + s);
			}
			return new DotOperator(st.alignmentBytes, null, 0, "___structfunc__" + Util.escape(new String[]{st.name, s}));
		}
		return new DotOperator(st.alignmentBytes, g.type, g.offset, null);
	}
	FullType apply(FullType typ) throws NotImplementedException, SizeNotFitException, InternalCompilerException {
		if (field == null) {
			throw new NotImplementedException();
		}
		if (offset == 0) {
			return new FullType(typ.type, field);
		}
		if (offset == 1) {
			switch (typ.type.size()) {
				case (16):
					Compiler.text.println("incw %ax");
					break;
				case (32):
					if (Compiler.mach < 1) {
						throw new NotImplementedException();
					}
					Compiler.text.println("incl %eax");
					break;
				case (64):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException();
			}
			return new FullType(typ.type, field);
		}
		switch (typ.type.size()) {
			case (16):
				Compiler.text.print("addw $");
				Compiler.text.print(Util.signedRestrict(offset, 16));
				Compiler.text.println(",%ax");
				break;
			case (32):
				if (Compiler.mach < 1) {
					throw new NotImplementedException();
				}
				Compiler.text.print("addl $");
				Compiler.text.print(Util.signedRestrict(offset, 33));
				Compiler.text.println(",%eax");
				break;
			case (64):
				throw new NotImplementedException();
			default:
				throw new InternalCompilerException();
		}
		return new FullType(typ.type, field);
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
	static final Operator MOD = new Operator(false, '%');//("%") Arithmetic modulo
	static final Operator EQ = new Operator(false, '=');//("==") Equality
	static final Operator GTEQ = new Operator(false, 'g');//(">=") Greater than or equal to
	static final Operator LTEQ = new Operator(false, 'l');//("<=") Less than or equal to
	static final Operator LNEG = new Operator(true, '!');//("!") Logical negation
	static final Operator NEQ = new Operator(false, 'N');//("!=") Inequality
	static final Operator MSHL = new Operator(false, '(');//("<<|") Bit-wise zero-filling left shift
	static String[] eops = new String[]{"add", "sub", "cmp", "and", "or", "xor"};
	static String[] cond = new String[]{"z", "nz", "a", "ae", "b", "be", "z", "nz", "g", "ge", "l", "le"};
	static boolean[] condz = new boolean[]{true, true, true, false, false, true, true, true, true, false, false, true};
	static String[] rsh = new String[]{"shr", "sar"};
	static final Operator CMP = new Operator(false, 'C');// Comparison, for internal use
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
	FullType apply(FullType LHO, Value RHO) throws CompilationException, InternalCompilerException {
		return apply(LHO, RHO, 0);
	}
	FullType apply(FullType LHO, Value RHO, int condNum) throws CompilationException, InternalCompilerException {//Binary; The LHO has already been brought
		if ((LHO.type instanceof StructuredType) || (RHO.type.type instanceof StructuredType)) {
			throw new CompilationException("Usage of primitive operators with a structured type");
		}
		if (unary) {
			throw new InternalCompilerException("Not a binary operator: " + this.toString());
		}
		FullType RHtyp = RHO.type;
		int fn = 0;
			switch (id) {
			case ('^'):
				fn++;
			case ('|'):
				fn++;
			case ('&'):
				fn++;
			case ('C'):
				fn++;
			case ('-'):
				fn++;
			case ('+'):
				if (LHO.type.floating() || RHO.type.type.floating()) {
					throw new NotImplementedException();
				}
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
										Compiler.text.println("pushw %dx");
										Compiler.text.println("pushw %ax");
										RHO.bring();
										Compiler.text.println("movw %ax,%cx");
										Compiler.text.println("movw %dx,%bx");
										Compiler.text.println("popw %ax");
										Compiler.text.println("popw %dx");
										Compiler.text.print(eops[fn]);
										Compiler.text.println("w %cx,%ax");
										if (fn == 0) {
											Compiler.text.println("adcw %bx,%dx");
										}
										else if (fn == 1) {
											Compiler.text.println("sbcw %bx,%dx");
										}
										else if (fn == 2) {
											if (condz[condNum]) {
												Compiler.text.println("setnz %cl");
												Compiler.text.println("shlw $6,%cx");
												Compiler.text.println("notw %cx");
											}
											Compiler.text.println("sbcw %bx,%dx");
											if (condz[condNum]) {
												Compiler.text.println("pushf");
												Compiler.text.println("popw %bx");
												Compiler.text.println("andw %cx,%bx");
												Compiler.text.println("pushw %bx");
												Compiler.text.println("popf");
											}
										}
										else {
											Compiler.text.print(eops[fn]);
											Compiler.text.println("w %bx,%dx");
										}
										break;
									case (1):
										Compiler.text.println("pushl %eax");//TODO prevent the need for this move by bring()-ing directly to %bx and preserving %ax (unless it's significantly slower than using the accumulator %ax or it's impossible not to use %ax), in which cases the called function might warn this function that it would be left in %ax)
										RHO.bring();
										Compiler.text.println("movl %eax,%ebx");
										Compiler.text.println("popl %eax");
										Compiler.text.print(eops[fn]);
										Compiler.text.println("l %ebx,%eax");//TODO perform to %bx and then notify the caller that it was left in %bx, unless it's significantly slower than using the accumulator
										break;
									case (2):
										throw new NotImplementedException();
									default:
										throw new InternalCompilerException("Unidentifiable target");
								}
								if ((LHO.type == Type.a32) || (RHtyp.type == Type.a32)) {
									if (LHO.type == RHtyp.type) {
										return FullType.a32;
									}
									if ((LHO.gives == null) != (LHO.runsWith == null)) {
										return LHO;
									}
									if ((RHtyp.gives == null) != (RHtyp.runsWith == null)) {
										return RHtyp;
									}
									return FullType.a32;
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
								Compiler.text.println("movw %ax,%bx");
								Compiler.text.println("popw %ax");
								Compiler.text.print(eops[fn]);
								Compiler.text.println("w %bx,%ax");//TODO perform to %bx and then notify the caller that it was left in %bx, unless it's significantly slower than using the accumulator
								if ((LHO.type == Type.a16) || (RHtyp.type == Type.a16)) {
									if (LHO.type == RHtyp.type) {
										return FullType.a16;
									}
									if ((LHO.gives == null) != (LHO.runsWith == null)) {
										return LHO;
									}
									if ((RHtyp.gives == null) != (RHtyp.runsWith == null)) {
										return RHtyp;
									}
									return FullType.a16;
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
						switch (RHtyp.type.size()) {
							case (64):
							case (32):
							case (16):
								LHO.cast(RHtyp);
								return this.apply(RHtyp, RHO);
							case (8):
								LHO.type.pushMain();
								RHO.bring();
								Compiler.text.println("movb %al,%dl");// not to %ah because the byte popping leaves garbage in %ah
								LHO.type.popMain();
								Compiler.text.print(eops[fn]);
								Compiler.text.println("b %dl,%al");
								return (LHO.type.signed() || RHO.type.type.signed()) ? FullType.s8 : FullType.u8;
							default:
								throw new InternalCompilerException("Illegal datum size");
					}
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
			case ('%'):
				fn++;
			case ('/'):
				fn++;
			case ('*'):
				if ((LHO.type.floating()) || (RHO.type.type.floating())) {
					throw new NotImplementedException();
				}
				switch (LHO.type.size()) {
					case (8):
						LHO.type.pushMain();
						RHO.bring();
						if (LHO.type.signed()) {
							RHO.type.cast(FullType.s8);
						}
						else {
							RHO.type.cast(FullType.u8);
						}
						Compiler.text.println("movb %al,%dl");//not to %ah because the byte popping puts garbage in %ah
						LHO.type.popMain();
						switch (fn) {
							case (0):
								if (LHO.type.signed()) {
									Compiler.text.println("imulb %dl");
									return LHO;//TODO allow larger type to be gotten
								}
								else {
									Compiler.text.println("mulb %dl");
									return LHO;//TODO allow larger type to be gotten
								}
							case (1):
							case (2):
								Compiler.text.println("xorb %ah,%ah");
								if (LHO.type.signed()) {
									Compiler.text.println("idivb %dl");
									if (fn == 2) {
										Compiler.text.println("movb %ah,%al");
									}
									return LHO;
								}
								else {
									Compiler.text.println("divb %dl");
									if (fn == 2) {
										Compiler.text.println("movb %ah,%al");
									}
									return LHO;
								}
							default:
								throw new InternalCompilerException("Unknown operation");
						}
					case (16):
						LHO.type.pushMain();
						RHO.bring();
						if (LHO.type.signed()) {
							RHO.type.cast(FullType.s16);
						}
						else {
							RHO.type.cast(FullType.u16);
						}
						if (fn == 0) {
							Compiler.text.println("movw %ax,%dx");
						}
						else {
							Compiler.text.println("movw %ax,%cx");
							Compiler.text.println("xorw %dx,%dx");
						}
						LHO.type.popMain();
						switch (fn) {
							case (0):
								if (LHO.type.signed()) {
									Compiler.text.println("imulw %dx");
									return LHO;//TODO allow larger type to be gotten
								}
								else {
									Compiler.text.println("mulw %dx");
									return LHO;//TODO allow larger type to be gotten
								}
							case (1):
							case (2):
								if (LHO.type.signed()) {
									Compiler.text.println("idivw %cx");
									if (fn == 2) {
										Compiler.text.println("movw %dx,%ax");
									}
									return LHO;
								}
								else {
									Compiler.text.println("divw %cx");
									if (fn == 2) {
										Compiler.text.println("movw %dx,%ax");
									}
									return LHO;
								}
							default:
								throw new InternalCompilerException("Unknown operation");
						}
					case (32):
						if (Compiler.mach < 1) {
							LHO.type.pushMain();
							RHO.bring();
							if (LHO.type.signed()) {
								RHO.type.cast(FullType.s32);
							}
							else {
								RHO.type.cast(FullType.u32);
							}
							if (fn == 0) {
								Compiler.text.println("pushw %dx");
								Compiler.text.println("pushw %ax");
								Compiler.text.println("movw %sp,%bx");
								Compiler.text.println("movw 4(%bx),%dx");
								Compiler.text.println("mulw %dx");
								Compiler.text.println("pushw %ax");
								Compiler.text.println("movw %dx,%cx");
								Compiler.text.println("movw 6(%bx),%dx");
								Compiler.text.println("movw (%bx),%ax");
								Compiler.text.println("mulw %dx");
								Compiler.text.println("addw %ax,%cx");
								Compiler.text.println("movw 4(%bx),%dx");
								Compiler.text.println("movw 2(%bx),%ax");
								Compiler.text.println("mulw %dx");
								Compiler.text.println("addw %ax,%cx");
								Compiler.text.println("movw %cx,%dx");
								Compiler.text.println("popw %ax");
								Compiler.text.println("addw $0x08,%sp");
								return LHO;
							}
							throw new NotImplementedException();
						}
						LHO.type.pushMain();
						RHO.bring();
						if (LHO.type.signed()) {
							RHO.type.cast(FullType.s32);
						}
						else {
							RHO.type.cast(FullType.u32);
						}
						if (fn == 0) {
							Compiler.text.println("movl %eax,%edx");
						}
						else {
							Compiler.text.println("movl %eax,%ecx");
							Compiler.text.println("xorl %edx,%edx");
						}
						LHO.type.popMain();
						switch (fn) {
							case (0):
								if (LHO.type.signed()) {
									Compiler.text.println("imull %edx");
									return LHO;//TODO allow larger type to be gotten
								}
								else {
									Compiler.text.println("mull %edx");
									return LHO;//TODO allow larger type to be gotten
								}
							case (1):
							case (2):
								if (LHO.type.signed()) {
									Compiler.text.println("idivl %ecx");
									if (fn == 2) {
										Compiler.text.println("movl %edx,%eax");
									}
									return LHO;
								}
								else {
									Compiler.text.println("divl %ecx");
									if (fn == 2) {
										Compiler.text.println("movl %edx,%eax");
									}
									return LHO;
								}
							default:
								throw new InternalCompilerException("Unknown operation");
						}
					case (64):
						if (Compiler.mach < 2) {
							throw new NotImplementedException();
						}
						LHO.type.pushMain();
						RHO.bring();
						if (LHO.type.signed()) {
							RHO.type.cast(FullType.s32);
						}
						else {
							RHO.type.cast(FullType.u32);
						}
						if (fn == 0) {
							Compiler.text.println("movq %rax,%rdx");
						}
						else {
							Compiler.text.println("movq %rax,%rcx");
							Compiler.text.println("xorq %rdx,%rdx");
						}
						LHO.type.popMain();
						switch (fn) {
							case (0):
								if (LHO.type.signed()) {
									Compiler.text.println("imulq %rdx");
									return LHO;//TODO allow larger type to be gotten
								}
								else {
									Compiler.text.println("mulq %rdx");
									return LHO;//TODO allow larger type to be gotten
								}
							case (1):
							case (2):
								if (LHO.type.signed()) {
									Compiler.text.println("idivq %rcx");
									if (fn == 2) {
										Compiler.text.println("movq %rdx,%rax");
									}
									return LHO;
								}
								else {
									Compiler.text.println("divq %rcx");
									if (fn == 2) {
										Compiler.text.println("movq %rdx,%rax");
									}
									return LHO;
								}
							default:
								throw new InternalCompilerException("Unknown operation");
						}
					default:
						throw new NotImplementedException();
				}
			case ('l'):
				fn++;
			case ('<'):
				fn++;
			case ('g'):
				fn++;
			case ('>'):
				fn++;
			case ('N'):
				fn++;
			case ('='):
				if (LHO.type.signed() && RHO.type.type.signed()) {//TODO proper casting when different sizes
					fn += 6;
				}
				CMP.apply(LHO, RHO);
				Compiler.text.print("set");
				Compiler.text.print(cond[fn]);
				Compiler.text.println(" %al");
				return FullType.u8;
			case (')'):
				fn++;
			case (']'):
				LHO.type.pushMain();
				RHO.bring();
				Compiler.text.println("movb %al,%cl");
				LHO.type.popMain();
				switch (LHO.type.size()) {
					case (8):
						Compiler.text.print(rsh[fn]);
						Compiler.text.println("b %cl,%al");
						break;
					case (16):
						Compiler.text.print(rsh[fn]);
						Compiler.text.println("w %cl,%ax");
						break;
					case (32):
						if (Compiler.mach < 1) {
							throw new NotImplementedException();//no shrd on CPUs before the 80386
						}
						else {//else since the NotImplementedException will be replaced with an implementation
							Compiler.text.print(rsh[fn]);
							Compiler.text.print("l %cl,%eax");
						}
						break;
					case (64):
						if (Compiler.mach < 1) {
							throw new NotImplementedException();//no shrd on CPUs before the 80386
						}
						else if (Compiler.mach < 2){//else since the NotImplementedException will be replaced with an implementation
							Compiler.text.println("shrdl %cl,%ebx,%eax");
							Compiler.text.print(rsh[fn]);
							Compiler.text.println("l %cl,%ebx");
						}
						else {
							Compiler.text.print(rsh[fn]);
							Compiler.text.println("q %cl,%rax");
						}
						break;
					default:
						throw new InternalCompilerException();
				}
				return LHO;
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
	final boolean ref;
	NoScopeVar(String nam, FullType typ) {
		name = nam;
		type = typ;
		ref = false;
	}
	NoScopeVar(NoScopeVar based, boolean refer) throws InternalCompilerException {
		name = based.name;
		if (refer) {
			type = new FullType(Compiler.defAdr, based.type);
		}
		else {
			type = based.type;
		}
		ref = refer;
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
		if (ref) {
			switch (type.type.size()) {
				case (16):
					Compiler.text.println("movw $" + name + ",%ax");
					break;
				case (32):
					Compiler.text.println("movl $" + name + ",%eax");
					break;
				case (64):
					Compiler.text.println("movq $" + name + ",%rax");
					break;	
				default:
					throw new InternalCompilerException("Illegal datum size for global variable reference");
			}
			return type;
		}
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
	final long pos;//Offset from base pointer of calling convention
	final boolean ref;
	StackVar(long p, FullType typ) {
		pos = p;
		type = typ;
		ref = false;
	}
	StackVar(StackVar based, boolean refer) throws InternalCompilerException {
		pos = based.pos;
		if (refer) {
			type = new FullType(Compiler.defAdr, based.type);
		}
		else {
			type = based.type;
		}
		ref = refer;
	}
	public FullType type() {
		return type;
	}
	public FullType bring() throws InternalCompilerException, SizeNotFitException {
		if (ref) {
			switch (type.type.size()) {
				case (16):
					Compiler.text.println("leaw " + Util.signedRestrict(pos, 16) + "(%bp),%ax");
					break;
				case (32):
					Compiler.text.println("leal " + Util.signedRestrict(pos, 33) + "(%ebp),%eax");
					if (Compiler.mach < 1) {
						Compiler.text.println("rorl 16,%eax");
						Compiler.text.println("movw %ax,%dx");
						Compiler.text.println("rorl 16,%eax");
					}
					break;
				case (64):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException("Illegal datum size for stack variable reference");
			}
			return type;
		}
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
				throw new InternalCompilerException("Unidentifiable target");
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
				throw new InternalCompilerException("Unidentifiable target");
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
	static Expression from(Item[] ites) throws CompilationException, InternalCompilerException {
		Expression en = new Expression();
		for (Item ite : ites) {
			if (ite == null) {
				continue;
			}
			en.items.add(ite);
		}
		en.finalised = true;
		PrintStream pstemp = Compiler.text;
		PrintStream rwdtemp = Compiler.rwdata;
		Compiler.rwdata = Compiler.text = Compiler.nowhere;
		en.type = en.bring();//TODO maybe un-bodge
		Compiler.text = pstemp;
		Compiler.rwdata = rwdtemp;
		return en;
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
		boolean cont;
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
			cont = false;
			if (tg == '\"') {
				ex.add(last = Str.from());
				continue;
			}
			else if ((tg == ending) || (tg == ending2)) {
				if (ex.items.isEmpty()) {
					throw new CompilationException("Empty expression");
				}
				ex.finalised = true;
				PrintStream pstemp = Compiler.text;
				PrintStream rwdtemp = Compiler.rwdata;
				Compiler.rwdata = Compiler.text = Compiler.nowhere;
				ex.type = ex.bring();//TODO maybe un-bodge
				Compiler.text = pstemp;
				Compiler.rwdata = rwdtemp;
				ex.auxEnding = tg != ending;
				return ex;
			}
			else if (tg == '%') {
				if ((tg = Util.read()) == '[') {
					Util.skipWhite();
					String s = Util.phrase(0x2f);
					switch (s) {
						case ("size"):
							Util.skipWhite();
							ex.add(last = new Literal(FullType.u16, FullType.from().type.size()));
							Util.skipWhite();
							if ((tg = Util.read()) != ']') {
								throw new CompilationException("Unexpected operator: " + new String(new int[]{tg}, 0, 1));
							}
							continue;
						case ("prop")://the properties are compiler-dependent but the existence of the `prop' evaluator command is mandated
							Util.skipWhite();
							String p = Util.phrase(0x2f);
							long l;//bits higher than the lowest 16 bits must not be set
							switch (p) {//TODO use a mapping between property names and values
								case ("ver_major"):
									l = Compiler.ver_major;
									break;
								case ("ver_minor"):
									l = Compiler.ver_minor;
									break;
								case ("ver_inframinor"):
									l = Compiler.ver_inframinor;
									break;
								case ("ver_subinframinor"):
									l = Compiler.ver_subinframinor;
									break;
								default:
									throw new CompilationException("Unknown property: " + p);
							}
							ex.add(last = new Literal(FullType.u16, l));
							Util.skipWhite();
							if ((tg = Util.read()) != ']') {
								throw new CompilationException("Unexpected operator: " + new String(new int[]{tg}, 0, 1));
							}
							continue;
						default://TODO allow plug-ins to take advantage of this if / when plug-ins are supported
							throw new CompilationException("Evaluator command not found: " + s);
					}
				}
				else {
					Util.unread(tg);
					tg = '%';
					cont = true;
				}
			}
			else {
				cont = true;
			}
			if (cont) {
				Util.unread(tg);
				String s = Util.phrase(0x2f);//do NOT change from 0x2f without updating the delimiter likewise for the cast / conversion chain checking for when performing a cast / conversion
				Literal lit;
				if (Util.nulitk.contains(s)) {
					ex.add(last = Compiler.nul);
					continue;
				}
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
						if (!(last instanceof Value)) {
							throw new CompilationException(raw ? "Raw conversion of a non-value" : "Casting of a non-value");
						}
						Util.skipWhite();
						FullType fto = FullType.from();
						/*
						Util.skipWhite();
						int ir = Util.read();
						if (ir == '(') {
							Util.unread(ir);
						*/
						Value ra = (Value) last;
						ex.skim(ra);
						Expression en = new Expression();
						en.add(ra);
						en.add(new Casting(raw, ra.type, fto));
						en.finalised = true;
						en.type = fto;
						ex.add(last = en);
						/*
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
						*/
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
							Expression ej;
							switch (i) {
								case ('['):
									if (!(last instanceof Value)) {
										throw new CompilationException("Attempt to use the indexing operator on a non-value");
									}
									if (!(((Value) last).type.type.addressable())) {
										throw new CompilationException("Attempt to use the indexing operator on a non-addressable type");
									}
									ej = Expression.from(']');
									ej = Expression.from(new Item[]{last, Operator.ADD, Expression.from(new Item[]{Expression.from(new Item[]{ej, new Casting(false, ej.type, FullType.of(Compiler.defOff))}), (((((Value) last).type.gives == null) == (((Value) last).type.runsWith == null)) && (((Value) last).type.gives.type.size() != 8)) ? null : Operator.MUL, (((((Value) last).type.gives == null) == (((Value) last).type.runsWith == null)) && (((Value) last).type.gives.type.size() != 8)) ? null : (new Literal(FullType.of(Compiler.defOff), (long) (((((Value) last).type.gives == null) == (((Value) last).type.runsWith == null)) ? 1 : (((Value) last).type.gives.type.size() / 8))))})});
									ex.skim(last);
									ex.add(last = ej);
									break;
								case ('&'):
									ex.add(last = Operator.AND);
									break;
								case ('|'):
									ex.add(last = Operator.OR);
									break;
								case ('^'):
									ex.add(last = Operator.XOR);
									break;
								case ('.'):
									if (!(last instanceof Value)) {
										throw new CompilationException("Attempt to use the dot operator on a non-value");
									}
									if (!(((Value) last).type.type.addressable())) {
										throw new CompilationException("Attempt to use the dot operator on a non-addressable type");
									}
									if ((((Value) last).type.gives == null) || (((Value) last).type.runsWith != null)) {
										throw new CompilationException("Attempt to use the dot operator on an address that does not have a pointing clause");
									}
									if (!(((Value) last).type.gives.type instanceof StructuredType)) {
										throw new CompilationException("Attempt to use the dot operator on an address with a pointing clause for a non-structural type");
									}
									Expression en = new Expression();
									en.add(last);
									DotOperator dop = DotOperator.from(((StructuredType) (((Value) last).type.gives.type)).struct);
									en.add(dop);
									en.finalised = true;
									en.type = new FullType(((Value) last).type.type, dop.field);
									ex.skim(last);
									ex.add(last = en);
									break;
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
									ej = Expression.from(new Item[]{last, Operator.GET});
									ex.skim(last);
									ex.add(last = ej);
									break;
								case ('$'):
									if (last instanceof NoScopeVar) {
										if (((NoScopeVar) last).ref) {
											throw new CompilationException("Attampted referencing of an already-referenced global variable expression item");
										}
										ex.skim(last);
										ex.add(last = new NoScopeVar((NoScopeVar) last, true));
									}
									else if (last instanceof StackVar) {
										if (((StackVar) last).ref) {
											throw new CompilationException("Attampted referencing of an already-referenced stack variable expression item");
										}
										ex.skim(last);
										ex.add(last = new StackVar((StackVar) last, true));
									}
									else {
										throw new CompilationException("Attampted referencing of an expression item which is neither a global variable nor a stack variable");
									}
									break;
								case ('='):
									if ((i = Util.read()) == '=') {
										ex.add(last = Operator.EQ);
									}
									else {
										throw new NotImplementedException();
									}
									break;
								case ('>'):
									if ((i = Util.read()) == '=') {
										ex.add(last = Operator.GTEQ);
									}
									else if (i == '>') {
										if ((i = Util.read()) == '>') {
											ex.add(last = Operator.ROR);
										}
										else if (i == '|') {
											ex.add(last = Operator.MSHR);
										}
										else {
											Util.unread(i);
											ex.add(last = Operator.SHR);
										}
									}
									else {
										Util.unread(i);
										ex.add(last = Operator.GT);
									}
									break;
								case ('<'):
									if ((i = Util.read()) == '=') {
										ex.add(last = Operator.LTEQ);
									}
									else {
										Util.unread(i);
										ex.add(last = Operator.LT);
									}
									break;
								case ('!'):
									if ((i = Util.read()) == '=') {
										ex.add(last = Operator.NEQ);
									}
									else {
										Util.unread(i);
										ej = Expression.from(new Item[]{last, Operator.LNEG});
										ex.skim(last);
										ex.add(last = ej);
									}
									break;
								case ('~'):
									ej = Expression.from(new Item[]{last, Operator.BNEG});
									ex.skim(last);
									ex.add(last = ej);
									break;
								case ('%'):
									ex.add(last = Operator.MOD);
									break;
								default:
									throw new NotImplementedException("Not-yet-implemented or illegal operator");
							}
						}
					}
				}
			}
		}
	}
}
class Label implements Doable {
	final String symb;
	long bpOffset;
	transient boolean valid;//what this was supposed to be for was forgotten
	public boolean resolved;
	final Stacked parent;
	Label(Stacked par) {
		symb = Util.reserve();
		parent = par;
		resolved = false;
	}
	public void compile() {
		bpOffset = parent.curOff();
		resolved = true;
		Compiler.text.print(symb);
		Compiler.text.println(':');
	}
}
class Jump implements Doable {
	String destName;
	Stacked parent;
	Jump(String nam, Stacked par) {
		destName = nam;
		parent = par;
	}
	public void compile() throws CompilationException, InternalCompilerException {
		Label l = parent.assoc().labels.get(destName);
		if (l == null) {
			throw new CompilationException("Jumping statement attempts to jump to a named label which does not exist in the funciton under the specified name: " + destName);
		}
		if (!(l.resolved)) {
			throw new NotImplementedException("Jumping statement attempts to jump to a label yet-unresolved base pointer offset");
		}
		long n = parent.curOff() - l.bpOffset;
		if (n > 0) {
			switch (Compiler.mach) {
				case (0):
					Compiler.text.print("subw $");
					Compiler.text.print(Util.signedRestrict(n, 16));
					Compiler.text.println(",%sp");
					break;
				case (1):
					Compiler.text.print("subl $");
					Compiler.text.print(Util.signedRestrict(n, 33));
					Compiler.text.println(",%esp");
					break;
				case (2):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException("Unidentifiable target");
			}
		}
		else if (n != 0) {
			switch (Compiler.mach) {
				case (0):
					Compiler.text.print("addw $");
					Compiler.text.print(Util.signedRestrict(-n, 16));
					Compiler.text.println(",%sp");
					break;
				case (1):
					Compiler.text.print("addl $");
					Compiler.text.print(Util.signedRestrict(-n, 33));
					Compiler.text.println(",%esp");
					break;
				case (2):
					throw new NotImplementedException();
				default:
					throw new InternalCompilerException("Unidentifiable target");
			}
		}
		Compiler.text.print("jmp ");
		Compiler.text.println(l.symb);
	}
}
class InternalCompilerException extends Exception {
	InternalCompilerException() {
		super();
	}
	InternalCompilerException(String reas) {
		super(reas);
	}
}
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
class NondefinitionException extends CompilationException {
	NondefinitionException(String reas) {
		super(reas);
	}
}
class NotImplementedException extends InternalCompilerException {
	NotImplementedException() {
		super();
	}
	NotImplementedException(String reas) {
		super(reas);
	}
}
class UnidentifiableTypeException extends CompilationException {
	final String verbatim;
	UnidentifiableTypeException(String s) {
		super("Unidentifiable type: " + s);
		verbatim = s;
	}
}
class BlockEndException extends CompilationException {
	BlockEndException() {
		super();
	}
	BlockEndException(String reas) {
		super(reas);
	}
}
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
