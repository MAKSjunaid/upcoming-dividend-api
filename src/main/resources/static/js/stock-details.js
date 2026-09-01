/* =========================================================
   STOCK DETAILS
   =========================================================
 *
 * This file ONLY handles clicking a stock card andd
 * displaying stock historical return data.
 *
 * It does NOT:
 *
 * - modify app.js
 * - modify dividend-ui.js
 * - modify favorites
 * - modify sorting
 * - modify investment calculations
 * - modify dividend cards
 * - perform dividend searches
 *
 *
 * IMPORTANT:
 *
 * The backend API returns HISTORICAL PRICES.
 *
 * Example:
 *
 * {
 *     "symbol": "RADIANTCMS.NS",
 *     "5Y": null,
 *     "3Y": 95.70,
 *     "1Y": 58.87,
 *     "6M": 36.72,
 *     "3M": 41.90,
 *     "1M": 36.64,
 *     "5D": 34.52
 * }
 *
 * These numbers are NOT percentages.
 *
 * The frontend calculates the percentage using:
 *
 * Return % =
 *
 * ((Current Price - Historical Price)
 *      / Historical Price) * 100
 *
 * ========================================================= */


/* =========================================================
   CONFIGURATION
   ========================================================= */

const STOCK_DETAILS_API =
    "/api/stock-details";


/* =========================================================
   STATE
   ========================================================= */

let currentlyOpenStockDetails = null;


/* =========================================================
   INITIALIZE
   ========================================================= */

function initializeStockDetails() {

    const stockList =
        document.getElementById(
            "stockList"
        );


    if (!stockList) {

        console.warn(
            "stock-details.js: #stockList not found."
        );

        return;
    }


    /*
     * Stock cards are created dynamically
     * by dividend-ui.js.
     *
     * Therefore we use event delegation.
     */

    stockList.addEventListener(
        "click",
        handleStockCardClick
    );


    console.log(
        "stock-details.js initialized."
    );
}


/* =========================================================
   STOCK CARD CLICK
   ========================================================= */

function handleStockCardClick(event) {

    /*
     * Do not interfere with favorite button.
     */

    if (
        event.target.closest(
            ".favorite-button"
        )
    ) {

        return;
    }


    /*
     * Find the stock card that was clicked.
     */

    const card =
        event.target.closest(
            ".stock-card"
        );


    /*
     * Click was not inside a stock card.
     */

    if (!card) {

        return;
    }


    /*
     * If this card is already open,
     * close it.
     */

    if (
        currentlyOpenStockDetails ===
        card
    ) {

        closeStockDetails(
            card
        );

        return;
    }


    /*
     * Close any previously opened
     * stock details panel.
     */

    if (
        currentlyOpenStockDetails
    ) {

        closeStockDetails(
            currentlyOpenStockDetails
        );
    }


    /*
     * Read the existing symbol from
     * the stock card.
     */

    const symbolElement =
        card.querySelector(
            ".symbol"
        );


    if (!symbolElement) {

        console.warn(
            "stock-details.js: Symbol element not found."
        );

        return;
    }


    const symbol =
        symbolElement.textContent
            .trim();


    if (!symbol) {

        console.warn(
            "stock-details.js: Stock symbol is empty."
        );

        return;
    }


    /*
     * Open the stock details panel.
     */

    openStockDetails(
        card,
        symbol
    );
}


/* =========================================================
   OPEN DETAILS
   ========================================================= */

