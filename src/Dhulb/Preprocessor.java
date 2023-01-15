package Dhulb;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Stack;

import DExt.DhulbExtension;
class IfdefStackItem {
    boolean succeeded;
    IfdefStackItem () {
        succeeded = false;
    }
}
public class Preprocessor {// takes file stream and the directory path where the files are stored and from which relative paths should be constructed
    private static PrintStream printstrm;
    private static boolean importComments = false;
    private static boolean passComments = false;
    private static boolean minimalImport = false;
    private static Path libPath = null;
    private static Path extPath = null;
    private static Path defPath = null;
    private static HashSet<String> imported = new HashSet<>();
    private static HashSet<String> required = new HashSet<>();
    private static HashSet<String> defined = new HashSet<>();
    private static Stack<IfdefStackItem> ifdefStack = new Stack<>();
    private static boolean suppressWarnings = false;
    private static void info (String msg) {
        System.err.println("INFO: " + msg);
    }
    private static void warning (String msg) {
        if (suppressWarnings) {
            return;
        }
        System.err.println("WARN: " + msg);
    }
    private static void error (String msg) {
        System.err.println("ERROR:" + msg);
        System.exit(1);
    }
    private static void preprocess(BufferedReader reader, Path cwd, PrintStream output) throws Exception {
        int cbyte = reader.read();
        boolean test = true;
        while (cbyte != -1) {
            boolean startIfdefTypeSkip = false;
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
                String[] manip = reader.readLine().split("\"");
                for (int i = 1; i < manip.length; i += 2) {
                    manip[i] = manip[i].replace(' ', '\u0007');
                }
                String[] line = String.join("", manip).split("[\\s]");
                for (int i = 0; i < line.length; i ++) {
                    line[i] = line[i].replace('\u0007', ' ');
                }
                printstrm.println(Arrays.toString(line));
                if (line.length > 1 && line[1].startsWith("\"")) {
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
                    if (imported.contains(line[1])) {
                        printstrm.println("already imported");
                    } else {
                        printstrm.println(line[1]);
                        imported.add(line[1]);
                        try {
                            if (importComments) {
                                output.print("/* begin imported content from: " + line[1] + " */\n");
                            }
                            File f = new File(cwd.toString(), line[1]);
                            if (!f.exists()) {
                                f = new File(libPath.toString(), line[1] + ".dhulb");
                            }
                            preprocess(new BufferedReader(new InputStreamReader(new FileInputStream(f))), Path.of(f.getParent()), output);
                            if (importComments) {
                                output.print("\n/* end imported content from: " + line[1] + "*/");
                            }
                        } catch (FileNotFoundException FNF) {
                            printstrm.println("File Not Found: " + line[1]);
                            throw new Exception("Could not find file:" + line[1]);
                        } catch (Exception _E) {
                            System.err.println("error preprocessing import");
                            _E.printStackTrace(System.err);
                            System.exit(1);
                        }
                        if (started) {
                            minimalImport = false;
                        }
                    }
                } else if (line[0].equalsIgnoreCase("utilise")) {
                    printstrm.println(line[1]);
                    printstrm.println("NOT IMPLEMENTED USE OF \"utilise\"");
                    //TODO: implement an assembler
                    throw new NotImplementedException("Use of assembly files has not been implemented yet: " + line[1]);
                } else if (line[0].equalsIgnoreCase("require")) {
                    printstrm.println(line[1]);
                    required.add(line[1]);
                } else if (line[0].equalsIgnoreCase("ifdef")) {
                    boolean isdef = defined.contains(line[1]);
                    printstrm.println("ifdef: " + line[1] + " (" + isdef + ")");
                    ifdefStack.add(new IfdefStackItem());
                    if (!isdef) {
                        printstrm.println("ifdef check failed, skipping");
                        startIfdefTypeSkip = true;
                    } else {
                        ifdefStack.peek().succeeded = true;
                    }
                } else if (line[0].equalsIgnoreCase("elif")) {
                    boolean isdef = defined.contains(line[1]);
                    printstrm.println("elif: " + line[1] + " (" + isdef + ")");
                    if (ifdefStack.peek().succeeded) {
                        printstrm.println("elif check failed (else component), skipping");
                        startIfdefTypeSkip = true;
                    } else if (!isdef) {
                        printstrm.println("elif check failed (if component), skipping");
                        startIfdefTypeSkip = true;
                    } else {
                        ifdefStack.peek().succeeded = true;
                    }
                } else if (line[0].equalsIgnoreCase("else")) {
                    if (ifdefStack.peek().succeeded) {
                        printstrm.println("else check failed");
                        startIfdefTypeSkip = true;
                    } else {
                        ifdefStack.peek().succeeded = true;
                    }
                } else if (line[0].equalsIgnoreCase("endif")) {
                    ifdefStack.pop();
                } else if (line[0].equalsIgnoreCase("warn")) {
                    warning(line[1]);
                } else if (line[0].equalsIgnoreCase("error")) {
                    error(line[1]);
                } else if (line[0].equalsIgnoreCase("info")) {
                    info(line[1]);
                } else if (line[0].equalsIgnoreCase("define")) {
                    defined.add(line[1]);
                }
                if (startIfdefTypeSkip) {
                    int cdepth = 0;
                    while (true) {
                        String nline = reader.readLine().strip();
                        if (nline.startsWith("#")) {
                            int TMP1_spaceIdx = nline.indexOf(" ");
                            String cmd = nline.substring(1, TMP1_spaceIdx > -1 ? TMP1_spaceIdx : nline.length());
                            if (cmd.equalsIgnoreCase("ifdef")) {
                                cdepth ++;
                            } else if (cmd.equalsIgnoreCase("elif")) {
                                if (cdepth == 0) {
                                    if (ifdefStack.peek().succeeded || !defined.contains(nline.split(" ")[1])) { // if check fails then the result would be same is if an independent #ifdef failed, so just continue
                                        continue;
                                    }
                                    ifdefStack.peek().succeeded = true;
                                    break; // this only is reached if the #elif succeeded, so enter normal success result
                                }
                            } else if (cmd.equalsIgnoreCase("else")) {
                                if (cdepth == 0) {
                                    if (ifdefStack.peek().succeeded) {
                                        continue;
                                    }
                                    ifdefStack.peek().succeeded = true;
                                    break;
                                }
                            } else if (cmd.equalsIgnoreCase("endif")) {
                                if (cdepth == 0) {
                                    ifdefStack.pop();
                                    break;
                                }
                                cdepth --;
                            }
                        }
                    }
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
    private static void parseConfig (Path cfgFile, Path cwd) throws Exception {
        File cfgF = cfgFile.toFile();
        String cwdStr = cwd.toString();
        if (!cfgF.exists()) {
            printstrm.println("Config File Not Found:" + cfgFile.toString());
            return;
        }
        printstrm.println("Loading Config File:");
        FileInputStream fIn = new FileInputStream(cfgF);
        String[] cfg = new String(fIn.readAllBytes(), StandardCharsets.UTF_8).split("[\n\r]+");
        fIn.close();
        for (String line : cfg) {
            line = line.trim();
            if (line.startsWith("#")) { // comments
                continue;
            }
            if (libPath == null && line.startsWith("LibPath=")) { // dhulb library path
                String s = line.split("=",2)[1].replaceAll("%CWD", cwdStr);
                libPath = Path.of(s);
                printstrm.println("LibPath="+s);
            } else if (extPath == null && line.startsWith("ExtPath=")) { // dhulb extension path
                String s = line.split("=",2)[1].replaceAll("%CWD", cwdStr);
                extPath = Path.of(s);
                printstrm.println("ExtPath="+s);
            } else if (defPath == null && line.startsWith("DefPath=")) { // preprocessor name definition path
                String s = line.split("=",2)[1].replaceAll("%CWD", cwdStr);
                defPath = Path.of(s);
                printstrm.println("DefPath="+s);
            }
        }
    }
    public static void main (String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--help")) {
            System.out.println("Usage:\njava Dhulb/Preprocessor debug-dest|- (no debug) working-path|- (use the current working directory) [options]\n -comment-imports -- marks where imported content begins, ends, and the source file of the imported content\n -pass-comments --passes comments through the preprocessor instead of stripping them");
            return;
        }
        Path cwd = Path.of(System.getenv("PWD"));
        Path cfgPath = Path.of(System.getenv("HOME"), ".dhulb_conf");
        if (args.length > 0 && !args[0].equals("-")) {
            printstrm = new PrintStream(new File(cwd.toString(), args[0]));
        } else {
            printstrm = new PrintStream(new NullOutputStream());
        }
        for (int i = 1; i < args.length; i ++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-comment-imports")) {
                importComments = true;
            } else if (arg.equalsIgnoreCase("-pass-comments")) {
                passComments = true;
            } else if (arg.matches("-(cwd|CWD)=")) {
                cwd = Path.of(arg.split("=",2)[1]);
            } else if (arg.matches("-(conf|CONF)=.*")) {
                cfgPath = Path.of(arg.split("=",2)[1]);
            } else if (arg.matches("-(lp|LP)=")) {
                libPath = Path.of(arg.split("=",2)[1]);
            } else if (arg.matches("-(ep|EP)=")) {
                extPath = Path.of(arg.split("=",2)[1]);
            } else if (arg.matches("-(dp|DP)=")) {
                defPath = Path.of(arg.split("=",2)[1]);
            } else {
                defined.add(arg);
            }
        }
        PrintStream output = System.out;
        InputStreamReader inreader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(inreader);
        parseConfig(cfgPath, cwd);
        if (defPath != null) {
            FileInputStream fIn = new FileInputStream(new File(defPath.toString()));
            printstrm.println("reading def path:");
            for (String s : new String(fIn.readAllBytes()).split("\n")) {
                if (s.length() > 0 && !(s.charAt(0) == '#')) {
                    printstrm.println(s);
                    defined.add(s);
                }
            }
            fIn.close();
        }
        RecoverableOutputStream rOut = new RecoverableOutputStream();
        preprocess(reader, cwd, new PrintStream(rOut));
        for (String req : required) {
            @SuppressWarnings("unchecked")
            Class<? extends DhulbExtension> cls = (Class<? extends DhulbExtension>) Class.forName(extPath.resolve(req).toString().replace(cwd.toString(), "").substring(1).replaceAll("/", "."));
            Method m = cls.getDeclaredMethod("dothing", new Class[]{InputStream.class, OutputStream.class});
            InputStream send = new ByteArrayInputStream(Arrays.copyOfRange(rOut.data, 0, rOut.wpos));
            RecoverableOutputStream rec = new RecoverableOutputStream();
            m.invoke(null, new Object[]{send, rec});
            rOut.close();
            rOut = rec;
        }
        output.write(Arrays.copyOfRange(rOut.data, 0, rOut.wpos));
        rOut.close();
    }
}
