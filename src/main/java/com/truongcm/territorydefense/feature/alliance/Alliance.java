package com.truongcm.territorydefense.feature.alliance;

import java.util.*;

/**
 * MODEL LIÊN MINH (ALLIANCE MODEL)
 * Đại diện cho một bang hội/liên minh trong hệ thống.
 * Quản trị trưởng nhóm, danh sách thành viên, quỹ tài chính và các Lõi thuộc sở hữu.
 */
public class Alliance {

    private final String allyId; // UUID viết ngắn độc bản định danh Liên minh
    private String name;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> activeCoreIds = new HashSet<>(); // Danh sách Lõi thuộc sở hữu phục vụ việc gộp đất
    private double bankBalance;

    public Alliance(String allyId, String name, UUID leader) {
        this.allyId = allyId;
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
        this.bankBalance = 0.0;
    }

    // --- TIỆN ÍCH QUẢN TRỊ THÀNH VIÊN ---

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        if (leader.equals(uuid) && !members.isEmpty()) {
            // Tự động thăng chức cho người tiếp theo nếu Leader rời bang
            leader = members.iterator().next();
        }
    }

    public void addCore(UUID coreId) {
        activeCoreIds.add(coreId);
    }

    public void removeCore(UUID coreId) {
        activeCoreIds.remove(coreId);
    }

    // --- GETTERS & SETTERS ---

    public String getAllyId() {
        return allyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getActiveCoreIds() {
        return activeCoreIds;
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = Math.max(0.0, bankBalance);
    }
}