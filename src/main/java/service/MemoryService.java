package service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryService {

    private final MemoryStorage storage;

    public MemoryService(MemoryStorage storage) {
        this.storage = storage;
    }

    public void remember(String information) {

        if (information == null || information.isBlank()) {
            return;
        }

        storage.save(information);
    }

    public List<String> getMemories() {
        return storage.load();
    }

    public void clear() {
        // Memory is stored permanently.
        // Clearing can be implemented later with explicit confirmation.
    }
}
