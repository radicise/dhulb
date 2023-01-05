package Dhulb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class Test {
    public static void main(String[] args) throws Exception {
        Preprocessor.processData(new FileInputStream(new File(args[0])), Path.of("."), OutputStream.nullOutputStream());
    }
}
// class Preprocessor {
//     // takes file stream and the directory path where the files are stored and from which relative paths should be constructed
//     public static void processData (InputStream data, Path cwd, OutputStream out) throws Exception {
//         InputStreamReader inreader = new InputStreamReader(data, StandardCharsets.UTF_8);
//         BufferedReader reader = new BufferedReader(inreader);
//         int cbyte = reader.read();
//         boolean test = true;
//         while (cbyte != -1) {
//             if (test && cbyte == '#') {
//                 String[] line = reader.readLine().split("[\\s]");
//                 System.out.println(line[0]);
//                 continue;
//             }
//             if (!Character.isWhitespace(cbyte)) {
//                 test = false;
//             }
//             if (cbyte == '\n' || cbyte == '\r') {
//                 test = true;
//             }
//             cbyte = reader.read();
//         }
//     }
// }
