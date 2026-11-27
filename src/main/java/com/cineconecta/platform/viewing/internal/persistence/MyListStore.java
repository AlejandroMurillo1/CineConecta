package com.cineconecta.platform.viewing.internal.persistence;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.addToSet;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.pull;
import static com.mongodb.client.model.Updates.set;

@Repository
public class MyListStore {
    private final MongoTemplate mongo;
    private final Clock clock;

    public MyListStore(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    public List<String> findIds(int profileId) {
        Document list = collection().find(eq("_id", profileId)).first();
        if (list == null) {
            return List.of();
        }
        return list.getList("contentIds", ObjectId.class).stream().map(ObjectId::toHexString).toList();
    }

    public void add(int profileId, String contentId) {
        collection().updateOne(eq("_id", profileId),
                combine(addToSet("contentIds", new ObjectId(contentId)), set("updatedAt", Date.from(clock.instant()))),
                new UpdateOptions().upsert(true));
    }

    public void remove(int profileId, String contentId) {
        collection().updateOne(eq("_id", profileId),
                combine(pull("contentIds", new ObjectId(contentId)), set("updatedAt", Date.from(clock.instant()))));
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection("my_lists");
    }
}
