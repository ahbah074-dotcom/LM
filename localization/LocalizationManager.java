package com.livemine.localization;

import com.livemine.LiveMineMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер локализации (ТЗ 20.1 §91).
 *
 * 13 языков:
 *   ru_ru, en_us, fr_fr, de_de, ar_sa (RTL), zh_cn, hi_in, es_es,
 *   bn_in, pt_br, fa_ir (RTL), el_gr, grc_gr (частично)
 *
 * Fallback: запрошенный → en_us → ru_ru → key.
 * RTL-языки: ar_sa, fa_ir.
 *
 * Хранит предпочтение языка каждого игрока.
 */
public final class LocalizationManager {

    private static LocalizationManager INSTANCE;

    public static final String DEFAULT_LANG = "ru_ru";
    public static final String FALLBACK_LANG = "en_us";

    private static final Set<String> SUPPORTED_LANGS = new HashSet<>();
    private static final Set<String> RTL_LANGS = new HashSet<>();

    static {
        SUPPORTED_LANGS.add("ru_ru");
        SUPPORTED_LANGS.add("en_us");
        SUPPORTED_LANGS.add("fr_fr");
        SUPPORTED_LANGS.add("de_de");
        SUPPORTED_LANGS.add("ar_sa");
        SUPPORTED_LANGS.add("zh_cn");
        SUPPORTED_LANGS.add("hi_in");
        SUPPORTED_LANGS.add("es_es");
        SUPPORTED_LANGS.add("bn_in");
        SUPPORTED_LANGS.add("pt_br");
        SUPPORTED_LANGS.add("fa_ir");
        SUPPORTED_LANGS.add("el_gr");
        SUPPORTED_LANGS.add("grc_gr");

        RTL_LANGS.add("ar_sa");
        RTL_LANGS.add("fa_ir");
    }

    private final Map<UUID, String> playerLanguages = new HashMap<>();

    private LocalizationManager() {}

    public static synchronized LocalizationManager getInstance() {
        if (INSTANCE == null) INSTANCE = new LocalizationManager();
        return INSTANCE;
    }

    // =========================================================================
    // Язык игрока
    // =========================================================================

    public String getLanguage(ServerPlayer player) {
        if (player == null) return DEFAULT_LANG;
        return playerLanguages.getOrDefault(player.getUUID(), DEFAULT_LANG);
    }

    public void setLanguage(ServerPlayer player, String lang) {
        if (player == null || !isSupported(lang)) return;
        playerLanguages.put(player.getUUID(), lang);
        LiveMineMod.LOGGER.info("Player {} set language to {}",
            player.getName().getString(), lang);
    }

    public Set<String> getSupportedLanguages() {
        return new HashSet<>(SUPPORTED_LANGS);
    }

    public boolean isSupported(String lang) {
        return lang != null && SUPPORTED_LANGS.contains(lang);
    }

    public boolean isRTL(String lang) {
        return lang != null && RTL_LANGS.contains(lang);
    }

    // =========================================================================
    // Перевод
    // =========================================================================

    /**
     * Возвращает Component по ключу на языке игрока.
     */
    public MutableComponent translate(ServerPlayer player, String key, Object... args) {
        return Component.translatable(key, args);
    }

    public MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    // =========================================================================
    // Утилиты для языков
    // =========================================================================

    /**
     * Язык по культуре (для генерации NPC).
     */
    public String getCultureLanguage(String culture) {
        if (culture == null) return DEFAULT_LANG;
        return switch (culture.toLowerCase()) {
            case "rus", "фины", "норманны" -> "ru_ru";
            case "майя" -> "es_es";
            case "индейцы", "англы", "романы" -> "en_us";
            case "пустынные", "арабы" -> "ar_sa";
            case "таёжные" -> "ru_ru";
            case "франки" -> "fr_fr";
            case "саксоны" -> "de_de";
            case "греки" -> "el_gr";
            case "персы" -> "fa_ir";
            case "китайцы" -> "zh_cn";
            case "индийцы" -> "hi_in";
            default -> DEFAULT_LANG;
        };
    }

    /**
     * Форматирование текста с учётом RTL.
     */
    public String formatRTL(String text, boolean rtl) {
        if (!rtl) return text;
        // Простой bidi: RTL-метка в начале и в конце
        return "\u202B" + text + "\u202C";
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public net.minecraft.nbt.CompoundTag save() {
        var tag = new net.minecraft.nbt.CompoundTag();
        var langTag = new net.minecraft.nbt.CompoundTag();
        for (var e : playerLanguages.entrySet()) {
            langTag.putString(e.getKey().toString(), e.getValue());
        }
        tag.put("players", langTag);
        return tag;
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        playerLanguages.clear();
        var langTag = tag.getCompound("players");
        for (String k : langTag.getAllKeys()) {
            try {
                playerLanguages.put(UUID.fromString(k), langTag.getString(k));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void clear() {
        playerLanguages.clear();
    }
}
