package com.livemine.library;

import com.livemine.LiveMineMod;
import com.livemine.VillageData;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер библиотек и книг.
 *
 * v2.0: NPC-летописец пишет книги из хроники деревни.
 */
public final class LibraryManager {

    private static LibraryManager INSTANCE;

    public static final int MIN_BOOKS_FOR_LIBRARY = 5;
    public static final int MAX_BOOKS_PER_LIBRARY = 64;

    public enum BookType {
        CHRONICLE("Хроника событий"),
        BIOGRAPHY("Биография мастера"),
        RECIPE("Сборник рецептов"),
        MAP("Карта"),
        LEGEND("Легенда");

        public final String ruName;
        BookType(String ru) { this.ruName = ru; }
    }

    public static final class Book {
        public UUID bookId;
        public UUID authorId;
        public String authorName;
        public String title;
        public BookType type;
        public String content;
        public long writtenDay;

        public Book(UUID authorId, String authorName, String title,
                     BookType type, String content, long day) {
            this.bookId = UUID.randomUUID();
            this.authorId = authorId;
            this.authorName = authorName != null ? authorName : "Неизвестный";
            this.title = title != null ? title : "Без названия";
            this.type = type;
            this.content = content != null ? content : "";
            this.writtenDay = day;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", bookId);
            if (authorId != null) tag.putUUID("authorId", authorId);
            tag.putString("authorName", authorName);
            tag.putString("title", title);
            tag.putString("type", type.name());
            tag.putString("content", content);
            tag.putLong("day", writtenDay);
            return tag;
        }

        public static Book load(CompoundTag tag) {
            Book b = new Book(
                tag.hasUUID("authorId") ? tag.getUUID("authorId") : null,
                tag.getString("authorName"),
                tag.getString("title"),
                BookType.valueOf(tag.getString("type")),
                tag.getString("content"),
                tag.getLong("day")
            );
            b.bookId = tag.getUUID("id");
            return b;
        }
    }

    public static final class Library {
        public final UUID villageId;
        public long foundedDay;
        public final List<Book> books = new ArrayList<>();

        public Library(UUID villageId, long foundedDay) {
            this.villageId = villageId;
            this.foundedDay = foundedDay;
        }

        public boolean addBook(Book book) {
            if (books.size() >= MAX_BOOKS_PER_LIBRARY) return false;
            books.add(book);
            return true;
        }

        public int size() { return books.size(); }
    }

    private final Map<UUID, Library> libraries = new HashMap<>();

    private LibraryManager() {}

    public static synchronized LibraryManager getInstance() {
        if (INSTANCE == null) INSTANCE = new LibraryManager();
        return INSTANCE;
    }

    // =========================================================================
    // Библиотеки
    // =========================================================================

    public Library getLibrary(UUID villageId) { return libraries.get(villageId); }
    public boolean hasLibrary(UUID villageId) { return libraries.containsKey(villageId); }

    public Library createLibrary(UUID villageId, long foundedDay) {
        Library lib = new Library(villageId, foundedDay);
        libraries.put(villageId, lib);
        LiveMineMod.LOGGER.info("Library created for village {}", villageId);
        return lib;
    }

    public boolean canBuildLibrary(UUID villageId, int totalBooks) {
        return !hasLibrary(villageId) && totalBooks >= MIN_BOOKS_FOR_LIBRARY;
    }

    // =========================================================================
    // Книги
    // =========================================================================

    public boolean addBook(UUID villageId, Book book) {
        Library lib = libraries.get(villageId);
        if (lib == null) return false;
        boolean added = lib.addBook(book);
        if (added) {
            LiveMineMod.LOGGER.info("Book '{}' added to library of {}",
                book.title, villageId);
        }
        return added;
    }

    public List<Book> getBooks(UUID villageId) {
        Library lib = libraries.get(villageId);
        return lib != null ? Collections.unmodifiableList(lib.books) : Collections.emptyList();
    }

