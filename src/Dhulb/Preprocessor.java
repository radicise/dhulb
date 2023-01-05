package Dhulb;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
public class Preprocessor {// takes file stream and the directory path where the files are stored and from which relative paths should be constructed
    public static void processData (InputStream data, Path cwd, OutputStream out) throws Exception {
        InputStreamReader inreader = new InputStreamReader(data, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(inreader);
        int cbyte = reader.read();
        boolean test = true;
        while (cbyte != -1) {
            if (test && cbyte == '#') {
                String[] line = reader.readLine().split("[\\s]");
                System.out.println(line[0]);
            }
            if (!Character.isWhitespace(cbyte)) {
                test = false;
            }
            if (cbyte == '\n' || cbyte == '\r') {
                test = true;
            }
            cbyte = reader.read();
        }
    }
}
