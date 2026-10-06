# LiveMine — текстуры блоков

Все текстуры — **16×16 PNG** (RGBA), стиль совпадает с ванильным Minecraft.

## Список (15 файлов)

| Файл | Описание | Цвет-доминанта |
|---|---|---|
| village_center.png | Полированный камень с золотой гравировкой | #B4AA8C |
| communal_fire_pit.png | Камень с огнём (lit=true) | #503C28 |
| communal_fire_pit_off.png | Камень без огня (lit=false) | #323232 |
| storage.png | Деревянный ящик с железом | #8C643C |
| storage_warehouse.png | Крупные деревянные ворота | #6E5032 |
| workshop_forge.png | Тёмный камень с горном | #464646 |
| farm_plot.png | Вспаханная земля | #5A3C28 |
| infirmary.png | Кварц с красным крестом | #DCDCDC |
| magic_tower_core.png | Обсидиан с фиолетовым свечением | #321E50 |
| market_stall.png | Прилавок с навесом | #B48C5A |
| cemetery_marker.png | Тёмный обелиск | #3C3C46 |
| trophy_display.png | Стеклянная витрина | #F0DC64 |
| village_road.png | Утрамбованная земля | #826E50 |
| village_bridge.png | Деревянный настил | #785A3C |
| village_fence.png | Деревянный столб | #966E46 |

## Замена заглушек

1. Запустите `generate_textures.py` (создаст заглушки).
2. Откройте нужный PNG в любом редакторе (Aseprite, Paint.NET, Photoshop).
3. Нарисуйте финальную текстуру.
4. Сохраните с тем же именем.

## Требования

- Формат: **PNG** (RGBA)
- Размер: **16×16**
- Стиль: **пиксель-арт**, как ванильный Minecraft
- Прозрачность: только если необходимо (для большинства — opaque)