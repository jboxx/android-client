package io.split.android.client;

import com.google.common.base.Strings;

import io.split.android.client.api.Key;
import io.split.android.client.dtos.ConditionType;
import io.split.android.client.dtos.Event;
import io.split.android.client.events.SplitEvent;
import io.split.android.client.events.SplitEventTask;
import io.split.android.client.events.SplitEventsManager;
import io.split.android.client.exceptions.ChangeNumberExceptionWrapper;
import io.split.android.client.impressions.Impression;
import io.split.android.client.impressions.ImpressionListener;
import io.split.android.client.utils.Logger;
import io.split.android.engine.experiments.ParsedCondition;
import io.split.android.engine.experiments.ParsedSplit;
import io.split.android.engine.experiments.SplitFetcher;
import io.split.android.engine.metrics.Metrics;
import io.split.android.engine.splitter.Splitter;
import io.split.android.grammar.Treatments;


import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A basic implementation of SplitClient.
 *
 */
public final class SplitClientImpl implements SplitClient {

    private static final String NOT_IN_SPLIT = "not in split";
    private static final String DEFAULT_RULE = "default rule";
    private static final String DEFINITION_NOT_FOUND = "definition not found";
    private static final String EXCEPTION = "exception";
    private static final String KILLED = "killed";

    private final SplitFactory _container;
    private final SplitFetcher _splitFetcher;
    private final ImpressionListener _impressionListener;
    private final Metrics _metrics;
    private final SplitClientConfig _config;
    private final String _matchingKey;
    private final String _bucketingKey;

    private final SplitEventsManager _eventsManager;

    private final TrackClient _trackClient;

    public SplitClientImpl(SplitFactory container, Key key, SplitFetcher splitFetcher, ImpressionListener impressionListener, Metrics metrics, SplitClientConfig config, SplitEventsManager eventsManager, TrackClient trackClient) {
        _container = container;
        _splitFetcher = splitFetcher;
        _impressionListener = impressionListener;
        _metrics = metrics;
        _config = config;
        _matchingKey = key.matchingKey();
        _bucketingKey = key.bucketingKey();
        _eventsManager = eventsManager;
        _trackClient = trackClient;

        checkNotNull(_splitFetcher);
        checkNotNull(_impressionListener);
        checkNotNull(_matchingKey);
        checkNotNull(_eventsManager);
        checkNotNull(_trackClient);

    }

    @Override
    public void destroy() {
        _container.destroy();
    }

    @Override
    public void flush() {
        _container.flush();
    }

    @Override
    public boolean isReady() {
        return _container.isReady();
    }

    @Override
    public String getTreatment(String split) {
        return getTreatment(split, Collections.<String, Object>emptyMap());
    }

    @Override
    public String getTreatment(String split, Map<String, Object> attributes) {
        return getTreatment(_matchingKey, _bucketingKey, split, attributes);
    }

    private String getTreatment(String matchingKey, String bucketingKey, String split, Map<String, Object> attributes) {
        try {
            if (matchingKey == null) {
                Logger.w("matchingKey was null for split: %s", split);
                return Treatments.CONTROL;
            }

            if (split == null) {
                Logger.w("split was null for key: %s", matchingKey);
                return Treatments.CONTROL;
            }

            long start = System.currentTimeMillis();

            TreatmentLabelAndChangeNumber result = getTreatmentResultWithoutImpressions(matchingKey, bucketingKey, split, attributes);

            recordStats(
                    matchingKey,
                    bucketingKey,
                    split,
                    start,
                    result._treatment,
                    "sdk.getTreatment",
                    _config.labelsEnabled() ? result._label : null,
                    result._changeNumber,
                    attributes
            );

            return result._treatment;
        } catch (Exception e) {
            try {
                Logger.e(e, "CatchAll Exception");
            } catch (Exception e1) {
                // ignore
            }
            return Treatments.CONTROL;
        }
    }

