package com.myepg.player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class XtreamClient {
    static final class Item {
        String id = "";
        String epgId = "";
        String name = "";
        String category = "Altro";
        String logo = "";
        String url = "";
    }

    static final class Result {
        final List<Item> items = new ArrayList<>();
        String epgUrl = "";
    }

    private XtreamClient() {}

    static Result load(String rawServer, String username, String password) throws Exception {
        String server = normalizeServer(rawServer);
        String u = enc(username);
        String p = enc(password);

        Map<String, String> categories = new HashMap<>();
        JSONArray categoryArray = new JSONArray(get(server + "/player_api.php?username=" + u + "&password=" + p + "&action=get_live_categories"));
        for (int i = 0; i < categoryArray.length(); i++) {
            JSONObject c = categoryArray.optJSONObject(i);
            if (c == null) continue;
            categories.put(c.optString("category_id"), c.optString("category_name", "Altro"));
        }

        JSONArray streams = new JSONArray(get(server + "/player_api.php?username=" + u + "&password=" + p + "&action=get_live_streams"));
        Result result = new Result();
        for (int i = 0; i < streams.length(); i++) {
            JSONObject s = streams.optJSONObject(i);
            if (s == null) continue;
            String streamId = s.optString("stream_id");
            if (streamId.isEmpty()) continue;
            Item item = new Item();
            item.id = streamId;
            item.epgId = s.optString("epg_channel_id", s.optString("custom_sid", ""));
            item.name = s.optString("name", "Canale");
            item.logo = s.optString("stream_icon", "");
            item.category = categories.getOrDefault(s.optString("category_id"), "Altro");
            String direct = s.optString("direct_source", "");
            item.url = direct.isEmpty()
                    ? server + "/live/" + u + "/" + p + "/" + streamId + ".ts"
                    : direct;
            result.items.add(item);
        }
        result.epgUrl = server + "/xmltv.php?username=" + u + "&password=" + p;
        return result;
    }

    private static String normalizeServer(String s) {
        s = s == null ? "" : s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        return s;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "MYEPG Player/0.3");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        try (InputStream in = c.getInputStream(); BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line);
            return b.toString();
        }
    }
}
