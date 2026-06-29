import os
import shutil
import re

# Base directory paths
src_dir = r"c:\Users\Public\territorydefense\territorydefense\src\main\java\com\truongcm\territorydefense"

# 1. Define files to move
# Format: (old_relative_path, new_relative_path, new_package)
moves = [
    # API / Hook
    ("api/ProtocolLibHook.java", "hook/ProtocolLibHook.java", "com.truongcm.territorydefense.hook"),
    ("api/ServerChestHook.java", "hook/ServerChestHook.java", "com.truongcm.territorydefense.hook"),
    ("api/VaultHook.java", "hook/VaultHook.java", "com.truongcm.territorydefense.hook"),
    
    # Core
    ("core/BorderVisualizer.java", "feature/core/BorderVisualizer.java", "com.truongcm.territorydefense.feature.core"),
    ("core/CoreManager.java", "feature/core/CoreManager.java", "com.truongcm.territorydefense.feature.core"),
    ("core/PDCKeys.java", "feature/core/PDCKeys.java", "com.truongcm.territorydefense.feature.core"),
    ("core/TerritoryCore.java", "feature/core/TerritoryCore.java", "com.truongcm.territorydefense.feature.core"),
    ("security/CoreProtectionListener.java", "feature/core/CoreProtectionListener.java", "com.truongcm.territorydefense.feature.core"),
    ("commands/TerritoryCommands.java", "feature/core/TerritoryCommands.java", "com.truongcm.territorydefense.feature.core"),
    ("commands/TerritoryTabCompleter.java", "feature/core/TerritoryTabCompleter.java", "com.truongcm.territorydefense.feature.core"),
    
    # Alliance
    ("alliance/Alliance.java", "feature/alliance/Alliance.java", "com.truongcm.territorydefense.feature.alliance"),
    ("alliance/AllianceManager.java", "feature/alliance/AllianceManager.java", "com.truongcm.territorydefense.feature.alliance"),
    ("commands/AllyCommands.java", "feature/alliance/AllyCommands.java", "com.truongcm.territorydefense.feature.alliance"),
    ("commands/AllyTabCompleter.java", "feature/alliance/AllyTabCompleter.java", "com.truongcm.territorydefense.feature.alliance"),
    
    # Logistics
    ("logistics/FarmerManager.java", "feature/logistics/FarmerManager.java", "com.truongcm.territorydefense.feature.logistics"),
    ("logistics/FEPManager.java", "feature/logistics/FEPManager.java", "com.truongcm.territorydefense.feature.logistics"),
    ("logistics/NPCFarmer.java", "feature/logistics/NPCFarmer.java", "com.truongcm.territorydefense.feature.logistics"),
    
    # Security
    ("security/SecureEntityTracker.java", "feature/security/SecureEntityTracker.java", "com.truongcm.territorydefense.feature.security"),
    ("security/ItemSecurityListener.java", "feature/security/ItemSecurityListener.java", "com.truongcm.territorydefense.feature.security"),
    
    # Combat - Mercenary
    ("combat/mercenary/Mercenary.java", "feature/combat/mercenary/Mercenary.java", "com.truongcm.territorydefense.feature.combat.mercenary"),
    ("combat/mercenary/MercenaryAI.java", "feature/combat/mercenary/MercenaryAI.java", "com.truongcm.territorydefense.feature.combat.mercenary"),
    
    # Combat - Raid
    ("combat/raid/ActiveRaid.java", "feature/combat/raid/ActiveRaid.java", "com.truongcm.territorydefense.feature.combat.raid"),
    ("combat/raid/CombatDamageTracker.java", "feature/combat/raid/CombatDamageTracker.java", "com.truongcm.territorydefense.feature.combat.raid"),
    ("combat/raid/RaidSession.java", "feature/combat/raid/RaidSession.java", "com.truongcm.territorydefense.feature.combat.raid"),
    
    # Combat - Siege
    ("combat/siege/PostWarManager.java", "feature/combat/siege/PostWarManager.java", "com.truongcm.territorydefense.feature.combat.siege"),
    ("combat/siege/SiegeSession.java", "feature/combat/siege/SiegeSession.java", "com.truongcm.territorydefense.feature.combat.siege"),
    
    # Combat - Tower
    ("combat/tower/Tower.java", "feature/combat/tower/Tower.java", "com.truongcm.territorydefense.feature.combat.tower"),
    ("combat/tower/TowerManager.java", "feature/combat/tower/TowerManager.java", "com.truongcm.territorydefense.feature.combat.tower"),
    
    # Combat - Tower - types (we will handle types folder dynamically or add here)
    ("combat/tower/types/ArrowTower.java", "feature/combat/tower/types/ArrowTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    ("combat/tower/types/ArtilleryTower.java", "feature/combat/tower/types/ArtilleryTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    ("combat/tower/types/FireTower.java", "feature/combat/tower/types/FireTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    ("combat/tower/types/FrostTower.java", "feature/combat/tower/types/FrostTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    ("combat/tower/types/HealingTower.java", "feature/combat/tower/types/HealingTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    ("combat/tower/types/LightningTower.java", "feature/combat/tower/types/LightningTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    ("combat/tower/types/SpellTower.java", "feature/combat/tower/types/SpellTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
]

# 2. Define package and import replacement rules
import_replacements = {
    # hook
    "com.truongcm.territorydefense.api.ProtocolLibHook": "com.truongcm.territorydefense.hook.ProtocolLibHook",
    "com.truongcm.territorydefense.api.ServerChestHook": "com.truongcm.territorydefense.hook.ServerChestHook",
    "com.truongcm.territorydefense.api.VaultHook": "com.truongcm.territorydefense.hook.VaultHook",
    
    # core
    "com.truongcm.territorydefense.core.BorderVisualizer": "com.truongcm.territorydefense.feature.core.BorderVisualizer",
    "com.truongcm.territorydefense.core.CoreManager": "com.truongcm.territorydefense.feature.core.CoreManager",
    "com.truongcm.territorydefense.core.PDCKeys": "com.truongcm.territorydefense.feature.core.PDCKeys",
    "com.truongcm.territorydefense.core.TerritoryCore": "com.truongcm.territorydefense.feature.core.TerritoryCore",
    
    # alliance
    "com.truongcm.territorydefense.alliance.Alliance": "com.truongcm.territorydefense.feature.alliance.Alliance",
    "com.truongcm.territorydefense.alliance.AllianceManager": "com.truongcm.territorydefense.feature.alliance.AllianceManager",
    
    # logistics
    "com.truongcm.territorydefense.logistics.FarmerManager": "com.truongcm.territorydefense.feature.logistics.FarmerManager",
    "com.truongcm.territorydefense.logistics.FEPManager": "com.truongcm.territorydefense.feature.logistics.FEPManager",
    "com.truongcm.territorydefense.logistics.NPCFarmer": "com.truongcm.territorydefense.feature.logistics.NPCFarmer",
    
    # security
    "com.truongcm.territorydefense.security.SecureEntityTracker": "com.truongcm.territorydefense.feature.security.SecureEntityTracker",
    "com.truongcm.territorydefense.security.ItemSecurityListener": "com.truongcm.territorydefense.feature.security.ItemSecurityListener",
    "com.truongcm.territorydefense.security.CoreProtectionListener": "com.truongcm.territorydefense.feature.core.CoreProtectionListener",
    
    # combat
    "com.truongcm.territorydefense.combat.mercenary.Mercenary": "com.truongcm.territorydefense.feature.combat.mercenary.Mercenary",
    "com.truongcm.territorydefense.combat.mercenary.MercenaryAI": "com.truongcm.territorydefense.feature.combat.mercenary.MercenaryAI",
    
    "com.truongcm.territorydefense.combat.raid.ActiveRaid": "com.truongcm.territorydefense.feature.combat.raid.ActiveRaid",
    "com.truongcm.territorydefense.combat.raid.CombatDamageTracker": "com.truongcm.territorydefense.feature.combat.raid.CombatDamageTracker",
    "com.truongcm.territorydefense.combat.raid.RaidSession": "com.truongcm.territorydefense.feature.combat.raid.RaidSession",
    
    "com.truongcm.territorydefense.combat.siege.PostWarManager": "com.truongcm.territorydefense.feature.combat.siege.PostWarManager",
    "com.truongcm.territorydefense.combat.siege.SiegeSession": "com.truongcm.territorydefense.feature.combat.siege.SiegeSession",
    
    "com.truongcm.territorydefense.combat.tower.Tower": "com.truongcm.territorydefense.feature.combat.tower.Tower",
    "com.truongcm.territorydefense.combat.tower.TowerManager": "com.truongcm.territorydefense.feature.combat.tower.TowerManager",
    "com.truongcm.territorydefense.combat.tower.types": "com.truongcm.territorydefense.feature.combat.tower.types",
    
    # commands
    "com.truongcm.territorydefense.commands.TerritoryCommands": "com.truongcm.territorydefense.feature.core.TerritoryCommands",
    "com.truongcm.territorydefense.commands.TerritoryTabCompleter": "com.truongcm.territorydefense.feature.core.TerritoryTabCompleter",
    "com.truongcm.territorydefense.commands.AllyCommands": "com.truongcm.territorydefense.feature.alliance.AllyCommands",
    "com.truongcm.territorydefense.commands.AllyTabCompleter": "com.truongcm.territorydefense.feature.alliance.AllyTabCompleter",
    
    # gui -> feature GUI mapping
    "com.truongcm.territorydefense.gui.CoreGuiHolder": "com.truongcm.territorydefense.feature.core.CoreGui",
    "com.truongcm.territorydefense.gui.FarmerGuiHolder": "com.truongcm.territorydefense.feature.logistics.FarmerUpgradeGui",
    "com.truongcm.territorydefense.gui.TowerGuiHolder": "com.truongcm.territorydefense.feature.combat.tower.TowerUpgradeGui",
    "com.truongcm.territorydefense.gui.AllyGuiHolder": "com.truongcm.territorydefense.feature.alliance.AllyMainMenuGui",
    "com.truongcm.territorydefense.gui.AllyInviteGuiHolder": "com.truongcm.territorydefense.feature.alliance.AllyInviteGui",
    "com.truongcm.territorydefense.gui.AllyKickGuiHolder": "com.truongcm.territorydefense.feature.alliance.AllyKickGui",
    "com.truongcm.territorydefense.gui.WarDeclarationGuiHolder": "com.truongcm.territorydefense.feature.combat.siege.WarDeclarationGui",
}

def process_file(src_path, dest_path, new_package):
    with open(src_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Update package statement
    content = re.sub(r'package\s+com\.truongcm\.territorydefense[^;]+;', f'package {new_package};', content)

    # 2. Update imports
    for old_imp, new_imp in import_replacements.items():
        # Replace specific imports
        content = content.replace(f"import {old_imp};", f"import {new_imp};")
        # Replace type references if class names changed
        # e.g., CoreGuiHolder -> CoreGui
        old_class = old_imp.split('.')[-1]
        new_class = new_imp.split('.')[-1]
        if old_class != new_class:
            content = content.replace(old_class, new_class)

    # Make parent dirs for dest_path
    os.makedirs(os.path.dirname(dest_path), exist_ok=True)

    with open(dest_path, 'w', encoding='utf-8') as f:
        f.write(content)

# Move and update package/imports
for old_rel, new_rel, new_pkg in moves:
    old_full = os.path.join(src_dir, old_rel)
    new_full = os.path.join(src_dir, new_rel)
    if os.path.exists(old_full):
        print(f"Processing: {old_rel} -> {new_rel}")
        process_file(old_full, new_full, new_pkg)
        os.remove(old_full)
    else:
        print(f"Skipping (does not exist): {old_rel}")

# Update main file com/truongcm/territorydefense/TerritoryDefense.java
main_file = os.path.join(src_dir, "TerritoryDefense.java")
if os.path.exists(main_file):
    with open(main_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Clean up imports
    # Remove gui.GuiListener import since we are deleting that class
    content = re.sub(r'import\s+com\.truongcm\.territorydefense\.gui\.GuiListener;\s*\n', '', content)
    
    # Update other imports
    for old_imp, new_imp in import_replacements.items():
        content = content.replace(f"import {old_imp};", f"import {new_imp};")
        # Replace types if class name changed
        old_class = old_imp.split('.')[-1]
        new_class = new_imp.split('.')[-1]
        if old_class != new_class:
            content = content.replace(old_class, new_class)
            
    with open(main_file, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Updated TerritoryDefense.java main imports")

# Clean up empty folders
for root, dirs, files in os.walk(src_dir, topdown=False):
    for d in dirs:
        full_d = os.path.join(root, d)
        if not os.listdir(full_d):
            os.rmdir(full_d)
            print(f"Cleaned empty dir: {full_d}")
