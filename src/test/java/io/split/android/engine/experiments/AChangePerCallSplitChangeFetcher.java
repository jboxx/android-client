package io.split.android.engine.experiments;

import com.google.common.collect.Lists;
import io.split.android.client.dtos.Condition;
import io.split.android.client.dtos.ConditionType;
import io.split.android.client.dtos.Split;
import io.split.android.client.dtos.SplitChange;
import io.split.android.client.dtos.Status;
import io.split.android.engine.ConditionsTestUtil;
import io.split.android.grammar.Treatments;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Activates one new name, and archives one previous name per call.
 *
 */
public class AChangePerCallSplitChangeFetcher implements SplitChangeFetcher {

    private AtomicLong _lastAdded = new AtomicLong(-1);
    private String _segmentName;
    public Map<String, String> configurations = null;

    public AChangePerCallSplitChangeFetcher() {
        this(null);
    }

    public AChangePerCallSplitChangeFetcher(String segmentName) {
        _segmentName = segmentName;
    }


    @Override
    public SplitChange fetch(long since) {
        long latestChangeNumber = since + 1;
        Condition condition;

        if (_segmentName != null) {
            condition = ConditionsTestUtil.makeUserDefinedSegmentCondition(ConditionType.ROLLOUT, _segmentName, Lists.newArrayList(ConditionsTestUtil.partition("on", 10)));
        } else {
            condition = ConditionsTestUtil.makeAllKeysCondition(Lists.newArrayList(ConditionsTestUtil.partition("on", 10)));
        }


        Split add = new Split();
        add.status = Status.ACTIVE;
        add.trafficAllocation = 100;
        add.trafficAllocationSeed = (int) latestChangeNumber;
        add.seed = (int) latestChangeNumber;
        add.conditions = Lists.newArrayList(condition);
        add.name = "" + latestChangeNumber;
        add.defaultTreatment = Treatments.OFF;
        add.changeNumber = latestChangeNumber;

        if(this.configurations != null) {
            add.configurations = this.configurations;
        }

        Split remove = new Split();
        remove.status = Status.ACTIVE;
        remove.trafficAllocation = 100;
        remove.trafficAllocationSeed = (int) since;
        remove.seed = (int) since;
        remove.conditions = Lists.newArrayList(condition);
        remove.defaultTreatment = Treatments.OFF;
        remove.name = "" + since;
        remove.killed = true;
        remove.changeNumber = latestChangeNumber;

        if(this.configurations != null) {
            remove.configurations = this.configurations;
        }


        SplitChange splitChange = new SplitChange();
        splitChange.splits = Lists.newArrayList(add, remove);
        splitChange.since = since;
        splitChange.till = latestChangeNumber;

        _lastAdded.set(latestChangeNumber);

        return splitChange;
    }

    @Override
    public SplitChange fetch(long since, FetcherPolicy fetcherPolicy) {
        return fetch(since);
    }

    public long lastAdded() {
        return _lastAdded.get();
    }


    @Override
    public boolean isSourceReachable() {
        return true;
    }
}
