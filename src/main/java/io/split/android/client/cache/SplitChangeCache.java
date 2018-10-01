package io.split.android.client.cache;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;

import io.split.android.client.dtos.Split;
import io.split.android.client.dtos.SplitChange;
import io.split.android.client.storage.IStorage;
import io.split.android.client.utils.Json;
import io.split.android.client.utils.Logger;

/**
 * Created by guillermo on 11/23/17.
 */

public class SplitChangeCache implements ISplitChangeCache {

    private ISplitCache _splitCache;

    public SplitChangeCache(IStorage storage) {
        this._splitCache = new SplitCache(storage);
    }

    @Override
    public boolean addChange(SplitChange splitChange) {
        if (_splitCache == null || splitChange == null || splitChange.splits == null) return false;
        boolean result = true;
        _splitCache.setChangeNumber(splitChange.till);
        for (Split split : splitChange.splits) {
            if (split != null && split.name != null) {
                result = result && _splitCache.addSplit(split.name, Json.toJson(split));
            } else {
                result = false;
            }
        }
        return result;
    }

    @Override
    public SplitChange getChanges(long since) {

        long changeNumber = _splitCache.getChangeNumber();

        SplitChange splitChange = new SplitChange();

        splitChange.splits = new ArrayList<>();
        splitChange.since = changeNumber;
        splitChange.till = changeNumber;

        if (since == -1 || since < changeNumber) {
            addAllSplits(splitChange.splits);
        }

        return splitChange;
    }

    private void addAllSplits(List<Split> splits) {
        for (String splitName :
                _splitCache.getSplitNames()) {
            String cachedSplit = _splitCache.getSplit(splitName);
            if (cachedSplit != null && !cachedSplit.isEmpty()) {
                try {
                    Split split = Json.fromJson(cachedSplit, Split.class);
                    if (split != null) {
                        splits.add(split);
                    }
                } catch (JsonSyntaxException e) {
                    Logger.e(e, "Failed to parse split %s, cache: %s", splitName, cachedSplit);
                }
            } else {
                Logger.w("split %s was not cached", splitName);
            }
        }
    }
}