function openStockDetails(
    card,
    symbol
) {

    /*
     * Prevent duplicate details panels.
     */

    const existingDetails =
        card.querySelector(
            ".stock-details-panel"
        );


    if (existingDetails) {

        /*
         * Simply restore visibility.
         *
         * No movement.
         * No height animation.
         * No transform.
         */

        existingDetails.classList.add(
            "open"
        );


        currentlyOpenStockDetails =
            card;

        return;
    }


    /*
     * Create details panel.
     */

    const detailsPanel =
        document.createElement(
            "div"
        );


    detailsPanel.className =
        "stock-details-panel";


    /*
     * Stock Performance heading.
     */

    detailsPanel.innerHTML = `

       <div class="stock-performance-title">
            Stock Performance
        </div>


        <div class="stock-details-periods">


            <!-- 5Y -->

            <div class="stock-period-box">

                <span class="stock-period-label">
                    5Y:
                </span>

                <span
                    class="stock-period-value"
                    data-period-value="5Y">

                    —

                </span>

            </div>


            <!-- 3Y -->

            <div class="stock-period-box">

                <span class="stock-period-label">
                    3Y:
                </span>

                <span
                    class="stock-period-value"
                    data-period-value="3Y">

                    —

                </span>

            </div>


            <!-- 1Y -->

            <div class="stock-period-box">

                <span class="stock-period-label">
                    1Y:
                </span>

                <span
                    class="stock-period-value"
                    data-period-value="1Y">

                    —

                </span>

            </div>


            <!-- 6M -->

            <div class="stock-period-box">

                <span class="stock-period-label">
                    6M:
                </span>

                <span
                    class="stock-period-value"
                    data-period-value="6M">

                    —

                </span>

            </div>


            <!-- 3M -->

            <div class="stock-period-box">

                <span class="stock-period-label">
                    3M:
                </span>

                <span
                    class="stock-period-value"
                    data-period-value="3M">

                    —

                </span>

            </div>


            <!-- 1M -->

            <div class="stock-period-box">

                <span class="stock-period-label">
                    1M:
                </span>

                <span
                    class="stock-period-value"
                    data-period-value="1M">

                    —

                </span>

            </div>


            <!-- 5D -->

            <div class="stock-period-box">

                <span class="stock-period-label">
                    5D:
                </span>

                <span
                    class="stock-period-value"
                    data-period-value="5D">

                    —

                </span>

            </div>


        </div>

    `;


    /*
     * Prevent clicking inside the details
     * panel from triggering the stock card
     * click again.
     */

    detailsPanel.addEventListener(
        "click",
        function(event) {

            event.stopPropagation();

        }
    );


    /*
     * Add panel to the stock card.
     */

    card.appendChild(
        detailsPanel
    );


    /*
     * Allow CSS opening animation.
     *
     * Only opacity changes.
     *
     * The panel itself does NOT move.
     */

    requestAnimationFrame(
        function() {

            detailsPanel.classList.add(
                "open"
            );

        }
    );


    /*
     * Remember currently opened card.
     */

    currentlyOpenStockDetails =
        card;


    /*
     * Load historical price data.
     */

    loadStockDetails(
        detailsPanel,
        card,
        symbol
    );
}


/* =========================================================
   CLOSE DETAILS
   ========================================================= */

function closeStockDetails(
    card
) {

    if (!card) {

        return;
    }


    const detailsPanel =
        card.querySelector(
            ".stock-details-panel"
        );


    if (!detailsPanel) {

        if (
            currentlyOpenStockDetails ===
            card
        ) {

            currentlyOpenStockDetails =
                null;
        }

        return;
    }


    /*
     * =====================================================
     * IMPORTANT CLOSING BEHAVIOR
     * =====================================================
     *
     * We ONLY remove the "open" class.
     *
     * CSS changes ONLY opacity.
     *
     * The panel does NOT:
     *
     * - move
     * - translate
     * - scale
     * - change height
     * - change padding
     * - change margin
     *
     * Therefore the panel stays exactly where it is
     * while disappearing.
     */

    detailsPanel.classList.remove(
        "open"
    );


    /*
     * Wait for the opacity animation to finish.
     *
     * CSS transition:
     *
     * 160ms
     *
     * We wait 180ms.
     *
     * The panel remains in the document during
     * this time, so the stock card does not
     * immediately change its layout.
     */

    setTimeout(
        function() {

            if (
                detailsPanel.parentNode ===
                card
            ) {

                detailsPanel.remove();
            }

        },
        180
    );


    /*
     * Clear current card.
     */

    if (
        currentlyOpenStockDetails ===
        card
    ) {

        currentlyOpenStockDetails =
            null;
    }
}


/* =========================================================
   LOAD STOCK DETAILS
   ========================================================= */

async function loadStockDetails(
    detailsPanel,
    card,
    symbol
) {

    if (!detailsPanel) {

        return;
    }


    /*
     * Find all period values.
     */

    const periodValues =
        detailsPanel.querySelectorAll(
            ".stock-period-value"
        );


    /*
     * Show loading state.
     */

    periodValues.forEach(
        function(element) {

            element.textContent =
                "...";

            element.classList.remove(
                "positive",
                "negative"
            );

        }
    );


    try {

        /*
         * Read CURRENT PRICE directly
         * from the already-rendered stock card.
         */

        const currentPrice =
            getCurrentPriceFromCard(
                card
            );


        console.log(
            "Stock details current price:",
            currentPrice
        );


        if (
            currentPrice === null ||
            currentPrice <= 0
        ) {

            throw new Error(
                "Current price could not be read from stock card."
            );
        }


        /*
         * Build API URL.
         */

        const url =
            STOCK_DETAILS_API +
            "?symbol=" +
            encodeURIComponent(
                symbol
            ) +
            "&period=5Y";


        console.log(
            "Loading stock details:",
            url
        );


        /*
         * Call backend.
         */

        const response =
            await fetch(
                url,
                {
                    method: "GET",
                    cache: "no-store"
                }
            );


        /*
         * Check HTTP response.
         */

        if (!response.ok) {

            const message =
                await response.text();


            throw new Error(
                message ||
                "Unable to load stock details."
            );
        }


        /*
         * Convert response to JSON.
         */

        const data =
            await response.json();


        console.log(
            "Stock historical price API response:",
            data
        );


        /*
         * Calculate and render percentages.
         */

        renderStockDetails(
            detailsPanel,
            data,
            currentPrice
        );


    } catch (error) {

        console.error(
            "Stock details error:",
            error
        );


        /*
         * If API fails, display N/A.
         */

        periodValues.forEach(
            function(element) {

                element.textContent =
                    "N/A";

                element.classList.remove(
                    "positive",
                    "negative"
                );

            }
        );

    }
}


