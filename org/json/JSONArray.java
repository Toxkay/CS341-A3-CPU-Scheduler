package org.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JSONArray {
    final List<Object> values;

    JSONArray(List<Object> values) {
        this.values = values;
    }

    public JSONArray() {
        this.values = new ArrayList<>();
    }

    public int length() {
        return values.size();
    }

    public String getString(int index) {
        Object v = get(index);
        if (v instanceof String s) return s;
        throw new JSONException("JSONArray[" + index + "] is not a String");
    }

    public int getInt(int index) {
        Object v = get(index);
        if (v instanceof Number n) return n.intValue();
        throw new JSONException("JSONArray[" + index + "] is not a Number");
    }

    public double getDouble(int index) {
        Object v = get(index);
        if (v instanceof Number n) return n.doubleValue();
        throw new JSONException("JSONArray[" + index + "] is not a Number");
    }

    public JSONObject getJSONObject(int index) {
        Object v = get(index);
        if (v instanceof JSONObject o) return o;
        throw new JSONException("JSONArray[" + index + "] is not a JSONObject");
    }

    public JSONArray getJSONArray(int index) {
        Object v = get(index);
        if (v instanceof JSONArray a) return a;
        throw new JSONException("JSONArray[" + index + "] is not a JSONArray");
    }

    public Object get(int index) {
        if (index < 0 || index >= values.size()) {
            throw new JSONException("JSONArray index out of bounds: " + index);
        }
        return values.get(index);
    }

    public List<Object> toList() {
        return Collections.unmodifiableList(values);
    }
}
