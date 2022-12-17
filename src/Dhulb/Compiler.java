package Dhulb;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.TreeMap;

import Dhulb.Exceptions.CompilationException;
import Dhulb.Exceptions.InternalCompilerException;
import Dhulb.Exceptions.NotImplementedException;
import Dhulb.Exceptions.UnidentifiableTypeText;

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