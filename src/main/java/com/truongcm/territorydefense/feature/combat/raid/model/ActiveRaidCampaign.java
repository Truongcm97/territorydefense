package com.truongcm.territorydefense.feature.combat.raid.model;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.raid.AdvancedSpawnMatrix;
import com.truongcm.territorydefense.feature.combat.raid.ui.RaidDisplayManager;
import com.truongcm.territorydefense.feature.combat.raid.rewards.RaidRewardCalculator;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TRẠNG THÁI CHIẾN DỊCH RAID (ACTIVE RAID CAMPAIGN MODEL)
 * Đại diện cho dữ liệu và trạng thái hoạt động của một đợt Raid cụ thể.
 */
public class ActiveRaidCampaign {
    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    public final boolean isPurchased;
    public final int purchasedIndex;
    private int currentWave = 0;
    private final int maxWaves;
    private final AdvancedSpawnMatrix spawnMatrix = new AdvancedSpawnMatrix();

    private final Set<Entity> aliveMobs = Collections.synchronizedSet(new HashSet<>());
    private final List<EntityType> pendingSpawnQueue = Collections.synchronizedList(new ArrayList<>());
    private BukkitRunnable spawnTask = null;
    private volatile boolean spawnCompleted = false;

    private final RaidDisplayManager displayManager;
    private int totalWaveMobs = 0;
    private long waveStartTime = 0;
    private final long waveDurationLimitMillis;
    private final List<TerritoryCore.BlockSnapshot> preRaidSnapshot = new ArrayList<>();

    public final Map<UUID, Integer> waveDirectShards = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> waveHarvestedShards = new ConcurrentHashMap<>();
    public final Map<UUID, Double> waveCoinsEarned = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> waveMobsContributed = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> waveMobsMissed = new ConcurrentHashMap<>();

