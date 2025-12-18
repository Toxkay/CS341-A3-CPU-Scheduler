package org.json;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JSONObject {
    private final Map<String, Object> map;

    public JSONObject() {
        this.map = new HashMap<>();
    }

    public JSONObject(String json) {
        try {
            JSONTokener tokener = new JSONTokener(json);
            Object value = tokener.parseValue();
            if (!(value instanceof JSONObject obj)) {
                throw new JSONException("Root value is not a JSON object");
            }
            this.map = obj.map;
            tokener.skipWhitespace();
            if (!tokener.isAtEnd()) {
                throw new JSONException("Trailing characters after JSON document");
            }
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            throw new JSONException("Invalid JSON", e);
        }
    }

    JSONObject(Map<String, Object> map) {
        this.map = map;
    }

    public String getString(String key) {
        Object v = get(key);
        if (v instanceof String s) return s;
        throw new JSONException("JSONObject['" + key + "'] is not a String");
    }

    public int getInt(String key) {
        Object v = get(key);
        if (v instanceof Number n) return n.intValue();
        throw new JSONException("JSONObject['" + key + "'] is not a Number");
    }

    public double getDouble(String key) {
        Object v = get(key);
        if (v instanceof Number n) return n.doubleValue();
        throw new JSONException("JSONObject['" + key + "'] is not a Number");
    }

    public JSONObject getJSONObject(String key) {
        Object v = get(key);
        if (v instanceof JSONObject o) return o;
        throw new JSONException("JSONObject['" + key + "'] is not a JSONObject");
    }

    public JSONArray getJSONArray(String key) {
        Object v = get(key);
        if (v instanceof JSONArray a) return a;
        throw new JSONException("JSONObject['" + key + "'] is not a JSONArray");
    }

    public Object get(String key) {
        if (!map.containsKey(key)) {
            throw new JSONException("JSONObject has no key: " + key);
        }
        return map.get(key);
    }

    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(map);
    }

    /**
     * Very small JSON tokenizer/parser for the assignment test inputs.
     */
    static final class JSONTokener {
        private final String s;
        private int i;

        JSONTokener(String s) {
            this.s = s;
            this.i = 0;
        }

        boolean isAtEnd() {
            return i >= s.length();
        }

        void skipWhitespace() {
            while (!isAtEnd()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    i++;
                    continue;
                }
                break;
            }
        }

        char peek() {
            if (isAtEnd()) return '\0';
            return s.charAt(i);
        }

        char next() {
            if (isAtEnd()) throw new JSONException("Unexpected end of input");
            return s.charAt(i++);
        }

        void expect(char expected) {
            skipWhitespace();
            char c = next();
            if (c != expected) {
                throw new JSONException("Expected '" + expected + "' but got '" + c + "'");
            }
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            if (c == 't') return parseLiteral("true", Boolean.TRUE);
            if (c == 'f') return parseLiteral("false", Boolean.FALSE);
            if (c == 'n') return parseLiteral("null", null);
            throw new JSONException("Unexpected character: '" + c + "'");
        }

        private Object parseLiteral(String literal, Object value) {
            for (int k = 0; k < literal.length(); k++) {
                if (isAtEnd() || s.charAt(i + k) != literal.charAt(k)) {
                    throw new JSONException("Expected literal: " + literal);
                }
            }
            i += literal.length();
            return value;
        }

        private JSONObject parseObject() {
            expect('{');
            skipWhitespace();

            Map<String, Object> m = new HashMap<>();
            if (peek() == '}') {
                next();
                return new JSONObject(m);
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                m.put(key, value);

                skipWhitespace();
                char c = next();
                if (c == '}') break;
                if (c != ',') {
                    throw new JSONException("Expected ',' or '}' in object but got '" + c + "'");
                }
            }

            return new JSONObject(m);
        }

        private JSONArray parseArray() {
            expect('[');
            skipWhitespace();

            java.util.List<Object> list = new java.util.ArrayList<>();
            if (peek() == ']') {
                next();
                return new JSONArray(list);
            }

            while (true) {
                Object value = parseValue();
                list.add(value);

                skipWhitespace();
                char c = next();
                if (c == ']') break;
                if (c != ',') {
                    throw new JSONException("Expected ',' or ']' in array but got '" + c + "'");
                }
            }

            return new JSONArray(list);
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (isAtEnd()) throw new JSONException("Unterminated string");
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    if (isAtEnd()) throw new JSONException("Unterminated escape");
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw new JSONException("Invalid unicode escape");
                            String hex = s.substring(i, i + 4);
                            try {
                                int code = Integer.parseInt(hex, 16);
                                sb.append((char) code);
                            } catch (NumberFormatException ex) {
                                throw new JSONException("Invalid unicode escape: \\u" + hex);
                            }
                            i += 4;
                        }
                        default -> throw new JSONException("Invalid escape: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Number parseNumber() {
            skipWhitespace();
            int start = i;
            if (peek() == '-') i++;
            while (true) {
                char c = peek();
                if (c >= '0' && c <= '9') {
                    i++;
                } else {
                    break;
                }
            }
            boolean isFloat = false;
            if (peek() == '.') {
                isFloat = true;
                i++;
                while (true) {
                    char c = peek();
                    if (c >= '0' && c <= '9') {
                        i++;
                    } else {
                        break;
                    }
                }
            }
            char c = peek();
            if (c == 'e' || c == 'E') {
                isFloat = true;
                i++;
                c = peek();
                if (c == '+' || c == '-') i++;
                while (true) {
                    char d = peek();
                    if (d >= '0' && d <= '9') {
                        i++;
                    } else {
                        break;
                    }
                }
            }

            String num = s.substring(start, i);
            try {
                if (isFloat) {
                    return Double.parseDouble(num);
                }
                long l = Long.parseLong(num);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            } catch (NumberFormatException ex) {
                throw new JSONException("Invalid number: " + num);
            }
        }
    }
}
