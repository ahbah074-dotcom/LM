package com.livemine.education;

import com.livemine.LiveMineMod;
import com.livemine.NPCSkills;
import com.livemine.VillageData;
import com.livemine.ai.NPCRegistry;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер образования.
 *
 * v2.0: использует rawId ("village_<asLong>").
 */
public final class EducationManager {

    private static EducationManager INSTANCE;

    public static final int SMALL_SCHOOL_SIZE = 4;
    public static final int MEDIUM_SCHOOL_SIZE = 8;
    public static final int LARGE_SCHOOL_SIZE = 12;
    public static final double SCHOOL_BONUS = 1.5;
    public static final int TRUANCY_LIMIT = 3;

    public static final class Student {
        public UUID studentId;
        public UUID teacherId;
        public long enrolledDay;
        public int truancyCount;
        public NPCSkills.SkillType studyingSkill;
        public double bonus;

        public Student(UUID studentId, UUID teacherId, NPCSkills.SkillType skill, long day) {
            this.studentId = studentId;
            this.teacherId = teacherId;
            this.studyingSkill = skill;
            this.enrolledDay = day;
            this.bonus = SCHOOL_BONUS;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("student", studentId);
            tag.putUUID("teacher", teacherId);
            tag.putLong("enrolled", enrolledDay);
            tag.putInt("truancy", truancyCount);
            tag.putString("skill", studyingSkill != null ? studyingSkill.name() : "GENERAL_LABOR");
            tag.putDouble("bonus", bonus);
            return tag;
        }

        public static Student load(CompoundTag tag) {
            NPCSkills.SkillType skill;
            try {
                skill = NPCSkills.SkillType.valueOf(tag.getString("skill"));
            } catch (IllegalArgumentException e) {
                skill = NPCSkills.SkillType.GENERAL_LABOR;
            }
            Student s = new Student(
                tag.getUUID("student"),
                tag.getUUID("teacher"),
                skill,
                tag.getLong("enrolled")
            );
            s.truancyCount = tag.getInt("truancy");
            s.bonus = tag.getDouble("bonus");
            return s;
        }
    }

    public static final class School {
        public final String villageRawId;
        public int capacity;
        public final Map<UUID, Student> students = new HashMap<>();
        public final Set<UUID> teachers = new HashSet<>();
        public long foundedDay;

        public School(String villageRawId, int capacity, long day) {
            this.villageRawId = villageRawId;
            this.capacity = capacity;
            this.foundedDay = day;
        }

        public boolean hasSpace() { return students.size() < capacity; }
        public void addStudent(Student s) { students.put(s.studentId, s); }
        public void removeStudent(UUID id) { students.remove(id); }
    }

    private final Map<String, School> schools = new HashMap<>();

    private EducationManager() {}

    public static synchronized EducationManager getInstance() {
        if (INSTANCE == null) INSTANCE = new EducationManager();
        return INSTANCE;
    }

    public School getSchool(String villageRawId) { return schools.get(villageRawId); }
    public boolean hasSchool(String villageRawId) { return schools.containsKey(villageRawId); }

    public School createSchool(String villageRawId, int capacity, long day) {
        School s = new School(villageRawId, capacity, day);
        schools.put(villageRawId, s);
        LiveMineMod.LOGGER.info("School created for village {} (capacity {})",
            villageRawId, capacity);
        return s;
    }

    public int suggestCapacity(String villageRawId, ServerLevel level) {
        VillageData vd = new VillageData(villageRawId, level);
        if (!vd.exists()) return SMALL_SCHOOL_SIZE;

        int children = 0;
        for (UUID id : vd.getResidentNpcIds()) {
            LiveNPCEntity npc = NPCRegistry.getNPC(id);
            if (npc != null && npc.isChild()) children++;
        }

        if (children >= 12) return LARGE_SCHOOL_SIZE;
        if (children >= 8) return MEDIUM_SCHOOL_SIZE;
        return SMALL_SCHOOL_SIZE;
    }

    public boolean enroll(LiveNPCEntity student, LiveNPCEntity teacher,
                            NPCSkills.SkillType skill, ServerLevel level) {
        String rawId = student.getVillageRawId();
        if (rawId.isEmpty()) return false;

        School school = schools.get(rawId);
        if (school == null || !school.hasSpace()) return false;

        if (teacher == null || teacher.getSkills() == null) return false;
        double teacherLevel = teacher.getSkills().getLevel(skill);
        if (teacherLevel < 7.0) return false;

        Student s = new Student(student.getUUID(), teacher.getUUID(), skill,
            level.getDayTime() / 24000L);
        school.addStudent(s);
        school.teachers.add(teacher.getUUID());

        LiveMineMod.LOGGER.info("Student {} enrolled to {} (teacher {})",
            student.getCustomNameTag(), skill, teacher.getCustomNameTag());
        return true;
    }

    public void recordTruancy(String villageRawId, UUID studentId) {
        School s = schools.get(villageRawId);
        if (s == null) return;

        Student student = s.students.get(studentId);
        if (student == null) return;

        student.truancyCount++;
        if (student.truancyCount >= TRUANCY_LIMIT) {
            s.removeStudent(studentId);
            LiveMineMod.LOGGER.info("Student {} excluded for truancy", studentId);
        }
    }

    public double getLearningBonus(UUID npcId, ServerLevel level) {
        for (School school : schools.values()) {
            Student s = school.students.get(npcId);
            if (s != null) return s.bonus;
        }
        return 1.0;
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();

        for (School s : schools.values()) {
            CompoundTag st = new CompoundTag();
            st.putString("villageId", s.villageRawId);
            st.putInt("capacity", s.capacity);
            st.putLong("founded", s.foundedDay);

            ListTag studentsTag = new ListTag();
            for (Student student : s.students.values()) studentsTag.add(student.save());
            st.put("students", studentsTag);

            ListTag teachersTag = new ListTag();
            for (UUID t : s.teachers) {
                CompoundTag tt = new CompoundTag();
                tt.putUUID("uuid", t);
                teachersTag.add(tt);
            }
            st.put("teachers", teachersTag);

            list.add(st);
        }
        root.put("schools", list);
        return root;
    }

    public void load(CompoundTag tag) {
        schools.clear();
        ListTag list = tag.getList("schools", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag st = list.getCompound(i);
            School school = new School(
                st.getString("villageId"),
                st.getInt("capacity"),
                st.getLong("founded")
            );

            ListTag studentsTag = st.getList("students", Tag.TAG_COMPOUND);
            for (int j = 0; j < studentsTag.size(); j++) {
                Student s = Student.load(studentsTag.getCompound(j));
                school.students.put(s.studentId, s);
            }

            ListTag teachersTag = st.getList("teachers", Tag.TAG_COMPOUND);
            for (int j = 0; j < teachersTag.size(); j++) {
                school.teachers.add(teachersTag.getCompound(j).getUUID("uuid"));
            }

            schools.put(school.villageRawId, school);
        }
    }

    public void clear() { schools.clear(); }
    public int getSchoolCount() { return schools.size(); }

    public List<UUID> getAllStudents(String villageRawId) {
        School s = schools.get(villageRawId);
        if (s == null) return java.util.Collections.emptyList();
        return new ArrayList<>(s.students.keySet());
    }
}