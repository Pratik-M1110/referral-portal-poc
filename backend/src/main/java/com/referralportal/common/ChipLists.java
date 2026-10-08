package com.referralportal.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Chip lists (skills, roles) are stored in MySQL JSON columns; the allowed characters make hand-built JSON safe. */
public final class ChipLists {

    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N} .+#/&-]{1,30}");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    private ChipLists() {}

    public static List<String> clean(List<String> input, int maxItems, String field) {
        if (input == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : input) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!ALLOWED.matcher(value).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " contains an invalid value.");
            }
            out.add(value);
        }
        if (out.size() > maxItems) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at most " + maxItems + " " + field + ".");
        }
        return new ArrayList<>(out);
    }

    public static String toJson(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(values.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    public static List<String> fromJson(String json) {
        List<String> out = new ArrayList<>();
        if (json == null) {
            return out;
        }
        Matcher m = QUOTED.matcher(json);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
