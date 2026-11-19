package dev.faststats.featureflag;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.faststats.Attributes;
import dev.faststats.SimpleContext;
import dev.faststats.SubmissionService;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

final class SimpleFeatureFlagService extends SubmissionService implements FeatureFlagService {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final String CHECK_PATH = "/v1/check";
    private static final String OPT_IN_PATH = "/v1/opt-in";
    private static final String OPT_OUT_PATH = "/v1/opt-out";

    private final URI url = getServerUrl("faststats.flags-server", "https://flags.faststats.dev");

    private final SimpleContext context;

    private final Attributes attributes;
    private final Duration ttl;

    private final Map<String, CompletableFuture<?>> fetchesInProgress = new ConcurrentHashMap<>();

    SimpleFeatureFlagService(
            final SimpleContext context,
            final Attributes attributes,
            final Duration ttl
    ) throws IllegalArgumentException {
        super(context);
        if (ttl.isNegative()) throw new IllegalArgumentException("TTL cannot be negative");
        this.attributes = attributes;
        this.context = context;
        this.ttl = ttl;
    }

    @SuppressWarnings("unchecked")
    <T> CompletableFuture<T> fetch(final SimpleFeatureFlag<T> flag) {
        if (!context.isSubmitting()) return CompletableFuture.completedFuture(flag.getDefaultValue());
        final var future = new CompletableFuture<T>();
        final var existing = fetchesInProgress.putIfAbsent(flag.getId(), future);
        if (existing != null) return (CompletableFuture<T>) existing;
        createFetch(flag).whenComplete((value, throwable) -> {
            fetchesInProgress.remove(flag.getId(), future);
            if (throwable != null) future.completeExceptionally(throwable);
            else future.complete(value);
        });
        return future;
    }

    <T> CompletableFuture<T> optIn(final SimpleFeatureFlag<T> flag) {
        return sendOptRequest(flag, OPT_IN_PATH);
    }

    <T> CompletableFuture<T> optOut(final SimpleFeatureFlag<T> flag) {
        return sendOptRequest(flag, OPT_OUT_PATH);
    }

