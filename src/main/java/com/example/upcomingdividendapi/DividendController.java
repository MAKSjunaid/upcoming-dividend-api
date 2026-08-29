package com.example.upcomingdividendapi;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dividends")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    /**
     * Lightweight endpoint used by the frontend to check
     * whether the dividend data has changed.
     *
     * The frontend can call this every 1 minute without
     * downloading the complete dividend data.
     */
    @GetMapping("/version")
    public ResponseEntity<?> getVersion() {

        try {
            String version = dividendService.getCacheVersion();

            return ResponseEntity.ok(
                    new VersionResponse(version)
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(e.getMessage());
        }
    }

    /**
     * Existing endpoint for fetching the actual
     * upcoming dividend data.
     */
    @PostMapping
    public ResponseEntity<?> getUpcomingDividends(
            @RequestBody(required = false) DividendRequest request) {

        try {
            List<DividendResponse> response =
                    dividendService.getUpcomingDividends(request);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    /**
     * Response object for the version endpoint.
     */
    public static class VersionResponse {

        private final String version;

        public VersionResponse(String version) {
            this.version = version;
        }

        public String getVersion() {
            return version;
        }
    }
}
