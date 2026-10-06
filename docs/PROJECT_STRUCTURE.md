LiveMine/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── generate_textures.py
├── generate_sounds.py
├── generate_logo.py
├── README.md
├── LICENSE
├── CHANGELOG.md (символическая ссылка на docs/)
├── .gitignore
│
├── docs/
│ ├── README.md
│ ├── INSTRUCTIONS.md
│ ├── ADMIN_COMMANDS.md
│ ├── CONFIG_GUIDE.md
│ ├── USAGE_GUIDE.md
│ ├── NPC_LOGIC.md
│ ├── SPECIFICATION.md
│ ├── CHANGELOG.md
│ ├── ACCEPTANCE.md
│ └── API.md
│
└── src/main/
├── java/com/livemine/
│ ├── LiveMineMod.java
│ ├── LiveMineConfig.java
│ ├── ConfigManager.java
│ ├── PersistenceManager.java
│ ├── LiveMineSavedData.java
│ ├── NPCSkills.java
│ ├── NamePool.java
│ ├── Chronicle.java
│ ├── CollectiveMemory.java
│ ├── CemeteryManager.java
│ ├── VillageManager.java
│ ├── VillageData.java
│ ├── ReputationManager.java
│ ├── HolidayManager.java
│ ├── MarriageManager.java
│ ├── InheritanceManager.java
│ ├── DiseaseManager.java
│ ├── EconomyManager.java
│ │
│ ├── entity/
│ │ ├── LiveNPCEntity.java
│ │ ├── GraveStoneEntity.java
│ │ └── GraveStoneDataAccessor.java
│ │
│ ├── registry/
│ │ ├── ModEntities.java
│ │ ├── ModBlocks.java
│ │ ├── ModBlockEntities.java
│ │ ├── ModItems.java
│ │ ├── ModSounds.java
│ │ └── ModCreativeTab.java
│ │
│ ├── ai/
│ │ ├── NPCBrain.java
│ │ ├── NPCRegistry.java
│ │ ├── NPCActivityManager.java
│ │ ├── AdaptiveTickRate.java
│ │ ├── BrainDebugger.java
│ │ ├── BrainScheduler.java
│ │ ├── AgentBrain.java
│ │ ├── AgentTask.java
│ │ ├── NPCSimulationTickHandler.java
│ │ ├── SimulationEngine.java
│ │ ├── TickDistributor.java
│ │ ├── ResourceCache.java
│ │ ├── PathCache.java
│ │ ├── NPCState.java
│ │ ├── NPCSpawner.java
│ │ ├── NPCRelationship.java
│ │ ├── NPCMemory.java
│ │ ├── WorkGoalManager.java
│ │ └── goals/
│ │ ├── WorkGoal.java
│ │ ├── RestGoal.java
│ │ ├── HealGoal.java
│ │ ├── EatGoal.java
│ │ ├── SocializeGoal.java
│ │ └── WanderGoal.java
│ │
│ ├── blocks/
│ │ ├── VillageCenterBlock.java + BE
│ │ ├── CommunalFirePitBlock.java + BE
│ │ ├── StorageBlock.java + BE
│ │ ├── StorageWarehouseBlock.java + BE
│ │ ├── WorkshopForgeBlock.java + BE
│ │ ├── FarmPlotBlock.java + BE
│ │ ├── InfirmaryBlock.java + BE
│ │ ├── MagicTowerCoreBlock.java + BE
│ │ ├── MarketStallBlock.java + BE
│ │ ├── CemeteryMarkerBlock.java + BE
│ │ └── TrophyDisplayBlock.java + BE
│ │
│ ├── network/
│ │ ├── NetworkHandler.java
│ │ ├── NPCStatePacket.java
│ │ ├── NPCInteractionPayload.java
│ │ └── VillageClientCache.java
│ │
│ ├── client/
│ │ ├── ClientSetup.java
│ │ ├── ClientEventHandler.java
│ │ ├── ClientTickHandler.java
│ │ ├── VillageClientCacheStorage.java
│ │ ├── render/
│ │ │ ├── NPCRenderer.java
│ │ │ └── GraveStoneRenderer.java
│ │ ├── gui/
│ │ │ ├── NPCStatusScreen.java
│ │ │ └── InGameGuideScreen.java
│ │ └── guide/
│ │ ├── GuideContent.java
│ │ └── GuideOpenHandler.java
│ │
│ ├── commands/
│ │ ├── LiveMineCommands.java
│ │ └── CommandRegistrationHandler.java
│ │
│ ├── domain/
│ │ ├── Goal.java
│ │ ├── Needs.java
│ │ ├── SimulationMode.java
│ │ ├── AgentRecord.java
│ │ ├── ModeController.java
│ │ ├── VillageRecord.java
│ │ ├── NavigationArbiter.java
│ │ └── NavigationLease.java
│ │
│ ├── navigation/
│ │ └── NavigationSystem.java
│ │
│ ├── task/
│ │ ├── TaskType.java
│ │ └── TaskStateMachine.java
│ │
│ ├── economy/
│ │ └── TransactionJournal.java
│ │
│ ├── weather/
│ │ └── WeatherHandler.java
│ │
│ ├── voting/
│ │ ├── VotingManager.java
│ │ └── ElectionManager.java
│ │
│ ├── wanderer/
│ │ └── WandererManager.java
│ │
│ ├── abandoned/
│ │ └── AbandonedVillageManager.java
│ │
│ ├── property/
│ │ └── PropertyManager.java
│ │
│ ├── pet/
│ │ └── PetManager.java
│ │
│ ├── building/
│ │ └── BuildingSystem.java
│ │
│ ├── recipe/
│ │ └── RecipeSafetyPolicy.java
│ │
│ ├── compat/
│ │ ├── CreateIntegration.java
│ │ └── MagicIntegration.java
│ │
│ ├── dimension/
│ │ └── DimensionExpedition.java
│ │
│ ├── world/
│ │ ├── VillageSpawner.java
│ │ ├── ScoutManager.java
│ │ ├── RoadPlanner.java
│ │ └── CaravanManager.java
│ │
│ ├── diplomacy/
│ │ ├── DiplomacyManager.java
│ │ ├── AllianceManager.java
│ │ ├── WarManager.java
│ │ └── MailManager.java
│ │
│ ├── family/
│ │ └── DynastyManager.java
│ │
│ ├── leadership/
│ │ └── LeadershipManager.java
│ │
│ ├── orphan/
│ │ └── OrphanManager.java
│ │
│ ├── education/
│ │ └── EducationManager.java
│ │
│ ├── quest/
│ │ └── QuestManager.java
│ │
│ ├── achievement/
│ │ └── AchievementManager.java
│ │
│ ├── diary/
│ │ └── DiaryManager.java
│ │
│ ├── debt/
│ │ └── DebtManager.java
│ │
│ ├── ranking/
│ │ └── RankingManager.java
│ │
│ ├── territory/
│ │ └── TerritoryManager.java
│ │
│ ├── temple/
│ │ └── TempleManager.java
│ │
│ ├── library/
│ │ └── LibraryManager.java
│ │
│ ├── crafting/
│ │ ├── CraftManager.java
│ │ └── CookingManager.java
│ │
│ ├── simulation/
│ │ └── SimulatedAgent.java
│ │
│ ├── localization/
│ │ └── LocalizationManager.java
│ │
│ ├── sound/
│ │ └── SoundManager.java
│ │
│ ├── ui/
│ │ └── UIRenderer.java
│ │
│ ├── api/
│ │ └── APIManager.java
│ │
│ ├── crash/
│ │ └── CrashHandler.java
│ │
│ ├── util/
│ │ ├── NotificationManager.java
│ │ ├── ProfilingManager.java
│ │ └── ServerLifecycleHandler.java
│ │
│ └── migration/
│ ├── MigrationManager.java
│ ├── MigrationStep.java
│ ├── MigrationException.java
│ └── SchemaVersion.java
│
└── resources/
├── META-INF/
│ └── neoforge.mods.toml
├── pack.mcmeta
├── livemine_logo.png
├── mod_compat.json
└── assets/livemine/
├── lang/
│ ├── ru_ru.json
│ ├── en_us.json
│ ├── fr_fr.json
│ ├── de_de.json
│ ├── ar_sa.json
│ ├── zh_cn.json
│ ├── hi_in.json
│ ├── es_es.json
│ ├── bn_in.json
│ ├── pt_br.json
│ ├── fa_ir.json
│ ├── el_gr.json
│ └── grc_gr.json
├── blockstates/ (15 файлов)
├── models/
│ ├── block/ (15 файлов)
│ └── item/ (27 файлов)
├── textures/
│ ├── block/ (15 PNG + README)
│ ├── item/ (11 PNG + README)
│ ├── entity/
│ │ ├── npc/ (7 PNG + README)
│ │ └── grave_stone.png
│ └── README.md
├── sounds.json
└── sounds/ (15 OGG + README)