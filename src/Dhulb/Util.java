package Dhulb;

import java.io.EOFException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import Dhulb.Exceptions.InternalCompilerException;

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