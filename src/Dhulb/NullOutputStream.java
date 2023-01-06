package Dhulb;
import java.io.IOException;
import java.io.OutputStream;
public class NullOutputStream extends OutputStream {
	NullOutputStream() {
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