    public boolean transferBook(UUID fromVillage, UUID toVillage, UUID bookId) {
        Library source = libraries.get(fromVillage);
        Library target = libraries.get(toVillage);
        if (source == null || target == null) return false;

        Book found = null;
        for (Book b : source.books) {
            if (b.bookId.equals(bookId)) { found = b; break; }
        }
        if (found == null) return false;

        source.books.remove(found);
        target.addBook(found);
        LiveMineMod.LOGGER.info("Book '{}' transferred: {} -> {}",
            found.title, fromVillage, toVillage);
        return true;
    }

    public int getTotalBooks(UUID villageId) {
        Library lib = libraries.get(villageId);
        return lib != null ? lib.size() : 0;
    }

    public int getLibraryCount() { return libraries.size(); }

    // =========================================================================
    // v2.0: Автоматическая запись книг летописцем
    // =========================================================================

    /**
     * Пытается написать новую книгу из хроники деревни.
     * Требуется NPC-летописец с высоким навыком.
     *
     * @return true, если книга написана
     */
    public boolean tryWriteBook(LiveNPCEntity scribe, ServerLevel level) {
        if (scribe == null) return false;
        if (!scribe.isAdult()) return false;
        if (scribe.getSkills() == null) return false;

        // Требуется любой навык ≥ 7 (упрощённо: DIPLOMACY).
        double skill = scribe.getSkills().getLevel(com.livemine.NPCSkills.SkillType.DIPLOMACY);
        if (skill < 7.0) return false;

        String rawId = scribe.getVillageRawId();
        if (rawId.isEmpty()) return false;

        VillageData vd = new VillageData(rawId, level);
        if (!vd.exists()) return false;

        // Получаем хронику.
        var chronicle = vd.getChronicle();
        if (chronicle.getEntryCount() < 5) return false;

        var recent = chronicle.getRecentEntries(20);
        StringBuilder content = new StringBuilder();
        for (var e : recent) {
            content.append("[день ").append(e.day).append("] ")
                .append(e.description).append("\n");
        }

        long day = level.getDayTime() / 24000L;
        String title = "Хроника " + rawId + " (день " + day + ")";

        Book book = new Book(
            scribe.getUUID(),
            scribe.getCustomNameTag(),
            title,
            BookType.CHRONICLE,
            content.toString(),
            day
        );

        // Куда класть? Если есть библиотека — в неё.
        UUID villageUuid = scribe.getVillageId();
        if (villageUuid != null) {
            if (hasLibrary(villageUuid)) {
                return addBook(villageUuid, book);
            } else if (canBuildLibrary(villageUuid, 5)) {
                createLibrary(villageUuid, day);
                return addBook(villageUuid, book);
            }
        }

        // Библиотеки нет — книгу не пишем, но можно расширить позже.
        LiveMineMod.LOGGER.info("Scribe {} tried to write book but no library",
            scribe.getCustomNameTag());
        return false;
    }

    /**
     * Возвращает true, если NPC может быть летописцем.
     */
    public boolean isScribe(LiveNPCEntity npc) {
        if (npc == null || npc.getSkills() == null) return false;
        return npc.getSkills().getLevel(com.livemine.NPCSkills.SkillType.DIPLOMACY) >= 7.0;
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();

        for (Library lib : libraries.values()) {
            CompoundTag libTag = new CompoundTag();
            libTag.putUUID("villageId", lib.villageId);
            libTag.putLong("foundedDay", lib.foundedDay);

            ListTag booksList = new ListTag();
            for (Book b : lib.books) booksList.add(b.save());
            libTag.put("books", booksList);

            list.add(libTag);
        }
        root.put("libraries", list);
        return root;
    }

    public void load(CompoundTag tag) {
        libraries.clear();
        if (tag == null) return;
        ListTag list = tag.getList("libraries", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag libTag = list.getCompound(i);
            UUID villageId = libTag.getUUID("villageId");
            long foundedDay = libTag.getLong("foundedDay");

            Library lib = new Library(villageId, foundedDay);

            ListTag booksList = libTag.getList("books", Tag.TAG_COMPOUND);
            for (int j = 0; j < booksList.size(); j++) {
                lib.books.add(Book.load(booksList.getCompound(j)));
            }
            libraries.put(villageId, lib);
        }
    }

    public void clear() { libraries.clear(); }
}