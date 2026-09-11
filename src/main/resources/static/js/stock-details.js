/* =========================================================
   STOCK DETAILS
   =========================================================
 *
 * This file ONLY handles clicking a stock card and
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


/*
 * Duration of the percentage count-up animation.
 *
 * This matches the 3.28s visual animation already
 * configured in your CSS.
 */

const STOCK_DETAILS_COUNT_DURATION =
    1280; /* 3280 */


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
     * Stock Performance heading
     * and horizontal performance chart.
     */

    detailsPanel.innerHTML = `

        <div class="stock-performance-title">
            Stock Performance
        </div>


        <div class="stock-details-chart">

            <!-- 5D -->

            <div
                class="stock-performance-row"
                data-period-row="5D"
            >

                <div
                    class="stock-performance-period"
                >
                    5D
                </div>

                <div
                    class="stock-performance-value-left"
                    data-period-value-left="5D"
                ></div>

                <div class="stock-performance-track">

                    <div
                        class="stock-performance-zero"
                    ></div>

                    <div
                        class="stock-performance-candle"
                        data-period-candle="5D"
                    ></div>

                </div>

                <div
                    class="stock-performance-value-right"
                    data-period-value-right="5D"
                ></div>

            </div>


            <!-- 1M -->

            <div
                class="stock-performance-row"
                data-period-row="1M"
            >

                <div
                    class="stock-performance-period"
                >
                    1M
                </div>

                <div
                    class="stock-performance-value-left"
                    data-period-value-left="1M"
                ></div>

                <div class="stock-performance-track">

                    <div
                        class="stock-performance-zero"
                    ></div>

                    <div
                        class="stock-performance-candle"
                        data-period-candle="1M"
                    ></div>

                </div>

                <div
                    class="stock-performance-value-right"
                    data-period-value-right="1M"
                ></div>

            </div>


            <!-- 3M -->

            <div
                class="stock-performance-row"
                data-period-row="3M"
            >

                <div
                    class="stock-performance-period"
                >
                    3M
                </div>

                <div
                    class="stock-performance-value-left"
                    data-period-value-left="3M"
                ></div>

                <div class="stock-performance-track">

                    <div
                        class="stock-performance-zero"
                    ></div>

                    <div
                        class="stock-performance-candle"
                        data-period-candle="3M"
                    ></div>

                </div>

                <div
                    class="stock-performance-value-right"
                    data-period-value-right="3M"
                ></div>

            </div>


            <!-- 6M -->

            <div
                class="stock-performance-row"
                data-period-row="6M"
            >

                <div
                    class="stock-performance-period"
                >
                    6M
                </div>

                <div
                    class="stock-performance-value-left"
                    data-period-value-left="6M"
                ></div>

                <div class="stock-performance-track">

                    <div
                        class="stock-performance-zero"
                    ></div>

                    <div
                        class="stock-performance-candle"
                        data-period-candle="6M"
                    ></div>

                </div>

                <div
                    class="stock-performance-value-right"
                    data-period-value-right="6M"
                ></div>

            </div>


            <!-- 1Y -->

            <div
                class="stock-performance-row"
                data-period-row="1Y"
            >

                <div
                    class="stock-performance-period"
                >
                    1Y
                </div>

                <div
                    class="stock-performance-value-left"
                    data-period-value-left="1Y"
                ></div>

                <div class="stock-performance-track">

                    <div
                        class="stock-performance-zero"
                    ></div>

                    <div
                        class="stock-performance-candle"
                        data-period-candle="1Y"
                    ></div>

                </div>

                <div
                    class="stock-performance-value-right"
                    data-period-value-right="1Y"
                ></div>

            </div>


            <!-- 3Y -->

            <div
                class="stock-performance-row"
                data-period-row="3Y"
            >

                <div
                    class="stock-performance-period"
                >
                    3Y
                </div>

                <div
                    class="stock-performance-value-left"
                    data-period-value-left="3Y"
                ></div>

                <div class="stock-performance-track">

                    <div
                        class="stock-performance-zero"
                    ></div>

                    <div
                        class="stock-performance-candle"
                        data-period-candle="3Y"
                    ></div>

                </div>

                <div
                    class="stock-performance-value-right"
                    data-period-value-right="3Y"
                ></div>

            </div>


            <!-- 5Y -->

            <div
                class="stock-performance-row"
                data-period-row="5Y"
            >

                <div
                    class="stock-performance-period"
                >
                    5Y
                </div>

                <div
                    class="stock-performance-value-left"
                    data-period-value-left="5Y"
                ></div>

                <div class="stock-performance-track">

                    <div
                        class="stock-performance-zero"
                    ></div>

                    <div
                        class="stock-performance-candle"
                        data-period-candle="5Y"
                    ></div>

                </div>

                <div
                    class="stock-performance-value-right"
                    data-period-value-right="5Y"
                ></div>

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
   RESTORE EXISTING DETAILS PANEL
   =========================================================
 *
 * Used by app.js when the dividend backend version
 * changes and the dividend cards are rebuilt.
 *
 * IMPORTANT:
 *
 * This function DOES NOT call:
 *
 *     openStockDetails()
 *
 * and therefore DOES NOT call:
 *
 *     /api/stock-details
 *
 * The existing DOM panel is moved into the new
 * stock card.
 *
 * This preserves:
 *
 * - 5Y value
 * - 3Y value
 * - 1Y value
 * - 6M value
 * - 3M value
 * - 1M value
 * - 5D value
 * - candle widths
 * - candle colors
 * - percentage positions
 * - current count-up state
 *
 * ========================================================= */

function restoreStockDetailsPanel(
    card,
    detailsPanel
) {

    if (
        !card ||
        !detailsPanel
    ) {

        return;
    }


    /*
     * Prevent duplicate details panels.
     */

    const existingDetails =
        card.querySelector(
            ".stock-details-panel"
        );


    if (
        existingDetails &&
        existingDetails !== detailsPanel
    ) {

        existingDetails.remove();
    }


    /*
     * Move the EXISTING panel into the
     * newly rendered stock card.
     *
     * IMPORTANT:
     *
     * This does NOT recreate the panel.
     *
     * Therefore all existing DOM values,
     * classes and inline styles remain.
     */

    card.appendChild(
        detailsPanel
    );


    /*
     * Make sure the panel remains open.
     */

    detailsPanel.classList.add(
        "open"
    );


    /*
     * Remember the newly rendered card
     * as the currently open stock.
     */

    currentlyOpenStockDetails =
        card;
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
     * Stop any percentage count-up
     * animations running inside this panel.
     */

    stopPerformanceCountAnimations(
        detailsPanel
    );


    detailsPanel.classList.remove(
        "open"
    );


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
     * Find all performance values.
     */

    const periodValues =
        detailsPanel.querySelectorAll(
            ".stock-performance-value-left, " +
            ".stock-performance-value-right"
        );


    /*
     * Find all candles.
     */

    const periodCandles =
        detailsPanel.querySelectorAll(
            ".stock-performance-candle"
        );


    /*
     * Stop any previous count animations.
     */

    stopPerformanceCountAnimations(
        detailsPanel
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
                "negative",
                "neutral"
            );

            element.style.left =
                "33px";

        }
    );


    periodCandles.forEach(
        function(element) {

            element.classList.remove(
                "positive",
                "negative",
                "neutral",
                "visible"
            );

            element.style.width =
                "0%";

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

                element.classList.add(
                    "neutral"
                );

                element.style.left =
                    "40px";

            }
        );


        /*
         * Reset candles when API fails.
         */

        periodCandles.forEach(
            function(element) {

                element.classList.remove(
                    "positive",
                    "negative",
                    "visible"
                );

                element.classList.add(
                    "neutral"
                );

                element.style.width =
                    "0%";

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

        "5D",
        "1M",
        "3M",
        "6M",
        "1Y",
        "3Y",
        "5Y"

    ];


    /*
     * =====================================================
     * FIRST PASS
     * =====================================================
     */

    const calculatedReturns = {};


    let maxAbsoluteReturn = 0;


    periods.forEach(
        function(period) {

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

                calculatedReturns[period] =
                    null;

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


            calculatedReturns[period] =
                returnPercentage;


            const absoluteReturn =
                Math.abs(
                    returnPercentage
                );


            if (
                absoluteReturn >
                maxAbsoluteReturn
            ) {

                maxAbsoluteReturn =
                    absoluteReturn;
            }

        }
    );


    /*
     * =====================================================
     * SECOND PASS
     * =====================================================
     */

    periods.forEach(
        function(period) {

            const returnPercentage =
                calculatedReturns[period];


            const leftValue =
                detailsPanel.querySelector(
                    '[data-period-value-left="' +
                    period +
                    '"]'
                );


            const rightValue =
                detailsPanel.querySelector(
                    '[data-period-value-right="' +
                    period +
                    '"]'
                );


            const candle =
                detailsPanel.querySelector(
                    '[data-period-candle="' +
                    period +
                    '"]'
                );


            /*
             * Stop any previous animation
             * on these value elements.
             */

            stopPerformanceCountAnimation(
                leftValue
            );

            stopPerformanceCountAnimation(
                rightValue
            );


            /*
             * Clear both value positions.
             */

            if (leftValue) {

                leftValue.textContent =
                    "";

                leftValue.classList.remove(
                    "positive",
                    "negative",
                    "neutral"
                );

                leftValue.style.left =
                    "33px";
            }


            if (rightValue) {

                rightValue.textContent =
                    "";

                rightValue.classList.remove(
                    "positive",
                    "negative",
                    "neutral"
                );

                rightValue.style.left =
                    "33px";
            }


            /*
             * No historical price available.
             */

            if (
                returnPercentage ===
                null
            ) {

                if (rightValue) {

                    rightValue.textContent =
                        "N/A";

                    rightValue.classList.add(
                        "neutral"
                    );

                    rightValue.style.left =
                        "40px";
                }


                if (candle) {

                    candle.style.width =
                        "0%";

                    candle.classList.remove(
                        "positive",
                        "negative"
                    );

                    candle.classList.add(
                        "neutral"
                    );
                }


                return;
            }


            /*
             * Render percentage.
             */

            const formattedValue =
                formatReturnPercentage(
                    returnPercentage
                );


            /*
             * Positive = value at RIGHT
             * END of candle.
             */

            if (
                returnPercentage > 0
            ) {

                if (rightValue) {

                    rightValue.textContent =
                        formattedValue;

                    rightValue.classList.add(
                        "positive"
                    );
                }


                if (candle) {

                    candle.classList.remove(
                        "negative",
                        "neutral"
                    );

                    candle.classList.add(
                        "positive"
                    );

                }

            }


            /*
             * Negative = value at RIGHT
             * END of candle.
             */

            else if (
                returnPercentage < 0
            ) {

                if (leftValue) {

                    leftValue.textContent =
                        formattedValue;

                    leftValue.classList.add(
                        "negative"
                    );
                }


                if (candle) {

                    candle.classList.remove(
                        "positive",
                        "neutral"
                    );

                    candle.classList.add(
                        "negative"
                    );

                }

            }


            /*
             * Zero = neutral.
             */

            else {

                if (rightValue) {

                    rightValue.textContent =
                        formattedValue;

                    rightValue.classList.add(
                        "neutral"
                    );
                }


                if (candle) {

                    candle.classList.remove(
                        "positive",
                        "negative"
                    );

                    candle.classList.add(
                        "neutral"
                    );

                    candle.style.width =
                        "4%";

                    candle.classList.add(
                        "visible"
                    );


                    requestAnimationFrame(
                        function() {

                            positionPerformanceBubble(
                                rightValue,
                                candle,
                                candle.closest(
                                    ".stock-performance-track"
                                )
                            );

                        }
                    );

                }

                return;
            }


            /*
             * =================================================
             * SCALE CANDLE
             * =================================================
             *
             * Fixed common scale:
             *
             * 100% return = 92% candle width.
             *
             * This means different stocks are now visually
             * comparable.
             *
             * Minimum:
             * 8%
             *
             * Maximum:
             * 92%
             */

            if (
                candle &&
                maxAbsoluteReturn > 0
            ) {

                const absoluteReturn =
                    Math.abs(
                        returnPercentage
                    );


                const MAX_RETURN_SCALE =
                    100;


                let candleWidth =
                    (
                        absoluteReturn /
                        MAX_RETURN_SCALE
                    ) *
                    92;


                candleWidth =
                    Math.min(
                        92,
                        candleWidth
                    );


                candleWidth =
                    Math.max(
                        8,
                        Math.min(
                            92,
                            candleWidth
                        )
                    );


                /*
                 * Set target candle width.
                 */

                candle.style.width =
                    candleWidth + "%";


                candle.classList.add(
                    "visible"
                );


                /*
                 * =================================================
                 * POSITION VALUE AT CANDLE RIGHT EDGE
                 * =================================================
                 *
                 * IMPORTANT:
                 *
                 * Do NOT use candle.offsetWidth here.
                 *
                 * The candle has a CSS width transition.
                 *
                 * offsetWidth can therefore still contain
                 * the old width when this code runs.
                 *
                 * Instead positionPerformanceBubble()
                 * calculates the target endpoint from:
                 *
                 * track width × candle percentage
                 *
                 * This makes the percentage appear at the
                 * RIGHT END of the colored candle.
                 */

                requestAnimationFrame(
                    function() {

                        const track =
                            candle.closest(
                                ".stock-performance-track"
                            );


                        const valueElement =
                            returnPercentage > 0
                                ? rightValue
                                : leftValue;


                        positionPerformanceBubble(
                            valueElement,
                            candle,
                            track
                        );


                        /*
                         * Start the percentage count-up
                         * after the target position has
                         * been established.
                         */

                        animatePerformanceValue(
                            valueElement,
                            returnPercentage
                        );

                    }
                );

            }

        }
    );
}


/* =========================================================
   ANIMATE PERFORMANCE VALUE
   =========================================================
 *
 * Example:
 *
 * Final value:
 *
 * +64.00%
 *
 * The bubble will count:
 *
 * +1%
 * +2%
 * +3%
 * ...
 * +63%
 * +64%
 *
 * Negative values work the same way:
 *
 * -1%
 * -2%
 * -3%
 * ...
 * -64%
 *
 * The animation uses requestAnimationFrame so
 * it remains smooth on phones and desktop.
 *
 * ========================================================= */

function animatePerformanceValue(
    valueElement,
    finalValue
) {

    if (
        !valueElement ||
        !Number.isFinite(finalValue)
    ) {

        return;
    }


    stopPerformanceCountAnimation(
        valueElement
    );


    const finalAbsoluteValue =
        Math.abs(
            finalValue
        );


    if (
        finalAbsoluteValue <= 0
    ) {

        valueElement.textContent =
            formatReturnPercentage(
                finalValue
            );

        return;
    }


    const isNegative =
        finalValue < 0;


    valueElement.textContent =
        isNegative
            ? "-0.00%"
            : "+0.00%";


    const startTime =
        performance.now();


    function updateCount(
        currentTime
    ) {

        const elapsed =
            currentTime -
            startTime;


        const progress =
            Math.min(
                1,
                elapsed /
                STOCK_DETAILS_COUNT_DURATION
            );


        const easedProgress =
            1 -
            Math.pow(
                1 - progress,
                2
            );


        let currentValue =
            finalAbsoluteValue *
            easedProgress;


        currentValue =
            Math.min(
                finalAbsoluteValue,
                currentValue
            );


        valueElement.textContent =
            (
                isNegative
                    ? "-"
                    : "+"
            ) +
            currentValue.toFixed(2) +
            "%";


        if (
            progress < 1
        ) {

            valueElement._performanceCountFrame =
                requestAnimationFrame(
                    updateCount
                );

            return;
        }


        valueElement.textContent =
            formatReturnPercentage(
                finalValue
            );


        valueElement._performanceCountFrame =
            null;
    }


    valueElement._performanceCountFrame =
        requestAnimationFrame(
            updateCount
        );
}


/* =========================================================
   STOP ONE PERFORMANCE COUNT ANIMATION
   ========================================================= */

function stopPerformanceCountAnimation(
    valueElement
) {

    if (!valueElement) {

        return;
    }


    if (
        valueElement._performanceCountFrame
    ) {

        cancelAnimationFrame(
            valueElement._performanceCountFrame
        );


        valueElement._performanceCountFrame =
            null;
    }
}


/* =========================================================
   STOP ALL PERFORMANCE COUNT ANIMATIONS
   ========================================================= */

function stopPerformanceCountAnimations(
    detailsPanel
) {

    if (!detailsPanel) {

        return;
    }


    const valueElements =
        detailsPanel.querySelectorAll(
            ".stock-performance-value-left, " +
            ".stock-performance-value-right"
        );


    valueElements.forEach(
        function(element) {

            stopPerformanceCountAnimation(
                element
            );

        }
    );
}


/* =========================================================
   POSITION PERFORMANCE VALUE BUBBLE
   =========================================================
 *
 * IMPORTANT:
 *
 * The percentage must be positioned at the
 * RIGHT END of the colored candle.
 *
 * We calculate the target candle endpoint from
 * the candle's percentage width instead of using
 * candle.offsetWidth immediately after changing
 * the width.
 *
 * ========================================================= */

function positionPerformanceBubble(
    valueElement,
    candle,
    track
) {

    if (
        !valueElement ||
        !candle ||
        !track
    ) {

        return;
    }


    /*
     * Read the percentage that was assigned
     * to the candle.
     *
     * Example:
     *
     * "65.4%" -> 65.4
     */

    const candlePercentage =
        parseFloat(
            candle.style.width
        );


    if (
        !Number.isFinite(
            candlePercentage
        )
    ) {

        return;
    }


    /*
     * The candle starts at the LEFT edge
     * of the track.
     *
     * clientWidth gives the usable track width
     * without the border.
     */

    const trackWidth =
        track.clientWidth;


    if (
        trackWidth <= 0
    ) {

        return;
    }


    /*
     * Calculate the target candle width.
     *
     * This is the width the candle is
     * animating toward.
     */

    const candleTargetWidth =
        (
            trackWidth *
            candlePercentage
        ) /
        100;


    /*
     * Find the track's position relative
     * to the performance row.
     */

    const row =
        valueElement.closest(
            ".stock-performance-row"
        );


    if (!row) {

        return;
    }


    const trackRect =
        track.getBoundingClientRect();


    const rowRect =
        row.getBoundingClientRect();


    /*
     * Calculate the RIGHT EDGE of the candle.
     *
     * Track left
     * +
     * target candle width
     */

    const candleRight =
        (
            trackRect.left -
            rowRect.left
        ) +
        candleTargetWidth;


    /*
     * Because the bubble is centered using:
     *
     * translate(-50%, -50%)
     *
     * we need half of the bubble width
     * when keeping it inside the track.
     */

    const bubbleWidth =
        valueElement.offsetWidth;


    const halfBubbleWidth =
        bubbleWidth / 2;


    /*
     * Track boundaries relative to row.
     */

    const trackLeft =
        trackRect.left -
        rowRect.left;


    const trackRight =
        trackRect.right -
        rowRect.left;


    /*
     * Keep the bubble inside the available
     * performance track when the candle gets
     * very close to the right edge.
     */

    const minimumCenter =
        trackLeft +
        halfBubbleWidth;


    const maximumCenter =
        trackRight -
        halfBubbleWidth;


    const bubbleCenter =
        Math.max(
            minimumCenter,
            Math.min(
                maximumCenter,
                candleRight
            )
        );


    /*
     * FINAL POSITION
     *
     * This is the important part.
     *
     * The percentage bubble is now placed
     * at the RIGHT END of the colored candle.
     */

    valueElement.style.left =
        bubbleCenter + "px";
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

//PERFECT
