pip install pydub numpy
python generate_sounds.py
Замена на финальные звуки
Откройте .ogg в Audacity / Adobe Audition / любой DAW.

Запишите/вставьте финальный звук.

Экспортируйте в .ogg (Vorbis).

Сохраните с тем же именем.

Где используются
Звук	Класс-вызов
npc_work, npc_eat, npc_social, npc_hurt, npc_death, npc_greeting	SoundManager, WorkGoal, EatGoal, SocializeGoal
block_place, block_break	SoundManager
village_bell	SoundManager.alert() (тревога в деревне)
building_hammer	WorkGoal.doWork()
magic_cast	MagicIntegration.castSpell()
wedding	MarriageManager.marry()
festival, holiday_cheer	HolidayManager.startHoliday()
Требования
Формат: OGG Vorbis

Каналы: mono (для звуков с позицией)

Sample rate: 44100 Hz или 22050 Hz

Битрейт: 128 kbps (рекомендуется)

Длительность: 0.2–2 сек (как ванильные звуки)