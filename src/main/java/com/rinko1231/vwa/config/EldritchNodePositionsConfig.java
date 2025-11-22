package com.rinko1231.vwa.config;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class EldritchNodePositionsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, NodePos>>() {}.getType();

    private static Map<String, NodePos> positions = new HashMap<>();

    private static File getFile() {
        return new File(Minecraft.getInstance().gameDirectory, "config/vwa_eldritch_research_nodes.json");
    }

    public static class NodePos {
        public int x, y;
        public NodePos(int x, int y) { this.x = x; this.y = y; }
    }

    public static void load() {
        File file = getFile();
        if (!file.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            positions = GSON.fromJson(reader, TYPE);
            if (positions == null) positions = new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        File file = getFile();
        file.getParentFile().mkdirs();

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(positions, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static NodePos get(String spellId) {
        return positions.get(spellId);
    }

    public static void set(String spellId, int x, int y) {
        positions.put(spellId, new NodePos(x, y));
    }
}
