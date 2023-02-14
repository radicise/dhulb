package Dhulb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class Dhelp {
    public static void main(String[] args) {
        final String version = "0.0.1";
        if (args.length == 0) {
            System.out.println("dhelp --help");
            return;
        }
        if (args[0].equalsIgnoreCase("--help")) {
            System.out.println("dhelp\n  dhelp --help : shows this page\n  dhelp --version : shows dhelp version\n  dhelp --info : provides access to info on dhelpdoc\n  dhelp [filename] [symbolname?] [origin?]\n  filename : name of file to scan for dhelpdoc\n  symbolname : symbol to scan for (is a regex). If not given, lists all dhelpdoc symbols in file\n  origin : origin of symbol (regex), not applicable if symbol name is not present. if symbol name is \".+\" (match all), the origin will filter the list to only include symbols from the specified origin");
            return;
        }
        if (args[0].equalsIgnoreCase("--version")) {
            System.out.println("Dhelp Version Java " + version + "\nLast Modified: Fri, Feb 10 2023");
            return;
        }
        if (args[0].equalsIgnoreCase("--info")) {
            System.out.println("https://github.com/radicise/Dhulb \nplease go to the readme and find the section detailing dhelpdoc");
            return;
        }
        Path pwd = Path.of(System.getenv("PWD"));
        String content;
        try {
            content = Files.readString(pwd.resolve(args[0]));
        } catch (IOException e) {
            System.out.println("failed to read file");
            return;
        }
        boolean useSymbol = false;
        String symname = null;
        boolean useOrg = false;
        String orgname = null;
        if (args.length > 1) {
            useSymbol = true;
            symname = args[1];
        }
        if (args.length > 2) {
            useOrg = true;
            orgname = args[2];
        }
        ArrayList<String[]> dhelpdocs = new ArrayList<>();
        int i = 0;
        int length = content.length();
        while (i < length) {
            if (content.charAt(i) == '"') { // prevent strings
                i ++;
                while (true) {
                    if (content.charAt(i) == '"') {
                        if (!(content.charAt(i-1) == '\\')) {
                            break;
                        }
                    }
                    i ++;
                }
            }
            if (i+9 <= length && content.substring(i, i+9).equalsIgnoreCase("@dhelpdoc")) {
                i += 9;
                String[] props = new String[13];
                while (true) {
                    if (content.charAt(i) == '@') {
                        int bind = content.indexOf('{', i);
                        int nind = content.indexOf('\n', i);
                        String name = content.substring(i+1, nind < bind ? nind : bind).trim();
                        if (nind < bind) { // no argument dhelpdoc
                            if (name.equalsIgnoreCase("class")) {
                                props[0] = "class";
                            } else if (name.equalsIgnoreCase("func")) {
                                props[1] = "func";
                            }
                            i = nind;
                        } else { // dhelpdoc with argument
                            i = bind+1;
                            int cidx = i;
                            int depth = 1;
                            while (true) {
                                if (content.charAt(i) == '{') {
                                    depth ++;
                                }
                                if (content.charAt(i) == '}') {
                                    depth --;
                                }
                                i ++;
                                if (depth == 0) {
                                    break;
                                }
                            }
                            String value = content.substring(cidx, i-1);
                            if (name.equalsIgnoreCase("version")) {
                                props[2] = value;
                            } else if (name.equalsIgnoreCase("since")) {
                                props[3] = value;
                            } else if (name.equalsIgnoreCase("name")) {
                                props[4] = value;
                            } else if (name.equalsIgnoreCase("origin")) {
                                props[5] = value;
                            } else if (name.equalsIgnoreCase("description")) {
                                props[6] = value;
                            } else if (name.equalsIgnoreCase("generic")) {
                                props[7] = value;
                            } else if (name.equalsIgnoreCase("provides")) {
                                props[8] = value;
                            } else if (name.equalsIgnoreCase("methods")) {
                                props[9] = value;
                            } else if (name.equalsIgnoreCase("fields")) {
                                props[10] = value;
                            } else if (name.equalsIgnoreCase("params")) {
                                props[11] = value;
                            } else if (name.equalsIgnoreCase("returns")) {
                                props[12] = value;
                            }
                        }
                    }
                    if (content.charAt(i+1) == '/' && content.charAt(i) == '*') { // exit loop at end of dhelpdoc comment
                        i ++;
                        break;
                    }
                    i ++;
                }
                dhelpdocs.add(props);
            }
            i ++;
        }
        if (!useSymbol) {
            for (String[] doc : dhelpdocs) {
                if (doc[0] == null) {
                    System.out.println(doc[4] + " (" + doc[5] + ") - " + doc[1]);
                } else {
                    System.out.println(doc[4] + " (" + doc[5] + ") - " + doc[0]);
                }
            }
        } else {
            ArrayList<String[]> rel = new ArrayList<>();
            int len = dhelpdocs.size();
            for (int j = 0; j < len; j ++) {
                String[] doc = dhelpdocs.get(j);
                if (doc[4] != null && doc[4].matches(symname)) {
                    rel.add(doc);
                }
            }
            boolean ov = rel.size() == 1;
            boolean ov2 = symname.equals(".+");
            if ((useOrg || ov) && !ov2) {
                for (String[] doc : rel) {
                    if (doc[5].matches(orgname) || ov) {
                        for (int z = 0; z < doc.length; z ++) {
                            if (doc[z] == null) {
                                continue;
                            }
                            switch (z) {
                                case 0:
                                case 1:
                                    System.out.println(doc[z]);
                                    break;
                                case 2:
                                    System.out.println("dhelpdoc version: " + doc[z]);
                                    break;
                                case 3:
                                    System.out.println("feature implemented since: " + doc[z]);
                                    break;
                                case 4:
                                    System.out.println("name: " + doc[z]);
                                    break;
                                case 5:
                                    System.out.println("origin: " + doc[z]);
                                    break;
                                case 6:
                                    System.out.println(doc[z].trim());
                                    break;
                                case 8:
                                    System.out.println("provides support for: " + String.join(", ", doc[z].trim().split("\n")));
                                    break;
                                case 9:
                                    System.out.println("methods:\n" + doc[z].trim());
                                    break;
                                case 10:
                                    System.out.println("fields:\n" + doc[z].trim());
                                    break;
                                case 11:
                                    System.out.println("parameters:\n" + doc[z].trim());
                                    break;
                                case 12:
                                    System.out.println("returns: " + doc[z].trim());
                                    break;
                            }
                        }
                        return;
                    }
                }
            } else {
                for (String[] doc : rel) {
                    if (ov2 && !doc[5].matches(orgname)) {
                        continue;
                    }
                    if (doc[0] == null) {
                        System.out.println(doc[4] + " (" + doc[5] + ") - " + doc[1]);
                    } else {
                        System.out.println(doc[4] + " (" + doc[5] + ") - " + doc[0]);
                    }
                }
            }
        }
        // System.out.println(Arrays.deepToString(dhelpdocs.toArray()));
    }
}