    public ActiveRaidCampaign(TerritoryDefense plugin, TerritoryCore core, boolean isPurchased, int purchasedIndex) {
        this.plugin = plugin;
        this.core = core;
        this.isPurchased = isPurchased;
        this.purchasedIndex = purchasedIndex;
        int waveMinutes = plugin.getConfig().getInt("raid-settings.wave-duration-limit-minutes", 10);
        this.waveDurationLimitMillis = (long) waveMinutes * 60 * 1000L;
        this.maxWaves = (core.getLevel() == 5) ? 5 : 3;

        // Khởi tạo Display Manager riêng cho Campaign
        this.displayManager = new RaidDisplayManager(plugin, this, core);

        // Vòng lặp quản lý AI quái: Chạy mỗi 1 giây (20 ticks)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getRaidSession() == null || !plugin.getRaidSession().activeCampaigns().containsKey(core.getCoreId())) {
                    cancel();
                    return;
                }
                tickMobAI();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Vòng lặp cập nhật UI: Chạy mỗi 2 giây (40 ticks)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getRaidSession() == null || !plugin.getRaidSession().activeCampaigns().containsKey(core.getCoreId())) {
                    cancel();
                    return;
                }
                displayManager.updateBossBar();
            }
        }.runTaskTimer(plugin, 20L, 40L);
    }

    public void addWaveDirectShards(UUID playerUuid, int amount) {
        waveDirectShards.put(playerUuid, waveDirectShards.getOrDefault(playerUuid, 0) + amount);
    }

    public void addWaveHarvestedShards(UUID playerUuid, int amount) {
        waveHarvestedShards.put(playerUuid, waveHarvestedShards.getOrDefault(playerUuid, 0) + amount);
    }

    public void addWaveCoinsEarned(UUID playerUuid, double amount) {
        waveCoinsEarned.put(playerUuid, waveCoinsEarned.getOrDefault(playerUuid, 0.0) + amount);
    }

    public void incrementWaveMobsContributed(UUID playerUuid) {
        waveMobsContributed.put(playerUuid, waveMobsContributed.getOrDefault(playerUuid, 0) + 1);
    }

    public void incrementWaveMobsMissed(UUID playerUuid) {
        waveMobsMissed.put(playerUuid, waveMobsMissed.getOrDefault(playerUuid, 0) + 1);
    }

    public List<TerritoryCore.BlockSnapshot> getPreRaidSnapshot() {
        return preRaidSnapshot;
    }

    public TerritoryCore getCore() {
        return core;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getMaxWaves() {
        return maxWaves;
    }

    public int getTotalWaveMobs() {
        return totalWaveMobs;
    }

    public long getWaveStartTime() {
        return waveStartTime;
    }

    public long getWaveDurationLimitMillis() {
        return waveDurationLimitMillis;
    }

    public Set<Entity> getAliveMobs() {
        return aliveMobs;
    }

    public List<EntityType> getPendingSpawnQueue() {
        return pendingSpawnQueue;
    }

    public RaidDisplayManager getDisplayManager() {
        return displayManager;
    }

    public void cleanup() {
        displayManager.cleanup();
        if (spawnTask != null) {
            try {
                spawnTask.cancel();
            } catch (Exception ignored) {}
            spawnTask = null;
        }
        pendingSpawnQueue.clear();
        synchronized (aliveMobs) {
            for (Entity mob : aliveMobs) {
                if (mob.isValid()) {
                    removeNoCollision(mob);
                    mob.remove();
                }
            }
            aliveMobs.clear();
        }
    }

    private void removeNoCollision(Entity mob) {
        try {
            org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            org.bukkit.scoreboard.Team team = scoreboard.getTeam("td_raid_team");
            if (team != null) {
                team.removeEntry(mob.getUniqueId().toString());
            }
        } catch (Throwable ignored) {}
    }

    private void applyNoCollision(LivingEntity mob) {
        try {
            org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            org.bukkit.scoreboard.Team team = scoreboard.getTeam("td_raid_team");
            if (team == null) {
                team = scoreboard.registerNewTeam("td_raid_team");
                team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
            }
            team.addEntry(mob.getUniqueId().toString());
        } catch (Throwable ignored) {}
    }

    private boolean isFlyingMob(LivingEntity mob) {
        String name = mob.getType().name();
        return name.equals("VEX") || name.equals("BLAZE") || name.equals("WITHER") ||
               name.equals("ENDER_DRAGON") || name.equals("ALLAY") || name.equals("BAT") ||
               name.equals("BEE") || name.equals("PARROT") || name.equals("PHANTOM") || name.equals("GHAST");
    }

    private boolean isFlyingMobType(EntityType type) {
        String name = type.name();
        return name.equals("VEX") || name.equals("BLAZE") || name.equals("WITHER") ||
               name.equals("ENDER_DRAGON") || name.equals("ALLAY") || name.equals("BAT") ||
               name.equals("BEE") || name.equals("PARROT") || name.equals("PHANTOM") || name.equals("GHAST");
    }

    private void tickMobAI() {
        List<Entity> invalidMobs = new ArrayList<>();
        synchronized (aliveMobs) {
            for (Entity entity : aliveMobs) {
                if (!(entity instanceof org.bukkit.entity.Mob mob) || !mob.isValid()) {
                    invalidMobs.add(entity);
                    continue;
                }

                displayManager.updateMobCustomName(mob);

                Location mobLoc = mob.getLocation();
                Location coreLoc = core.getLocation();
                double distToCore = mobLoc.distance(coreLoc);
                boolean isFlying = isFlyingMob(mob);

                double coreAttackRange = plugin.getConfig().getDouble("raid-settings.core-attack-range", 4.0);
                int mobAiAttackInterval = plugin.getConfig().getInt("raid-settings.mob-ai-attack-interval-ticks", 20);
                double stuckDistThreshold = plugin.getConfig().getDouble("raid-settings.stuck-distance-threshold", 0.2);
                int stuckSecondsThreshold = plugin.getConfig().getInt("raid-settings.stuck-seconds-threshold", 2);
                boolean shouldCheckBlocksAndStuck = (mob.getEntityId() + System.currentTimeMillis() / 1000) % 2 == 0;

                Player nearestPlayer = null;
                double closestPlayerDist = 12.0;
                for (Player p : mob.getWorld().getPlayers()) {
                    if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                        double dist = p.getLocation().distance(mobLoc);
                        if (dist < closestPlayerDist) {
                            closestPlayerDist = dist;
                            nearestPlayer = p;
                        }
                    }
                }

                Location activeTargetLoc = coreLoc;
                if (nearestPlayer != null) {
                    mob.setTarget(nearestPlayer);
                    activeTargetLoc = nearestPlayer.getLocation();
                } else {
                    mob.setTarget(null);
                }

                double distanceToActiveTarget = mobLoc.distance(activeTargetLoc);

                if (isFlying) {
                    if (distanceToActiveTarget > 1.0) {
                        org.bukkit.util.Vector flyDir = activeTargetLoc.toVector().subtract(mobLoc.toVector()).normalize();
                        mob.setVelocity(flyDir.multiply(0.35D));
                    }
                } else {
                    long secondCounter = System.currentTimeMillis() / 1000;
                    boolean shouldPathfind = (mob.getEntityId() + secondCounter) % 3 == 0;

                    if (shouldPathfind) {
                        if (distanceToActiveTarget > 3.0) {
                            if (mob.getType() == EntityType.SLIME || mob.getType() == EntityType.MAGMA_CUBE) {
                                org.bukkit.util.Vector slimeDir = activeTargetLoc.toVector().subtract(mobLoc.toVector()).normalize();
                                float yaw = (float) Math.toDegrees(Math.atan2(-slimeDir.getX(), slimeDir.getZ()));
                                mob.setRotation(yaw, mob.getLocation().getPitch());
                                if (mob.isOnGround()) {
                                    mob.setVelocity(slimeDir.multiply(0.2D).setY(0.3D));
                                }
                            } else {
                                mob.getPathfinder().moveTo(activeTargetLoc, 1.25D);
                            }
                        } else {
                            mob.getPathfinder().moveTo(activeTargetLoc, 1.25D);
                        }
                    }
                }

                if (shouldCheckBlocksAndStuck && !isFlying) {
                    org.bukkit.util.Vector direction = activeTargetLoc.toVector().subtract(mobLoc.toVector()).normalize();
                    boolean isObstructed = false;

                    for (double d = 0.5; d <= 1.5; d += 0.5) {
                        for (double yOffset = 0.0; yOffset <= 2.0; yOffset += 1.0) {
                            Location checkLoc = mobLoc.clone().add(direction.clone().multiply(d)).add(0, yOffset, 0);
                            org.bukkit.block.Block block = checkLoc.getBlock();
                            Material mat = block.getType();

                            if (mat.isSolid() && mat != Material.CONDUIT && mat != Material.BEDROCK) {
                                isObstructed = true;
                                String blockName = mat.name();
                                boolean isGateOrWall = blockName.contains("DOOR") || blockName.contains("GATE") ||
                                                       blockName.contains("WALL") || blockName.contains("FENCE") ||
                                                       blockName.contains("BARS") || blockName.contains("GLASS");

                                int breakTicks = mob.getMetadata("td_break_ticks").stream().findFirst().map(m -> m.asInt()).orElse(0);
                                int blockBreakThreshold = isGateOrWall ? 2 : 4;

                                if (breakTicks >= blockBreakThreshold) {
                                    block.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 18, block.getBlockData());
                                    block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.9f);
                                    block.setType(Material.AIR);
                                    mob.setMetadata("td_break_ticks", new FixedMetadataValue(plugin, 0));
                                    mob.getPathfinder().moveTo(activeTargetLoc, 1.25D);
                                } else {
                                    block.getWorld().spawnParticle(org.bukkit.Particle.CRIT, block.getLocation().add(0.5, 0.5, 0.5), 8);
                                    block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.7f, 1.1f);
                                    mob.setMetadata("td_break_ticks", new FixedMetadataValue(plugin, breakTicks + 1));
                                    mob.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                                }
                                break;
                            }
                        }
                        if (isObstructed) break;
                    }
                }

                double distance = mobLoc.distance(coreLoc);
                if (distance <= coreAttackRange) {
                    int damageTicks = mob.getMetadata("td_attack_ticks").stream().findFirst().map(m -> m.asInt()).orElse(0);
                    if (damageTicks >= mobAiAttackInterval) {
                        mob.setMetadata("td_attack_ticks", new FixedMetadataValue(plugin, 0));

                        double damage = plugin.getConfig().getDouble("raid-settings.mob-core-damage.DEFAULT", 50.0);
                        if (mob.getType() == EntityType.GIANT) {
                            damage = plugin.getConfig().getDouble("raid-settings.mob-core-damage.GIANT", 300.0);
                        } else if (mob.getType() == EntityType.RAVAGER) {
                            damage = plugin.getConfig().getDouble("raid-settings.mob-core-damage.RAVAGER", 150.0);
                        } else if (mob.getType() == EntityType.VINDICATOR) {
                            damage = plugin.getConfig().getDouble("raid-settings.mob-core-damage.VINDICATOR", 40.0);
                        } else if (mob.getType() == EntityType.EVOKER) {
                            damage = plugin.getConfig().getDouble("raid-settings.mob-core-damage.EVOKER", 30.0);
                        }

                        double newHealth = core.getTempHealth() - damage;
                        core.setTempHealth(newHealth);

                        coreLoc.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, coreLoc.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1);
                        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

                        displayManager.broadcastToAlliance(ChatColor.RED + "[Cảnh báo] Lõi đang bị " + mob.getName() + ChatColor.RED + " cắn phá! HP Lõi còn lại: " + String.format("%.0f", newHealth) + "/" + core.getMaxShieldCapacity());

                        if (newHealth <= 0.0) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (plugin.getRaidSession() != null) {
                                        plugin.getRaidSession().endRaid(core, false);
                                    }
                                }
                            }.runTask(plugin);
                            return;
                        }
                    } else {
                        mob.setMetadata("td_attack_ticks", new FixedMetadataValue(plugin, damageTicks + 1));
                    }
                }

                if (shouldCheckBlocksAndStuck) {
                    Location lastLoc = mob.hasMetadata("td_last_loc") ? (Location) mob.getMetadata("td_last_loc").get(0).value() : null;
                    int stuckSeconds = mob.hasMetadata("td_stuck_seconds") ? mob.getMetadata("td_stuck_seconds").get(0).asInt() : 0;

                    if (lastLoc != null && lastLoc.getWorld() != null && lastLoc.getWorld().equals(mobLoc.getWorld()) && mobLoc.distance(lastLoc) < stuckDistThreshold) {
                        stuckSeconds += 2;
                    } else {
                        stuckSeconds = 0;
                    }
                    mob.setMetadata("td_last_loc", new FixedMetadataValue(plugin, mobLoc.clone()));
                    mob.setMetadata("td_stuck_seconds", new FixedMetadataValue(plugin, stuckSeconds));

                    if (stuckSeconds >= stuckSecondsThreshold * 2) {
                        if (isFlying) {
                            if (stuckSeconds >= 6) {
                                org.bukkit.util.Vector stuckDirection = coreLoc.toVector().subtract(mobLoc.toVector()).normalize();
                                Location newLoc = mobLoc.clone().add(stuckDirection.multiply(1.5));
                                if (newLoc.getY() > newLoc.getWorld().getMinHeight() && newLoc.getY() < newLoc.getWorld().getMaxHeight()) {
                                    mob.teleport(newLoc);
                                    mob.setMetadata("td_stuck_seconds", new FixedMetadataValue(plugin, 0));
                                }
                            }
                        } else {
                            org.bukkit.block.Block feetBlock = mobLoc.getBlock();
                            org.bukkit.block.Block belowBlock = feetBlock.getRelative(org.bukkit.block.BlockFace.DOWN);

                            org.bukkit.util.Vector stuckDirection = coreLoc.toVector().subtract(mobLoc.toVector()).normalize();
                            Location checkLoc = mobLoc.clone().add(stuckDirection.multiply(1.2));
                            org.bukkit.block.Block frontFeetBlock = checkLoc.getBlock();
                            org.bukkit.block.Block frontHeadBlock = checkLoc.clone().add(0, 1, 0).getBlock();

                            boolean isHole = feetBlock.getType().isAir() || feetBlock.isLiquid() || belowBlock.getType().isAir() || belowBlock.isLiquid();
                            boolean isFrontBlocked = frontFeetBlock.getType().isSolid() || frontHeadBlock.getType().isSolid();

                            if (isHole || isFrontBlocked) {
                                if (!feetBlock.getType().isSolid() && feetBlock.getType() != Material.CONDUIT) {
                                    feetBlock.setType(Material.DIRT);
                                    mob.teleport(mobLoc.clone().add(0, 1.0, 0));
                                    feetBlock.getWorld().playSound(feetBlock.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 1.0f, 1.0f);
                                    stuckSeconds = 0;
                                    mob.setMetadata("td_stuck_seconds", new FixedMetadataValue(plugin, stuckSeconds));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!invalidMobs.isEmpty()) {
            for (Entity entity : invalidMobs) {
                registerMobKill(entity);
            }
        }
    }

    public void launchNextWave() {
        currentWave++;
        if (currentWave > maxWaves) {
            if (plugin.getRaidSession() != null) {
                plugin.getRaidSession().endRaid(core, true);
            }
            return;
        }

        displayManager.broadcastToAlliance(ChatColor.GOLD + ">>> BẮT ĐẦU ĐỢT " + currentWave + "/" + maxWaves + " <<<");
        core.getLocation().getWorld().playSound(core.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.8f);

        buildWaveSpawnQueue();

        this.totalWaveMobs = pendingSpawnQueue.size();
        this.waveStartTime = System.currentTimeMillis();

        startStaggeredSpawning();
    }

    private void startStaggeredSpawning() {
        if (spawnTask != null) spawnTask.cancel();

        double staticMult = plugin.getConfig().getDouble("raid-settings.static-multiplier." + core.getLevel(), 1.0);
        double dynamicMult = 1.0;
        if (isPurchased) {
            dynamicMult = plugin.getConfig().getDouble("raid-settings.dynamic-multiplier." + purchasedIndex, 1.2);
        }
        double callScalingFactor = plugin.getConfig().getDouble("raid-settings.call-scaling-factor", 1.20);
        double totalRaidScalingFactor = plugin.getConfig().getDouble("raid-settings.total-raid-scaling-factor", 1.05);
        double callScaling = Math.pow(callScalingFactor, core.getRaidCallCount());
        double totalRaidScaling = Math.pow(totalRaidScalingFactor, core.getTotalRaidCount());

        double otr = calculateCoreOtr();
        double hpOtrBonus = 1.0 + (otr * plugin.getConfig().getDouble("raid-settings.difficulty-scaling.otr-multipliers.hp-increase-per-otr", 0.06));
        double finalHpMultiplier = staticMult * dynamicMult * callScaling * totalRaidScaling * hpOtrBonus;

        double radius = core.getRadius();
        Location cLoc = core.getLocation();

        int spawnPointsCount = (Math.random() < 0.5) ? 1 : 2;
        double[] spawnAngles = new double[spawnPointsCount];
        for (int k = 0; k < spawnPointsCount; k++) {
            spawnAngles[k] = Math.random() * Math.PI * 2;
        }

        spawnCompleted = false;
        spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingSpawnQueue.isEmpty()) {
                    cancel();
                    spawnTask = null;
                    spawnCompleted = true;
                    if (aliveMobs.isEmpty() && plugin.getRaidSession() != null && plugin.getRaidSession().activeCampaigns().containsKey(core.getCoreId())) {
                        RaidRewardCalculator.sendWaveSummary(plugin, ActiveRaidCampaign.this, core);
                        displayManager.broadcastToAlliance(ChatColor.GREEN + "Đã quét sạch đợt " + currentWave + ". Hồi sức chuẩn bị đợt kế tiếp sau " + (plugin.getConfig().getInt("raid-settings.wave-delay-ticks", 900) / 20) + " giây!");
                        core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (plugin.getRaidSession() != null && plugin.getRaidSession().activeCampaigns().containsKey(core.getCoreId())) {
                                    launchNextWave();
                                }
                            }
                        }.runTaskLater(plugin, (long) plugin.getConfig().getInt("raid-settings.wave-delay-ticks", 900));
                    }
                    return;
                }

                int batchMin = plugin.getConfig().getInt("raid-settings.spawn-batch-min", 3);
                int batchMax = plugin.getConfig().getInt("raid-settings.spawn-batch-max", 5);
                int spawnCountThisTick = Math.min(pendingSpawnQueue.size(), batchMin + (int)(Math.random() * (batchMax - batchMin + 1)));
                for (int i = 0; i < spawnCountThisTick; i++) {
                    if (pendingSpawnQueue.isEmpty()) break;
                    pendingSpawnQueue.remove(0);

                    double baseAngle = spawnAngles[(int) (Math.random() * spawnAngles.length)];
                    double angle = baseAngle + (Math.random() * 0.5 - 0.25);
                    double spawnDistance = radius + 1.0;

                    double candidateX = cLoc.getX() + spawnDistance * Math.cos(angle);
                    double candidateZ = cLoc.getZ() + spawnDistance * Math.sin(angle);
                    Location candidateLoc = new Location(cLoc.getWorld(), candidateX, cLoc.getY(), candidateZ);

                    EntityType type = spawnMatrix.selectNextMob(cLoc.getWorld(), candidateLoc);

                    boolean isFlying = isFlyingMobType(type);
                    Location spawnLoc = findSmartSpawnLocation(cLoc, spawnDistance, angle, isFlying);
                    boolean success = spawnSingleMob(type, spawnLoc, finalHpMultiplier);
                    if (!success) {
                        pendingSpawnQueue.add(0, EntityType.UNKNOWN);
                    }
                }
            }
        };
        spawnTask.runTaskTimer(plugin, 0L, (long) plugin.getConfig().getInt("raid-settings.spawn-interval-ticks", 40));
    }

    private Location findSmartSpawnLocation(Location coreLoc, double targetDistance, double initialAngle, boolean isFlying) {
        org.bukkit.World world = coreLoc.getWorld();
        if (world == null) return coreLoc.clone().add(0, 5, 0);

        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = initialAngle + (attempt * (Math.PI / 4));
            double x = coreLoc.getX() + targetDistance * Math.cos(angle);
            double z = coreLoc.getZ() + targetDistance * Math.sin(angle);

            if (!world.isChunkLoaded((int) x >> 4, (int) z >> 4)) continue;

            if (isFlying) {
                int highestY = world.getHighestBlockYAt((int) x, (int) z);
                double y = Math.min(highestY + 10.0, world.getMaxHeight() - 5.0);
                org.bukkit.block.Block feet = world.getBlockAt((int) x, (int) y, (int) z);
                org.bukkit.block.Block head = world.getBlockAt((int) x, (int) y + 1, (int) z);
                if (feet.getType().isAir() && head.getType().isAir()) return new Location(world, x, y, z);
            } else {
                int highestY = world.getHighestBlockYAt((int) x, (int) z);
                if (highestY > world.getMinHeight() && highestY < world.getMaxHeight() - 2) {
                    org.bukkit.block.Block feet = world.getBlockAt((int) x, highestY + 1, (int) z);
                    org.bukkit.block.Block head = world.getBlockAt((int) x, highestY + 2, (int) z);
                    org.bukkit.block.Block ground = world.getBlockAt((int) x, highestY, (int) z);
                    if (feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid()) {
                        return new Location(world, x, highestY + 1, z);
                    }
                }
            }
        }

        double fallbackX = coreLoc.getX() + targetDistance * Math.cos(initialAngle);
        double fallbackZ = coreLoc.getZ() + targetDistance * Math.sin(initialAngle);
        if (isFlying) {
            return new Location(world, fallbackX, coreLoc.getY() + 10.0, fallbackZ);
        } else {
            try {
                int highestY = world.getHighestBlockYAt((int) fallbackX, (int) fallbackZ);
                return new Location(world, fallbackX, highestY + 1.0, fallbackZ);
            } catch (Exception e) {
                return new Location(world, fallbackX, coreLoc.getY(), fallbackZ);
            }
        }
    }

    private boolean spawnSingleMob(EntityType type, Location loc, double hpMultiplier) {
        LivingEntity mob = null;
        try {
            mob = (LivingEntity) loc.getWorld().spawn(loc, type.getEntityClass(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.RAID);
        } catch (Throwable t) {
            try {
                mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
            } catch (Throwable ignored) {}
        }

        if (mob == null || !mob.isValid()) {
            return false;
        }

        mob.getPersistentDataContainer().set(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE, (byte) 1);
        mob.getPersistentDataContainer().set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, core.getCoreId().toString());
        mob.setMetadata("td_raid_mob", new FixedMetadataValue(plugin, true));
        mob.setMetadata("td_owner_core", new FixedMetadataValue(plugin, core.getCoreId().toString()));
        mob.setMetadata("td_raid_call_count", new FixedMetadataValue(plugin, core.getRaidCallCount()));

        for (AdvancedSpawnMatrix.MobSpawnRule rule : spawnMatrix.getSpawnRules()) {
            if (rule.getEntityType() == type) {
                mob.setMetadata("td_spawn_rarity", new FixedMetadataValue(plugin, rule.getRarity().name()));
                spawnMatrix.incrementActiveCount(rule.getRarity());
                break;
            }
        }

        double spawnChance = plugin.getConfig().getDouble("raid-settings.elite-boss.spawn-chance", 0.01);
        boolean isEliteBoss = (currentWave == maxWaves) && (Math.random() < spawnChance);
        if (isEliteBoss) {
            mob.setMetadata("td_elite_boss", new FixedMetadataValue(plugin, true));
        }

        plugin.getSecureEntityTracker().stampSecureHash(mob, "RAID_MOB");

        double baseHp = com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMobRegistry.getBaseHp(type);
        double scaledHp = baseHp * hpMultiplier;
        if (isEliteBoss) {
            double hpMult = plugin.getConfig().getDouble("raid-settings.elite-boss.hp-multiplier", 2.0);
            scaledHp *= hpMult;
        }

        try {
            org.bukkit.attribute.AttributeInstance maxHealthAttr = null;
            try {
                java.lang.reflect.Field field = org.bukkit.attribute.Attribute.class.getField("GENERIC_MAX_HEALTH");
                org.bukkit.attribute.Attribute attrEnum = (org.bukkit.attribute.Attribute) field.get(null);
                maxHealthAttr = mob.getAttribute(attrEnum);
            } catch (Throwable ignored) {}

            if (maxHealthAttr == null) {
                try {
                    maxHealthAttr = mob.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("generic.max_health")));
                } catch (Throwable ignored) {}
            }

            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(scaledHp);
                mob.setHealth(scaledHp);
            }
        } catch (Throwable t) {
            try {
                mob.setHealth(Math.min(scaledHp, mob.getMaxHealth()));
            } catch (Throwable ignored) {}
        }

        try {
            double actualHp = mob.getMaxHealth();
            org.bukkit.attribute.AttributeInstance maxHealthAttr = null;
            try {
                java.lang.reflect.Field field = org.bukkit.attribute.Attribute.class.getField("GENERIC_MAX_HEALTH");
                org.bukkit.attribute.Attribute attrEnum = (org.bukkit.attribute.Attribute) field.get(null);
                maxHealthAttr = mob.getAttribute(attrEnum);
            } catch (Throwable ignored) {}
            if (maxHealthAttr == null) {
                try {
                    maxHealthAttr = mob.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("generic.max_health")));
                } catch (Throwable ignored) {}
            }
            if (maxHealthAttr != null) {
                actualHp = maxHealthAttr.getValue();
            }
            if (actualHp <= 0) actualHp = mob.getMaxHealth();
            mob.setMetadata("td_intended_max_hp", new FixedMetadataValue(plugin, scaledHp));
            mob.setMetadata("td_actual_max_hp", new FixedMetadataValue(plugin, actualHp));
        } catch (Throwable ignored) {}

        try {
            org.bukkit.attribute.AttributeInstance attackDamageAttr = null;
            try {
                java.lang.reflect.Field field = org.bukkit.attribute.Attribute.class.getField("GENERIC_ATTACK_DAMAGE");
                org.bukkit.attribute.Attribute attrEnum = (org.bukkit.attribute.Attribute) field.get(null);
                attackDamageAttr = mob.getAttribute(attrEnum);
            } catch (Throwable ignored) {}

            if (attackDamageAttr == null) {
                try {
                    attackDamageAttr = mob.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("generic.attack_damage")));
                } catch (Throwable ignored) {}
            }

            if (attackDamageAttr != null) {
                double otr = calculateCoreOtr();
                double damageOtrBonus = 1.0 + (otr * plugin.getConfig().getDouble("raid-settings.difficulty-scaling.otr-multipliers.damage-increase-per-otr", 0.04));
                double finalDmgMult = damageOtrBonus;
                if (mob.hasMetadata("td_elite_boss")) {
                    double dmgMult = plugin.getConfig().getDouble("raid-settings.elite-boss.damage-multiplier", 1.5);
                    finalDmgMult *= dmgMult;
                }
                double baseDamage = attackDamageAttr.getBaseValue();
                attackDamageAttr.setBaseValue(baseDamage * finalDmgMult);
            }
        } catch (Throwable ignored) {}

        if (isEliteBoss) {
            try {
                List<UUID> members = plugin.getAllianceManager() != null ?
                        plugin.getAllianceManager().getAllianceMembers(core.getAllyId()) : new ArrayList<>();
                com.truongcm.territorydefense.feature.alliance.Alliance alliance = plugin.getAllianceManager() != null ?
                        plugin.getAllianceManager().getAlliance(core.getAllyId()) : null;
                String allyName = alliance != null ? alliance.getName() : "Không Xác Định";

                String rawMsg = plugin.getConfig().getString("raid-settings.elite-boss.spawn-warning-message", "&6★ SIÊU CẤP MINI-BOSS ★ &cMột &eElite Boss &ccực kỳ nguy hiểm đã xuất hiện tại Wave cuối của Lõi Liên Minh &e%alliance%&c! Hãy hợp lực cùng các dũng sĩ tiêu diệt!");
                String message = ChatColor.translateAlternateColorCodes('&', rawMsg.replace("%alliance%", allyName));

                String soundStr = plugin.getConfig().getString("raid-settings.elite-boss.spawn-sound", "ENTITY_WITHER_SPAWN");
                Sound soundVal = Sound.ENTITY_WITHER_SPAWN;
                try {
                    soundVal = Sound.valueOf(soundStr.toUpperCase());
                } catch (Exception ignored) {}

                for (Player p : Bukkit.getOnlinePlayers()) {
                    boolean isMember = members.contains(p.getUniqueId());
                    boolean isNear = p.getLocation().getWorld().equals(loc.getWorld()) && p.getLocation().distanceSquared(loc) < 2500;
                    if (isMember || isNear) {
                        p.sendMessage(message);
                        p.playSound(p.getLocation(), soundVal, 1.0f, 0.9f);
                    }
                }
            } catch (Throwable ignored) {}
        }

        applyNoCollision(mob);
        displayManager.updateMobCustomName(mob);

        plugin.getCombatDamageTracker().registerRaidMob(mob, scaledHp);
        aliveMobs.add(mob);
        return true;
    }

    public void registerMobKill(Entity entity) {
        aliveMobs.remove(entity);
        removeNoCollision(entity);

        if (entity.hasMetadata("td_spawn_rarity")) {
            try {
                String rarityStr = entity.getMetadata("td_spawn_rarity").get(0).asString();
                AdvancedSpawnMatrix.SpawnRarity rarity = AdvancedSpawnMatrix.SpawnRarity.valueOf(rarityStr);
                spawnMatrix.decrementActiveCount(rarity);
            } catch (Exception ignored) {}
        }

        if (aliveMobs.isEmpty() && pendingSpawnQueue.isEmpty() && spawnCompleted) {
            RaidRewardCalculator.sendWaveSummary(plugin, this, core);

            displayManager.broadcastToAlliance(ChatColor.GREEN + "Đã quét sạch đợt " + currentWave + ". Hồi sức chuẩn bị đợt kế tiếp sau " + (plugin.getConfig().getInt("raid-settings.wave-delay-ticks", 900) / 20) + " giây!");
            core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (plugin.getRaidSession() != null && plugin.getRaidSession().activeCampaigns().containsKey(core.getCoreId())) {
                        launchNextWave();
                    }
                }
            }.runTaskLater(plugin, (long) plugin.getConfig().getInt("raid-settings.wave-delay-ticks", 900));
        }
    }

    public double calculateCoreOtr() {
        double lvl = core.getLevel();
        double towerCount = 0;
        double totalTowerLevels = 0;
        if (plugin.getTowerManager() != null) {
            List<com.truongcm.territorydefense.feature.combat.tower.Tower> towers = plugin.getTowerManager().getTowersForCore(core.getCoreId());
            if (towers != null) {
                towerCount = towers.size();
                for (com.truongcm.territorydefense.feature.combat.tower.Tower t : towers) {
                    totalTowerLevels += t.getLevel();
                }
            }
        }
        double completedRaids = core.getCompletedRaids();

        double countWeight = plugin.getConfig().getDouble("raid-settings.difficulty-scaling.otr-formula.tower-count-weight", 0.5);
        double lvlWeight = plugin.getConfig().getDouble("raid-settings.difficulty-scaling.otr-formula.tower-level-weight", 0.2);
        double raidsWeight = plugin.getConfig().getDouble("raid-settings.difficulty-scaling.otr-formula.completed-raids-weight", 0.02);

        return lvl + (towerCount * countWeight) + (totalTowerLevels * lvlWeight) + (completedRaids * raidsWeight);
    }

    private void buildWaveSpawnQueue() {
        pendingSpawnQueue.clear();
        spawnMatrix.resetActiveCounts();

        double otr = calculateCoreOtr();

        double baseQty = plugin.getConfig().getDouble("raid-settings.difficulty-scaling.otr-multipliers.spawn-quantity.base", 15);
        double weightPerOtr = plugin.getConfig().getDouble("raid-settings.difficulty-scaling.otr-multipliers.spawn-quantity.weight-per-otr", 2.5);
        int minLimit = plugin.getConfig().getInt("raid-settings.difficulty-scaling.otr-multipliers.spawn-quantity.min-limit", 10);
        int maxLimit = plugin.getConfig().getInt("raid-settings.difficulty-scaling.otr-multipliers.spawn-quantity.max-limit", 60);

        int expectedTotal = (int) (baseQty + (weightPerOtr * otr));
        expectedTotal = Math.max(minLimit, Math.min(maxLimit, expectedTotal));

        for (int i = 0; i < expectedTotal; i++) {
            pendingSpawnQueue.add(EntityType.UNKNOWN);
        }
    }

    public boolean isRunning() {
        return true;
    }
}
