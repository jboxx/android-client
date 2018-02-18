package io.split.android.client.impressions;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import io.split.android.client.dtos.TestImpressions;
import io.split.android.client.utils.Json;
import io.split.android.client.utils.Utils;
import timber.log.Timber;

public class HttpImpressionsSender implements ImpressionsSender {

    private CloseableHttpClient _client;
    private URI _eventsEndpoint;
    private ImpressionsStorageManager _storageManager;

    public HttpImpressionsSender(CloseableHttpClient client, String eventsEndpoint, ImpressionsStorageManager impressionsStorageManager) throws URISyntaxException {
        _client = client;
        _eventsEndpoint = new URIBuilder(eventsEndpoint).setPath("/api/testImpressions/bulk").build();
        _storageManager = impressionsStorageManager;
    }

    @Override
    public boolean post(List<TestImpressions> impressions) {
        if (impressions == null || impressions.isEmpty()) {
            return false;
        }

        if (!Utils.isReachable(_eventsEndpoint)) {
            Timber.d("%s is NOT REACHABLE. Avoid trying to send impressions this time.", _eventsEndpoint.getHost());
            return false;
        }

        synchronized (this) {
            Timber.d("Posting %d Split impressions", impressions.size());

            String json = Json.toJson(impressions);
            try {
                StringEntity entity = new StringEntity(json, "UTF-8");
                entity.setContentType("application/json");
                return post(entity);
            } catch (UnsupportedEncodingException e) {
                Timber.e(e);
                return false;
            }
        }
    }

    private boolean post(StringEntity entity) {

        CloseableHttpResponse response = null;

        try {

            HttpPost request = new HttpPost(_eventsEndpoint);
            request.setEntity(entity);

            response = _client.execute(request);

            int status = response.getStatusLine().getStatusCode();
            String reason = response.getStatusLine().getReasonPhrase();
            if (status < 200 || status >= 300) {
                Timber.w("Response status was: %d. Reason: %s", status, reason);
                return false;
            }
            Timber.d("Entity sent: %s", entity);

            return true;
        } catch (Throwable t) {
            Timber.e(t, "Exception when posting impressions %s", entity);
            return false;
        } finally {
            Utils.forceClose(response);
        }

    }

}
