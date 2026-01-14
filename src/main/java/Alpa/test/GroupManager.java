package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class GroupManager {
    private final main plugin;
    // 초대 정보를 저장할 맵 (초대받은 사람 UUID : 그룹 이름)
    private final Map<UUID, String> invitations = new HashMap<>();

    public GroupManager(main plugin) {
        this.plugin = plugin;
    }

    public void createGroup(String groupName, Player owner) {
        String path = "groups." + groupName;
        plugin.getConfig().set(path + ".owner", owner.getUniqueId().toString());
        List<String> members = new ArrayList<>();
        members.add(owner.getUniqueId().toString());
        plugin.getConfig().set(path + ".members", members);
        plugin.saveConfig();
    }

    // 초대하기
    public void invitePlayer(Player target, String groupName) {
        invitations.put(target.getUniqueId(), groupName);
        // 1분 후 초대 만료 (선택 사항)
        Bukkit.getScheduler().runTaskLater(plugin, () -> invitations.remove(target.getUniqueId()), 1200L);
    }

    // 초대 확인 및 수락
    public String getInvitation(Player player) {
        return invitations.get(player.getUniqueId());
    }

    public void removeInvitation(Player player) {
        invitations.remove(player.getUniqueId());
    }

    // 기존 가입 로직을 수락 로직으로 활용
    public boolean acceptInvite(Player player) {
        String groupName = invitations.get(player.getUniqueId());
        if (groupName == null) return false;

        List<String> members = plugin.getConfig().getStringList("groups." + groupName + ".members");
        if (!members.contains(player.getUniqueId().toString())) {
            members.add(player.getUniqueId().toString());
            plugin.getConfig().set("groups." + groupName + ".members", members);
            plugin.saveConfig();
            removeInvitation(player);
            return true;
        }
        return false;
    }

    public boolean isGroupMember(Player player, String doorOwnerUUID) {
        // 최신 데이터를 위해 리로드 (필요 시)
        ConfigurationSection groups = plugin.getConfig().getConfigurationSection("groups");
        if (groups == null) return false;

        String pUUID = player.getUniqueId().toString();

        for (String key : groups.getKeys(false)) {
            String gOwner = groups.getString(key + ".owner");
            List<String> members = groups.getStringList(key + ".members");

            // 문의 주인이 해당 그룹의 방장이고, 플레이어가 현재 그 그룹의 멤버 목록에 있는지 확인
            if (doorOwnerUUID.equals(gOwner) && members.contains(pUUID)) {
                return true;
            }
        }
        return false;
    }
    // GroupManager 클래스 내부에 추가
    public List<String> getGroupMembers(String groupName) {
        if (!plugin.getConfig().contains("groups." + groupName)) return new ArrayList<>();
        return plugin.getConfig().getStringList("groups." + groupName + ".members");
    }

    // 그룹 나가기 (탈퇴)
    public boolean leaveGroup(Player player) {
        String groupName = getPlayerGroup(player);
        if (groupName == null) return false;

        // 만약 그룹장이라면 탈퇴 대신 삭제를 유도하거나 방지해야 함
        if (isGroupOwner(groupName, player)) {
            player.sendMessage("§c그룹장은 그룹을 나갈 수 없습니다. '/그룹 삭제'를 이용해 주세요.");
            return false;
        }

        List<String> members = plugin.getConfig().getStringList("groups." + groupName + ".members");
        members.remove(player.getUniqueId().toString());
        plugin.getConfig().set("groups." + groupName + ".members", members);
        plugin.saveConfig();
        return true;
    }

    // 이미 그룹에 속해 있는지 확인 (생성 방지용)
    public boolean hasGroup(Player player) {
        return getPlayerGroup(player) != null;
    }

    // 그룹 삭제 메서드
    public void deleteGroup(String groupName) {
        plugin.getConfig().set("groups." + groupName, null);
        plugin.saveConfig();
    }

    // 해당 플레이어가 그룹의 주인(생성자)인지 확인하는 유틸리티
    public boolean isGroupOwner(String groupName, Player player) {
        String ownerUUID = plugin.getConfig().getString("groups." + groupName + ".owner");
        return ownerUUID != null && ownerUUID.equals(player.getUniqueId().toString());
    }

    // 플레이어가 속한 그룹 이름을 찾는 유틸리티 (GUI 호출용)
    public String getPlayerGroup(Player player) {
        ConfigurationSection groups = plugin.getConfig().getConfigurationSection("groups");
        if (groups == null) return null;
        String pUUID = player.getUniqueId().toString();
        for (String gName : groups.getKeys(false)) {
            if (groups.getStringList(gName + ".members").contains(pUUID)) return gName;
        }
        return null;
    }
    public String getGroupViaUUID(String uuid) {
        ConfigurationSection groups = plugin.getConfig().getConfigurationSection("groups");
        if (groups == null) return null;
        for (String gName : groups.getKeys(false)) {
            if (groups.getStringList(gName + ".members").contains(uuid)) return gName;
        }
        return null;
    }
}