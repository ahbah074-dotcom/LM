import com.livemine.api.APIManager;
import com.livemine.api.APIManager.VillageEvent;

// Подписка
APIManager.getInstance().subscribe(VillageEvent.NPC_BORN, event -> {
    String name = (String) event.data.get("name");
    long day = event.day;
    System.out.println("NPC " + name + " born on day " + day);
});

// Отписка
APIManager.getInstance().unsubscribe(VillageEvent.NPC_BORN, handler);