package Dhulb.Exceptions;

@SuppressWarnings("serial")
public class NotImplementedException extends InternalCompilerException {
	public NotImplementedException() {
		super();
	}
	public NotImplementedException(String reas) {
		super(reas);
	}
}