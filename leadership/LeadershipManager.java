package com.livemine.leadership;

import com.livemine.LiveMineMod;
import com.livemine.VillageManager;
import com.livemine.domain.VillageRecord;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер лидерства (ТЗ 20.0 §21).
 *
 * Иерархия:
 *   - Лидер — выборный
 *   - Заместитель — назначает лидер
 *   - Казначей — назначает лидер (Торговля ≥ 5)
 *   - Начальник стражи — назначает лидер (Защита ≥ 5)
 *   - Старейшина-летописец — назначает лидер (любой навык ≥ 7)
 *
 * Каждая роль — разные права доступа к коллективной памяти и складам.
 */
public final class LeadershipManager {

    private static LeadershipManager INSTANCE;

    public enum Role {
        LEADER, DEPUTY, TREASURER, GUARD_CAPTAIN, CHRONICLER;

        public String ruName() {
            return switch (this) {
                case LEADER -> "Лидер";
                case DEPUTY -> "Заместитель";
                case TREASURER -> "Казначей";
                case GUARD_CAPTAIN -> "Начальник стражи";
                case CHRONICLER -> "Старейшина-летописец";
            };
        }

        public double requiredSkill() {
            return switch (this) {
                case LEADER -> 0;
                case DEPUTY -> 0;
                case TREASURER -> 5.0;
                case GUARD_CAPTAIN -> 5.0;
                case CHRONICLER -> 7.0;
            };
        }
    }

    public static final class RoleAssignment {
        public final UUID npcId;
        public final Role role;
        public final long assignedDay;

        public RoleAssignment(UUID npcId, Role role, long day) {
            this.npcId = npcId;
            this.role = role;
            this.assignedDay = day;
        }
    }

    // villageId в†' (role в†' assignment)
    private final Map<UUID, Map<Role, RoleAssignment>> villageRoles = new HashMap<>();

    private LeadershipManager() {}

    public static synchronized LeadershipManager getInstance() {
        if (INSTANCE == null) INSTANCE = new LeadershipManager();
        return INSTANCE;
    }

    // =========================================================================
    // Назначение
    // =========================================================================

    public boolean assignRole(UUID villageId, Role role, UUID npcId, long day) {
        LiveNPCEntity npc = com.livemine.ai.NPCRegistry.getNPC(npcId);
        if (npc == null || !npc.isAlive() || !npc.isAdult()) return false;

        // Проверка навыка (кроме лидера и заместителя)
        if (role != Role.LEADER && role != Role.DEPUTY) {
            double req = role.requiredSkill();
            boolean ok = false;
            if (npc.getSkills() != null) {
                for (var type : com.livemine.NPCSkills.SkillType.values()) {
                    if (npc.getSkills().getLevel(type) >= req) {
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) {
                LiveMineMod.LOGGER.debug(
                    "Cannot assign {} — no skill {}", role, req);
                return false;
            }
        }

        villageRoles.computeIfAbsent(villageId, k -> new HashMap<>())
            .put(role, new RoleAssignment(npcId, role, day));

        LiveMineMod.LOGGER.info("Role {} assigned to {} in village {}",
            role.ruName(), npcId, villageId);
        return true;
    }

    public void removeRole(UUID villageId, Role role) {
        Map<Role, RoleAssignment> roles = villageRoles.get(villageId);
        if (roles != null) roles.remove(role);
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public UUID getRoleHolder(UUID villageId, Role role) {
        Map<Role, RoleAssignment> roles = villageRoles.get(villageId);
        if (roles == null) return null;
        RoleAssignment a = roles.get(role);
        return a != null ? a.npcId : null;
    }

    public boolean hasRole(UUID villageId, UUID npcId) {
        Map<Role, RoleAssignment> roles = villageRoles.get(villageId);
        if (roles == null) return false;
        for (RoleAssignment a : roles.values()) {
            if (a.npcId.equals(npcId)) return true;
        }
        return false;
    }

    public Role getRoleOf(UUID villageId, UUID npcId) {
        Map<Role, RoleAssignment> roles = villageRoles.get(villageId);
        if (roles == null) return null;
        for (var e : roles.entrySet()) {
            if (e.getValue().npcId.equals(npcId)) return e.getKey();
        }
        return null;
    }

    public void clearVillage(UUID villageId) {
        villageRoles.remove(villageId);
    }

    public void clear() {
        villageRoles.clear();
    }
}
