package database;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import helper.FileHelper;
import helper.IntegrationHelper;
import io.split.android.client.dtos.KeyImpression;
import io.split.android.client.storage.db.ImpressionEntity;
import io.split.android.client.storage.db.ImpressionEntity;
import io.split.android.client.utils.Json;
import io.split.android.client.storage.db.ImpressionEntity;
import io.split.android.client.storage.db.SplitRoomDatabase;

public class ImpressionDaoTest {

    SplitRoomDatabase mRoomDb;
    Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mRoomDb = SplitRoomDatabase.getDatabase(mContext, "encripted_api_key");
        mRoomDb.clearAllTables();
    }

    @Test
    public void insertRetrieve() throws InterruptedException {
        long timestamp = System.currentTimeMillis();
        List<ImpressionEntity> impressions = generateData(1, 10, timestamp, false);
        impressions.addAll(generateData(11, 15, timestamp, true));
        for(ImpressionEntity impression : impressions) {
            mRoomDb.impressionDao().insert(impression);
        }

        List<ImpressionEntity> activeImpressions = mRoomDb.impressionDao().getBy(timestamp, ImpressionEntity.STATUS_ACTIVE);
        List<ImpressionEntity> deletedImpressions = mRoomDb.impressionDao().getBy(timestamp, ImpressionEntity.STATUS_DELETED);

        Assert.assertEquals(10, activeImpressions.size());
        Assert.assertEquals(5, deletedImpressions.size());
    }

    @Test
    public void insertUpdateRetrieve() throws InterruptedException {
        long timestamp = System.currentTimeMillis();
        List<ImpressionEntity> impressions = generateData(1, 20, timestamp, false);
        for(ImpressionEntity impression : impressions) {
            mRoomDb.impressionDao().insert(impression);
        }

        List<ImpressionEntity> activeImpressions = mRoomDb.impressionDao().getBy(timestamp, ImpressionEntity.STATUS_ACTIVE);
        List<Long> ids = activeImpressions.stream().map(ImpressionEntity::getId).collect(Collectors.toList());
        List<Long> idsToSoftDelete = ids.subList(15, 20);
        List<Long> idsToDelete = ids.subList(10, 15);

        mRoomDb.impressionDao().markAsDeleted(idsToSoftDelete);
        List<ImpressionEntity> afterSoftDelete = mRoomDb.impressionDao().getBy(0, ImpressionEntity.STATUS_ACTIVE);

        mRoomDb.impressionDao().delete(idsToDelete);
        List<ImpressionEntity> afterDelete = mRoomDb.impressionDao().getBy(0, ImpressionEntity.STATUS_ACTIVE);
        List<ImpressionEntity> softDeletedAfterDelete = mRoomDb.impressionDao().getBy(0, ImpressionEntity.STATUS_DELETED);

        mRoomDb.impressionDao().deleteOutdated(timestamp + 6);
        List<ImpressionEntity> afterAll = mRoomDb.impressionDao().getBy(timestamp, ImpressionEntity.STATUS_ACTIVE);
        List<ImpressionEntity> deletedAfterAll = mRoomDb.impressionDao().getBy(timestamp, ImpressionEntity.STATUS_DELETED);

        Assert.assertEquals(20, activeImpressions.size());
        Assert.assertEquals(15, afterSoftDelete.size());
        Assert.assertEquals(10, afterDelete.size());
        Assert.assertEquals(5, softDeletedAfterDelete.size());
        Assert.assertEquals(5, afterAll.size());
        Assert.assertEquals(5, deletedAfterAll.size());
    }

    @Test
    public void dataIntegrity() {
        long timestamp = System.currentTimeMillis();
        mRoomDb.impressionDao().insert(generateData(1, 1, timestamp, false).get(0));
        mRoomDb.impressionDao().insert(generateData(2, 2, timestamp, true).get(0));

        ImpressionEntity activeImpressionEntity = mRoomDb.impressionDao().getBy(timestamp, ImpressionEntity.STATUS_ACTIVE).get(0);
        ImpressionEntity deletedImpressionEntity = mRoomDb.impressionDao().getBy(timestamp, ImpressionEntity.STATUS_DELETED).get(0);

        KeyImpression activeImpression = Json.fromJson(activeImpressionEntity.getBody(), KeyImpression.class);
        KeyImpression deletedImpression = Json.fromJson(deletedImpressionEntity.getBody(), KeyImpression.class);
        
        Assert.assertEquals("t_1", activeImpression.treatment);
        Assert.assertEquals("default rule", activeImpression.label);
        Assert.assertEquals(timestamp + 1, activeImpression.time);
        Assert.assertEquals(timestamp + 1 * 10, activeImpression.changeNumber.longValue());
        Assert.assertEquals("key", activeImpression.keyName);
        Assert.assertNull(activeImpression.bucketingKey);
        Assert.assertEquals("test_1", activeImpressionEntity.getTestName());
        Assert.assertEquals(ImpressionEntity.STATUS_ACTIVE, activeImpressionEntity.getStatus());
        Assert.assertEquals(timestamp + 1, activeImpressionEntity.getTimestamp());

        Assert.assertEquals("t_2", deletedImpression.treatment);
        Assert.assertEquals("default rule", deletedImpression.label);
        Assert.assertEquals(timestamp + 2, deletedImpression.time);
        Assert.assertEquals(timestamp + 2 * 10, deletedImpression.changeNumber.longValue());
        Assert.assertEquals("key", deletedImpression.keyName);
        Assert.assertNull(deletedImpression.bucketingKey);
        Assert.assertEquals("test_2", deletedImpressionEntity.getTestName());
        Assert.assertEquals(ImpressionEntity.STATUS_DELETED, deletedImpressionEntity.getStatus());
        Assert.assertEquals(timestamp + 2, deletedImpressionEntity.getTimestamp());
    }

    @Test
    public void performance10() {
        performance(10);
    }

    @Test
    public void performance100() {
        performance(100);
    }

    @Test
    public void performance1000() {
        performance(1000);
    }

    @Test
    public void performance10000() {
        performance(10000);
    }

    private void performance(int count) {

        final String TAG = "ImpressionDaoTest_performance";

        List<ImpressionEntity> impressionEntities = generateData(1, count, 100000, false);
        long start = System.currentTimeMillis();
        for(ImpressionEntity impressionEntity : impressionEntities) {
            mRoomDb.impressionDao().insert(impressionEntity);
        }
        long writeTime = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        impressionEntities = mRoomDb.impressionDao().getBy(0, ImpressionEntity.STATUS_ACTIVE);
        long readTime = System.currentTimeMillis() - start;

        IntegrationHelper.logSeparator(TAG);
        Log.i(TAG, "-> " +count  + " impressions");
        Log.i(TAG, String.format("Write time: %d segs, (%d millis) ", readTime / 100, readTime));
        Log.i(TAG, String.format("Read time: %d segs, (%d millis) ", writeTime / 100, writeTime));
        IntegrationHelper.logSeparator(TAG);

        Assert.assertEquals(count, impressionEntities.size());
    }

    private List<ImpressionEntity> generateData(int from, int to, long timestamp, boolean markAsDeleted) {
        List<ImpressionEntity> impressionList = new ArrayList<>();
        for(int i = from; i<=to; i++) {
            KeyImpression impression = new KeyImpression();
            impression.treatment = "t_" + i;
            impression.keyName = "key";
            impression.time = timestamp + i;
            impression.changeNumber = timestamp + i * 10;
            impression.label = "default rule";
            impression.bucketingKey = null;

            ImpressionEntity impressionEntity = new ImpressionEntity();
            impressionEntity.setTestName("test_" + i);
            impressionEntity.setBody(Json.toJson(impression));
            impressionEntity.setTimestamp(timestamp + i);
            impressionEntity.setStatus(!markAsDeleted ? ImpressionEntity.STATUS_ACTIVE : ImpressionEntity.STATUS_DELETED);
            impressionList.add(impressionEntity);
        }
        return impressionList;
    }
}
