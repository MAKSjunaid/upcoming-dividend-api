package com.example.upcomingdividendapi;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dividends")
public class DividendController {

    private final DividendService dividendService;

    private final LiveUpdates liveUpdates;

    public DividendController(
            DividendService dividendService,
            LiveUpdates liveUpdates
    ) {
        this.dividendService = dividendService;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Lightweight endpoint used by the frontend to check
     * whether the dividend data has changed.
     *
     * Response (answered from memory):
     * {
     *   "version": "42",
     *   "pricesAsOf": "2026-11-18T14:47:15",
     *   "marketOpen": true,
     *   "priceStatus": "LIVE" | "CLOSING" | "UPDATING",
     *   "lastCloseDate": "2026-11-17"
     * }
     */
    @GetMapping("/version")
    public ResponseEntity<?> getVersion() {

        try {
            dividendService.getCacheVersion();

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(dividendService.getStatus());

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(e.getMessage());
        }
    }

    /**
     * Live updates (Server-Sent Events). The browser keeps
     * this connection open and receives a "status" event
     * whenever prices or dividends change.
     */
    @GetMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(HttpServletResponse response) {

        /*
         * Stop proxies (Render / Nginx) from buffering events.
         */
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        dividendService.onLiveSubscriber();

        return liveUpdates.subscribe(
                dividendService.getStatus()
        );
    }

    /**
     * Upcoming dividend data, answered from memory.
     *
     * Headers carry the data version and price status, so the
     * frontend needs only this one request (no extra
     * /version calls).
     */
    @PostMapping
    public ResponseEntity<?> getUpcomingDividends(
            @RequestBody(required = false) DividendRequest request) {

        try {
            List<DividendResponse> data =
                    dividendService.getUpcomingDividends(request);

            Map<String, Object> status =
                    dividendService.getStatus();

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Data-Version", String.valueOf(status.get("version")))
                    .header("X-Price-Status", String.valueOf(status.get("priceStatus")))
                    .header("X-Prices-As-Of", String.valueOf(status.get("pricesAsOf")))
                    .header("X-Market-Open", String.valueOf(status.get("marketOpen")))
                    .header("X-Last-Close-Date", String.valueOf(status.get("lastCloseDate")))
                    .body(data);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
