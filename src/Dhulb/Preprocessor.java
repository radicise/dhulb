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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import java.nio.file.Paths;

import javax.naming.directory.InvalidAttributeValueException;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

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
    private static ArrayList<Path> libPaths = new ArrayList<>();
    private static ArrayList<Path> extPaths = new ArrayList<>();
    private static ArrayList<Path> defPaths = new ArrayList<>();
    private static HashSet<String> imported = new HashSet<>();
    private static HashSet<String> utilized = new HashSet<>();
    private static HashSet<String> required = new HashSet<>();
    private static HashMap<String, Integer> defined = new HashMap<>();
    private static Stack<IfdefStackItem> ifdefStack = new Stack<>();
    private static boolean suppressWarnings = false;
    private static ScriptEngine javaScriptEngine = new ScriptEngineManager().getEngineByName("js");
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
    private static boolean parseIf (String[] line) throws Exception {
        line[0] = "";
        for (int i = 1; i < line.length; i ++) {
            if (defined.containsKey(line[i])) {
                line[i] = Integer.toString(defined.get(line[i]).intValue());
            }
        }
        return (Boolean) javaScriptEngine.eval(String.join("", line));
    }
    private static void preprocess (BufferedReader reader, Path cwd, PrintStream output) throws Exception {
        int cbyte = reader.read();
        boolean test = true;
        while (cbyte != -1) {
            boolean startIfdefTypeSkip = false;
            if (cbyte == '/') {
                int tbyte = reader.read();
                // if (tbyte == '/' || tbyte == '*') {
                if (tbyte == '*') {
                    if (passComments) {
                        output.write(cbyte);
                        output.write(tbyte);
                    }
                    cbyte = reader.read();
                    while (cbyte != -1) {
                        if (passComments) {
                            output.write(cbyte);
                        }
                        if (cbyte == '*') {
                            cbyte = reader.read();
                            if (cbyte == '/') {
                                if (passComments) {
                                    output.write(cbyte);
                                }
                                // cbyte = '\n';
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
                } else {
                    output.write(cbyte);
                    cbyte = tbyte;
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
                    File f = null;
                    if (line[1].charAt(0) == '<') {
                        if (libPaths == null) {
                            throw new InvalidAttributeValueException("tried to access library when libPath is null");
                        }
                        for (Path libPath : libPaths) {
                            f = new File(libPath.toString(), line[1].substring(1, line[1].length()-1));
                            if (f.exists()) {
                                break;
                            }
                        }
                    } else {
                        f = new File(cwd.toString(), line[1]);
                    }
                    if (imported.contains(f.toPath().toString())) {
                        printstrm.println("already imported");
                    } else {
                        printstrm.println(line[1]);
                        imported.add(f.toPath().toString());
                        try {
                            if (importComments) {
                                output.print("/* begin imported content from: " + line[1] + " */\n");
                            }
                            preprocess(new BufferedReader(new InputStreamReader(new FileInputStream(f))), Paths.get(f.getParent()), output);
                            if (importComments) {
                                output.print("\n/* end imported content from: " + line[1] + "*/");
                            }
                        } catch (FileNotFoundException FNF) {
                            printstrm.println("File Not Found: " + line[1]);
                            throw FNF;
                        } catch (Exception _E) {
                            System.err.println("error preprocessing import");
                            _E.printStackTrace(System.err);
                            System.exit(1);
                        }
                        if (started) {
                            minimalImport = false;
                        }
                    }
                } else if (line[0].equalsIgnoreCase("utilize") || line[0].equalsIgnoreCase("utilise")) {
                    File f = null;
                    if (line[1].charAt(0) == '<') {
                        if (libPaths == null) {
                            throw new InvalidAttributeValueException("tried to access library when libPath is null");
                        }
                        for (Path libPath : libPaths) {
                            f = new File(libPath.toString(), line[1].substring(1, line[1].length()-1));
                            if (f.exists()) {
                                break;
                            }
                        }
                    } else {
                        f = new File(cwd.toString(), line[1]);
                    }
                    if (utilized.contains(f.toPath().toString())) {
                        printstrm.println("already utilized");
                    } else {
                        printstrm.println(line[1]);
                        utilized.add(f.toPath().toString());
                        /*
                         * #utilise nosymb|nodoc|docscan|all “file”, defaults to "docscan"
                         * Assembles an assembly file to a file of the name of the same name with “__assembled” appended
                         * 
                         * if “docscan” is used, it is scanned for dhulbDoc markers and places appropriate “imply” statements in the current file
                         * 
                         * if “all” is used, applies "docscan", then adds "imply u8" for all other symbols
                         * 
                         * if “nodoc” is used, effect is equivalent to "all" when no dhulbdoc is present
                         * 
                         * if “nosymb” is used, no “imply” statements will be added
                         */
                        try {
                            if (importComments) {
                                output.print("/* begin utilized content from: " + line[1] + " */\n");
                            }
                            int argval = line.length > 2 ? (line[2].equalsIgnoreCase("docscan") ? 0 : (line[2].equalsIgnoreCase("nosymb") ? 3 : (line[2].equalsIgnoreCase("all") ? 1 : (line[2].equalsIgnoreCase("nodoc") ? 2 : 0)))) : 0;
                            String toScan = new String(Files.readAllBytes(f.toPath()));
                            for (String scanLine : toScan.split("\n")) {
                                if (scanLine.matches("^[\\s]*[a-zA-Z0-9_]+:")) { // does checks on labels
                                    if (scanLine.contains("/*dhulbDoc") && argval < 2) {
                                        
                                    } else if (argval > 0 && argval < 3) {
                                        output.println("imply u8 " + scanLine.trim().split(":", 2)[0] + ";");
                                    }
                                } else if (scanLine.contains("/*dhulbDoc")) { // does checks on standalone dhulbdoc
                                    //
                                }
                            }
                            if (importComments) {
                                output.print("\n/* end utilized content from: " + line[1] + "*/");
                            }
                        } catch (FileNotFoundException FNF) {
                            printstrm.println("File Not Found: " + line[1]);
                            throw FNF;
                        } catch (Exception _E) {
                            System.err.println("error preprocessing utilize");
                            _E.printStackTrace(System.err);
                            System.exit(1);
                        }
                    }
                } else if (line[0].equalsIgnoreCase("require")) {
                    printstrm.println(line[1]);
                    required.add(line[1]);
                } else if (line[0].equalsIgnoreCase("ifdef")) {
                    boolean isdef = defined.containsKey(line[1]);
                    printstrm.println("ifdef: " + line[1] + " (" + isdef + ")");
                    ifdefStack.add(new IfdefStackItem());
                    if (!isdef) {
                        printstrm.println("ifdef check failed, skipping");
                        startIfdefTypeSkip = true;
                    } else {
                        ifdefStack.peek().succeeded = true;
                    }
                } else if (line[0].equalsIgnoreCase("if")) {
                    if (!defined.containsKey(line[1])) {
                        throw new Exception("Attempted to compare the value of a preprocessor variable that does not exist");
                    }
                    boolean cmpRes = parseIf(line);
                    printstrm.println("elif: " + String.join("", Arrays.copyOfRange(line, 1, line.length)) + " (" + cmpRes + ")");
                    ifdefStack.add(new IfdefStackItem());
                    if (!cmpRes) {
                        printstrm.println("if check failed, skipping");
                        startIfdefTypeSkip = true;
                    } else {
                        ifdefStack.peek().succeeded = true;
                    }
                } else if (line[0].equalsIgnoreCase("elif")) {
                    boolean cmpRes = parseIf(line);
                    printstrm.println("elif: " + String.join("", Arrays.copyOfRange(line, 1, line.length)) + " (" + cmpRes + ")");
                    if (ifdefStack.peek().succeeded) {
                        printstrm.println("elif check failed (else component), skipping");
                        startIfdefTypeSkip = true;
                    } else if (!cmpRes) {
                        printstrm.println("elif check failed (if component), skipping");
                        startIfdefTypeSkip = true;
                    } else {
                        ifdefStack.peek().succeeded = true;
                    }
                } else if (line[0].equalsIgnoreCase("elifdef")) {
                    boolean isdef = defined.containsKey(line[1]);
                    printstrm.println("elifdef: " + line[1] + " (" + isdef + ")");
                    if (ifdefStack.peek().succeeded) {
                        printstrm.println("elifdef check failed (else component), skipping");
                        startIfdefTypeSkip = true;
                    } else if (!isdef) {
                        printstrm.println("elifdef check failed (if component), skipping");
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
                    defined.put(line[1], line.length > 2 ? Integer.parseInt(line[2]) : 1);
                } else {
                    throw new Exception("invalid directive name: " + line[0]);
                }
                if (startIfdefTypeSkip) {
                    int cdepth = 0;
                    while (true) {
                        String nline = reader.readLine().trim();//TODO restore trimming just of `Java white space' characters instead of including all C0 control codes
                        if (nline.startsWith("#")) {
                            int TMP1_spaceIdx = nline.indexOf(" ");
                            String cmd = nline.substring(1, TMP1_spaceIdx > -1 ? TMP1_spaceIdx : nline.length());
                            if (cmd.equalsIgnoreCase("ifdef")) {
                                cdepth ++;
                            } else if (cmd.equalsIgnoreCase("if")) {
                                cdepth ++;
                            } else if (cmd.equalsIgnoreCase("elifdef")) {
                                if (cdepth == 0) {
                                    if (ifdefStack.peek().succeeded || !defined.containsKey(nline.split(" ")[1])) { // if check fails then the result would be same is if an independent #ifdef failed, so just continue
                                        continue;
                                    }
                                    ifdefStack.peek().succeeded = true;
                                    break; // this only is reached if the #elif succeeded, so enter normal success result
                                }
                            } else if (cmd.equalsIgnoreCase("elif")) {
                                if (cdepth == 0) {
                                    if (ifdefStack.peek().succeeded || !parseIf(nline.split(" "))) {
                                        continue;
                                    }
                                    ifdefStack.peek().succeeded = true;
                                    break;
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
        if (cfgFile == null) {
            return;
        }
    	File cfgF = cfgFile.toFile();
        String cwdStr = cwd.toString();
        if (!cfgF.exists()) {
            printstrm.println("Config File Not Found:" + cfgFile.toString());
            return;
        }
        printstrm.println("Loading Config File:");
        String[] cfg = new String(Files.readAllBytes(cfgFile), StandardCharsets.UTF_8).split("[\n\r]+");
        for (String line : cfg) {
            line = line.trim();
            if (line.startsWith("#")) { // comments
                continue;
            }
            if (libPaths == null && line.startsWith("LibPath=")) { // dhulb library path
                String s = line.split("=",2)[1].replaceAll("%CWD", cwdStr);
                libPaths.add(Paths.get(s));
                printstrm.println("LibPath="+s);
            } else if (extPaths == null && line.startsWith("ExtPath=")) { // dhulb extension path
                String s = line.split("=",2)[1].replaceAll("%CWD", cwdStr);
                extPaths.add(Paths.get(s));
                printstrm.println("ExtPath="+s);
            } else if (defPaths == null && line.startsWith("DefPath=")) { // preprocessor name definition path
                String s = line.split("=",2)[1].replaceAll("%CWD", cwdStr);
                defPaths.add(Paths.get(s));
                printstrm.println("DefPath="+s);
            }
        }
    }
    public static void main (String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--help")) {
            System.out.println(
                "Usage:\n"+
                "java Dhulb/Preprocessor debug-dest|- (no debug) working-path|- (use the current working directory) [options]\n"+
                " -comment-imports -- marks where imported content begins, ends, and the source file of the imported content\n"+
                " -pass-comments -- passes comments through the preprocessor instead of stripping them"+
                " -no-cfg-file -- causes no config files to be read"+
                " -cwd= -CWD= -- sets the working directory of the preprocessor"+
                " -conf= -CONF= -- specifies a config file to use (used in combination with the /etc and $HOME files)"+
                " -lp= -LP= -- adds a library path"+
                " -ep= -EP= -- adds an extension path"+
                " -dp= -DP= -- adds a definition path"+
                " [NAME] -- adds preprocessor variable [NAME] with value 1"+
                " [NAME]=[VALUE] -- adds preprocessor variable [NAME] with value [VALUE]"
            );
            return;
        }
        Path cwd = Paths.get(System.getenv("PWD"));
        Path cfgPath = null;
        boolean noCfgFiles = false;
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
            } else if (arg.equalsIgnoreCase("-no-cfg-file")) {
                noCfgFiles = true;
            } else if (arg.matches("-(cwd|CWD)=")) {
                cwd = Paths.get(arg.split("=",2)[1]);
            } else if (arg.matches("-(conf|CONF)=.*")) {
                cfgPath = Paths.get(arg.split("=",2)[1]);
            } else if (arg.matches("-(lp|LP)=")) {
                libPaths.add(Paths.get(arg.split("=",2)[1]));
            } else if (arg.matches("-(ep|EP)=")) {
                extPaths.add(Paths.get(arg.split("=",2)[1]));
            } else if (arg.matches("-(dp|DP)=")) {
                defPaths.add(Paths.get(arg.split("=",2)[1]));
            } else {
                boolean isa = arg.contains("=");
                defined.put(isa ? arg.split("=")[0] : arg, isa ? Integer.parseInt(arg.split("=")[1]) : 1);
            }
        }
        PrintStream output = System.out;
        InputStreamReader inreader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(inreader);
        if (!noCfgFiles) {
            parseConfig(Paths.get("/etc/.dhulb_conf"), cwd);
            parseConfig(Paths.get(System.getenv("HOME"), ".dhulb_conf"), cwd);
            parseConfig(cfgPath, cwd);
        }
        if (defPaths != null) {
            printstrm.println("reading def path:");
            for (Path defPath : defPaths) {
                for (String s : new String(Files.readAllBytes(defPath)).split("\n")) {
                    if (s.length() > 0 && !(s.charAt(0) == '#')) {
                        printstrm.println(s);
                        boolean isa = s.contains("=");
                        defined.put(isa ? s.split("=")[0] : s, isa ? Integer.parseInt(s.split("=")[1]) : 1);
                    }
                }
            }
        }
        RecoverableOutputStream rOut = new RecoverableOutputStream();
        preprocess(reader, cwd, new PrintStream(rOut));
        for (String req : required) {
            for (Path extPath : extPaths) {
                @SuppressWarnings("unchecked")
                Class<? extends DhulbExtension> cls = (Class<? extends DhulbExtension>) Class.forName(extPath.resolve(req).toString().replace(cwd.toString(), "").substring(1).replaceAll("/", "."));
                Method m = cls.getDeclaredMethod("dothing", new Class[]{InputStream.class, OutputStream.class});
                InputStream send = new ByteArrayInputStream(Arrays.copyOfRange(rOut.data, 0, rOut.wpos));
                RecoverableOutputStream rec = new RecoverableOutputStream();
                m.invoke(null, new Object[]{send, rec});
                rOut.close();
                rOut = rec;
            }
        }
        output.println("/*@Dhulb\n\n@utilize\n" + String.join("\n", utilized) + "\n*/");
        output.write(Arrays.copyOfRange(rOut.data, 0, rOut.wpos));
        rOut.close();
    }
}
