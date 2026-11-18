package com.example.upcomingdividendapi;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * ============================================================
 * KEEP-ALIVE PING
 * ============================================================
 *
 * GET or HEAD /api/ping  ->  "ok"
 *
 * For an external cron (cron-job.org, UptimeRobot, etc.) to
 * stop Render's free instance from sleeping.
 *
 * Deliberately does NOTHING else:
 *   - no DividendService, cache, file or Neon access
 *   - no NSE / Groww / Yahoo calls
 *   - does NOT count as user activity, so it never wakes
 *     the background price/dividend refresh
 */
@RestController
public class PingController {

    @RequestMapping(
            value = "/api/ping",
            method = {
                    RequestMethod.GET,
                    RequestMethod.HEAD
            }
    )
    public ResponseEntity<String> ping() {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body("ok");
    }
}
