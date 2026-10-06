# LiveMine

**Живые деревни с автономными NPC для Minecraft NeoForge 1.21.11**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.11.42-orange)](https://neoforged.net)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

---

## Что это

LiveMine — мод, который превращает Minecraft в **живой мир**:

- **29 навыков** — от рудного дела до дипломатии
- **14 целей** — NPC сами решают, что делать
- **5 уровней жилья** + 1 (поместье)
- **11 состояний репутации** — от Изгнанного до Легенды
- **16 культур** — Русы, Арабы, Китайцы, Греки, Майя и др.
- **13 языков** — включая RTL (арабский, фарси)
- **Династии**, **сироты**, **школы**, **альянсы**, **войны**, **караваны**
- **Торговая площадь** с динамическими ценами
- **Полноправное членство** — игрок может стать жителем деревни

---

## Установка

### Требования
- Minecraft **1.21.11**
- NeoForge **21.11.42+**
- Java **21**

### Шаги
1. Установите NeoForge 1.21.11
2. Скачайте `livemine-1.0.0.jar`
3. Поместите в папку `mods/`
4. Запустите игру

---

## Быстрый старт

1. Создайте новый мир
2. Деревни появятся автоматически (3–5 на карту)
3. Нажмите **G** — откроется справочник
4. Кликните по NPC — увидите его состояние
5. Начните помогать деревне — репутация откроет новые возможности

---

## Документация

- [Руководство игрока](docs/USAGE_GUIDE.md)
- [Руководство администратора](docs/ADMIN_COMMANDS.md)
- [Инструкция по сборке](docs/INSTRUCTIONS.md)
- [Описание конфигурации](docs/CONFIG_GUIDE.md)
- [Логика NPC](docs/NPC_LOGIC.md)
- [Спецификация](docs/SPECIFICATION.md)

---

## Команды

Основные команды: `/lm` или `/livemine`
/livemine guide # открыть справочник
/livemine stats # статистика
/livemine npc list # список NPC
/livemine npc info <uuid> # информация о NPC
/livemine village list # список деревень
/livemine reload # перезагрузить конфиг
/livemine save # сохранить данные