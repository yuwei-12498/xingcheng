package com.citytrip.service.ai.model;

public class AiPlatformProperties {
    private final Toggle orchestrator = new Toggle(true);
    private final Toggle rag = new Toggle(true);
    private final Toggle mcp = new Toggle(true);
    private final Toggle tools = new Toggle(true);
    private final CityGuide cityGuide = new CityGuide();
    private final Retrieval retrieval = new Retrieval();
    private final Augmentation augmentation = new Augmentation();

    public Toggle getOrchestrator() {
        return orchestrator;
    }

    public Toggle getRag() {
        return rag;
    }

    public Toggle getMcp() {
        return mcp;
    }

    public Toggle getTools() {
        return tools;
    }

    public CityGuide getCityGuide() {
        return cityGuide;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Augmentation getAugmentation() {
        return augmentation;
    }

    public static class Toggle {
        private boolean enabled;

        public Toggle(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class CityGuide {
        private String defaultCityKey = "chengdu";

        public String getDefaultCityKey() {
            return defaultCityKey;
        }

        public void setDefaultCityKey(String defaultCityKey) {
            this.defaultCityKey = defaultCityKey;
        }
    }

    public static class Retrieval {
        private int maxDocuments = 6;
        private int maxPerSource = 2;

        public int getMaxDocuments() {
            return maxDocuments;
        }

        public void setMaxDocuments(int maxDocuments) {
            this.maxDocuments = maxDocuments;
        }

        public int getMaxPerSource() {
            return maxPerSource;
        }

        public void setMaxPerSource(int maxPerSource) {
            this.maxPerSource = maxPerSource;
        }
    }

    public static class Augmentation {
        private int maxRagDocuments = 6;
        private int maxToolPayloads = 3;
        private int maxMcpEvidence = 4;
        private int maxContextChars = 520;

        public int getMaxRagDocuments() {
            return maxRagDocuments;
        }

        public void setMaxRagDocuments(int maxRagDocuments) {
            this.maxRagDocuments = maxRagDocuments;
        }

        public int getMaxToolPayloads() {
            return maxToolPayloads;
        }

        public void setMaxToolPayloads(int maxToolPayloads) {
            this.maxToolPayloads = maxToolPayloads;
        }

        public int getMaxMcpEvidence() {
            return maxMcpEvidence;
        }

        public void setMaxMcpEvidence(int maxMcpEvidence) {
            this.maxMcpEvidence = maxMcpEvidence;
        }

        public int getMaxContextChars() {
            return maxContextChars;
        }

        public void setMaxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
        }
    }
}
