package com.cineconecta.platform.catalog.repositories;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.repositories.ContentDetailsAliasMigration;
import com.cineconecta.platform.catalog.models.Content;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContentDetailsAliasMigrationTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void backfillsTheConcreteAliasForEveryLegacyContentType() throws Exception {
        ApplicationRunner migration = new ContentDetailsAliasMigration()
                .contentDetailsAliasMigrationRunner(mongoTemplate);

        migration.run(null);

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(3)).updateMulti(queries.capture(), updates.capture(), eq(Content.class));

        List<ContentType> types = List.of(ContentType.values());
        for (int index = 0; index < types.size(); index++) {
            Document criteria = queries.getAllValues().get(index).getQueryObject();
            Document update = updates.getAllValues().get(index).getUpdateObject();

            assertEquals(types.get(index), criteria.get("type"));
            assertEquals(false, ((Document) criteria.get("contentDetails._class")).get("$exists"));
            assertEquals(types.get(index).name(),
                    ((Document) update.get("$set")).get("contentDetails._class"));
        }
    }
}
