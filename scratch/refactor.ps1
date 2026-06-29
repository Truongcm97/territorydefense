$srcDir = "c:\Users\Public\territorydefense\territorydefense\src\main\java\com\truongcm\territorydefense"

# 1. Define files to move
# Format: @(old_rel, new_rel, new_package)
$moves = @(
    # API / Hook
    @("api/ProtocolLibHook.java", "hook/ProtocolLibHook.java", "com.truongcm.territorydefense.hook"),
    @("api/ServerChestHook.java", "hook/ServerChestHook.java", "com.truongcm.territorydefense.hook"),
    @("api/VaultHook.java", "hook/VaultHook.java", "com.truongcm.territorydefense.hook"),
    
    # Core
    @("core/BorderVisualizer.java", "feature/core/BorderVisualizer.java", "com.truongcm.territorydefense.feature.core"),
    @("core/CoreManager.java", "feature/core/CoreManager.java", "com.truongcm.territorydefense.feature.core"),
    @("core/PDCKeys.java", "feature/core/PDCKeys.java", "com.truongcm.territorydefense.feature.core"),
    @("core/TerritoryCore.java", "feature/core/TerritoryCore.java", "com.truongcm.territorydefense.feature.core"),
    @("security/CoreProtectionListener.java", "feature/core/CoreProtectionListener.java", "com.truongcm.territorydefense.feature.core"),
    @("commands/TerritoryCommands.java", "feature/core/TerritoryCommands.java", "com.truongcm.territorydefense.feature.core"),
    @("commands/TerritoryTabCompleter.java", "feature/core/TerritoryTabCompleter.java", "com.truongcm.territorydefense.feature.core"),
    
    # Alliance
    @("alliance/Alliance.java", "feature/alliance/Alliance.java", "com.truongcm.territorydefense.feature.alliance"),
    @("alliance/AllianceManager.java", "feature/alliance/AllianceManager.java", "com.truongcm.territorydefense.feature.alliance"),
    @("commands/AllyCommands.java", "feature/alliance/AllyCommands.java", "com.truongcm.territorydefense.feature.alliance"),
    @("commands/AllyTabCompleter.java", "feature/alliance/AllyTabCompleter.java", "com.truongcm.territorydefense.feature.alliance"),
    
    # Logistics
    @("logistics/FarmerManager.java", "feature/logistics/FarmerManager.java", "com.truongcm.territorydefense.feature.logistics"),
    @("logistics/FEPManager.java", "feature/logistics/FEPManager.java", "com.truongcm.territorydefense.feature.logistics"),
    @("logistics/NPCFarmer.java", "feature/logistics/NPCFarmer.java", "com.truongcm.territorydefense.feature.logistics"),
    
    # Security
    @("security/SecureEntityTracker.java", "feature/security/SecureEntityTracker.java", "com.truongcm.territorydefense.feature.security"),
    @("security/ItemSecurityListener.java", "feature/security/ItemSecurityListener.java", "com.truongcm.territorydefense.feature.security"),
    
    # Combat - Mercenary
    @("combat/mercenary/Mercenary.java", "feature/combat/mercenary/Mercenary.java", "com.truongcm.territorydefense.feature.combat.mercenary"),
    @("combat/mercenary/MercenaryAI.java", "feature/combat/mercenary/MercenaryAI.java", "com.truongcm.territorydefense.feature.combat.mercenary"),
    
    # Combat - Raid
    @("combat/raid/ActiveRaid.java", "feature/combat/raid/ActiveRaid.java", "com.truongcm.territorydefense.feature.combat.raid"),
    @("combat/raid/CombatDamageTracker.java", "feature/combat/raid/CombatDamageTracker.java", "com.truongcm.territorydefense.feature.combat.raid"),
    @("combat/raid/RaidSession.java", "feature/combat/raid/RaidSession.java", "com.truongcm.territorydefense.feature.combat.raid"),
    
    # Combat - Siege
    @("combat/siege/PostWarManager.java", "feature/combat/siege/PostWarManager.java", "com.truongcm.territorydefense.feature.combat.siege"),
    @("combat/siege/SiegeSession.java", "feature/combat/siege/SiegeSession.java", "com.truongcm.territorydefense.feature.combat.siege"),
    
    # Combat - Tower
    @("combat/tower/Tower.java", "feature/combat/tower/Tower.java", "com.truongcm.territorydefense.feature.combat.tower"),
    @("combat/tower/TowerManager.java", "feature/combat/tower/TowerManager.java", "com.truongcm.territorydefense.feature.combat.tower"),
    
    # Combat - Tower - types
    @("combat/tower/types/ArrowTower.java", "feature/combat/tower/types/ArrowTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    @("combat/tower/types/ArtilleryTower.java", "feature/combat/tower/types/ArtilleryTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    @("combat/tower/types/FireTower.java", "feature/combat/tower/types/FireTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    @("combat/tower/types/FrostTower.java", "feature/combat/tower/types/FrostTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    @("combat/tower/types/HealingTower.java", "feature/combat/tower/types/HealingTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    @("combat/tower/types/LightningTower.java", "feature/combat/tower/types/LightningTower.java", "com.truongcm.territorydefense.feature.combat.tower.types"),
    @("combat/tower/types/SpellTower.java", "feature/combat/tower/types/SpellTower.java", "com.truongcm.territorydefense.feature.combat.tower.types")
)

# 2. Define package and import replacement rules
$replacements = @{
    "com.truongcm.territorydefense.api.ProtocolLibHook" = "com.truongcm.territorydefense.hook.ProtocolLibHook"
    "com.truongcm.territorydefense.api.ServerChestHook" = "com.truongcm.territorydefense.hook.ServerChestHook"
    "com.truongcm.territorydefense.api.VaultHook" = "com.truongcm.territorydefense.hook.VaultHook"
    
    "com.truongcm.territorydefense.core.BorderVisualizer" = "com.truongcm.territorydefense.feature.core.BorderVisualizer"
    "com.truongcm.territorydefense.core.CoreManager" = "com.truongcm.territorydefense.feature.core.CoreManager"
    "com.truongcm.territorydefense.core.PDCKeys" = "com.truongcm.territorydefense.feature.core.PDCKeys"
    "com.truongcm.territorydefense.core.TerritoryCore" = "com.truongcm.territorydefense.feature.core.TerritoryCore"
    
    "com.truongcm.territorydefense.alliance.Alliance" = "com.truongcm.territorydefense.feature.alliance.Alliance"
    "com.truongcm.territorydefense.alliance.AllianceManager" = "com.truongcm.territorydefense.feature.alliance.AllianceManager"
    
    "com.truongcm.territorydefense.logistics.FarmerManager" = "com.truongcm.territorydefense.feature.logistics.FarmerManager"
    "com.truongcm.territorydefense.logistics.FEPManager" = "com.truongcm.territorydefense.feature.logistics.FEPManager"
    "com.truongcm.territorydefense.logistics.NPCFarmer" = "com.truongcm.territorydefense.feature.logistics.NPCFarmer"
    
    "com.truongcm.territorydefense.security.SecureEntityTracker" = "com.truongcm.territorydefense.feature.security.SecureEntityTracker"
    "com.truongcm.territorydefense.security.ItemSecurityListener" = "com.truongcm.territorydefense.feature.security.ItemSecurityListener"
    "com.truongcm.territorydefense.security.CoreProtectionListener" = "com.truongcm.territorydefense.feature.core.CoreProtectionListener"
    
    "com.truongcm.territorydefense.combat.mercenary.Mercenary" = "com.truongcm.territorydefense.feature.combat.mercenary.Mercenary"
    "com.truongcm.territorydefense.combat.mercenary.MercenaryAI" = "com.truongcm.territorydefense.feature.combat.mercenary.MercenaryAI"
    
    "com.truongcm.territorydefense.combat.raid.ActiveRaid" = "com.truongcm.territorydefense.feature.combat.raid.ActiveRaid"
    "com.truongcm.territorydefense.combat.raid.CombatDamageTracker" = "com.truongcm.territorydefense.feature.combat.raid.CombatDamageTracker"
    "com.truongcm.territorydefense.combat.raid.RaidSession" = "com.truongcm.territorydefense.feature.combat.raid.RaidSession"
    
    "com.truongcm.territorydefense.combat.siege.PostWarManager" = "com.truongcm.territorydefense.feature.combat.siege.PostWarManager"
    "com.truongcm.territorydefense.combat.siege.SiegeSession" = "com.truongcm.territorydefense.feature.combat.siege.SiegeSession"
    
    "com.truongcm.territorydefense.combat.tower.Tower" = "com.truongcm.territorydefense.feature.combat.tower.Tower"
    "com.truongcm.territorydefense.combat.tower.TowerManager" = "com.truongcm.territorydefense.feature.combat.tower.TowerManager"
    "com.truongcm.territorydefense.combat.tower.types" = "com.truongcm.territorydefense.feature.combat.tower.types"
    
    "com.truongcm.territorydefense.commands.TerritoryCommands" = "com.truongcm.territorydefense.feature.core.TerritoryCommands"
    "com.truongcm.territorydefense.commands.TerritoryTabCompleter" = "com.truongcm.territorydefense.feature.core.TerritoryTabCompleter"
    "com.truongcm.territorydefense.commands.AllyCommands" = "com.truongcm.territorydefense.feature.alliance.AllyCommands"
    "com.truongcm.territorydefense.commands.AllyTabCompleter" = "com.truongcm.territorydefense.feature.alliance.AllyTabCompleter"
    
    "com.truongcm.territorydefense.gui.CoreGuiHolder" = "com.truongcm.territorydefense.feature.core.CoreGui"
    "com.truongcm.territorydefense.gui.FarmerGuiHolder" = "com.truongcm.territorydefense.feature.logistics.FarmerUpgradeGui"
    "com.truongcm.territorydefense.gui.TowerGuiHolder" = "com.truongcm.territorydefense.feature.combat.tower.TowerUpgradeGui"
    "com.truongcm.territorydefense.gui.AllyGuiHolder" = "com.truongcm.territorydefense.feature.alliance.AllyMainMenuGui"
    "com.truongcm.territorydefense.gui.AllyInviteGuiHolder" = "com.truongcm.territorydefense.feature.alliance.AllyInviteGui"
    "com.truongcm.territorydefense.gui.AllyKickGuiHolder" = "com.truongcm.territorydefense.feature.alliance.AllyKickGui"
    "com.truongcm.territorydefense.gui.WarDeclarationGuiHolder" = "com.truongcm.territorydefense.feature.combat.siege.WarDeclarationGui"
}

function Process-File($srcPath, $destPath, $newPackage) {
    if (-not (Test-Path $srcPath)) { return }
    $content = [System.IO.File]::ReadAllText($srcPath)
    
    # 1. Update package statement
    $content = $content -replace "package\s+com\.truongcm\.territorydefense[^;]+;", "package $newPackage;"
    
    # 2. Update imports and type references
    foreach ($entry in $replacements.GetEnumerator()) {
        $oldImp = $entry.Key
        $newImp = $entry.Value
        
        $content = $content.Replace("import $oldImp;", "import $newImp;")
        
        # Replace class names if they changed
        $oldClass = $oldImp.Split('.')[-1]
        $newClass = $newImp.Split('.')[-1]
        if ($oldClass -ne $newClass) {
            $content = $content.Replace($oldClass, $newClass)
        }
    }
    
    # Ensure parent dir exists
    $parentDir = Split-Path $destPath -Parent
    if (-not (Test-Path $parentDir)) {
        New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
    }
    
    [System.IO.File]::WriteAllText($destPath, $content)
}

# Run movements
foreach ($m in $moves) {
    $oldFile = Join-Path $srcDir $m[0]
    $newFile = Join-Path $srcDir $m[1]
    $newPkg = $m[2]
    
    if (Test-Path $oldFile) {
        Write-Host "Moving & Processing: $($m[0]) -> $($m[1])"
        Process-File $oldFile $newFile $newPkg
        Remove-Item $oldFile
    } else {
        Write-Host "Skipping: $($m[0])"
    }
}

# Process TerritoryDefense.java
$mainFile = Join-Path $srcDir "TerritoryDefense.java"
if (Test-Path $mainFile) {
    $content = [System.IO.File]::ReadAllText($mainFile)
    
    # Remove GuiListener import
    $content = $content -replace "import\s+com\.truongcm\.territorydefense\.gui\.GuiListener;\s*\r?\n", ""
    
    # Replace other imports and class references
    foreach ($entry in $replacements.GetEnumerator()) {
        $oldImp = $entry.Key
        $newImp = $entry.Value
        
        $content = $content.Replace("import $oldImp;", "import $newImp;")
        
        $oldClass = $oldImp.Split('.')[-1]
        $newClass = $newImp.Split('.')[-1]
        if ($oldClass -ne $newClass) {
            $content = $content.Replace($oldClass, $newClass)
        }
    }
    
    [System.IO.File]::WriteAllText($mainFile, $content)
    Write-Host "Updated TerritoryDefense.java main imports and type names."
}

# Clean empty directories
Get-ChildItem $srcDir -Directory -Recurse | Sort-Object {$_.FullName.Length} -Descending | ForEach-Object {
    if ((Get-ChildItem $_.FullName -Force | Measure-Object).Count -eq 0) {
        Remove-Item $_.FullName
        Write-Host "Removed empty directory: $($_.FullName)"
    }
}
