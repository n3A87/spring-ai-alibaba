class NotionDBLoaderIT {
    private static final String NOTION_TOKEN = "${token}";

	private static final String DATABASE_ID = "${db_id}";

    NotionDBReader reader;

    @BeforeEach
	public void beforeEach() {
		reader = new NotionDBReader(source, new TikaDocumentParser());
	}
    
}
