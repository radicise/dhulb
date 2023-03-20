package Dhulb;

import java.io.IOException;
import java.io.OutputStream;
// import java.io.PrintStream;
import java.util.Arrays;

public class RecoverableOutputStream extends OutputStream {
    public byte[] data;
    public int wpos;
    // public PrintStream dbglog;
    // private int lwrit = -1;
    // private long scount = 0;
    // public RecoverableOutputStream (PrintStream dbg) {
    public RecoverableOutputStream () {
        data = new byte[50];
        wpos = 0;
        // dbglog = dbg;
    }
    public void write (int b) throws IOException {
        // if (b == lwrit) {
        //     scount ++;
        // } else {
        //     dbglog.println("RECOUTPUTSTRM: SWRITE: OINT="+lwrit+" NINT="+b+" COUNTWRIT="+scount);
        //     lwrit = b;
        //     scount = 0;
        // }
        if (wpos == data.length) {
            // dbglog.println("RECOUTPUTSTRM: RESIZE: OSIZE="+data.length+" NSIZE="+(data.length*2));
            data = Arrays.copyOf(data, data.length*2);
            // try {
            //     data = Arrays.copyOf(data, data.length*2);
            // } catch (OutOfMemoryError E) {
            //     dbglog.println(scount);
            //     throw E;
            // }
        }
        data[wpos++] = (byte) b;
    }
}