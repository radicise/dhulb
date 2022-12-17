package Dhulb.Exceptions;

@SuppressWarnings("serial")
public class UnidentifiableTypeText extends Throwable {
	public final String verbatim;
	public UnidentifiableTypeText(String s) {
		super();
		verbatim = s;
	}
}