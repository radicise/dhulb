package Dhulb.Exceptions;

@SuppressWarnings("serial")
public class SymbolUndefinedException extends CompilationException {
	public SymbolUndefinedException(String reas) {
		super(reas);
	}
}