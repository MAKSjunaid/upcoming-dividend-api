package com.example.upcomingdividendapi;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ============================================================
 * LIVE UPDATES (Server-Sent Events)
 * ============================================================
 *
 * Every open browser tab keeps ONE lightweight connection to
 * /api/dividends/stream.
 *
 * When prices or dividends change, the server pushes a small
 * "status" event ({version, pricesAsOf, marketOpen, ...}) to
 * every tab at once. The tab then fetches the new data.
 *
 * This replaces polling: updates appear the moment the
 * server has them, and idle tabs cost nothing. A "ping"
 * event every 25 seconds shows the page the connection is
 * alive.
 *
 * Browsers reconnect automatically if the connection drops.
 */
@Component
public class LiveUpdates {

    /*
     * Long-lived connections (6 hours). Dead ones are removed
     * when a heartbeat cannot be delivered; the browser
     * reconnects by itself if the connection ends.
     */
    private static final long EMITTER_TIMEOUT_MILLIS =
            6L * 60L * 60L * 1000L;

    private static final int MAX_SUBSCRIBERS = 1000;

    private final List<SseEmitter> emitters =
            new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(
            Map<String, Object> currentStatus
    ) {
        SseEmitter emitter =
                new SseEmitter(EMITTER_TIMEOUT_MILLIS);

        if (emitters.size() >= MAX_SUBSCRIBERS) {
            emitter.complete();
            return emitter;
        }

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));

        send(emitter, currentStatus);

        return emitter;
    }

    public boolean hasSubscribers() {
        return !emitters.isEmpty();
    }

    public int subscriberCount() {
        return emitters.size();
    }

    public void publish(Map<String, Object> status) {

        for (SseEmitter emitter : emitters) {
            send(emitter, status);
        }
    }

    /**
     * "ping" event every 25 seconds, carrying the current data
     * version. It keeps proxies (Render, Nginx) from closing
     * idle connections, lets the page see that the connection
     * is alive, and lets it catch any missed change, so the
     * page never needs to call /version.
     */
    public void heartbeat(String version) {

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("ping")
                                .data(version)
                );
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private void send(
            SseEmitter emitter,
            Map<String, Object> status
    ) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("status")
                            .data(
                                    status,
                                    MediaType.APPLICATION_JSON
                            )
            );
        } catch (Exception e) {
            emitters.remove(emitter);
        }
    }
}
