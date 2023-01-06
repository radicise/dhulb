package Dhulb;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
public class Preprocessor {// takes file stream and the directory path where the files are stored and from which relative paths should be constructed
    private static PrintStream printstrm;
    private static boolean importComments = false;
    private static boolean passComments = false;
    private static boolean minimalImport = false;
    private static void preprocess(BufferedReader reader, Path cwd, PrintStream output) throws Exception {
        int cbyte = reader.read();
        boolean test = true;
        while (cbyte != -1) {
            if (cbyte == '/') {
                int tbyte = reader.read();
                if (tbyte == '/' || tbyte == '*') {
                    if (passComments) {
                        output.write(cbyte);
                        output.write(tbyte);
                    }
                    cbyte = reader.read();
                    while (cbyte != -1) {
                        if (passComments) {
                            output.write(cbyte);
                        }
                        if (tbyte == '*' && cbyte == '*') {
                            cbyte = reader.read();
                            if (cbyte == '/') {
                                if (passComments) {
                                    output.write(cbyte);
                                }
                                cbyte = reader.read();
                                if (!passComments && Character.isWhitespace(cbyte)) {
                                    cbyte = reader.read();
                                }
                                break;
                            }
                            continue;
                        }
                        cbyte = reader.read();
                    }
                }
                continue;
            }
            if (test && cbyte == '#') {
                String[] line = reader.readLine().strip().split("[\\s]");
                printstrm.println(Arrays.toString(line));
                if (line[1].startsWith("\"")) {
                    line[1] = line[1].substring(1, line[1].length()-1);
                }
                if (line[0].equalsIgnoreCase("import")) {
                    boolean started = false;
                    if (!minimalImport && line.length > 2 && line[2].equalsIgnoreCase("minimal")) {
                        started = true;
                        minimalImport = true;
                    }
                    if (minimalImport) {
                        printstrm.print("minimal: ");
                    }
                    printstrm.println(line[1]);
                    try {
                        if (importComments) {
                            output.print("/* begin imported content from: " + line[1] + " */\n");
                        }
                        File f = new File(cwd.toString(), line[1]);
                        preprocess(new BufferedReader(new InputStreamReader(new FileInputStream(f))), Path.of(f.getParent()), output);
                        if (importComments) {
                            output.print("\n/* end imported content from: " + line[1] + "*/");
                        }
                    } catch (FileNotFoundException FNF) {
                        printstrm.println("File Not Found: " + line[1]);
                        output.print("#!missing \"" + line[1] + "\"");
                    } catch (Exception _E) {
                        System.err.println("error preprocessing import");
                        System.exit(1);
                    }
                    if (started) {
                        minimalImport = false;
                    }
                } else if (line[0].equalsIgnoreCase("utilise")) {
                    printstrm.println(line[1]);
                    printstrm.println("NOT IMPLEMENTED USE OF \"utilise\"");
                    //TODO: implement an assembler
                    output.print("#!missing \"" + line[1] + "\"");
                }
                cbyte = '\n';
            }
            if (!Character.isWhitespace(cbyte)) {
                test = false;
            }
            if (cbyte == '\n' || cbyte == '\r') {
                test = true;
            }
            output.write(cbyte);
            cbyte = reader.read();
        }
    }
    public static void main (String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--help")) {
            System.out.println("Usage:\njava Dhulb/Preprocessor [debug-dest|* (no debug)] [options]\n -comment-imports -- marks where imported content begins, ends, and the source file of the imported content\n -pass-comments --passes comments through the preprocessor instead of stripping them");
            return;
        }
        Path cwd = Path.of(System.getenv("PWD"));
        if (args.length > 0 && !args[0].equals("-")) {
            printstrm = new PrintStream(new File(cwd.toString(), args[0]));
        } else {
            printstrm = new PrintStream(OutputStream.nullOutputStream());
        }
        for (int i = 1; i < args.length; i ++) {
            String arg = args[i];
            if (arg.equals("-comment-imports")) {
                importComments = true;
            } else if (arg.equals("-pass-comments")) {
                passComments = true;
            }
        }
        PrintStream output = System.out;
        InputStreamReader inreader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(inreader);
        preprocess(reader, cwd, output);
    }
}
