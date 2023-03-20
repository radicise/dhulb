package DExt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

public class DhulbArray {
    private static String types = "(u32|u64)";
    public static void dothing (InputStream inp, OutputStream out) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(inp));
        PrintStream output = new PrintStream(out);
        for (Object lineobj : input.lines().toArray()) {
            String line = lineobj.toString();
            if (line.matches(".*"+types+"\\[].*")) {
                line = line.replaceAll("\\b(?="+types+"\\[])", "Array<");
                // line = "Array<" + line;
                line = line.replaceAll("(?<=\\b"+types+")\\[]", ">");
            }
            output.println(line);
        }
    }
}
