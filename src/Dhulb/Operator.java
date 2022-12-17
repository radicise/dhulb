package Dhulb;

import Dhulb.Exceptions.CompilationException;
import Dhulb.Exceptions.InternalCompilerException;
import Dhulb.Exceptions.NotImplementedException;

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