    private <T> CompletableFuture<T> sendOptRequest(final SimpleFeatureFlag<T> flag, final String path) {
        if (!context.isSubmitting()) {
            final CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException(
                    "FastStats submission is not active for " + context.getProjectName()
            ));
            return future;
        }
        final JsonObject requestBody = createRequestBody(flag);
        return CompletableFuture.supplyAsync(() -> {
            try {
                final HttpURLConnection connection = (HttpURLConnection) url.resolve(path).toURL().openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(TIMEOUT_MS);
                    connection.setReadTimeout(TIMEOUT_MS);
                    connection.setUseCaches(false);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Authorization", "Bearer " + context.getToken());
                    try (final OutputStream output = connection.getOutputStream()) {
                        output.write(requestBody.toString().getBytes(UTF_8));
                    }
                    final int statusCode = connection.getResponseCode();
                    final ByteArrayOutputStream bodyOutput = new ByteArrayOutputStream();
                    try (final InputStream input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                        if (input != null) {
                            final byte[] buffer = new byte[4096];
                            int read;
                            while ((read = input.read(buffer)) != -1) bodyOutput.write(buffer, 0, read);
                        }
                    }
                    return new AbstractMap.SimpleEntry<>(statusCode, new String(bodyOutput.toByteArray(), UTF_8).trim());
                } finally {
                    connection.disconnect();
                }
            } catch (final Exception e) {
                throw new CompletionException(e);
            }
        }).thenCompose(response -> {
            if (isSuccessful(response.getKey())) return fetch(flag);
            logUnsuccessfulResponse(response.getKey(), response.getValue());
            final CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException(String.format(
                    "Feature flag opt request failed with status %s (%s)", response.getKey(), response.getValue()
            )));
            return failed;
        });
    }

    private <T> CompletableFuture<T> createFetch(final SimpleFeatureFlag<T> flag) {
        final JsonObject requestBody = createRequestBody(flag);
        logger.info("Fetching %s: %s", url.resolve(CHECK_PATH), requestBody.toString());
        return CompletableFuture.supplyAsync(() -> {
            try {
                final HttpURLConnection connection = (HttpURLConnection) url.resolve(CHECK_PATH).toURL().openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(TIMEOUT_MS);
                    connection.setReadTimeout(TIMEOUT_MS);
                    connection.setUseCaches(false);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Authorization", "Bearer " + context.getToken());
                    try (final OutputStream output = connection.getOutputStream()) {
                        output.write(requestBody.toString().getBytes(UTF_8));
                    }
                    final int statusCode = connection.getResponseCode();
                    final ByteArrayOutputStream bodyOutput = new ByteArrayOutputStream();
                    try (final InputStream input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                        if (input != null) {
                            final byte[] buffer = new byte[4096];
                            int read;
                            while ((read = input.read(buffer)) != -1) bodyOutput.write(buffer, 0, read);
                        }
                    }
                    return handleFetchResponse(flag, statusCode, new String(bodyOutput.toByteArray(), UTF_8).trim());
                } finally {
                    connection.disconnect();
                }
            } catch (final Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    private JsonObject createRequestBody(final SimpleFeatureFlag<?> flag) {
        final var requestBody = new JsonObject();
        final var attributes = new JsonObject();

        requestBody.addProperty("identifier", context.getConfig().serverId().toString());
        requestBody.addProperty("key", flag.getId());

        this.attributes.forEachPrimitive(attributes::add);
        if (flag.attributes() != null) flag.attributes().forEachPrimitive(attributes::add);
        if (!attributes.isEmpty()) requestBody.add("attributes", attributes);

        return requestBody;
    }

    private <T> T handleFetchResponse(final SimpleFeatureFlag<T> flag, final int statusCode, final String body) {
        try {
            if (statusCode < 200 || statusCode >= 300) {
                logUnsuccessfulResponse(statusCode, body);
                throw new IllegalStateException(String.format("Unexpected response status: %s (%s)", statusCode, body));
            }

            final var json = JsonParser.parseString(body);
            final var value = getValue(flag, json);
            logger.info("Fetch returned body: %s (value: %s)", json, value);
            flag.setLastFetch(System.currentTimeMillis());
            flag.setValue(value);
            return value;
        } catch (final JsonParseException e) {
            logger.error("Unexpected response body: %s (%s)", e, body, statusCode);
            throw new IllegalStateException(String.format("Unexpected response body: %s (%s)", body, statusCode), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getValue(final SimpleFeatureFlag<T> flag, final JsonElement body) {
        if (!(body instanceof final JsonObject object)) {
            logger.warn("Unexpected JSON response: %s", body);
            throw new IllegalStateException("Unexpected JSON response: " + body);
        }
        if (!(object.get("value") instanceof final JsonPrimitive primitive)) {
            logger.warn("Missing or invalid 'value' in JSON response: %s", body);
            throw new IllegalStateException("Missing or invalid 'value' in JSON response: " + body);
        }

        return (T) switch (flag.getType()) {
            case STRING -> primitive.getAsString();
            case NUMBER -> getAsNumber(primitive);
            case BOOLEAN -> getAsBoolean(primitive);
        };
    }

    private Number getAsNumber(final JsonPrimitive primitive) {
        try {
            if (primitive.isNumber()) return primitive.getAsNumber();
            return new BigDecimal(primitive.getAsString());
        } catch (final NumberFormatException e) {
            logger.warn("Expected a number but got: %s", primitive.getAsString());
            throw new IllegalStateException("Expected a number but got: " + primitive.getAsString(), e);
        }
    }

    private boolean getAsBoolean(final JsonPrimitive primitive) {
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        return switch (primitive.getAsString()) {
            case "true" -> true;
            case "false" -> false;
            default -> {
                logger.warn("Expected a boolean but got: %s", primitive.getAsString());
                throw new IllegalStateException("Expected a boolean but got: " + primitive.getAsString());
            }
        };
    }

    @Override
    public FeatureFlag<Boolean> define(final String id, final boolean defaultValue) {
        return defineFlag(id, defaultValue, null);
    }

    @Override
    public FeatureFlag<Boolean> define(final String id, final boolean defaultValue, final Attributes attributes) {
        return defineFlag(id, defaultValue, attributes);
    }

    @Override
    public FeatureFlag<String> define(final String id, final String defaultValue) {
        return defineFlag(id, defaultValue, null);
    }

    @Override
    public FeatureFlag<String> define(final String id, final String defaultValue, final Attributes attributes) {
        return defineFlag(id, defaultValue, attributes);
    }

    @Override
    public FeatureFlag<Number> define(final String id, final Number defaultValue) {
        return defineFlag(id, defaultValue, null);
    }

    @Override
    public FeatureFlag<Number> define(final String id, final Number defaultValue, final Attributes attributes) {
        return defineFlag(id, defaultValue, attributes);
    }

    private <T> FeatureFlag<T> defineFlag(final String id, final T defaultValue, @Nullable final Attributes attributes) {
        return new SimpleFeatureFlag<>(id, defaultValue, attributes, this);
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public Duration getTTL() {
        return ttl;
    }

    @Override
    public void onShutdown() {
        fetchesInProgress.values().forEach(fetch -> fetch.cancel(true));
        fetchesInProgress.clear();
    }

    private static boolean isSuccessful(final int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private void logUnsuccessfulResponse(final int statusCode, final String body) {
        if (statusCode >= 300 && statusCode < 400) {
            logger.warn("Received redirect response from %s server: %s (%s)", serverType(), statusCode, body);
        } else if (statusCode >= 400 && statusCode < 500) {
            logger.error("Submitted invalid request to %s server: %s (%s)", null, serverType(), statusCode, body);
        } else if (statusCode >= 500 && statusCode < 600) {
            logger.error("Received server error response from %s server: %s (%s)", null, serverType(), statusCode, body);
        } else {
            logger.warn("Received unexpected response from %s server: %s (%s)", serverType(), statusCode, body);
        }
    }

    @Override
    protected String serverType() {
        return "feature flag";
    }

    static final class Factory implements FeatureFlagService.Factory {
        private final SimpleContext context;
        private @Nullable Attributes attributes;
        private Duration ttl = DEFAULT_TTL;

        Factory(final SimpleContext context) {
            this.context = context;
        }

        @Override
        public FeatureFlagService.Factory attributes(final Attributes attributes) {
            this.attributes = attributes;
            return this;
        }

        @Override
        public FeatureFlagService.Factory ttl(final Duration ttl) throws IllegalArgumentException {
            if (ttl.isNegative()) throw new IllegalArgumentException("TTL cannot be negative");
            this.ttl = ttl;
            return this;
        }

        @Override
        public FeatureFlagService create() throws IllegalArgumentException {
            final var attributes = this.attributes != null ? this.attributes : Attributes.empty();
            return new SimpleFeatureFlagService(context, attributes, ttl);
        }
    }
}
