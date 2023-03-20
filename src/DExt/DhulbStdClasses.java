package DExt;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public class DhulbStdClasses extends DhulbExtension {
    public static void dothing (InputStream inp, OutputStream out) throws Exception {
        PrintStream output = new PrintStream(out);
        String content = new String(inp.readNBytes(inp.available()));
        int clength = content.length();
        for (int i = 0; i < clength; i ++) {
            if ((i + 6) < clength && content.substring(i, i+6).equals("struct")) {
                i += 7;
                int tmp1_targetidx = content.indexOf(' ', i);
                String structName = content.substring(i, tmp1_targetidx >= 0 ? tmp1_targetidx : content.indexOf('{', i));
                String storedConstructor = "";
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
                    } else if (depth == 1 && content.substring(i, content.indexOf('{', i)).contains("constructor")) {
                        int gotoidx = content.indexOf('{', i) + 1;
                        int depth2 = 1;
                        while (true) {
                            if (content.charAt(gotoidx) == '{') {
                                depth2 ++;
                            } else if (content.charAt(gotoidx) == '}') {
                                depth2 --;
                                if (depth2 == 0) {
                                    break;
                                }
                            }
                            gotoidx ++;
                        }
                        // String calcConstructorName = "PREPROCESSORFUNCTION_constructor"
                        // constructorMap.put(structName, calcConstructorName);
                        storedConstructor = content.substring(i, gotoidx+1);
                        i = gotoidx + 1;
                    } else {
                        output.write(content.charAt(i));
                    }
                    i ++;
                }
                output.print(storedConstructor);
                i --;
            }
        }
    }
}
