import sys
import os

path = r'C:/Users/nhutt/OneDrive/Máy tính/MC/territorydefense/src/main/java/com/truongcm/territorydefense/feature/combat/raid/RaidSession.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Clean up duplicate message in onRaidMobMeleeAndProjectileDamageRedirect
target_dup = '''                                if (livingVictim instanceof Player p) {
                                    p.sendMessage(ChatColor.YELLOW + "[Khiên Lõi] Hấp thụ " + String.format("%.1f", damage) + " sát thương (đã giảm còn 10%) từ " + mobAttacker.getName() + "! Khiên còn lại: " + String.format("%.0f", core.getShield()) + " HP.");
                                }

                                // Hiển thị hiệu ứng khiên hấp thụ sát thương
                                livingVictim.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, livingVictim.getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.1);
                                livingVictim.getWorld().playSound(livingVictim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);

                                if (livingVictim instanceof Player p) {
                                    p.sendMessage(ChatColor.YELLOW + "[Khiên Lõi] Hấp thụ " + String.format("%.1f", damage) + " sát thương từ " + mobAttacker.getName() + "! Khiên còn lại: " + String.format("%.0f", core.getShield()) + " HP.");
                                }'''

repl_dup = '''                                if (livingVictim instanceof Player p) {
                                    p.sendMessage(ChatColor.YELLOW + "[Khiên Lõi] Hấp thụ " + String.format("%.1f", damage) + " sát thương (đã giảm còn 10%) từ " + mobAttacker.getName() + "! Khiên còn lại: " + String.format("%.0f", core.getShield()) + " HP.");
                                }

                                // Hiển thị hiệu ứng khiên hấp thụ sát thương
                                livingVictim.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, livingVictim.getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.1);
                                livingVictim.getWorld().playSound(livingVictim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);'''

# Replace with normalized line endings
content_norm = content.replace('\r\n', '\n')
target_dup_norm = target_dup.replace('\r\n', '\n')
repl_dup_norm = repl_dup.replace('\r\n', '\n')

if target_dup_norm in content_norm:
    content_norm = content_norm.replace(target_dup_norm, repl_dup_norm)
    print('Cleaned up duplicate redirect message!')
else:
    print('Duplicate redirect message not found precisely. Will do partial match/fallback.')

# 2. Refactor core biting shield absorption to 10%
target_biting_shield = '''                        // 1. Trừ Khiên nếu còn khiên
                        if (core.getShield() > 0) {
                            double newShield = Math.max(0.0, core.getShield() - mobDamage);
                            core.setShield(newShield);
                            plugin.getCoreManager().saveAllCores();

                            // Hạt bụi và âm thanh phản hồi từ khiên Lõi
                            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, coreLoc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
                            coreLoc.getWorld().playSound(coreLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                            
                            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage(ChatColor.YELLOW + "[Khiên Lãnh Thổ] Hấp thụ sát thương cắn phá của " + mob.getName() + "! -" + String.format("%.1f", mobDamage) + " Shield HP. Còn lại: " + String.format("%.0f", core.getShield()) + "/" + core.getMaxShieldCapacity() + " HP.");
                            }'''

repl_biting_shield = '''                        // 1. Trừ Khiên nếu còn khiên (Giảm còn 10% sát thương gốc)
                        if (core.getShield() > 0) {
                            double newShield = Math.max(0.0, core.getShield() - (mobDamage * 0.1));
                            core.setShield(newShield);
                            plugin.getCoreManager().saveAllCores();

                            // Hạt bụi và âm thanh phản hồi từ khiên Lõi
                            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, coreLoc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
                            coreLoc.getWorld().playSound(coreLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                            
                            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage(ChatColor.YELLOW + "[Khiên Lãnh Thổ] Hấp thụ sát thương cắn phá của " + mob.getName() + "! -" + String.format("%.1f", mobDamage * 0.1) + " Shield HP. Còn lại: " + String.format("%.0f", core.getShield()) + "/" + core.getMaxShieldCapacity() + " HP.");
                            }'''

target_biting_shield_norm = target_biting_shield.replace('\r\n', '\n')
repl_biting_shield_norm = repl_biting_shield.replace('\r\n', '\n')
if target_biting_shield_norm in content_norm:
    content_norm = content_norm.replace(target_biting_shield_norm, repl_biting_shield_norm)
    print('Updated core biting shield damage to 10%!')
else:
    print('Biting shield block not found!')

