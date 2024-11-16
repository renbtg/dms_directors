package com.domus.populardirectors;

import java.text.MessageFormat;
import java.util.regex.Pattern;

public class Utils {
    /**
     * Formats string with same parameter style used by SLF4j loggers
     */
    public static String logString(String s, Object... var2) {
        int i = 0;
        while (s.contains("{}")) {
            s = s.replaceFirst(Pattern.quote("{}"), "{" + i++ + "}");
        }
        s = s.replaceAll("'", "´"); // replace regular code with pretty quote
        return MessageFormat.format(s, var2);
    }

}
