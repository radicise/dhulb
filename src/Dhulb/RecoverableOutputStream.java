package Dhulb;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class RecoverableOutputStream extends OutputStream {
    public byte[] data;
    public int wpos;
    public RecoverableOutputStream () {
        data = new byte[50];
        wpos = 0;
    }
    public void write (int b) throws IOException {
        if (wpos == data.length) {
            data = Arrays.copyOf(data, data.length*2);
        }
        data[wpos++] = (byte) b;
    }
}