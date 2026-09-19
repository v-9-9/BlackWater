package service;

import java.util.List;

public interface AIProvider {

    String generate(String message);

    default String generate(
            String message,
            List<ImageInput> images
    ) {
        return generate(message);
    }

    record ImageInput(
            String fileName,
            String mediaType,
            String base64Data
    ) {
    }
}
