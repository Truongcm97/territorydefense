package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ServerBlueprintManager {

    public static class ServerBlueprint {
        private final String fileName;
        private final String displayName;
        private final List<TerritoryCore.BlockSnapshot> blocks;
        private final String format;

        public ServerBlueprint(String fileName, String displayName, List<TerritoryCore.BlockSnapshot> blocks, String format) {
            this.fileName = fileName;
            this.displayName = displayName;
            this.blocks = blocks;
            this.format = format;
        }

        public String getFileName() { return fileName; }
        public String getDisplayName() { return displayName; }
        public List<TerritoryCore.BlockSnapshot> getBlocks() { return blocks; }
        public String getFormat() { return format; }
    }

    private final TerritoryDefense plugin;
    private final File folder;
    private final List<ServerBlueprint> blueprints = new ArrayList<>();
    private final Object reloadLock = new Object();

    public ServerBlueprintManager(TerritoryDefense plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "server_blueprints");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public List<ServerBlueprint> getBlueprints() {
        return blueprints;
    }

    public void reload() {
        reload(null);
    }

    public void reload(Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (reloadLock) {
                List<ServerBlueprint> temp = new ArrayList<>();
                if (!folder.exists()) {
                    folder.mkdirs();
                }

                File[] files = folder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory() || !file.exists()) continue;

                        String name = file.getName();
                        String lower = name.toLowerCase();

                        List<TerritoryCore.BlockSnapshot> blocks = null;
                        String format = "UNKNOWN";

                        try {
                            boolean shouldConvert = false;
                            boolean shouldConvertToSchem = false;
                            if (lower.endsWith(".dat")) {
                                blocks = loadDatFormat(file);
                                format = ".DAT (Plugin Binary)";
                            } else if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
                                blocks = loadYmlFormat(file);
                                format = ".YML (Config)";
                                shouldConvert = true;
                            } else if (lower.endsWith(".schem") || lower.endsWith(".schematic")) {
                                blocks = loadSchemFormat(file);
                                format = ".SCHEM (WorldEdit)";
                                shouldConvert = false;
                            } else if (lower.endsWith(".litematic")) {
                                blocks = loadLitematicFormat(file);
                                format = ".LITEMATIC (Litematica)";
                                shouldConvertToSchem = true;
                            }

                            if (blocks != null && !blocks.isEmpty()) {
                                String displayName = name.substring(0, name.lastIndexOf('.'));
                                if (shouldConvert) {
                                    String safeBaseName = displayName.replaceAll("[\\\\/:*?\"<>|]", "_");
                                    File datFile = new File(folder, safeBaseName + ".dat");
                                    try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(datFile)))) {
                                        out.writeInt(blocks.size());
                                        for (TerritoryCore.BlockSnapshot snap : blocks) {
                                            out.writeInt(snap.relX);
                                            out.writeInt(snap.relY);
                                            out.writeInt(snap.relZ);
                                            out.writeUTF(snap.material != null ? snap.material : "");
                                            out.writeUTF(snap.blockData != null ? snap.blockData : "");
                                        }
                                        plugin.getLogger().info("[TD] Da tu dong chuyen doi '" + name + "' sang '" + datFile.getName() + "' (tieu chuan toi uu hoa mượt ma).");
                                        File convertedFile = new File(folder, name + ".converted");
                                        if (file.renameTo(convertedFile)) {
                                            plugin.getLogger().info("[TD] Da sao luu file goc cua ban thanh '" + convertedFile.getName() + "'.");
                                        } else {
                                            file.delete();
                                        }
                                        name = datFile.getName();
                                        displayName = safeBaseName;
                                        format = ".DAT (Plugin Binary)";
                                    } catch (Exception ex) {
                                        plugin.getLogger().severe("[TD] Loi khi chuyen doi tu dong '" + name + "': " + ex.getMessage());
                                    }
                                } else if (shouldConvertToSchem) {
                                    String safeBaseName = displayName.replaceAll("[\\\\/:*?\"<>|]", "_");
                                    File schemFile = new File(folder, safeBaseName + ".schem");
                                    try {
                                        saveAsSchemFormat(schemFile, blocks);
                                        plugin.getLogger().info("[TD] Da tu dong chuyen doi litematic '" + name + "' sang '" + schemFile.getName() + "' (tieu chuan .schem giu nguyen tam Core).");
                                        File convertedFile = new File(folder, name + ".converted");
                                        if (file.renameTo(convertedFile)) {
                                            plugin.getLogger().info("[TD] Da sao luu file litematic goc thanh '" + convertedFile.getName() + "'.");
                                        } else {
                                            file.delete();
                                        }
                                        name = schemFile.getName();
                                        displayName = safeBaseName;
                                        format = ".SCHEM (WorldEdit)";
                                    } catch (Exception ex) {
                                        plugin.getLogger().severe("[TD] Loi khi chuyen doi tu dong litematic sang .schem cho '" + name + "': " + ex.getMessage());
                                    }
                                }
                                temp.add(new ServerBlueprint(name, displayName, blocks, format));
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("[TD] Loi khi nap ban ve server '" + name + "': " + e.getMessage());
                        }
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    blueprints.clear();
                    blueprints.addAll(temp);
                    plugin.getLogger().info("[TD] Da nap thanh cong " + blueprints.size() + " ban ve server.");
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        });
    }


    private List<TerritoryCore.BlockSnapshot> loadDatFormat(File file) throws IOException {
        List<TerritoryCore.BlockSnapshot> snapshot = new ArrayList<>();
        if (!file.exists()) return snapshot;
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(new java.io.FileInputStream(file)))) {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                int relX = in.readInt();
                int relY = in.readInt();
                int relZ = in.readInt();
                String material = in.readUTF();
                String blockData = in.readUTF();
                snapshot.add(new TerritoryCore.BlockSnapshot(relX, relY, relZ, 
                    material.isEmpty() ? null : material, 
                    blockData.isEmpty() ? null : blockData));
            }
        }
        return snapshot;
    }

    private List<TerritoryCore.BlockSnapshot> loadYmlFormat(File file) {
        List<TerritoryCore.BlockSnapshot> snapshot = new ArrayList<>();
        if (!file.exists()) return snapshot;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("blocks")) {
            List<?> list = config.getList("blocks");
            if (list != null) {
                for (Object obj : list) {
                    if (obj instanceof java.util.Map<?, ?> map) {
                        try {
                            int relX = ((Number) map.get("relX")).intValue();
                            int relY = ((Number) map.get("relY")).intValue();
                            int relZ = ((Number) map.get("relZ")).intValue();
                            String material = (String) map.get("material");
                            String blockData = map.containsKey("blockData") ? (String) map.get("blockData") : "";
                            snapshot.add(new TerritoryCore.BlockSnapshot(relX, relY, relZ, material, blockData));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return snapshot;
    }

    public List<TerritoryCore.BlockSnapshot> loadSchemFormat(File file) {
        List<TerritoryCore.BlockSnapshot> snapshots = new ArrayList<>();
        if (!file.exists()) return snapshots;
        try (java.io.DataInputStream dis = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(new java.io.FileInputStream(file)))) {
            byte rootType = dis.readByte();
            if (rootType != 10) { // Must be COMPOUND
                throw new java.io.IOException("Root tag is not Compound");
            }
            dis.readUTF(); // Skip root tag name
            java.util.Map<String, Object> root = parseNbtCompound(dis);

            // Support Sponge V3 where everything is nested under a "Schematic" tag
            java.util.Map<String, Object> targetCompound = root;
            if (root.containsKey("Schematic") && root.get("Schematic") instanceof java.util.Map) {
                targetCompound = (java.util.Map<String, Object>) root.get("Schematic");
            }

            short width = targetCompound.containsKey("Width") ? ((Number) targetCompound.get("Width")).shortValue() : 0;
            short height = targetCompound.containsKey("Height") ? ((Number) targetCompound.get("Height")).shortValue() : 0;
            short length = targetCompound.containsKey("Length") ? ((Number) targetCompound.get("Length")).shortValue() : 0;

            if (width <= 0 || height <= 0 || length <= 0) {
                plugin.getLogger().warning("[TD] Kich thuoc ban ve khong hop le trong " + file.getName() + " (W:" + width + ", H:" + height + ", L:" + length + ")");
                return snapshots;
            }

            int[] offset = (int[]) targetCompound.get("Offset");
            int ox = 0, oy = 0, oz = 0;
            if (offset != null && offset.length >= 3) {
                ox = offset[0];
                oy = offset[1];
                oz = offset[2];
            }

            byte[] blockDataBytes = null;
            java.util.Map<String, Object> palette = null;

            if (targetCompound.containsKey("BlockData")) {
                blockDataBytes = (byte[]) targetCompound.get("BlockData");
            }
            if (targetCompound.containsKey("Palette")) {
                palette = (java.util.Map<String, Object>) targetCompound.get("Palette");
            }

            // Sponge V3 blocks container
            if (targetCompound.containsKey("Blocks") && targetCompound.get("Blocks") instanceof java.util.Map) {
                java.util.Map<String, Object> blocksTag = (java.util.Map<String, Object>) targetCompound.get("Blocks");
                if (blockDataBytes == null && blocksTag.containsKey("Data")) {
                    blockDataBytes = (byte[]) blocksTag.get("Data");
                }
                if (palette == null && blocksTag.containsKey("Palette")) {
                    palette = (java.util.Map<String, Object>) blocksTag.get("Palette");
                }
            }

            if (blockDataBytes == null || palette == null) {
                plugin.getLogger().warning("[TD] Thieu du lieu BlockData/Data hoac Palette trong " + file.getName());
                return snapshots;
            }

            // Invert the palette to map ID -> blockState string
            java.util.Map<Integer, String> idToState = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, Object> entry : palette.entrySet()) {
                if (entry.getValue() instanceof Number num) {
                    idToState.put(num.intValue(), entry.getKey());
                }
            }

            // Read VarInts from BlockData securely (preventing sign extension issues)
            int[] blockIds = new int[width * height * length];
            int byteIndex = 0;
            int blockIndex = 0;
            while (byteIndex < blockDataBytes.length && blockIndex < blockIds.length) {
                int value = 0;
                int size = 0;
                int b;
                while (true) {
                    b = blockDataBytes[byteIndex++] & 0xFF;
                    value |= (b & 0x7F) << (size * 7);
                    size++;
                    if (size > 5) {
                        throw new java.io.IOException("VarInt too big in BlockData");
                    }
                    if ((b & 0x80) == 0) {
                        break;
                    }
                }
                blockIds[blockIndex++] = value;
            }

            // Reconstruct coordinates
            for (int index = 0; index < blockIndex; index++) {
                int id = blockIds[index];
                String blockStateStr = idToState.get(id);
                if (blockStateStr == null || blockStateStr.equalsIgnoreCase("minecraft:air") || blockStateStr.equalsIgnoreCase("air")) {
                    continue;
                }

                int x = index % width;
                int z = (index / width) % length;
                int y = index / (width * length);

                int relX = x + ox;
                int relY = y + oy;
                int relZ = z + oz;

                String materialName;
                String blockData;
                int bracketIdx = blockStateStr.indexOf('[');
                if (bracketIdx != -1) {
                    materialName = blockStateStr.substring(0, bracketIdx).toUpperCase().replace("MINECRAFT:", "");
                    blockData = blockStateStr;
                } else {
                    materialName = blockStateStr.toUpperCase().replace("MINECRAFT:", "");
                    blockData = blockStateStr;
                }

                snapshots.add(new TerritoryCore.BlockSnapshot(relX, relY, relZ, materialName, blockData));
            }
            plugin.getLogger().info("[TD] Da nap thanh cong " + snapshots.size() + " khoi tu file .schem: " + file.getName() + " (W:" + width + ", H:" + height + ", L:" + length + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("[TD] Loi khi doc schematic doc la cho file " + file.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return snapshots;
    }

    public List<TerritoryCore.BlockSnapshot> loadLitematicFormat(File file) throws IOException {
        try {
            List<TerritoryCore.BlockSnapshot> snapshots = new ArrayList<>();
            if (!file.exists()) return snapshots;

            java.util.Map<String, Object> root = null;
            // Thử mở tệp dưới dạng nén GZIP trước
            try (java.io.DataInputStream dis = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(new java.io.FileInputStream(file)))) {
            byte rootType = dis.readByte();
            if (rootType == 10) { // COMPOUND
                dis.readUTF(); // Skip root tag name
                root = parseNbtCompound(dis);
            }
        } catch (java.util.zip.ZipException e) {
            // Fallback: Một số file litematic không được nén GZIP mà là file NBT thô không nén
            try (java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(file)))) {
                byte rootType = dis.readByte();
                if (rootType != 10) {
                    throw new java.io.IOException("Root tag is not NBT Compound (RAW format): " + rootType);
                }
                dis.readUTF(); // Skip root tag name
                root = parseNbtCompound(dis);
            }
        }

        if (root == null) {
            throw new java.io.IOException("Failed to parse NBT root Compound from " + file.getName());
        }

        if (!root.containsKey("Regions")) {
            throw new java.io.IOException("Invalid Litematic file: missing Regions tag");
        }

            java.util.Map<String, Object> regions = (java.util.Map<String, Object>) root.get("Regions");
            for (java.util.Map.Entry<String, Object> regionEntry : regions.entrySet()) {
                if (!(regionEntry.getValue() instanceof java.util.Map)) continue;
                java.util.Map<String, Object> region = (java.util.Map<String, Object>) regionEntry.getValue();

                // Đọc kích thước vùng (có thể âm nếu vùng chọn bị kéo ngược)
                java.util.Map<String, Object> sizeTag = (java.util.Map<String, Object>) region.get("Size");
                int width = Math.abs(((Number) sizeTag.get("x")).intValue());
                int height = Math.abs(((Number) sizeTag.get("y")).intValue());
                int length = Math.abs(((Number) sizeTag.get("z")).intValue());

                // Đọc vị trí tương đối
                java.util.Map<String, Object> posTag = (java.util.Map<String, Object>) region.get("Position");
                int startX = ((Number) posTag.get("x")).intValue();
                int startY = ((Number) posTag.get("y")).intValue();
                int startZ = ((Number) posTag.get("z")).intValue();

                // Đọc BlockStatePalette
                java.util.List<Object> paletteList = (java.util.List<Object>) region.get("BlockStatePalette");
                if (paletteList == null) continue;

                java.util.Map<Integer, String> idToState = new java.util.HashMap<>();
                for (int i = 0; i < paletteList.size(); i++) {
                    java.util.Map<String, Object> entry = (java.util.Map<String, Object>) paletteList.get(i);
                    String name = (String) entry.get("Name");
                    if (entry.containsKey("Properties") && entry.get("Properties") instanceof java.util.Map) {
                        java.util.Map<String, Object> props = (java.util.Map<String, Object>) entry.get("Properties");
                        java.util.List<String> propList = new java.util.ArrayList<>();
                        for (java.util.Map.Entry<String, Object> prop : props.entrySet()) {
                            propList.add(prop.getKey() + "=" + prop.getValue().toString());
                        }
                        name += "[" + String.join(",", propList) + "]";
                    }
                    idToState.put(i, name);
                }

                long[] blockStates = (long[]) region.get("BlockStates");
                if (blockStates == null || blockStates.length == 0) {
                    continue; // Vùng trống hoàn toàn
                }

                // Giải nén mảng bit BlockStates long[]
                int paletteSize = paletteList.size();
                int bitsPerBlock = Math.max(2, (int) Math.ceil(Math.log(paletteSize) / Math.log(2)));
                long mask = (1L << bitsPerBlock) - 1;

                int blocksInRegion = width * height * length;
                for (int index = 0; index < blocksInRegion; index++) {
                    long bitIndex = (long) index * bitsPerBlock;
                    int startLongIndex = (int) (bitIndex / 64);
                    int startBitOffset = (int) (bitIndex % 64);
                    
                    int id;
                    if (startBitOffset + bitsPerBlock <= 64) {
                        id = (int) ((blockStates[startLongIndex] >>> startBitOffset) & mask);
                    } else {
                        // Khối này bị chia cắt giữa 2 phần tử long liên tiếp
                        int bitsFromFirst = 64 - startBitOffset;
                        int bitsFromSecond = bitsPerBlock - bitsFromFirst;
                        long firstPart = (blockStates[startLongIndex] >>> startBitOffset) & ((1L << bitsFromFirst) - 1);
                        long secondPart = blockStates[startLongIndex + 1] & ((1L << bitsFromSecond) - 1);
                        id = (int) (firstPart | (secondPart << bitsFromFirst));
                    }

                    String blockStateStr = idToState.get(id);
                    if (blockStateStr == null || blockStateStr.equalsIgnoreCase("minecraft:air") || blockStateStr.equalsIgnoreCase("air")) {
                        continue;
                    }

                    // Toạ độ gốc trong litematic là (X, Y, Z) nằm trong vùng
                    int x = index % width;
                    int z = (index / width) % length;
                    int y = index / (width * length);

                    int relX = startX + x;
                    int relY = startY + y;
                    int relZ = startZ + z;

                    String materialName;
                    String blockData;
                    int bracketIdx = blockStateStr.indexOf('[');
                    if (bracketIdx != -1) {
                        materialName = blockStateStr.substring(0, bracketIdx).toUpperCase().replace("MINECRAFT:", "");
                        blockData = blockStateStr;
                    } else {
                        materialName = blockStateStr.toUpperCase().replace("MINECRAFT:", "");
                        blockData = blockStateStr;
                    }

                    snapshots.add(new TerritoryCore.BlockSnapshot(relX, relY, relZ, materialName, blockData));
                }
            }

            // Tự động căn chỉnh Core (Lõi) làm trung tâm (0, 0, 0)
            // Lõi (Conduit) thường được dùng làm mốc, nếu không thấy conduit, ta sẽ tìm khối quan trọng nhất hoặc lấy tâm toán học.
            int coreX = 0, coreY = 0, coreZ = 0;
            boolean foundCore = false;
            for (TerritoryCore.BlockSnapshot snap : snapshots) {
                if (snap.material != null && snap.material.equalsIgnoreCase("CONDUIT")) {
                    coreX = snap.relX;
                    coreY = snap.relY;
                    coreZ = snap.relZ;
                    foundCore = true;
                    break;
                }
            }

            if (!foundCore && !snapshots.isEmpty()) {
                // Nếu không tìm thấy Conduit làm tâm, dùng tâm toán học của hộp bao quanh
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (TerritoryCore.BlockSnapshot snap : snapshots) {
                    if (snap.relX < minX) minX = snap.relX;
                    if (snap.relX > maxX) maxX = snap.relX;
                    if (snap.relY < minY) minY = snap.relY;
                    if (snap.relY > maxY) maxY = snap.relY;
                    if (snap.relZ < minZ) minZ = snap.relZ;
                    if (snap.relZ > maxZ) maxZ = snap.relZ;
                }
                coreX = minX + (maxX - minX) / 2;
                coreY = minY; // Đáy của bản vẽ làm Y trung tâm
                coreZ = minZ + (maxZ - minZ) / 2;
            }

            // Dịch chuyển toàn bộ toạ độ khối sao cho Lõi là gốc (0,0,0)
            List<TerritoryCore.BlockSnapshot> offsetSnapshots = new ArrayList<>();
            for (TerritoryCore.BlockSnapshot snap : snapshots) {
                offsetSnapshots.add(new TerritoryCore.BlockSnapshot(
                    snap.relX - coreX,
                    snap.relY - coreY,
                    snap.relZ - coreZ,
                    snap.material,
                    snap.blockData
                ));
            }

            plugin.getLogger().info("[TD] Da nap thanh cong " + offsetSnapshots.size() + " khoi tu file .litematic: " + file.getName() + " (lay tam Core de doi xung)");
            return offsetSnapshots;
        } catch (Exception e) {
            plugin.getLogger().severe("[TD] Loi khi doc file .litematic " + file.getName() + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void saveAsSchemFormat(File file, List<TerritoryCore.BlockSnapshot> snapshot) throws IOException {
        if (snapshot == null || snapshot.isEmpty()) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (TerritoryCore.BlockSnapshot snap : snapshot) {
            if (snap.relX < minX) minX = snap.relX;
            if (snap.relX > maxX) maxX = snap.relX;
            if (snap.relY < minY) minY = snap.relY;
            if (snap.relY > maxY) maxY = snap.relY;
            if (snap.relZ < minZ) minZ = snap.relZ;
            if (snap.relZ > maxZ) maxZ = snap.relZ;
        }

        short width = (short) (maxX - minX + 1);
        short height = (short) (maxY - minY + 1);
        short length = (short) (maxZ - minZ + 1);

        Map<String, Integer> palette = new HashMap<>();
        List<String> paletteList = new ArrayList<>();
        palette.put("minecraft:air", 0);
        paletteList.add("minecraft:air");

        int nextId = 1;
        for (TerritoryCore.BlockSnapshot snap : snapshot) {
            String material = snap.material != null ? snap.material.toLowerCase() : "air";
            if (!material.contains(":")) material = "minecraft:" + material;
            String stateStr = material;
            if (snap.blockData != null && !snap.blockData.isEmpty()) {
                if (snap.blockData.contains("[")) {
                    stateStr = snap.blockData.toLowerCase();
                    if (!stateStr.contains(":")) {
                        stateStr = "minecraft:" + stateStr;
                    }
                } else {
                    stateStr = material + "[" + snap.blockData.toLowerCase() + "]";
                }
            }
            if (!palette.containsKey(stateStr)) {
                palette.put(stateStr, nextId);
                paletteList.add(stateStr);
                nextId++;
            }
        }

        int[] blockIds = new int[width * height * length];
        java.util.Arrays.fill(blockIds, 0);

        for (TerritoryCore.BlockSnapshot snap : snapshot) {
            String material = snap.material != null ? snap.material.toLowerCase() : "air";
            if (!material.contains(":")) material = "minecraft:" + material;
            String stateStr = material;
            if (snap.blockData != null && !snap.blockData.isEmpty()) {
                if (snap.blockData.contains("[")) {
                    stateStr = snap.blockData.toLowerCase();
                    if (!stateStr.contains(":")) {
                        stateStr = "minecraft:" + stateStr;
                    }
                } else {
                    stateStr = material + "[" + snap.blockData.toLowerCase() + "]";
                }
            }
            int id = palette.getOrDefault(stateStr, 0);
            int rx = snap.relX - minX;
            int ry = snap.relY - minY;
            int rz = snap.relZ - minZ;
            int index = (ry * length + rz) * width + rx;
            if (index >= 0 && index < blockIds.length) {
                blockIds[index] = id;
            }
        }

        java.io.ByteArrayOutputStream dataBytes = new java.io.ByteArrayOutputStream();
        for (int id : blockIds) {
            int val = id;
            while ((val & 0xFFFFFF80) != 0L) {
                dataBytes.write((val & 0x7F) | 0x80);
                val >>>= 7;
            }
            dataBytes.write(val & 0x7F);
        }
        byte[] blockData = dataBytes.toByteArray();

        try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(file)))) {
            out.writeByte(10);
            out.writeUTF("Schematic");

            out.writeByte(2);
            out.writeUTF("Width");
            out.writeShort(width);

            out.writeByte(2);
            out.writeUTF("Height");
            out.writeShort(height);

            out.writeByte(2);
            out.writeUTF("Length");
            out.writeShort(length);

            out.writeByte(11);
            out.writeUTF("Offset");
            out.writeInt(3);
            out.writeInt(minX);
            out.writeInt(minY);
            out.writeInt(minZ);

            out.writeByte(7);
            out.writeUTF("BlockData");
            out.writeInt(blockData.length);
            out.write(blockData);

            out.writeByte(10);
            out.writeUTF("Palette");
            for (int i = 0; i < paletteList.size(); i++) {
                String stateStr = paletteList.get(i);
                out.writeByte(3);
                out.writeUTF(stateStr);
                out.writeInt(i);
            }
            out.writeByte(0);
            out.writeByte(0);
        }
    }

    private static java.util.Map<String, Object> parseNbtCompound(java.io.DataInputStream dis) throws java.io.IOException {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        while (true) {
            byte type = dis.readByte();
            if (type == 0) { // TAG_End
                break;
            }
            String name = dis.readUTF();
            Object value = readTagValue(type, dis);
            map.put(name, value);
        }
        return map;
    }

    private static Object readTagValue(byte type, java.io.DataInputStream dis) throws java.io.IOException {
        switch (type) {
            case 1: return dis.readByte(); // TAG_Byte
            case 2: return dis.readShort(); // TAG_Short
            case 3: return dis.readInt(); // TAG_Int
            case 4: return dis.readLong(); // TAG_Long
            case 5: return dis.readFloat(); // TAG_Float
            case 6: return dis.readDouble(); // TAG_Double
            case 7: // TAG_Byte_Array
                int len = dis.readInt();
                byte[] bytes = new byte[len];
                dis.readFully(bytes);
                return bytes;
            case 8: return dis.readUTF(); // TAG_String
            case 9: // TAG_List
                byte listType = dis.readByte();
                int listLen = dis.readInt();
                java.util.List<Object> list = new java.util.ArrayList<>();
                for (int i = 0; i < listLen; i++) {
                    list.add(readTagValue(listType, dis));
                }
                return list;
            case 10: // TAG_Compound
                return parseNbtCompound(dis);
            case 11: // TAG_Int_Array
                int arrayLen = dis.readInt();
                int[] intArray = new int[arrayLen];
                for (int i = 0; i < arrayLen; i++) {
                    intArray[i] = dis.readInt();
                }
                return intArray;
            case 12: // TAG_Long_Array
                int longArrayLen = dis.readInt();
                long[] longArray = new long[longArrayLen];
                for (int i = 0; i < longArrayLen; i++) {
                    longArray[i] = dis.readLong();
                }
                return longArray;
            default:
                throw new java.io.IOException("Unsupported NBT tag type: " + type);
        }
    }

    public boolean exportAsDat(String name, List<TerritoryCore.BlockSnapshot> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return false;
        
        String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".dat";
        File file = new File(folder, safeName);
        
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(file)))) {
            out.writeInt(snapshot.size());
            for (TerritoryCore.BlockSnapshot snap : snapshot) {
                out.writeInt(snap.relX);
                out.writeInt(snap.relY);
                out.writeInt(snap.relZ);
                out.writeUTF(snap.material != null ? snap.material : "");
                out.writeUTF(snap.blockData != null ? snap.blockData : "");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[TD] Loi khi xuat ban ve dat: " + e.getMessage());
            return false;
        }
        reload();
        return true;
    }
}
