package dev.shop.common.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-process fan-out used when the services share one JVM and Kafka is not configured.
 * Each subscriber still deduplicates through its own inbox.
 */
public final class LocalEvents {
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();

    private LocalEvents() {}

    public static void subscribe(Consumer<String> listener) { LISTENERS.add(listener); }

    public static void publish(String body) {
        RuntimeException failure = null;
        for (var listener : LISTENERS) {
            try { listener.accept(body); }
            catch (RuntimeException ex) { failure = ex; }
        }
        if (failure != null) throw failure;
    }
}
