import sys
import os

path = r'C:/Users/nhutt/OneDrive/Máy tính/MC/territorydefense/src/main/java/com/truongcm/territorydefense/feature/combat/raid/RaidSession.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content_norm = content.replace('\r\n', '\n')

# Find the start and end anchors for replacement
target_block = '''                        } else {
                            // 2. Trừ máu Lõi trực tiếp (Khiên đã sập, Giảm còn 10% sát thương gốc)
                            double newHealth = Math.max(0.0, core.getTempHealth() - (mobDamage * 0.1));
                            core.setTempHealth(newHealth);

                            // Hiển thị hạt đỏ và âm thanh zombie gặm cửa sắt hỏng hóc
                            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, coreLoc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
                        boolean isFrontBlocked = frontFeetBlock.getType().isSolid() || frontHeadBlock.getType().isSolid();

                        if (isHole || isFrontBlocked) {
                            if (!feetBlock.getType().isSolid() && feetBlock.getType() != Material.CONDUIT) {
                                feetBlock.setType(Material.DIRT);
                                mob.teleport(mobLoc.clone().add(0, 1.0, 0));
                                feetBlock.getWorld().playSound(feetBlock.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 1.0f, 1.0f);
                                stuckSeconds = 0;
                                mob.setMetadata("td_stuck_seconds", new FixedMetadataValue(plugin, stuckSeconds));
                                continue;
                            }
                        }
                    }'''

repl_block = '''                        } else {
                            // 2. Trừ máu Lõi trực tiếp (Khiên đã sập, Giảm còn 10% sát thương gốc)
                            double newHealth = Math.max(0.0, core.getTempHealth() - (mobDamage * 0.1));
                            core.setTempHealth(newHealth);

                            // Hiển thị hạt đỏ và âm thanh zombie gặm cửa sắt hỏng hóc
                            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, coreLoc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
                            coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.7f);

                            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "⚠️ CẢNH BÁO: Lõi chính đang bị cắn phá bởi " + mob.getName() + "! -" + String.format("%.1f", mobDamage * 0.1) + " HP. Máu Lõi còn lại: " + String.format("%.0f", core.getTempHealth()) + "/" + String.format("%.0f", core.getMaxShieldCapacity()) + " HP.");
                            }

                            if (core.getTempHealth() <= 0.0) {
                                // Kết thúc Raid và báo bại trận
                                endRaid(core, false);
                                return; // Thoát ngay lập tức
                            }
                        }
                        continue; // Đã cắn Lõi, bỏ qua logic phá khối phụ
                    }

                    // --- KIỂM TRA BỊ KẸT HOẶC LỌT HỐ SÂU (Mục 3) ---
                    Location lastLoc = mob.hasMetadata("td_last_loc") ? (Location) mob.getMetadata("td_last_loc").get(0).value() : null;
                    int stuckSeconds = mob.hasMetadata("td_stuck_seconds") ? mob.getMetadata("td_stuck_seconds").get(0).asInt() : 0;

                    if (lastLoc != null && lastLoc.getWorld() != null && lastLoc.getWorld().equals(mobLoc.getWorld()) && mobLoc.distance(lastLoc) < 0.2) {
                        stuckSeconds++;
                    } else {
                        stuckSeconds = 0;
                    }
                    mob.setMetadata("td_last_loc", new FixedMetadataValue(plugin, mobLoc.clone()));
                    mob.setMetadata("td_stuck_seconds", new FixedMetadataValue(plugin, stuckSeconds));

                    if (stuckSeconds >= 2) {
                        // Bỏ qua cơ chế đặt block nếu là quái biết bay hoặc lơ lửng
                        boolean isFlyingMob = mob instanceof org.bukkit.entity.Flying || 
                                              mob instanceof org.bukkit.entity.Vex || 
                                              mob instanceof org.bukkit.entity.Blaze || 
                                              mob instanceof org.bukkit.entity.Wither ||
                                              mob instanceof org.bukkit.entity.EnderDragon ||
                                              mob.getType().name().equals("ALLAY") ||
                                              mob.getType().name().equals("BAT") ||
                                              mob.getType().name().equals("BEE") ||
                                              mob.getType().name().equals("PARROT") ||
                                              mob.getType().name().equals("PHANTOM") ||
                                              mob.getType().name().equals("GHAST");

                        if (!isFlyingMob) {
                            org.bukkit.block.Block feetBlock = mobLoc.getBlock();
                            org.bukkit.block.Block belowBlock = feetBlock.getRelative(org.bukkit.block.BlockFace.DOWN);

                            org.bukkit.util.Vector direction = coreLoc.toVector().subtract(mobLoc.toVector()).normalize();
                            Location checkLoc = mobLoc.clone().add(direction.multiply(1.2));
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
                                    continue;
                                }
                            }
                        }
                    }'''

target_block_norm = target_block.replace('\r\n', '\n')
repl_block_norm = repl_block.replace('\r\n', '\n')

if target_block_norm in content_norm:
    content_norm = content_norm.replace(target_block_norm, repl_block_norm)
    print('Successfully applied flying mob fix and restored corrupted code block!')
else:
    print('Target block not found precisely!')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content_norm.replace('\n', '\r\n'))

print('Process complete.')
