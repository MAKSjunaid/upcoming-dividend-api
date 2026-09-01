package com.example.upcomingdividendapi;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ============================================================
 * STOCK DETAILS CONTROLLER k
 * ============================================================
 *
 * Provides historical stock price data to the frontend.
 *
 * Example request:
 *
 * GET /api/stock-details?symbol=RADIANTCMS
 *
 * Example response:
 *
 * {
 *     "symbol": "RADIANTCMS.NS",
 *     "5Y": 125.20,
 *     "3Y": 232.00,
 *     "1Y": 310.50,
 *     "6M": 280.25,
 *     "3M": 295.40,
 *     "1M": 305.10,
 *     "5D": 315.75
 * }
 *
 * This controller does NOT:
 *
 * - handle dividend data
 * - modify favorites
 * - handle sorting
 * - handle pagination
 * - handle frontend logic
 *
 * All frontend stock-details logic remains inside:
 *
 *     stock-details.js
 *
 * ============================================================
 */
@RestController
@RequestMapping("/api/stock-details")
public class StockDetailsController {


    // ============================================================
    // SERVICE
    // ============================================================

    private final HistoricalReturnService historicalReturnService;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public StockDetailsController(
            HistoricalReturnService historicalReturnService
    ) {

        this.historicalReturnService =
                historicalReturnService;
    }


    // ============================================================
    // GET STOCK DETAILS
    // ============================================================

    /**
     * Example:
     *
     * GET /api/stock-details?symbol=RADIANTCMS
     *
     * The "period" parameter is accepted for compatibility
     * with the frontend.
     *
     * The current backend already fetches all periods:
     *
     * 5Y
     * 3Y
     * 1Y
     * 6M
     * 3M
     * 1M
     * 5D
     *
     * Therefore the backend returns all of them.
     *
     * Example:
     *
     * GET /api/stock-details?symbol=RADIANTCMS&period=5Y
     */
    @GetMapping
    public ResponseEntity<?> getStockDetails(
            @RequestParam("symbol") String symbol,
            @RequestParam(
                    value = "period",
                    required = false
            ) String period
    ) {

        try {

            // ----------------------------------------------------
            // Validate symbol
            // ----------------------------------------------------

            if (
                    symbol == null
                            || symbol.trim().isEmpty()
            ) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Symbol cannot be empty"
                        );
            }


            // ----------------------------------------------------
            // Fetch historical prices
            // ----------------------------------------------------

            HistoricalReturnService.HistoricalPriceResponse response =
                    historicalReturnService
                            .getHistoricalPrices(
                                    symbol
                            );


            // ----------------------------------------------------
            // No data found
            // ----------------------------------------------------

            if (response == null) {

                return ResponseEntity
                        .notFound()
                        .build();
            }


            // ----------------------------------------------------
            // Return JSON
            // ----------------------------------------------------

            return ResponseEntity.ok(
                    response
            );


        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            e.getMessage()
                    );


        } catch (Exception e) {

            e.printStackTrace();


            return ResponseEntity
                    .internalServerError()
                    .body(
                            e.getMessage()
                    );
        }
    }
}
