package io.split.android.client.service.mysegments;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

import io.split.android.client.events.SplitEventsManager;
import io.split.android.client.events.SplitInternalEvent;
import io.split.android.client.service.executor.SplitTask;
import io.split.android.client.service.executor.SplitTaskExecutionInfo;
import io.split.android.client.service.executor.SplitTaskType;
import io.split.android.client.service.synchronizer.MySegmentsChangeChecker;
import io.split.android.client.storage.mysegments.MySegmentsStorage;
import io.split.android.client.utils.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class MySegmentsUpdateTask implements SplitTask {

    private final List<String> mMySegments;
    private final MySegmentsStorage mMySegmentsStorage;
    private final SplitEventsManager mEventsManager;
    private MySegmentsChangeChecker mMySegmentsChangeChecker;

    public MySegmentsUpdateTask(@NonNull MySegmentsStorage mySegmentsStorage,
                                List<String> mySegments,
                                SplitEventsManager eventsManager) {
        mMySegmentsStorage = checkNotNull(mySegmentsStorage);
        mMySegments = mySegments;
        mEventsManager = eventsManager;
        mMySegmentsChangeChecker = new MySegmentsChangeChecker();
    }

    @Override
    @NonNull
    public SplitTaskExecutionInfo execute() {
        try {
            if (mMySegments == null) {
                logError("My segment list could not be null.");
                return SplitTaskExecutionInfo.error(SplitTaskType.MY_SEGMENTS_UPDATE);
            }
            List<String> oldSegments = new ArrayList(mMySegmentsStorage.getAll());
            mMySegmentsStorage.set(mMySegments);
            if(mMySegmentsChangeChecker.mySegmentsHaveChanged(oldSegments, mMySegments)) {
                mEventsManager.notifyInternalEvent(SplitInternalEvent.MY_SEGMENTS_UPDATED);
            }
        } catch (Exception e) {
            logError("Unknown error while updating my segments: " + e.getLocalizedMessage());
            return SplitTaskExecutionInfo.error(SplitTaskType.MY_SEGMENTS_UPDATE);
        }
        Logger.d("My Segments have been updated");
        return SplitTaskExecutionInfo.success(SplitTaskType.MY_SEGMENTS_UPDATE);
    }

    private void logError(String message) {
        Logger.e("Error while executing my segments update task: " + message);
    }

    @VisibleForTesting
    public void setChangesChecker(MySegmentsChangeChecker changesChecker) {
        mMySegmentsChangeChecker = changesChecker;
    }
}
