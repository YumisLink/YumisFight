package cn.yumis.fight.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从测试源码中删除达标测试方法；所在类因此不含任何测试方法时，连同类文件一起删除。
 * 只处理 src/test/java 下的源码。所有定位均先做一遍“代码掩码”
 * （排除注释、字符串字面量与文本块内容），保证不会误匹配到样例或注释里的注解与花括号。
 */
final class TestSourcePruner {
    enum Result { METHOD_REMOVED, CLASS_REMOVED, METHOD_NOT_FOUND, CLASS_NOT_FOUND, SOURCE_ROOT_MISSING, IO_ERROR }

    private static final String[] TEST_ANNOTATIONS =
            {"@Test", "@ParameterizedTest", "@RepeatedTest", "@TestFactory", "@TestTemplate"};
    /** 连续 3 个及以上换行（中间仅空白）折叠为一个换行，仅在删除接缝附近应用。 */
    private static final Pattern BLANK_RUN = Pattern.compile("(\\r?\\n)(?:[ \\t]*\\r?\\n){2,}");

    static Result prune(Path projectRoot, String className, String methodName) {
        Path root = projectRoot.resolve("src").resolve("test").resolve("java");
        if (!Files.isDirectory(root)) return Result.SOURCE_ROOT_MISSING;
        Path file = classFile(root, className);
        if (file == null || !Files.isRegularFile(file)) return Result.CLASS_NOT_FOUND;
        String text;
        try { text = Files.readString(file, StandardCharsets.UTF_8); }
        catch (IOException e) { return Result.IO_ERROR; }
        boolean[] mask = codeMask(text);
        int[] range = methodRange(text, mask, methodName);
        if (range == null) return Result.METHOD_NOT_FOUND;
        String updated = splice(text, range[0], range[1]);
        if (!hasTestAnnotation(updated, codeMask(updated))) {
            try {
                Files.delete(file);
                deleteEmptyParents(file.getParent(), root);
            } catch (IOException e) { return Result.IO_ERROR; }
            return Result.CLASS_REMOVED;
        }
        try { Files.writeString(file, updated, StandardCharsets.UTF_8); }
        catch (IOException e) { return Result.IO_ERROR; }
        return Result.METHOD_REMOVED;
    }

    /** 嵌套类回落到宿主类源码文件。 */
    private static Path classFile(Path root, String className) {
        Path file = root.resolve(className.replace('.', '/') + ".java");
        if (Files.isRegularFile(file)) return file;
        int nested = className.indexOf('$');
        if (nested > 0) {
            Path host = root.resolve(className.substring(0, nested).replace('.', '/') + ".java");
            if (Files.isRegularFile(host)) return host;
        }
        return null;
    }

