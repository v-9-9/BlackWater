package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryService {

    private final List<String> memories = new ArrayList<>();

    public void remember(String information) {
        if (information == null || information.isBlank()) {
            return;
        }

        memories.add(information.trim());
    }

    public List<String> getMemories() {
        return List.copyOf(memories);
    }

    public void clear() {
        memories.clear();
    }
}
