package io.split.android.client.cache;



import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.arch.lifecycle.ProcessLifecycleOwner;

import io.split.android.client.dtos.MySegment;
import io.split.android.client.storage.IStorage;
import io.split.android.client.utils.Json;
import io.split.android.client.utils.Logger;
import io.split.android.client.utils.Utils;

/**
 * Created by guillermo on 12/28/17.
 */

public class MySegmentsCache implements IMySegmentsCache, LifecycleObserver {

    private static final String MY_SEGMENTS_FILE_NAME = "SPLITIO.mysegments";

    private final IStorage mFileStorage;
    private Map<String, List<MySegment>> mSegments = null;

    public MySegmentsCache(IStorage storage) {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        mFileStorage = storage;
        mSegments = Collections.synchronizedMap(new HashMap<String, List<MySegment>>());
        loadSegmentsFromDisk();
    }

    private String getMySegmentsFileName() {
        return MY_SEGMENTS_FILE_NAME;
    }

    @Override
    public void setMySegments(String key, List<MySegment> mySegments) {
        mSegments.put(key, mySegments);
    }

    @Override
    public List<MySegment> getMySegments(String key) {
        return mSegments.get(key);
    }

    @Override
    public void deleteMySegments(String key) {
        mSegments.remove(key);
    }

    private void loadSegmentsFromDisk(){

        try {
            String storedMySegments = mFileStorage.read(getMySegmentsFileName());
            if(storedMySegments == null || storedMySegments.trim().equals("")) return;
            Type listType = new TypeToken<Map<String, List<MySegment>>>() {
            }.getType();

            Map<String, List<MySegment>> segments = Json.fromJson(storedMySegments, listType);
            Set<String> keys = segments.keySet();
            for (String key : keys) {
                List<MySegment> keySegments = segments.get(key);
                if(keySegments != null) {
                    mSegments.put(key, Collections.synchronizedList(keySegments));
                }
            }

        } catch (IOException e) {
            Logger.e(e, "Unable to get my segments");
        } catch (JsonSyntaxException syntaxException) {
            Logger.e(syntaxException, "Unable to parse saved segments");
        }
    }

    // Lifecyle observer
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private void saveToDisk() {
        try {
            String json = Json.toJson(mSegments);
            mFileStorage.write(getMySegmentsFileName(), json);
        } catch (IOException e) {
            Logger.e(e, "Could not save my segments");
        } catch (JsonSyntaxException syntaxException) {
            Logger.e(syntaxException, "Unable to parse segments to save");
        }
    }

}
