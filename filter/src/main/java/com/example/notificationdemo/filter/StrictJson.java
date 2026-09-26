package com.example.notificationdemo.filter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small RFC 8259 JSON reader/writer used by the platform-independent protocol tests. */
public final class StrictJson {
    private StrictJson() {}

    public static Object parse(String source) {
        if (source == null) throw invalid();
        Parser parser = new Parser(source);
        Object result = parser.value(0);
        parser.whitespace();
        if (parser.index != source.length()) throw invalid();
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String source) {
        Object value = parse(source);
        if (!(value instanceof Map)) throw invalid();
        return (Map<String, Object>) value;
    }

    public static String stringify(Object value) {
        StringBuilder result = new StringBuilder();
        write(value, result, 0);
        return result.toString();
    }

    private static void write(Object value, StringBuilder output, int depth) {
        if (depth > 24) throw invalid();
        if (value == null) { output.append("null"); return; }
        if (value instanceof String) { writeString((String) value, output); return; }
        if (value instanceof Boolean) { output.append(value); return; }
        if (value instanceof Number) {
            if (value instanceof Double && !Double.isFinite((Double) value)) throw invalid();
            if (value instanceof Float && !Float.isFinite((Float) value)) throw invalid();
            String number = value.toString();
            Object parsed = parse(number);
            if (!(parsed instanceof Number)) throw invalid();
            output.append(number);
            return;
        }
        if (value instanceof Map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) throw invalid();
                if (!first) output.append(',');
                first = false;
                writeString((String) entry.getKey(), output);
                output.append(':');
                write(entry.getValue(), output, depth + 1);
            }
            output.append('}');
            return;
        }
        if (value instanceof Iterable) {
            output.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) output.append(',');
                first = false;
                write(item, output, depth + 1);
            }
            output.append(']');
            return;
        }
        throw invalid();
    }

    private static void writeString(String value, StringBuilder output) {
        final char[] hex = "0123456789abcdef".toCharArray();
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') output.append('\\').append(c);
            else if (c < 0x20) output.append("\\u00").append(hex[(c >>> 4) & 15]).append(hex[c & 15]);
            else output.append(c);
        }
        output.append('"');
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid strict JSON");
    }

    private static final class Parser {
        final String source;
        int index;
        Parser(String source) { this.source = source; }

        void whitespace() {
            while (index < source.length()) {
                char c = source.charAt(index);
                if (c != ' ' && c != '\t' && c != '\r' && c != '\n') break;
                index++;
            }
        }

        Object value(int depth) {
            if (depth > 24) throw invalid();
            whitespace();
            if (index >= source.length()) throw invalid();
            char c = source.charAt(index);
            if (c == '{') return object(depth + 1);
            if (c == '[') return array(depth + 1);
            if (c == '"') return string();
            if (c == 't') { literal("true"); return Boolean.TRUE; }
            if (c == 'f') { literal("false"); return Boolean.FALSE; }
            if (c == 'n') { literal("null"); return null; }
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw invalid();
        }

        Map<String, Object> object(int depth) {
            index++;
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return result;
            while (true) {
                whitespace();
                if (index >= source.length() || source.charAt(index) != '"') throw invalid();
                String key = string();
                if (result.containsKey(key)) throw invalid();
                whitespace();
                if (!take(':')) throw invalid();
                result.put(key, value(depth));
                whitespace();
                if (take('}')) return result;
                if (!take(',')) throw invalid();
            }
        }

        List<Object> array(int depth) {
            index++;
            ArrayList<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value(depth));
                whitespace();
                if (take(']')) return result;
                if (!take(',')) throw invalid();
            }
        }

        String string() {
            index++;
            StringBuilder value = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return value.toString();
                if (c < 0x20) throw invalid();
                if (c != '\\') { value.append(c); continue; }
                if (index >= source.length()) throw invalid();
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"': case '\\': case '/': value.append(escaped); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        if (index + 4 > source.length()) throw invalid();
                        int unicode = 0;
                        for (int i = 0; i < 4; i++) {
                            char digit = source.charAt(index++);
                            int digitValue;
                            if (digit >= '0' && digit <= '9') digitValue = digit - '0';
                            else if (digit >= 'a' && digit <= 'f') digitValue = digit - 'a' + 10;
                            else if (digit >= 'A' && digit <= 'F') digitValue = digit - 'A' + 10;
                            else throw invalid();
                            unicode = (unicode << 4) | digitValue;
                        }
                        value.append((char) unicode);
                        break;
                    default: throw invalid();
                }
            }
            throw invalid();
        }

        BigDecimal number() {
            int start = index;
            take('-');
            if (take('0')) {
                if (isDigit()) throw invalid();
            } else {
                if (!isDigit() || source.charAt(index) == '0') throw invalid();
                while (isDigit()) index++;
            }
            if (take('.')) {
                if (!isDigit()) throw invalid();
                while (isDigit()) index++;
            }
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                if (!isDigit()) throw invalid();
                while (isDigit()) index++;
            }
            try { return new BigDecimal(source.substring(start, index)); }
            catch (NumberFormatException failure) { throw invalid(); }
        }

        boolean isDigit() {
            return index < source.length() && source.charAt(index) >= '0' && source.charAt(index) <= '9';
        }
        boolean take(char expected) {
            if (index >= source.length() || source.charAt(index) != expected) return false;
            index++;
            return true;
        }
        void literal(String expected) {
            if (!source.startsWith(expected, index)) throw invalid();
            index += expected.length();
        }
    }
}