# 3. Refactor core biting health damage to 10%
target_biting_health = '''                        } else {
                            // 2. Trừ máu Lõi trực tiếp (Khiên đã sập)
                            double newHealth = Math.max(0.0, core.getTempHealth() - mobDamage);
                            core.setTempHealth(newHealth);

                            // Hiển thị hạt đỏ và âm thanh zombie gặm cửa sắt hỏng hóc
                            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, coreLoc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
                            coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.7f);

                            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "⚠️ CẢNH BÁO: Lõi chính đang bị cắn phá bởi " + mob.getName() + "! -" + String.format("%.1f", mobDamage) + " HP. Máu Lõi còn lại: " + String.format("%.0f", core.getTempHealth()) + "/" + String.format("%.0f", core.getMaxShieldCapacity()) + " HP.");
                            }'''

repl_biting_health = '''                        } else {
                            // 2. Trừ máu Lõi trực tiếp (Khiên đã sập, Giảm còn 10% sát thương gốc)
                            double newHealth = Math.max(0.0, core.getTempHealth() - (mobDamage * 0.1));
                            core.setTempHealth(newHealth);

                            // Hiển thị hạt đỏ và âm thanh zombie gặm cửa sắt hỏng hóc
                            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, coreLoc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
                            coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.7f);

                            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "⚠️ CẢNH BÁO: Lõi chính đang bị cắn phá bởi " + mob.getName() + "! -" + String.format("%.1f", mobDamage * 0.1) + " HP. Máu Lõi còn lại: " + String.format("%.0f", core.getTempHealth()) + "/" + String.format("%.0f", core.getMaxShieldCapacity()) + " HP.");
                            }'''

target_biting_health_norm = target_biting_health.replace('\r\n', '\n')
repl_biting_health_norm = repl_biting_health.replace('\r\n', '\n')
if target_biting_health_norm in content_norm:
    content_norm = content_norm.replace(target_biting_health_norm, repl_biting_health_norm)
    print('Updated core biting health damage to 10%!')
else:
    print('Biting health block not found!')

# 4. Refactor block breaking shield absorption to 10%
target_block_shield = '''                            mobDamage = mobDamage * (0.85 + Math.random() * 0.3);

                            double newShield = Math.max(0.0, core.getShield() - mobDamage);
                            core.setShield(newShield);
                            plugin.getCoreManager().saveAllCores();

                            targetBlock.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, targetBlock.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
                            targetBlock.getWorld().playSound(targetBlock.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                            
                            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage(ChatColor.YELLOW + "[Khiên Lãnh Thổ] Hấp thụ sát thương cắn phá block của " + mob.getName() + "! -" + String.format("%.1f", mobDamage) + " Shield HP. Còn lại: " + String.format("%.0f", core.getShield()) + " HP.");
                            }'''

repl_block_shield = '''                            mobDamage = mobDamage * (0.85 + Math.random() * 0.3);

                            double newShield = Math.max(0.0, core.getShield() - (mobDamage * 0.1));
                            core.setShield(newShield);
                            plugin.getCoreManager().saveAllCores();

                            targetBlock.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, targetBlock.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
                            targetBlock.getWorld().playSound(targetBlock.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                            
                            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage(ChatColor.YELLOW + "[Khiên Lãnh Thổ] Hấp thụ sát thương cắn phá block của " + mob.getName() + "! -" + String.format("%.1f", mobDamage * 0.1) + " Shield HP. Còn lại: " + String.format("%.0f", core.getShield()) + " HP.");
                            }'''

target_block_shield_norm = target_block_shield.replace('\r\n', '\n')
repl_block_shield_norm = repl_block_shield.replace('\r\n', '\n')
if target_block_shield_norm in content_norm:
    content_norm = content_norm.replace(target_block_shield_norm, repl_block_shield_norm)
    print('Updated block breaking shield absorption to 10%!')
else:
    print('Block breaking shield block not found!')

# 5. Refactor physical block breaking ticks (threshold 10x slower)
target_ticks = '''                            int damageTicks = mob.getMetadata("td_break_ticks").stream().findFirst().map(m -> m.asInt()).orElse(0);
                            int breakThreshold = mob.hasMetadata("td_elite_boss") ? 1 : 3;'''

repl_ticks = '''                            int damageTicks = mob.getMetadata("td_break_ticks").stream().findFirst().map(m -> m.asInt()).orElse(0);
                            int breakThreshold = mob.hasMetadata("td_elite_boss") ? 10 : 30;'''

target_ticks_norm = target_ticks.replace('\r\n', '\n')
repl_ticks_norm = repl_ticks.replace('\r\n', '\n')
if target_ticks_norm in content_norm:
    content_norm = content_norm.replace(target_ticks_norm, repl_ticks_norm)
    print('Updated block breaking breakThreshold to 10x slower (10 and 30)!')
else:
    print('Block breaking tick block not found!')

# Write back with CRLF line endings
with open(path, 'w', encoding='utf-8') as f:
    f.write(content_norm.replace('\n', '\r\n'))

print('Python script execution completed successfully.')
