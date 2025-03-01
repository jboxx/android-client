package io.split.android.client.service.sseclient;

import android.util.Base64;

import androidx.annotation.Nullable;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.split.android.client.utils.Json;
import io.split.android.client.utils.Logger;

public class SseJwtParser {

    private final static String PUBLISHERS_CHANNEL_METADATA = "channel-metadata:publishers";
    private final static String PUBLISHERS_CHANNEL_PREFIX = "[?occupancy=metrics.publishers]";

    final static Type ALL_TOKEN_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    final static Type CHANNEL_TYPE = new TypeToken<Map<String, List<String>>>() {
    }.getType();

    public SseJwtToken parse(String rawToken) throws InvalidJwtTokenException {

        if (rawToken == null) {
            Logger.e("Error: JWT is null.");
            throw new InvalidJwtTokenException();
        }

        final String encodedPayload = extractTokenData(rawToken);
        if (encodedPayload == null) {
            Logger.e("SSE authentication JWT payload is not valid.");
            throw new InvalidJwtTokenException();
        }

        String payload = base64Decode(encodedPayload);
        if (payload == null) {
            Logger.e("Could not decode SSE authentication JWT payload.");
            throw new InvalidJwtTokenException();
        }

        Map<String, List<String>> channels = null;
        SseAuthToken authToken = null;
        try {
            authToken = Json.fromJson(payload, SseAuthToken.class);

            if (authToken == null || authToken.getChannelList() == null) {
                Logger.e("SSE JWT data is empty or not valid.");
                throw new InvalidJwtTokenException();
            }
            String unparsedChannels = authToken.getChannelList();
            channels = Json.fromJson(unparsedChannels, CHANNEL_TYPE);
            if (channels == null) {
                Logger.e("SSE JWT has not channels.");
                throw new InvalidJwtTokenException();
            }

        } catch (JsonSyntaxException e) {
            Logger.e("Error parsing SSE authentication JWT json " + e.getLocalizedMessage());
            throw new InvalidJwtTokenException();
        } catch (Exception e) {
            Logger.e("Unknonwn error while parsing SSE authentication JWT: " + e.getLocalizedMessage());
            throw new InvalidJwtTokenException();
        }

        List<String> processedChannels = new ArrayList<>();
        for (String channel : channels.keySet()) {
            List<String> channelInfo = channels.get(channel);
            if (channelInfo != null && channelInfo.contains(PUBLISHERS_CHANNEL_METADATA)) {
                processedChannels.add(PUBLISHERS_CHANNEL_PREFIX + channel);
            } else {
                processedChannels.add(channel);
            }
        }

        return new SseJwtToken(authToken.getIssuedAt(), authToken.getExpirationAt(), processedChannels, rawToken);
    }

    @Nullable
    private String extractTokenData(String token) {
        String[] components = token.split("\\.");
        if (components.length > 1) {
            return components[1];
        }
        return null;
    }

    @Nullable
    private String base64Decode(String string) {
        String decoded = null;
        try {
            byte[] bytes = Base64.decode(string, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            decoded = new String(bytes, Charset.defaultCharset());
        } catch (IllegalArgumentException e) {
            Logger.e("Received bytes didn't correspond to a valid Base64 encoded string." + e.getLocalizedMessage());
        } catch (Exception e) {
            Logger.e("An unknown error has ocurred " + e.getLocalizedMessage());
        }
        return decoded;
    }

    private List<String> emptyChannelList() {
        return new ArrayList<>();
    }
}
