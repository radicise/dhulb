package DExt;

// import java.io.BufferedReader;
import java.io.InputStream;
// import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;

public class DhulbStdRedirect extends DhulbExtension {
    private static HashMap<String, HashMap<String, String>> structRedirectMap = new HashMap<>();
    public static void dothing (InputStream inp, OutputStream out) throws Exception {
        // BufferedReader input = new BufferedReader(new InputStreamReader(inp));
        PrintStream output = new PrintStream(out);
        String content = new String(inp.readNBytes(inp.available()));
        int clength = content.length();
        for (int i = 0; i < clength; i ++) {
            if ((i + 6) < clength && content.substring(i, i+6).equals("struct")) {
                i += 7;
                int tmp1_targetidx = content.indexOf(' ', i);
                String structName = content.substring(i, tmp1_targetidx >= 0 ? tmp1_targetidx : content.indexOf('{', i));
                HashMap<String, String> nhm = new HashMap<>();
                structRedirectMap.put(structName, nhm);
                int depth = 1;
                i = content.indexOf('{', i) + 1;
                output.println("struct " + structName + " {");
                while (depth > 0) {
                    if (content.charAt(i) == '}') {
                        depth --;
                        output.write(content.charAt(i));
                    } else if (content.charAt(i) == '{') {
                        depth ++;
                        output.write(content.charAt(i));
                    } else if (depth == 1 && content.charAt(i) == '*' && content.substring(i, i+9).equals("*redirect")) {
                        i += 10;
                        int gotoidx = content.indexOf(';', i);
                        String[] parts = content.substring(i, gotoidx).split("[\\s]?->[\\s]?");
                        nhm.put(parts[0], parts[1]);
                        i = gotoidx;
                        output.print("u8 " + parts[1]);
                    } else {
                        output.write(content.charAt(i));
                    }
                    i ++;
                }
                i --;
            }
        }
    }
}