/* =========================================================
   GET CURRENT PRICE FROM STOCK CARD
   ========================================================= */

function getCurrentPriceFromCard(
    card
) {

    if (!card) {

        return null;
    }


    /*
     * Search all metrics.
     */

    const metrics =
        card.querySelectorAll(
            ".metric"
        );


    for (
        let i = 0;
        i < metrics.length;
        i++
    ) {

        const metric =
            metrics[i];


        const label =
            metric.querySelector(
                ".metric-label"
            );


        if (!label) {

            continue;
        }


        const labelText =
            label.textContent
                .trim()
                .toLowerCase();


        if (
            labelText ===
            "current price"
        ) {

            const valueElement =
                metric.querySelector(
                    ".metric-value"
                );


            if (!valueElement) {

                return null;
            }


            /*
             * Example:
             *
             * 📈 ₹39.29
             */

            const rawText =
                valueElement.textContent
                    .trim();


            const cleanedText =
                rawText.replace(
                    /[^0-9.-]/g,
                    ""
                );


            const price =
                Number(
                    cleanedText
                );


            if (
                Number.isFinite(price) &&
                price > 0
            ) {

                return price;
            }


            return null;
        }
    }


    console.warn(
        "stock-details.js: Current Price metric not found."
    );


    return null;
}


/* =========================================================
   RENDER STOCK DETAILS
   ========================================================= */

function renderStockDetails(
    detailsPanel,
    data,
    currentPrice
) {

    /*
     * Make sure API returned an object.
     */

    if (
        !data ||
        typeof data !== "object"
    ) {

        console.warn(
            "Invalid stock details response:",
            data
        );

        return;
    }


    /*
     * Make sure current price is valid.
     */

    if (
        !Number.isFinite(currentPrice) ||
        currentPrice <= 0
    ) {

        console.warn(
            "Invalid current price:",
            currentPrice
        );

        return;
    }


    /*
     * Exact period names returned by backend.
     */

    const periods = [

        "5Y",
        "3Y",
        "1Y",
        "6M",
        "3M",
        "1M",
        "5D"

    ];


    /*
     * Update each period.
     */

    periods.forEach(
        function(period) {

            const element =
                detailsPanel.querySelector(
                    '[data-period-value="' +
                    period +
                    '"]'
                );


            if (!element) {

                return;
            }


            /*
             * Historical price.
             */

            const historicalPrice =
                Number(
                    data[period]
                );


            /*
             * No historical price available.
             */

            if (
                !Number.isFinite(
                    historicalPrice
                ) ||
                historicalPrice <= 0
            ) {

                element.textContent =
                    "N/A";


                element.classList.remove(
                    "positive",
                    "negative"
                );


                return;
            }


            /*
             * =================================================
             * FRONTEND RETURN CALCULATION
             * =================================================
             */

            const returnPercentage =
                (
                    (
                        currentPrice -
                        historicalPrice
                    ) /
                    historicalPrice
                ) *
                100;


            /*
             * Render percentage.
             */

            element.textContent =
                formatReturnPercentage(
                    returnPercentage
                );


            /*
             * Remove previous color classes.
             */

            element.classList.remove(
                "positive",
                "negative"
            );


            /*
             * Positive = green.
             */

            if (
                returnPercentage > 0
            ) {

                element.classList.add(
                    "positive"
                );

            }


            /*
             * Negative = red.
             */

            else if (
                returnPercentage < 0
            ) {

                element.classList.add(
                    "negative"
                );
            }

        }
    );
}


/* =========================================================
   FORMAT RETURN PERCENTAGE
   ========================================================= */

function formatReturnPercentage(
    value
) {

    if (
        value === null ||
        value === undefined ||
        !Number.isFinite(value)
    ) {

        return "N/A";
    }


    /*
     * Positive.
     *
     * Example:
     *
     * +13.82%
     */

    if (
        value > 0
    ) {

        return "+" +
            value.toFixed(2) +
            "%";
    }


    /*
     * Negative.
     *
     * Example:
     *
     * -36.25%
     */

    if (
        value < 0
    ) {

        return value.toFixed(2) +
            "%";
    }


    /*
     * Zero.
     */

    return "0.00%";
}


/* =========================================================
   START
   ========================================================= */

if (
    document.readyState ===
    "loading"
) {

    document.addEventListener(
        "DOMContentLoaded",
        initializeStockDetails
    );

} else {

    initializeStockDetails();

}

// PERFECT<!--/* 30TH AUG FINAL */-->
 //
