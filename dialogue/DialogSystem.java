package com.livemine.dialogue;

import com.livemine.LiveMineSavedData;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.personality.NPCEmotions;
import com.livemine.personality.NPCTraits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/**
 * Система диалогов NPC.
 *
 * v4.0: 25 типов диалогов, 7 типов аргументов, модификаторы знакомства,
 * эмоций и черт. 250+ базовых реплик, 2500+ уникальных комбинаций.
 */
public final class DialogSystem {

    public enum DialogType {
        // Социальные
        GREETING, NEWS, TEACHING, HELP_REQUEST, SUPPORT,
        FINDING, THREAT, RECIPE, ERROR, SEASON, PLAYER,
        BIOME, NEIGHBOR, TRADE, DEBT, SECRET,
        // Работа
        TRADING, BUILDING, PATROLLING, MARCHING, DEFENDING,
        ATTACKING, FLEEING, HUNTING, MINING, FARMING,
        SMITHING, COOKING, READING, PRAYING,
        // Споры
        ARG_WORK, ARG_FAMILY, ARG_POLITICAL, ARG_TRADE,
        ARG_FAITH, ARG_PERSONAL, ARG_TERRITORY
    }

    private static final Random RANDOM = new Random();

    private DialogSystem() {}

    // =========================================================================
    // Определение контекста
    // =========================================================================

    public static DialogType detectContext(LiveNPCEntity npc, ServerPlayer player, ServerLevel level) {
        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return DialogType.GREETING;

        // Дети — только детские реплики.
        if (npc.isChild()) return DialogType.PLAYER;

        // Критические состояния.
        if (tag.getDouble("health") < 10) return DialogType.HELP_REQUEST;
        if (tag.getDouble("hunger") < 30) return DialogType.HELP_REQUEST;

        // Эмоции.
        NPCEmotions em = npc.getEmotions();
        if (em != null) {
            if (em.isAngry()) return pickArgType(level);
            if (em.isAfraid()) return DialogType.FLEEING;
            if (em.get(NPCEmotions.Emotion.GRIEF) > 40) return DialogType.SUPPORT;
        }

        // Ночь.
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime > 13000 && dayTime < 23000) return DialogType.SEASON;

        // Погода.
        if (level.isThundering()) return DialogType.THREAT;
        if (level.isRaining()) return DialogType.SEASON;

        // Контекст по текущей цели.
        String goal = npc.getNpcBrain().getCurrentGoalName();
        if (goal != null) {
            switch (goal) {
                case "WORK" -> {
                    return detectWorkType(npc, level);
                }
                case "REST" -> { return DialogType.NEWS; }
                case "SOCIAL" -> { return DialogType.SUPPORT; }
                case "COMBAT" -> { return DialogType.ATTACKING; }
                case "FLEE" -> { return DialogType.FLEEING; }
                case "HEALING", "HEAL" -> { return DialogType.HELP_REQUEST; }
                case "FOOD", "EAT" -> { return DialogType.COOKING; }
            }
        }

        // Обучение.
        if (npc.getSkills() != null) {
            double dip = npc.getSkills().getLevel(com.livemine.NPCSkills.SkillType.DIPLOMACY);
            if (dip > 5 && level.random.nextDouble() < 0.15) return DialogType.TEACHING;
        }

        // Редкие.
        double r = level.random.nextDouble();
        if (r < 0.05) return DialogType.PLAYER;
        if (r < 0.10) return DialogType.NEIGHBOR;
        if (r < 0.12) return DialogType.SECRET;
        if (r < 0.14) return DialogType.DEBT;
        if (r < 0.17) return DialogType.TRADING;

