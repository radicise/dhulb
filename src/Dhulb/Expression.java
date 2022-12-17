package Dhulb;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import Dhulb.Exceptions.CompilationException;
import Dhulb.Exceptions.InternalCompilerException;
import Dhulb.Exceptions.NotImplementedException;
import Dhulb.Exceptions.SymbolUndefinedException;

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