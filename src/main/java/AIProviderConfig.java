public class AIProviderConfig {

    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;

    public AIProviderConfig(
            String provider,
            String apiKey,
            String baseUrl,
            String model
    ) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public String getProvider() {
        return provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }
}