        return DialogType.GREETING;
    }

    /**
     * v4.0: определяет рабочий контекст (шахта, ферма, кузница).
     */
    private static DialogType detectWorkType(LiveNPCEntity npc, ServerLevel level) {
        if (npc.getSkills() == null) return DialogType.BUILDING;

        // Ищем активную цель через skill.
        var skills = npc.getSkills();
        double mining = skills.getLevel(com.livemine.NPCSkills.SkillType.MINING);
        double farming = skills.getLevel(com.livemine.NPCSkills.SkillType.FARMING);
        double smithing = skills.getLevel(com.livemine.NPCSkills.SkillType.SMITHING);
        double building = skills.getLevel(com.livemine.NPCSkills.SkillType.BUILDING);
        double guarding = skills.getLevel(com.livemine.NPCSkills.SkillType.GUARDING);
        double hunting = skills.getLevel(com.livemine.NPCSkills.SkillType.HUNTING);
        double cooking = skills.getLevel(com.livemine.NPCSkills.SkillType.COOKING);

        double max = Math.max(Math.max(mining, farming),
            Math.max(Math.max(smithing, building),
            Math.max(Math.max(guarding, hunting), cooking)));

        double r = level.random.nextDouble();
        if (r < 0.12) return DialogType.MINING;
        if (r < 0.24) return DialogType.FARMING;
        if (r < 0.36) return DialogType.SMITHING;
        if (r < 0.50) return DialogType.BUILDING;
        if (r < 0.62) return DialogType.PATROLLING;
        if (r < 0.74) return DialogType.HUNTING;
        if (r < 0.82) return DialogType.COOKING;
        if (r < 0.90) return DialogType.TRADING;
        if (r < 0.95) return DialogType.READING;
        return DialogType.PRAYING;
    }

    /**
     * v4.0: случайный тип аргумента.
     */
    private static DialogType pickArgType(ServerLevel level) {
        return switch (level.random.nextInt(7)) {
            case 0 -> DialogType.ARG_WORK;
            case 1 -> DialogType.ARG_FAMILY;
            case 2 -> DialogType.ARG_POLITICAL;
            case 3 -> DialogType.ARG_TRADE;
            case 4 -> DialogType.ARG_FAITH;
            case 5 -> DialogType.ARG_PERSONAL;
            default -> DialogType.ARG_TERRITORY;
        };
    }

    // =========================================================================
    // Генерация
    // =========================================================================

    public static String generate(LiveNPCEntity npc, ServerPlayer player, ServerLevel level) {
        NPCDialogMemory memory = npc.getDialogMemory();
        DialogType type = detectContext(npc, player, level);

        // Избегаем повторов.
        int attempts = 0;
        while (memory != null && memory.wasRecentlyUsed(type) && attempts++ < 4) {
            type = pickAlternative(type);
        }

        if (memory != null) {
            memory.remember(type);
            memory.recordTalkWith(player.getUUID(), level.getDayTime() / 24000L);
        }

        return compose(type, npc, player, memory);
    }

    /**
     * v4.0: композиция — база + префикс (знакомство) + суффикс (эмоции/черты).
     */
    private static String compose(DialogType type, LiveNPCEntity npc,
                                   ServerPlayer player, NPCDialogMemory memory) {
        if (npc == null) return baseRеplikа(type);

        // Дети и старики — свои реплики.
        if (npc.isChild()) return childReplica(type);
        if (npc.getAgeInDays() >= 90) return elderReplica(type);

        // Уровень знакомства.
        NPCDialogMemory.Familiarity fam = memory != null
            ? memory.getFamiliarity(player.getUUID())
            : NPCDialogMemory.Familiarity.STRANGER;

        // База.
        String base = baseRеplikа(type);

        // Префикс по знакомству (только в 20% случаев — для разнообразия).
        if (RANDOM.nextDouble() < 0.20) {
            base = familiarityPrefix(fam) + base;
        }

        // Суффикс от эмоции (в 15% случаев).
        NPCEmotions em = npc.getEmotions();
        if (em != null && RANDOM.nextDouble() < 0.15) {
            base = base + emotionSuffix(em);
        }

        // Суффикс от черты (в 10% случаев).
        NPCTraits traits = npc.getTraits();
        if (traits != null && RANDOM.nextDouble() < 0.10) {
            base = base + traitSuffix(traits);
        }

        return base;
    }

    // =========================================================================
    // База реплик по типу (25 типов × 8 = 200 реплик)
    // =========================================================================

    private static String baseRеplikа(DialogType type) {
        return switch (type) {
            case GREETING -> pick(
                "Приветствую, странник.",
                "Здравствуй!",
                "Рад видеть тебя в нашей деревне.",
                "Кто ты? Не часто у нас гости.",
                "Хэй! К нам пожаловал!",
                "Приветствую тебя, путник.",
                "Здорово, коли не шутишь.",
                "Мир тебе, странник."
            );

            case NEWS -> pick(
                "Слышал, у соседей новый лидер.",
                "Говорят, караван скоро придёт.",
                "Зима будет холодной, надо готовиться.",
                "Наш кузнец новое что-то выдумал.",
                "Праздник скоро — будем гулять!",
                "У рыбака сегодня улов богатый.",
                "Странник приходил вчера, интересное рассказывал.",
                "Лидер наш что-то задумал, только молчит."
            );

            case TEACHING -> pick(
                "Могу показать тебе пару приёмов ремесла.",
                "Хочешь научиться чему-нибудь полезному?",
                "Учиться никогда не поздно.",
                "Смотри, как надо — раз, два, три.",
                "Молодой, а уже интересуешься — хорошо.",
                "Вот, запоминай: главное — терпение.",
                "Мой дед так делал, и я так делаю.",
                "Не бойся ошибаться, бойся не пробовать."
            );

            case HELP_REQUEST -> pick(
                "Помоги, если можешь — деревне нужна твоя рука.",
                "Есть дело для тебя, странник.",
                "Не откажешь в помощи?",
                "Мы заплатим, чем сможем.",
                "Прошу, помоги — сам не справлюсь.",
                "Одна голова хорошо, а две — лучше.",
                "Без тебя не справимся.",
                "Выручай, брат."
            );

            case SUPPORT -> pick(
                "Ты хороший человек, я это вижу.",
                "Спасибо, что помогаешь нам.",
                "Деревня тебе благодарна.",
                "С тобой не страшно.",
                "Хорошо, что ты рядом.",
                "Мы это запомним.",
                "Дай тебе боги здоровья.",
                "Ты нам как родной уже."
            );

            case FINDING -> pick(
                "Нашёл вчера странный камень в шахте.",
                "Смотри, что я выкопал!",
                "Клад! Настоящий клад!",
                "Руда новая, необычная.",
                "Гриб нашёл, огромный!",
                "Кость древняя из-под земли.",
                "Монета старая, времён ещё до нас.",
                "Странный амулет, светится в темноте."
            );

            case THREAT -> pick(
                "Угроза близко — держись наготове.",
                "Чувствую опасность.",
                "Что-то нехорошее на горизонте.",
                "Осторожно! Тише.",
                "Враг рядом — я это чую.",
                "Тревога! Осторожно!",
                "Зверь рыщет где-то рядом.",
                "Не ходи один сегодня."
            );

            case RECIPE -> pick(
                "Хочешь рецепт? У меня есть один.",
                "Знаю, как сделать это лучше.",
                "Секрет мастерства — в деталях.",
                "Вот, попробуй по-моему.",
                "Огонь, вода, щепотка терпения — и готово.",
                "Дед мой так делал, и я так делаю.",
                "Три части — и получается чудо.",
                "Слыхал? Смешай в правильной пропорции."
            );

            case ERROR -> pick(
                "Опять не получилось...",
                "Что-то я сделал не так.",
                "Руки не там стоят, переделать надо.",
                "Проклятье! Всё заново.",
                "Так, отойди, я сам.",
                "Не выходит у меня сегодня.",
                "Тьфу ты, ну и денёк.",
                "Криво вышло — переделаю."
            );

            case SEASON -> pick(
                "Погода сегодня не для работы.",
                "Поздно уже — пора спать.",
                "Скоро зима, надо готовиться.",
                "Весна идёт, земля проснётся.",
                "Дождь, грязь — сиди дома.",
                "Летом-то лучше было.",
                "Осень золотая — красота!",
                "Холодно, промозгло... брр."
            );

            case PLAYER -> pick(
                "Ты не из наших краёв, верно?",
                "Много путешествуешь?",
                "Я слышал о тебе от других.",
                "Откуда путь держишь?",
                "Говорят, ты много где был.",
                "Что привело тебя сюда?",
                "Вид у тебя усталый.",
                "Странник — это судьба такая."
            );

            case BIOME -> pick(
                "Хорошие земли здесь, плодородные.",
                "Лес богатый, дичь водится.",
                "Река рядом — рыба ловится.",
                "Тут раньше пустошь была, теперь красота.",
                "Зимой, правда, холодно.",
                "Диких зверей много, но мы справляемся.",
                "Земля наша кормит нас.",
                "Красиво тут у нас, да?"
            );

            case NEIGHBOR -> pick(
                "У соседей дела идут неплохо.",
                "Мы с соседями в мире.",
                "Слыхал, в соседней деревне праздник.",
                "Караван от соседей приходил.",
                "Они нам помогают, а мы им.",
                "Дорога новая — теперь ближе.",
                "Хорошие люди эти соседи.",
                "Надо бы к ним заглянуть."
            );

            case TRADE -> pick(
                "Есть что продать?",
                "Хочешь обменяться?",
                "У меня найдётся товар.",
                "Цена — по-божески.",
                "Товар свежий, не сомневайся.",
                "Что тебе по нраву?",
                "Куплю, продам — всё честно.",
                "Слово твоё — мне цена."
            );

            case DEBT -> pick(
                "Ты мне должен, помнишь?",
                "Долг платежом красен.",
                "Не забудь про долг.",
                "Верни, когда сможешь, только верни.",
                "Сколько ждать-то?",
                "Долг — дело святое.",
                "Не хочу напоминать, но...",
                "Я запомнил. Ты тоже помни."
            );

            case SECRET -> pick(
                "Есть у меня тайна одна...",
                "Никому не рассказывай.",
                "Тс-с-с, это между нами.",
                "Я доверяю только тебе.",
                "Слушай, но не болтай.",
                "Слово дай, что никому.",
                "Узнаешь — умрёшь? Шутка.",
                "Это очень личное."
            );

            // ---- Рабочие контексты ----
            case TRADING -> pick(
                "У меня сегодня хороший товар.",
                "Купи — не пожалеешь.",
                "Цена сходная, честная.",
                "Хочешь скидку? Возьми два — третий в подарок.",
                "Товар ходовой, разбирают.",
                "Всё свежее, только что привезли.",
                "Отдам по-соседски.",
                "Смотри, вот это точно возьмёшь."
            );

            case BUILDING -> pick(
                "Строю вот — дом новый нужен.",
                "Ещё пара блоков — и готово.",
                "Хороший дом выйдет, крепкий.",
                "Брёвна таскаю с утра.",
                "Помощник нужен — не хочешь помочь?",
                "Фундамент уже залили.",
                "Крышу к вечеру закончу.",
                "Люблю строить — душа радуется."
            );

            case PATROLLING -> pick(
                "Обхожу деревню — всё тихо.",
                "Смотрю за порядком.",
                "Ночью спать нельзя — работа у меня такая.",
                "Ничего подозрительного пока.",
                "Каждые два часа — обход.",
                "Стена крепкая, ворота закрыты.",
                "Враг не пройдёт.",
                "Дозор — дело серьёзное."
            );

            case MARCHING -> pick(
                "Идём в дальний путь.",
                "Поход — это не прогулка.",
                "Караван наш уже в пути.",
                "Дорога длинная, но идём.",
                "За провизией собрались.",
                "Вернёмся через неделю.",
                "Путь нелёгкий, но нужный.",
                "Кто с нами — собирайся."
            );

            case DEFENDING -> pick(
                "Враг у ворот! Готовьтесь!",
                "Держим строй!",
                "Не пропустим никого!",
                "Луки наготове!",
                "Дети и старики — в подвал!",
                "Копья вперёд!",
                "До последнего вздоха!",
                "За деревню! За родных!"
            );

            case ATTACKING -> pick(
                "В атаку!",
                "Бей врага!",
                "За мной!",
                "Не отступать!",
                "Вперёд, во славу деревни!",
                "Кровь за кровь!",
                "Смерть врагам!",
                "Победа или смерть!"
            );

            case FLEEING -> pick(
                "Бежим! Скорее!",
                "Спасайся кто может!",
                "Не оглядывайся!",
                "Прочь отсюда!",
                "Нас слишком мало!",
                "К воротам!",
                "В укрытие!",
                "Живым надо выбраться!"
            );

            case HUNTING -> pick(
                "След нашёл — олень рядом.",
                "Кабан тут ходит, я знаю.",
                "Силки расставлю.",
                "Лук наготове.",
                "Тише, дичь близко.",
                "Мясо будет — знатное.",
                "Шкура хорошая выйдет.",
                "Зимой охота — самое то."
            );

            case MINING -> pick(
                "В шахте с утра.",
                "Руда попалась хорошая.",
                "Глубже надо копать.",
                "Жила кончается — новую искать.",
                "Тяжело внизу, но надо.",
                "Кирка уже тупится.",
                "Уголь есть, железо есть.",
                "Дома ждут — а я тут."
            );

            case FARMING -> pick(
                "Урожай нынче хороший.",
                "Полить надо — дождь-то был давно.",
                "Пшеница колосится.",
                "Сорняки одолели.",
                "Бурёнок пасти надо.",
                "Земля наша кормилица.",
                "Потрудишься — поешь.",
                "Весной посеял — к осени соберу."
            );

            case SMITHING -> pick(
                "У горна стою с утра.",
                "Меч новый кую.",
                "Железо калёное — крепкое.",
                "Молотом бью — искры летят.",
                "Клинок выходит знатный.",
                "Руда в слиток — слиток в клинок.",
                "Кузнец без огня — не кузнец.",
                "Дед мой кузнецом был, и я кузнец."
            );

            case COOKING -> pick(
                "Суп варю — вкусный выйдет.",
                "Хлеб печётся, запах — с ума сойти.",
                "Пироги будут к празднику.",
                "Мясо тушу с утра.",
                "Специи правильные — половина успеха.",
                "Гости будут — надо накрыть.",
                "Голодного накормлю — душа радуется.",
                "Соль, перец — и чудо!"
            );

            case READING -> pick(
                "Читаю хронику деревни.",
                "Книга эта — редкость.",
                "Знание — сила.",
                "Летописец много писал — интересно.",
                "Тут про прадеда нашего.",
                "Карта древняя попалась.",
                "Учусь по книгам — как дед.",
                "Грамоте обучен — читаю."
            );

            case PRAYING -> pick(
                "Молюсь о здравии близких.",
                "Богам — свеча, мне — покой.",
                "Прошу удачи в пути.",
                "Обряд провожу — как надо.",
                "Духи предков — с нами.",
                "Тихо здесь... хорошо.",
                "Мир душе твоей.",
                "Да будет воля высшая."
            );

            // ---- Аргументы ----
            case ARG_WORK -> pick(
                "Ты делаешь это неправильно!",
                "Мой дед иначе учил!",
                "Инструмент не для этого!",
                "Отойди, я сам сделаю!",
                "Ты не мастер — молчи!",
                "Халтура это, а не работа!",
                "Учись, пока жив!",
                "Так никто не делает!"
            );

            case ARG_FAMILY -> pick(
                "Не лезь в мою семью!",
                "Ты не знаешь их!",
                "Я за родных — горой!",
                "Как ты смеешь так говорить?",
                "Моя кровь — не твоё дело!",
                "Ты не заслужил такое слышать!",
                "Семья — это святое!",
                "Не тронь моих!"
            );

            case ARG_POLITICAL -> pick(
                "Лидер наш — мудрый!",
                "Не тебе судить!",
                "Ты бы лучше сделал?",
                "Мы голосовали — и точка!",
                "Иди своей дорогой!",
                "Тут не твоя деревня!",
                "Мы сами разберёмся!",
                "Слово твоё — пустой звук!"
            );

            case ARG_TRADE -> pick(
                "Обман! Цена не такая!",
                "Не хочу с тобой дело иметь!",
                "Ты мне не доверяешь — и я не буду!",
                "Плати, как договорились!",
                "Ты мне не первый раз так!",
                "Хватит, я не дурак!",
                "Слово дал — держи!",
                "Не куплю — другого найду!"
            );

            case ARG_FAITH -> pick(
                "Не тебе говорить о богах!",
                "Ты в храме был? То-то же!",
                "Обряды — не твоё дело!",
                "Хула на веру — грех!",
                "Мы верим — и будем!",
                "Не лезь в душу!",
                "Молчи, если не понимаешь!",
                "Духи предков слышат!"
            );

            case ARG_PERSONAL -> pick(
                "Я тебя раскусил!",
                "Ты мне не друг!",
                "Слово твоё — не верю!",
                "Хватит мне врать!",
                "Я тебе ничего не должен!",
                "Ты меня предал!",
                "Уходи, пока я добрый!",
                "Больше ты мне не нужен!"
            );

            case ARG_TERRITORY -> pick(
                "Это наша земля!",
                "Ты не в своей деревне!",
                "Убирайся с нашей территории!",
                "Нарушил границу — отвечай!",
                "Мы это место отстояли!",
                "Тут не гуляй!",
                "Ходи по дороге — не по полям!",
                "Ещё шаг — и стрела в тебя!"
            );
        };
    }

    // =========================================================================
    // Специальные реплики
    // =========================================================================

    private static String childReplica(DialogType type) {
        return pick(
            "Привет, дяденька!",
            "Ты поиграешь со мной?",
            "Мамка не велит с чужими говорить...",
            "А ты из другой деревни?",
            "У меня есть камушек. Покажу?",
            "Дай конфетку, а?",
            "Я вчера гриб нашёл!",
            "Ты странник? Расскажи что-нибудь!"
        );
    }

    private static String elderReplica(DialogType type) {
        return pick(
            "Много я видел на своём веку, странник.",
            "Сядь, послушай старого.",
            "Раньше здесь было по-другому...",
            "Здрав будь, путник.",
            "Внуки мои где-то бегают...",
            "Устал я, но не сдаюсь.",
            "Спина болит, а сидеть дома — не могу.",
            "Совет дам, если спросишь."
        );
    }

    // =========================================================================
    // Модификаторы
    // =========================================================================

    private static String familiarityPrefix(NPCDialogMemory.Familiarity fam) {
        return switch (fam) {
            case STRANGER -> "";
            case ACQUAINTANCE -> pick(
                "Снова ты. ",
                "А, знакомое лицо. ",
                "Здорово. "
            );
            case FRIEND -> pick(
                "А, друг! ",
                "Рад видеть! ",
                "Сколько зим! "
            );
            case CLOSE_FRIEND -> pick(
                "Брат мой! ",
                "Душа моя! ",
                "Родной! "
            );
            case FAMILY -> pick(
                "Сынок, ",
                "Родной мой, ",
                "Своя кровь, "
            );
        };
    }

    private static String emotionSuffix(NPCEmotions em) {
        if (em == null) return "";

        // Ищем доминирующую эмоцию.
        int joy = em.get(NPCEmotions.Emotion.JOY);
        int grief = em.get(NPCEmotions.Emotion.GRIEF);
        int anger = em.get(NPCEmotions.Emotion.ANGER);
        int fear = em.get(NPCEmotions.Emotion.FEAR);
        int conf = em.get(NPCEmotions.Emotion.CONFIDENCE);
        int fatigue = em.get(NPCEmotions.Emotion.FATIGUE);
        int boredom = em.get(NPCEmotions.Emotion.BOREDOM);

        if (joy >= 40) return " (на лице улыбка)";
        if (grief >= 40) return " (печально вздыхает)";
        if (anger >= 40) return " (сжимает кулаки)";
        if (fear >= 40) return " (озирается по сторонам)";
        if (conf >= 40) return " (уверенно улыбается)";
        if (fatigue >= 40) return " (устало трёт глаза)";
        if (boredom >= 40) return " (зевает)";
        return "";
    }

    private static String traitSuffix(NPCTraits traits) {
        if (traits == null) return "";
        if (traits.contains(NPCTraits.Trait.BRAVE)) return " (готов к любому)";
        if (traits.contains(NPCTraits.Trait.CAUTIOUS)) return " (осторожно оглядывается)";
        if (traits.contains(NPCTraits.Trait.DILIGENT)) return " (продолжает работать)";
        if (traits.contains(NPCTraits.Trait.LAZY)) return " (потягивается)";
        if (traits.contains(NPCTraits.Trait.GENEROUS)) return " (протягивает что-то)";
        if (traits.contains(NPCTraits.Trait.WITHDRAWN)) return " (молча кивает)";
        return "";
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    private static DialogType pickAlternative(DialogType old) {
        return switch (old) {
            case GREETING -> DialogType.NEWS;
            case NEWS -> DialogType.BIOME;
            case BIOME -> DialogType.NEIGHBOR;
            case NEIGHBOR -> DialogType.SEASON;
            case SEASON -> DialogType.SUPPORT;
            case SUPPORT -> DialogType.RECIPE;
            case RECIPE -> DialogType.FINDING;
            case FINDING -> DialogType.TRADE;
            case TRADE -> DialogType.GREETING;
            case MINING -> DialogType.SMITHING;
            case SMITHING -> DialogType.COOKING;
            case COOKING -> DialogType.FARMING;
            case FARMING -> DialogType.MINING;
            case BUILDING -> DialogType.PATROLLING;
            case PATROLLING -> DialogType.BUILDING;
            default -> DialogType.GREETING;
        };
    }

    /**
     * Базовый вызов без контекста (совместимость).
     */
    public static String generate(DialogType type) {
        return baseRеplikа(type);
    }

    private static String pick(String... variants) {
        return variants[RANDOM.nextInt(variants.length)];
    }
}