    private void recordStats(String matchingKey, String bucketingKey, String split, long start, String result,
                             String operation, String label, Long changeNumber, Map<String, Object> attributes) {
        try {
            _impressionListener.log(new Impression(matchingKey, bucketingKey, split, result, System.currentTimeMillis(), label, changeNumber, attributes));
            _metrics.time(operation, System.currentTimeMillis() - start);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    public String getTreatmentWithoutImpressions(String matchingKey, String bucketingKey, String split, Map<String, Object> attributes) {
        return getTreatmentResultWithoutImpressions(matchingKey, bucketingKey, split, attributes)._treatment;
    }

    private TreatmentLabelAndChangeNumber getTreatmentResultWithoutImpressions(String matchingKey, String bucketingKey, String split, Map<String, Object> attributes) {
        TreatmentLabelAndChangeNumber result;
        try {
            result = getTreatmentWithoutExceptionHandling(matchingKey, bucketingKey, split, attributes);
        } catch (ChangeNumberExceptionWrapper e) {
            result = new TreatmentLabelAndChangeNumber(Treatments.CONTROL, EXCEPTION, e.changeNumber());
            Logger.e(e.wrappedException());
        } catch (Exception e) {
            result = new TreatmentLabelAndChangeNumber(Treatments.CONTROL, EXCEPTION);
            Logger.e(e);
        }

        return result;
    }

    private TreatmentLabelAndChangeNumber getTreatmentWithoutExceptionHandling(String matchingKey, String bucketingKey, String split, Map<String, Object> attributes) throws ChangeNumberExceptionWrapper {
        ParsedSplit parsedSplit = _splitFetcher.fetch(split);

        if (parsedSplit == null) {
            Logger.d("Returning control because no split was found for: %s", split);
            return new TreatmentLabelAndChangeNumber(Treatments.CONTROL, DEFINITION_NOT_FOUND);
        }

        return getTreatment(matchingKey, bucketingKey, parsedSplit, attributes);
    }

    /**
     * @param matchingKey  MUST NOT be null
     * @param bucketingKey
     * @param parsedSplit  MUST NOT be null
     * @param attributes   MUST NOT be null
     * @return
     * @throws ChangeNumberExceptionWrapper
     */
    private TreatmentLabelAndChangeNumber getTreatment(String matchingKey, String bucketingKey, ParsedSplit parsedSplit, Map<String, Object> attributes) throws ChangeNumberExceptionWrapper {
        try {
            if (parsedSplit.killed()) {
                return new TreatmentLabelAndChangeNumber(parsedSplit.defaultTreatment(), KILLED, parsedSplit.changeNumber());
            }

            /*
             * There are three parts to a single Split: 1) Whitelists 2) Traffic Allocation
             * 3) Rollout. The flag inRollout is there to understand when we move into the Rollout
             * section. This is because we need to make sure that the Traffic Allocation
             * computation happens after the whitelist but before the rollout.
             */
            boolean inRollout = false;

            String bk = (bucketingKey == null) ? matchingKey : bucketingKey;

            for (ParsedCondition parsedCondition : parsedSplit.parsedConditions()) {

                if (!inRollout && parsedCondition.conditionType() == ConditionType.ROLLOUT) {

                    if (parsedSplit.trafficAllocation() < 100) {
                        // if the traffic allocation is 100%, no need to do anything special.
                        int bucket = Splitter.getBucket(bk, parsedSplit.trafficAllocationSeed(), parsedSplit.algo());

                        if (bucket >= parsedSplit.trafficAllocation()) {
                            // out of split
                            return new TreatmentLabelAndChangeNumber(parsedSplit.defaultTreatment(), NOT_IN_SPLIT, parsedSplit.changeNumber());
                        }

                    }
                    inRollout = true;
                }

                if (parsedCondition.matcher().match(matchingKey, bucketingKey, attributes, this)) {
                    String treatment = Splitter.getTreatment(bk, parsedSplit.seed(), parsedCondition.partitions(), parsedSplit.algo());
                    return new TreatmentLabelAndChangeNumber(treatment, parsedCondition.label(), parsedSplit.changeNumber());
                }
            }

            return new TreatmentLabelAndChangeNumber(parsedSplit.defaultTreatment(), DEFAULT_RULE, parsedSplit.changeNumber());
        } catch (Exception e) {
            throw new ChangeNumberExceptionWrapper(e, parsedSplit.changeNumber());
        }

    }

    private static final class TreatmentLabelAndChangeNumber {
        private final String _treatment;
        private final String _label;
        private final Long _changeNumber;

        public TreatmentLabelAndChangeNumber(String treatment, String label) {
            this(treatment, label, null);
        }

        public TreatmentLabelAndChangeNumber(String treatment, String label, Long changeNumber) {
            _treatment = treatment;
            _label = label;
            _changeNumber = changeNumber;
        }
    }


    public void on(SplitEvent event, SplitEventTask task){
        checkNotNull(event);
        checkNotNull(task);

        _eventsManager.register(event, task);
    }

    @Override
    public boolean track(String trafficType, String eventType) {
        Event event = createEvent(_matchingKey, trafficType, eventType);
        return track(event);
    }

    @Override
    public boolean track(String trafficType, String eventType, double value) {
        Event event = createEvent(_matchingKey, trafficType, eventType);
        event.value = value;

        return track(event);
    }

    @Override
    public boolean track(String eventType) {
        Event event = createEvent(_matchingKey, _config.trafficType(), eventType);
        return track(event);
    }

    @Override
    public boolean track(String eventType, double value) {
        Event event = createEvent(_matchingKey, _config.trafficType(), eventType);
        event.value = value;

        return track(event);
    }

    private Event createEvent(String key, String trafficType, String eventType) {
        Event event = new Event();
        event.eventTypeId = eventType;
        event.trafficTypeName = trafficType;
        event.key = key;
        event.timestamp = System.currentTimeMillis();
        return event;
    }

    private boolean track(Event event) {
        if (Strings.isNullOrEmpty(event.trafficTypeName)) {
            Logger.w("Traffic Type was null or empty");
            return false;
        }

        if (Strings.isNullOrEmpty(event.eventTypeId)) {
            Logger.w("Event Type was null or empty");
            return false;
        }

        if (Strings.isNullOrEmpty(event.key)) {
            Logger.w("Cannot track event for null key");
            return false;
        }

        return _trackClient.track(event);

    }

}
