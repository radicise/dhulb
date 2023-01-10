package DExt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

public class DhulbArray extends DhulbExtension {
    private static String types = "(u32|u64)";
    public static void main () throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        PrintStream output = System.out;
        System.out.println("HELLO");
        for (String line : (String[]) input.lines().toArray()) {
            if (line.matches(types+"\\[]")) {
                line = line.replaceAll("(?="+types+")\\[]", "Array<");
                line = line.replaceAll("(?<="+types+")\\[]", ">");
            }
            output.println(line);
        }
    }
}
