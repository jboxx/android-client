package io.split.android.client.service.mysegments;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.split.android.client.dtos.MySegment;
import io.split.android.client.network.SplitHttpHeadersBuilder;
import io.split.android.client.service.ServiceConstants;
import io.split.android.client.events.SplitEvent;
import io.split.android.client.events.SplitEventsManager;
import io.split.android.client.events.SplitInternalEvent;
import io.split.android.client.service.executor.SplitTask;
import io.split.android.client.service.executor.SplitTaskExecutionInfo;
import io.split.android.client.service.executor.SplitTaskType;
import io.split.android.client.service.http.HttpFetcher;
import io.split.android.client.service.http.HttpFetcherException;
import io.split.android.client.service.synchronizer.MySegmentsChangeChecker;
import io.split.android.client.storage.mysegments.MySegmentsStorage;
import io.split.android.client.utils.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class MySegmentsSyncTask implements SplitTask {

    private final HttpFetcher<List<MySegment>> mMySegmentsFetcher;
    private final MySegmentsStorage mMySegmentsStorage;
    private final boolean mAvoidCache;
    private final SplitEventsManager mEventsManager;
    private MySegmentsChangeChecker mMySegmentsChangeChecker;

    public MySegmentsSyncTask(@NonNull HttpFetcher<List<MySegment>> mySegmentsFetcher,
                              @NonNull MySegmentsStorage mySegmentsStorage,
                              boolean avoidCache,
                              SplitEventsManager eventsManager) {
        mMySegmentsFetcher = checkNotNull(mySegmentsFetcher);
        mMySegmentsStorage = checkNotNull(mySegmentsStorage);
        mAvoidCache = avoidCache;
        mEventsManager = eventsManager;
        mMySegmentsChangeChecker = new MySegmentsChangeChecker();
    }

    @Override
    @NonNull
    public SplitTaskExecutionInfo execute() {
        try {
            List<String> oldSegments = new ArrayList(mMySegmentsStorage.getAll());
            List<String> mySegments = getNameList(mMySegmentsFetcher.execute(new HashMap<>(), getHeaders()));
            mMySegmentsStorage.set(mySegments);
            fireMySegmentsUpdatedIfNeeded(oldSegments, mySegments);
        } catch (HttpFetcherException e) {
            logError("Network error while retrieving my segments: " + e.getLocalizedMessage());
            return SplitTaskExecutionInfo.error(SplitTaskType.MY_SEGMENTS_SYNC);
        } catch (Exception e) {
            logError("Unknown error while retrieving my segments: " + e.getLocalizedMessage());
            return SplitTaskExecutionInfo.error(SplitTaskType.MY_SEGMENTS_SYNC);
        }
        Logger.d("My Segments have been updated");
        return SplitTaskExecutionInfo.success(SplitTaskType.MY_SEGMENTS_SYNC);
    }

    private void logError(String message) {
        Logger.e("Error while executing my segments sync task: " + message);
    }

    private List<String> getNameList(List<MySegment> mySegments) {
        List<String> nameList = new ArrayList<String>();
        for (MySegment segment : mySegments) {
            nameList.add(segment.name);
        }
        return nameList;
    }

    private @Nullable Map<String, String> getHeaders() {
        if (mAvoidCache) {
            return SplitHttpHeadersBuilder.noCacheHeaders();
        }
        return null;
    }

    private void fireMySegmentsUpdatedIfNeeded(List<String> oldSegments, List<String> newSegments) {
        if(mEventsManager == null) {
            return;
        }
        mEventsManager.notifyInternalEvent(getInternalEvent(oldSegments, newSegments));
    }

    private SplitInternalEvent getInternalEvent(List<String> oldSegments, List<String> newSegments) {
        boolean haveChanged = mMySegmentsChangeChecker.mySegmentsHaveChanged(oldSegments, newSegments);
        if(haveChanged) {
            return SplitInternalEvent.MY_SEGMENTS_UPDATED;
        }
        return SplitInternalEvent.MY_SEGMENTS_FETCHED;
    }
}