    /** 掩码：true 表示该字符属于代码本身（不在注释、字符串、字符或文本块内容里）。 */
    static boolean[] codeMask(String text) {
        boolean[] mask = new boolean[text.length()];
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') { i = lineEnd(text, i); continue; }
            if (c == '/' && next == '*') {
                int close = text.indexOf("*/", i + 2);
                i = close < 0 ? text.length() : close + 2;
                continue;
            }
            if (c == '"') {
                if (next == '"' && i + 2 < text.length() && text.charAt(i + 2) == '"') { i = textBlockEnd(text, i + 3); continue; }
                i = stringEnd(text, i + 1);
                continue;
            }
            if (c == '\'') { i = charEnd(text, i + 1); continue; }
            mask[i] = true;
            i++;
        }
        return mask;
    }

    private static int lineEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) if (text.charAt(i) == '\n') return i + 1;
        return text.length();
    }

    private static int stringEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') return i + 1;
            if (c == '\n' || c == '\r') return i;
        }
        return text.length();
    }

    private static int charEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '\'') return i + 1;
            if (c == '\n' || c == '\r') return i;
        }
        return text.length();
    }

    private static int textBlockEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"' && i + 2 < text.length() && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') return i + 3;
        }
        return text.length();
    }

    /** 定位 methodName 对应的测试方法整段范围（含注解行），未找到返回 null。 */
    static int[] methodRange(String text, boolean[] mask, String methodName) {
        for (String annotation : TEST_ANNOTATIONS) {
            int searchFrom = 0;
            while (true) {
                int at = text.indexOf(annotation, searchFrom);
                if (at < 0) break;
                searchFrom = at + 1;
                int nameEnd = at + annotation.length();
                // 排除 @Test 匹配到 @TestFactory/@TestTemplate 前缀的情况。
                if (nameEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(nameEnd))) continue;
                if (!inMask(mask, at, nameEnd)) continue;
                int[] range = methodRangeAt(text, mask, at, nameEnd, methodName);
                if (range != null) return range;
            }
        }
        return null;
    }

    private static boolean inMask(boolean[] mask, int from, int to) {
        for (int i = from; i < to; i++) if (!mask[i]) return false;
        return true;
    }

    private static int[] methodRangeAt(String text, boolean[] mask, int annotationStart, int afterAnnotation, String methodName) {
        int i = skipWhitespace(text, afterAnnotation);
        if (i < text.length() && text.charAt(i) == '(') {
            int closed = matchingDelimiter(text, mask, i, '(', ')');
            if (closed < 0) return null;
            i = skipWhitespace(text, closed + 1);
        }
        int bodyOpen = firstOpenBrace(text, mask, i);
        if (bodyOpen < 0) return null;
        if (!declarationNamesMethod(text.substring(i, bodyOpen), methodName)) return null;
        int bodyClose = matchingDelimiter(text, mask, bodyOpen, '{', '}');
        if (bodyClose < 0) return null;
        int start = expandToAnnotationBlock(text, lineStart(text, annotationStart));
        int end = endThroughLineTerminator(text, bodyClose + 1);
        return new int[] {start, end};
    }

    /** 声明段中第一个后随左括号的标识符即方法名（修饰符与返回类型不会后随左括号）。 */
    private static boolean declarationNamesMethod(String declaration, String methodName) {
        int depth = 0;
        for (int i = 0; i < declaration.length(); i++) {
            char c = declaration.charAt(i);
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }
            if (depth != 0 || !Character.isJavaIdentifierStart(c)) continue;
            int j = i + 1;
            while (j < declaration.length() && Character.isJavaIdentifierPart(declaration.charAt(j))) j++;
            int k = skipWhitespace(declaration, j);
            if (k < declaration.length() && declaration.charAt(k) == '(')
                return declaration.substring(i, j).equals(methodName);
            i = j - 1;
        }
        return false;
    }

    /** 从 open 的左括号起找配对右括号，只统计掩码内的代码字符。 */
    private static int matchingDelimiter(String text, boolean[] mask, int open, char openCh, char closeCh) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (!mask[i]) continue;
            char c = text.charAt(i);
            if (c == openCh) depth++;
            else if (c == closeCh) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static int firstOpenBrace(String text, boolean[] mask, int from) {
        for (int i = from; i < text.length(); i++) if (mask[i] && text.charAt(i) == '{') return i;
        return -1;
    }

    /** 从注解行向上并入紧邻的连续注解行，空行或非注解行即止。 */
    private static int expandToAnnotationBlock(String text, int start) {
        while (start > 0) {
            int terminatorEnd = start;
            int t = terminatorEnd;
            while (t > 0 && (text.charAt(t - 1) == '\n' || text.charAt(t - 1) == '\r')) t--;
            if (t == terminatorEnd) return start;
            int previousStart = lineStart(text, t - 1);
            String previous = text.substring(previousStart, t).trim();
            if (previous.startsWith("@")) { start = previousStart; continue; }
            return start;
        }
        return start;
    }

    private static int lineStart(String text, int position) {
        int i = position;
        while (i > 0 && text.charAt(i - 1) != '\n' && text.charAt(i - 1) != '\r') i--;
        return i;
    }

    /** 吞掉闭括号后的行内空白与一个换行；同行还有内容则不动。 */
    private static int endThroughLineTerminator(String text, int from) {
        int i = from;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        if (i < text.length() && (text.charAt(i) == '\n' || text.charAt(i) == '\r')) {
            if (text.charAt(i) == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') return i + 2;
            return i + 1;
        }
        return i;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private static String splice(String text, int start, int end) {
        String joined = text.substring(0, start) + text.substring(end);
        int from = Math.max(0, start - 1);
        int to = Math.min(joined.length(), start + 300);
        Matcher matcher = BLANK_RUN.matcher(joined.substring(from, to));
        return joined.substring(0, from) + matcher.replaceAll("$1") + joined.substring(to);
    }

    private static boolean hasTestAnnotation(String text, boolean[] mask) {
        for (String annotation : TEST_ANNOTATIONS) {
            int searchFrom = 0;
            while (true) {
                int at = text.indexOf(annotation, searchFrom);
                if (at < 0) break;
                searchFrom = at + 1;
                int nameEnd = at + annotation.length();
                if (nameEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(nameEnd))) continue;
                if (inMask(mask, at, nameEnd)) return true;
            }
        }
        return false;
    }

    /** 删除空类文件后向上清理空目录，止于测试源码根。 */
    private static void deleteEmptyParents(Path directory, Path stop) {
        Path current = directory;
        while (current != null && current.startsWith(stop) && !current.equals(stop)) {
            try { Files.delete(current); } catch (IOException e) { return; }
            current = current.getParent();
        }
    }
}
