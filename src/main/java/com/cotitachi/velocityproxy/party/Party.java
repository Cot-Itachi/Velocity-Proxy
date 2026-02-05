package com.cotitachi.velocityproxy.party;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Party {

    private final int id;
    private UUID leader;
    private final Set<UUID> members;
    private final long created;

    public Party(int id, UUID leader, long created) {
        this.id = id;
        this.leader = leader;
        this.members = ConcurrentHashMap.newKeySet();
        this.members.add(leader);
        this.created = created;
    }

    public int getId() {
        return id;
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

    public boolean isMember(UUID player) {
        return members.contains(player);
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public void addMember(UUID player) {
        members.add(player);
    }

    public void removeMember(UUID player) {
        members.remove(player);
    }

    public int size() {
        return members.size();
    }

    public long getCreated() {
        return created;
    }
}