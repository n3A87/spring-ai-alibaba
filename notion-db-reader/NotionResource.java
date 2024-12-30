public class NotionResource implements Resource {

    private static final String BASE_URL = "https://api.notion.com/v1";
    private static final String DATABASE_URL = BASE_URL + "/databases/%s/query";
    private static final String PAGE_URL = BASE_URL + "/pages/%s";
    private static final String BLOCK_URL = BASE_URL + "/blocks/%s/children";
    
    private final HttpClient httpClient;
    private final InputStream inputStream;
    private final String integrationToken;
    private final String databaseId;
    private final Map<String, Object> filterObject;
    
    public NotionResource(String integrationToken, String databaseId, Map<String, Object> filterObject) {
        this.integrationToken = integrationToken;
        this.databaseId = databaseId;
        this.filterObject = filterObject;
        
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
            
        // 获取页面内容并转换为InputStream
        String content = loadContent();
        this.inputStream = new ByteArrayInputStream(content.getBytes());
    }

    private String loadContent() {
        // 1. 获取数据库中的所有页面
        List<JSONObject> pages = retrievePageSummaries();
        
        // 2. 读取每个页面的内容
        StringBuilder contentBuilder = new StringBuilder();
        for (JSONObject page : pages) {
            String pageContent = loadPage(page);
            contentBuilder.append(pageContent).append("\n");
        }
        
        return contentBuilder.toString();
    }

    private List<JSONObject> retrievePageSummaries() {
        List<JSONObject> pages = new ArrayList<>();
        String nextCursor = null;
        
        do {
            JSONObject queryBody = new JSONObject();
            queryBody.put("page_size", 100);
            if (nextCursor != null) {
                queryBody.put("start_cursor", nextCursor);
            }
            if (!filterObject.isEmpty()) {
                queryBody.put("filter", filterObject);
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(DATABASE_URL, databaseId)))
                .header("Authorization", "Bearer " + integrationToken)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(queryBody.toString()))
                .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject data = JSON.parseObject(response.body());
                
                pages.addAll(data.getJSONArray("results").toJavaList(JSONObject.class));
                
                nextCursor = data.getString("next_cursor");
                if (!data.getBooleanValue("has_more")) {
                    break;
                }
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to retrieve pages from Notion", e);
            }
        } while (true);

        return pages;
    }

    private String loadPage(JSONObject pageSummary) {
        String pageId = pageSummary.getString("id");
        return loadBlocks(pageId, 0);
    }

    private String loadBlocks(String blockId, int numTabs) {
        StringBuilder content = new StringBuilder();
        String nextCursor = null;

        do {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(BLOCK_URL, blockId)))
                .header("Authorization", "Bearer " + integrationToken)
                .header("Notion-Version", "2022-06-28")
                .GET()
                .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject data = JSON.parseObject(response.body());
                
                for (Object result : data.getJSONArray("results")) {
                    JSONObject block = (JSONObject) result;
                    String type = block.getString("type");
                    JSONObject blockContent = block.getJSONObject(type);
                    
                    if (blockContent.containsKey("rich_text")) {
                        String text = extractRichText(blockContent.getJSONArray("rich_text"));
                        content.append("\t".repeat(numTabs)).append(text).append("\n");
                        
                        if (block.getBooleanValue("has_children")) {
                            content.append(loadBlocks(block.getString("id"), numTabs + 1));
                        }
                    }
                }
                
                nextCursor = data.getString("next_cursor");
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to load blocks from Notion", e);
            }
        } while (nextCursor != null);

        return content.toString();
    }

    private String extractRichText(JSONArray richTextArray) {
        StringBuilder text = new StringBuilder();
        for (Object item : richTextArray) {
            JSONObject richText = (JSONObject) item;
            if (richText.containsKey("text")) {
                text.append(richText.getJSONObject("text").getString("content"));
            }
        }
        return text.toString();
    }

    // 实现Resource接口的必要方法
    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    // Builder模式实现
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String integrationToken;
        private String databaseId;
        private Map<String, Object> filterObject = new HashMap<>();

        public Builder integrationToken(String token) {
            this.integrationToken = token;
            return this;
        }

        public Builder databaseId(String id) {
            this.databaseId = id;
            return this;
        }

        public Builder filterObject(Map<String, Object> filter) {
            this.filterObject = filter;
            return this;
        }

        public NotionResource build() {
            Assert.notNull(integrationToken, "Integration token must not be null");
            Assert.notNull(databaseId, "Database ID must not be null");
            return new NotionResource(integrationToken, databaseId, filterObject);
        }
    }

    // ... 其他Resource接口方法的实现 ...
}