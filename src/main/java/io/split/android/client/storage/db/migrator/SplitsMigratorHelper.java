package io.split.android.client.storage.db.migrator;

import androidx.core.util.Pair;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.split.android.client.cache.SplitCacheMigrator;
import io.split.android.client.dtos.MySegment;
import io.split.android.client.dtos.Split;
import io.split.android.client.storage.db.MySegmentEntity;
import io.split.android.client.storage.db.SplitDao;
import io.split.android.client.storage.db.SplitEntity;
import io.split.android.client.utils.Json;
import io.split.android.client.utils.TimeUtils;

import static com.google.common.base.Preconditions.checkNotNull;

public class SplitsMigratorHelper {
    SplitCacheMigrator mSplitCacheMigrator;
    TimeUtils mTimeUtils;

    public SplitsMigratorHelper(@NotNull SplitCacheMigrator splitCacheMigrator) {
        mSplitCacheMigrator = checkNotNull(splitCacheMigrator);
        mTimeUtils = new TimeUtils();
    }

    public Pair<Long, List<SplitEntity>> loadLegacySplitsAsEntities() {
        List<Split> splits = mSplitCacheMigrator.getAll();
        List<SplitEntity> entities = new ArrayList<>();
        for(Split split : splits) {
            SplitEntity splitEntity = createSplitEntity(split);
            entities.add(splitEntity);
        }

        return new Pair<>(mSplitCacheMigrator.getChangeNumber(), entities);
    }

    private SplitEntity createSplitEntity(Split split) {
        SplitEntity entity = new SplitEntity();
        entity.setName(split.name);
        entity.setBody(Json.toJson(split));
        entity.setUpdatedAt(mTimeUtils.timeInSeconds());
        return entity;
    }
}