
/* =========================================================
   DIVIDEND UI RENDERING
   =========================================================
 *
 * This file ONLY handles rendering the dividend cards.
 *
 * It does NOT:
 *
 * - call /api/dividends
 * - call /api/dividends/version
 * - perform searches
 * - perform automatic refresh
 * - perform pull-to-refresh
 * - modify favorites storage
 * - change currentSort
 * - change currentInvestment
 *
 * app.js remains responsible for all of that.
 *
 *
 * PAGINATION:
 *
 * app.js prepares the COMPLETE sorted/filtered dataset.
 *
 * This file then displays only 50 records
 * for the current page.
 */


/* =========================================================
   FAVORITE RAPID-CLICK STAR SHATTER
   =========================================================
 *
 * Behavior:
 *
 * - 15 favorite/unfavorite operations within 4 seconds
 *   triggers the animation.
 *
 * - ONE yellow star appears.
 *
 * - The star breaks into many tiny pieces.
 *
 * - Pieces fly mostly UPWARD.
 *
 * - Only a small amount of left/right movement.
 *
 * - No extra star characters are created.
 *
 * - Animation lasts 3 seconds.
 *
 * - Favorite button stays hidden for 8 seconds.
 *
 * - The effect can be triggered repeatedly.
 */


/* =========================================================
   FAVORITE ANIMATION CONFIGURATION
   ========================================================= */

const FAVORITE_BURST_CLICK_LIMIT = 15;

const FAVORITE_BURST_TIME_WINDOW = 4000;

const FAVORITE_BURST_ANIMATION_DURATION = 4000;

const FAVORITE_BURST_BUTTON_COOLDOWN = 8000;


/* =========================================================
   GLOBAL FAVORITE BURST STATE
   =========================================================
 *
 * Stored on window so re-rendering the stock cards
 * does not reset the rapid-click tracking.
 */

if (
    !window.__favoriteBurstState
) {

    window.__favoriteBurstState = {

        clicks: new Map(),

        cooldowns: new Map(),

        cssLoaded: false
    };
}


/* =========================================================
   CREATE FAVORITE ANIMATION CSS
   ========================================================= */

function ensureFavoriteBurstCSS() {

    if (
        window.__favoriteBurstState.cssLoaded
    ) {

        return;
    }


    const style =
        document.createElement(
            "style"
        );


    style.id =
        "favorite-star-shatter-style";


    style.textContent = `

        /* =================================================
           ANIMATION CONTAINER
           ================================================= */

        .favorite-burst-container {

            position: fixed;

            left: 0;

            top: 0;

            width: 0;

            height: 0;

            z-index: 999999;

            pointer-events: none;
        }


        /* =================================================
           ORIGINAL SINGLE STAR
           ================================================= */

        .favorite-burst-main-star {

            position: absolute;

            left: 0;

            top: 0;

            transform:
                translate(-50%, -50%)
                scale(0);

            font-size: 56px;

            line-height: 1;

            color: #ffd700;

            text-shadow:
                0 0 6px rgba(255, 215, 0, 0.95),
                0 0 14px rgba(255, 215, 0, 0.70),
                0 0 25px rgba(255, 215, 0, 0.45);

            animation:
                favoriteStarAppearAndBreak
                850ms
                ease-out
                forwards;
        }


        /* =================================================
           BROKEN STAR PIECE
           ================================================= */

        .favorite-star-fragment {

            position: absolute;

            left: 0;

            top: 0;

            width: 7px;

            height: 7px;

            background:
                linear-gradient(
                    135deg,
                    #fff7a8,
                    #ffd700,
                    #f5b400
                );

            box-shadow:
                0 0 5px rgba(255, 215, 0, 0.80);

            opacity: 0;

            transform:
                translate(-50%, -50%)
                translate(0, 0)
                rotate(0deg)
                scale(0.2);

            animation:
                favoriteStarFragmentRise
                4000ms
                cubic-bezier(.25,.65,.35,1)
                forwards;
        }


        /* =================================================
           ORIGINAL STAR ANIMATION
           ================================================= */

        @keyframes favoriteStarAppearAndBreak {

            0% {

                opacity: 0;

                transform:
                    translate(-50%, -50%)
                    scale(0);
            }


            15% {

                opacity: 1;

                transform:
                    translate(-50%, -50%)
                    scale(1);
            }


            35% {

                opacity: 1;

                transform:
                    translate(-50%, -50%)
                    scale(1.08);
            }


            55% {

                opacity: 1;

                transform:
                    translate(-50%, -50%)
                    scale(1);
            }


            /*
             * The star remains visible briefly,
             * then disappears as if breaking apart.
             */

            65% {

                opacity: 1;

                transform:
                    translate(-50%, -50%)
                    scale(1.02);
            }


            72% {

                opacity: 0;

                transform:
                    translate(-50%, -50%)
                    scale(1.12);
            }


            100% {

                opacity: 0;

                transform:
                    translate(-50%, -50%)
                    scale(1.12);
            }
        }


        /* =================================================
           BROKEN PIECES FLY UPWARD
           ================================================= */

        @keyframes favoriteStarFragmentRise {

            0% {

                opacity: 0;

                transform:
                    translate(-50%, -50%)
                    translate(
                        var(--start-x),
                        var(--start-y)
                    )
                    rotate(0deg)
                    scale(0.2);
            }


            /*
             * Pieces appear at the moment
             * the original star breaks.
             */

            15% {

                opacity: 1;

                transform:
                    translate(-50%, -50%)
                    translate(
                        var(--start-x),
                        var(--start-y)
                    )
                    rotate(
                        var(--start-rotation)
                    )
                    scale(1);
            }


            /*
             * Stay visible while slowly
             * moving upward.
             */

            55% {

                opacity: 0.85;
            }


            /*
             * Start fading.
             */

            78% {

                opacity: 0.55;
            }


            /*
             * Finish high above the button.
             */

            100% {

                opacity: 0;

                transform:
                    translate(-50%, -50%)
                    translate(
                        var(--end-x),
                        var(--end-y)
                    )
                    rotate(
                        var(--end-rotation)
                    )
                    scale(0.15);
            }
        }


        /* =================================================
           FAVORITE BUTTON 8 SECOND COOLDOWN
           ================================================= */

        .favorite-button.favorite-burst-cooldown {

            visibility: hidden !important;

            opacity: 0 !important;

            pointer-events: none !important;
        }

    `;


    document.head.appendChild(
        style
    );


    window.__favoriteBurstState.cssLoaded =
        true;
}


/* =========================================================
   CREATE ONE BROKEN STAR PIECE
   ========================================================= */

function createFavoriteStarFragment(
    container
) {

    const fragment =
        document.createElement(
            "span"
        );


    fragment.className =
        "favorite-star-fragment";


    /*
     * Pieces begin very close to
     * the original star.
     */

    const startX =
        (
            Math.random() *
            22
        ) -
        11;


    const startY =
        (
            Math.random() *
            22
        ) -
        11;


    /*
     * IMPORTANT:
     *
     * Pieces move mostly UP.
     *
     * Horizontal movement is intentionally
     * limited so it does not look like an
     * explosion.
     */

    const endX =
        (
            Math.random() *
            100
        ) -
        50;


    const endY =
        -(
            80 +
            Math.random() *
            190
        );


    const startRotation =
        (
            Math.random() *
            80
        ) -
        40;


    const endRotation =
        (
            Math.random() *
            500
        ) -
        250;


    fragment.style.setProperty(
        "--start-x",
        `${startX}px`
    );


    fragment.style.setProperty(
        "--start-y",
        `${startY}px`
    );


    fragment.style.setProperty(
        "--end-x",
        `${endX}px`
    );


    fragment.style.setProperty(
        "--end-y",
        `${endY}px`
    );


    fragment.style.setProperty(
        "--start-rotation",
        `${startRotation}deg`
    );


    fragment.style.setProperty(
        "--end-rotation",
        `${endRotation}deg`
    );


    /*
     * Pieces start at slightly different
     * times so the break looks natural.
     */

    const delay =
        Math.random() *
        220;


    fragment.style.animationDelay =
        `${delay}ms`;


    /*
     * Random fragment size.
     */

    const size =
        4 +
        Math.random() *
        7;


    fragment.style.width =
        `${size}px`;


    fragment.style.height =
        `${size}px`;


    /*
     * Different geometric shapes make
     * the pieces look broken.
     */

    const shape =
        Math.floor(
            Math.random() * 5
        );


    if (
        shape === 0
    ) {

        fragment.style.clipPath =
            "polygon(50% 0%, 100% 100%, 0% 100%)";

    } else if (
        shape === 1
    ) {

        fragment.style.clipPath =
            "polygon(0% 0%, 100% 35%, 75% 100%, 0% 75%)";

    } else if (
        shape === 2
    ) {

        fragment.style.clipPath =
            "polygon(25% 0%, 100% 25%, 75% 100%, 0% 75%)";

    } else if (
        shape === 3
    ) {

        fragment.style.clipPath =
            "polygon(50% 0%, 100% 50%, 50% 100%, 0% 50%)";

    } else {

        fragment.style.clipPath =
            "polygon(0% 20%, 80% 0%, 100% 80%, 20% 100%)";
    }


    container.appendChild(
        fragment
    );
}


/* =========================================================
   PLAY FAVORITE STAR SHATTER
   ========================================================= */

function playFavoriteStarBurst(
    favoriteButton
) {

    if (
        !favoriteButton
    ) {

        return;
    }


    ensureFavoriteBurstCSS();


    /*
     * Find exact position of the
     * favorite button.
     */

    const rect =
        favoriteButton.getBoundingClientRect();


    const centerX =
        rect.left +
        rect.width /
        2;


    const centerY =
        rect.top +
        rect.height /
        2;


    /*
     * Create animation container.
     */

    const container =
        document.createElement(
            "div"
        );


    container.className =
        "favorite-burst-container";


    container.style.left =
        `${centerX}px`;


    container.style.top =
        `${centerY}px`;


    document.body.appendChild(
        container
    );


    /* =====================================================
       ONE ORIGINAL STAR
       ===================================================== */

    const mainStar =
        document.createElement(
            "span"
        );


    mainStar.className =
        "favorite-burst-main-star";


    mainStar.textContent =
        "★";


    container.appendChild(
        mainStar
    );


    /* =====================================================
       CREATE BROKEN PIECES
       =====================================================
     *
     * These are small geometric fragments.
     *
     * They are NOT additional stars.
     */

    const fragmentCount =
        65;


    for (
        let i = 0;
        i < fragmentCount;
        i++
    ) {

        createFavoriteStarFragment(
            container
        );
    }


    /*
     * Remove the animation after
     * the complete 3-second animation.
     */

    setTimeout(
        function() {

            if (
                container &&
                container.parentNode
            ) {

                container.parentNode.removeChild(
                    container
                );
            }

        },
        FAVORITE_BURST_ANIMATION_DURATION + 250
    );
}


/* =========================================================
   START FAVORITE BUTTON COOLDOWN
   ========================================================= */

function startFavoriteButtonCooldown(
    favoriteButton,
    favoriteKey
) {

    if (
        !favoriteButton
    ) {

        return;
    }


    const cooldowns =
        window.__favoriteBurstState.cooldowns;


    /*
     * Clear any existing timer.
     */

    const existingTimer =
        cooldowns.get(
            favoriteKey
        );


    if (
        existingTimer
    ) {

        clearTimeout(
            existingTimer
        );
    }


    /*
     * Hide button immediately.
     */

    favoriteButton.classList.add(
        "favorite-burst-cooldown"
    );


    /*
     * Keep it hidden for 8 seconds.
     */

    const timer =
        setTimeout(
            function() {

                cooldowns.delete(
                    favoriteKey
                );


                /*
                 * The card may still exist.
                 */

                if (
                    favoriteButton &&
                    favoriteButton.isConnected
                ) {

                    favoriteButton.classList.remove(
                        "favorite-burst-cooldown"
                    );
                }

            },
            FAVORITE_BURST_BUTTON_COOLDOWN
        );


    cooldowns.set(
        favoriteKey,
        timer
    );
}


/* =========================================================
   SYNC COOLDOWN AFTER RE-RENDER
   ========================================================= */

function syncFavoriteButtonCooldown(
    favoriteButton,
    favoriteKey
) {

    if (
        !favoriteButton
    ) {

        return;
    }


    const cooldowns =
        window.__favoriteBurstState.cooldowns;


    if (
        cooldowns.has(
            favoriteKey
        )
    ) {

        favoriteButton.classList.add(
            "favorite-burst-cooldown"
        );
    }
}


/* =========================================================
   RECORD FAVORITE CLICK
   ========================================================= */

function recordFavoriteClick(
    favoriteKey,
    favoriteButton
) {

    const now =
        Date.now();


    const clicks =
        window.__favoriteBurstState.clicks;


    let timestamps =
        clicks.get(
            favoriteKey
        );


    if (
        !timestamps
    ) {

        timestamps = [];

        clicks.set(
            favoriteKey,
            timestamps
        );
    }


    /*
     * Remove clicks older than 4 seconds.
     */

    timestamps =
        timestamps.filter(
            function(timestamp) {

                return (
                    now -
                    timestamp
                ) <=
                    FAVORITE_BURST_TIME_WINDOW;
            }
        );


    /*
     * Record current click.
     */

    timestamps.push(
        now
    );


    clicks.set(
        favoriteKey,
        timestamps
    );


    /*
     * 15 operations within 4 seconds.
     */

    if (
        timestamps.length >=
        FAVORITE_BURST_CLICK_LIMIT
    ) {

        /*
         * Reset immediately.
         *
         * This allows another burst later.
         */

        clicks.delete(
            favoriteKey
        );


        /*
         * Play the broken-star animation.
         */

        playFavoriteStarBurst(
            favoriteButton
        );


        /*
         * Hide the button for 8 seconds.
         */

        startFavoriteButtonCooldown(
            favoriteButton,
            favoriteKey
        );
    }
}


/* =========================================================
   HIGHEST EXPECTED DIVIDEND
   ========================================================= */

function getHighestExpectedDividend(
    data,
    investment
) {

    if (
        !Array.isArray(data) ||
        data.length === 0 ||
        investment === null ||
        investment <= 0
    ) {

        return null;
    }


    let highestValue =
        -1;


    data.forEach(
        function(stock) {

            const price =
                Number(
                    stock.current_share_price
                );


            const dividend =
                Number(
                    stock.dividend_amount
                );


            if (
                isNaN(price) ||
                price <= 0 ||
                isNaN(dividend) ||
                dividend < 0
            ) {

                return;
            }


            const shares =
                Math.floor(
                    investment /
                    price
                );


            if (shares <= 0) {
                return;
            }


            const expected =
                shares *
                dividend;


            if (
                expected >
                highestValue
            ) {

                highestValue =
                    expected;
            }
        }
    );


    if (
        highestValue < 0
    ) {

        return null;
    }


    return highestValue;
}


/* =========================================================
   PAGINATION HELPERS
   ========================================================= */

function getTotalPages(
    totalRecords
) {

    if (
        !totalRecords ||
        totalRecords <= 0
    ) {

        return 0;
    }


    return Math.ceil(
        totalRecords /
        PAGE_SIZE
    );
}


/* =========================================================
   PAGINATION BUTTON
   ========================================================= */

function createPaginationButton(
    label,
    page,
    disabled,
    active,
    extraClass = ""
) {

    const button =
        document.createElement(
            "button"
        );


    button.type =
        "button";


    button.className =
        "pagination-button";


    if (extraClass) {

        button.classList.add(
            extraClass
        );
    }


    if (active) {

        button.classList.add(
            "active"
        );
    }


    button.disabled =
        disabled;


    button.textContent =
        label;


    if (!disabled) {

        button.addEventListener(
            "click",
            function() {

                goToPage(
                    page
                );
            }
        );
    }


    return button;
}


/* =========================================================
   PAGINATION
   ========================================================= */

function renderPagination(
    totalRecords
) {

    const pagination =
        document.getElementById(
            "pagination"
        );


    if (!pagination) {
        return;
    }


    pagination.innerHTML =
        "";


    const totalPages =
        getTotalPages(
            totalRecords
        );


    if (
        totalPages <= 1
    ) {

        pagination.classList.add(
            "hidden"
        );

        return;
    }


    /*
     * Safety:
     *
     * If automatic refresh causes the
     * number of pages to decrease,
     * make sure currentPage remains valid.
     */

    if (
        currentPage >
        totalPages
    ) {

        currentPage =
            totalPages;
    }


    if (
        currentPage < 1
    ) {

        currentPage =
            1;
    }


    pagination.classList.remove(
        "hidden"
    );


    /* =====================================================
       PREVIOUS
       ===================================================== */

    pagination.appendChild(
        createPaginationButton(
            "‹ Previous",
            currentPage - 1,
            currentPage === 1,
            false,
            "previous"
        )
    );


    /*
     * For your current 300+ shares this will normally
     * produce around 6-7 page buttons.
     *
     * The logic below also keeps the UI manageable
     * if the dataset becomes much larger later.
     */


    if (
        totalPages <= 7
    ) {

        for (
            let page = 1;
            page <= totalPages;
            page++
        ) {

            pagination.appendChild(
                createPaginationButton(
                    String(page),
                    page,
                    false,
                    page === currentPage
                )
            );
        }

    } else {

        /*
         * Always show page 1.
         */

        pagination.appendChild(
            createPaginationButton(
                "1",
                1,
                false,
                currentPage === 1
            )
        );


        /*
         * Left ellipsis.
         */

        if (
            currentPage > 4
        ) {

            const ellipsis =
                document.createElement(
                    "span"
                );


            ellipsis.className =
                "pagination-ellipsis";


            ellipsis.textContent =
                "…";


            pagination.appendChild(
                ellipsis
            );
        }


        /*
         * Pages around the current page.
         */

        let startPage =
            Math.max(
                2,
                currentPage - 1
            );


        let endPage =
            Math.min(
                totalPages - 1,
                currentPage + 1
            );


        if (
            currentPage <= 3
        ) {

            startPage =
                2;

            endPage =
                4;

        } else if (
            currentPage >=
            totalPages - 2
        ) {

            startPage =
                totalPages - 3;

            endPage =
                totalPages - 1;
        }


        for (
            let page = startPage;
            page <= endPage;
            page++
        ) {

            if (
                page <= 1 ||
                page >= totalPages
            ) {

                continue;
            }


            pagination.appendChild(
                createPaginationButton(
                    String(page),
                    page,
                    false,
                    page === currentPage
                )
            );
        }


        /*
         * Right ellipsis.
         */

        if (
            currentPage <
            totalPages - 3
        ) {

            const ellipsis =
                document.createElement(
                    "span"
                );


            ellipsis.className =
                "pagination-ellipsis";


            ellipsis.textContent =
                "…";


            pagination.appendChild(
                ellipsis
            );
        }


        /*
         * Always show the last page.
         */

        pagination.appendChild(
            createPaginationButton(
                String(totalPages),
                totalPages,
                false,
                currentPage === totalPages
            )
        );
    }


    /* =====================================================
       NEXT
       ===================================================== */

    pagination.appendChild(
        createPaginationButton(
            "Next ›",
            currentPage + 1,
            currentPage === totalPages,
            false,
            "next"
        )
    );


    /* =====================================================
       PAGE INFORMATION
       ===================================================== */

    const startRecord =
        (
            (currentPage - 1) *
            PAGE_SIZE
        ) + 1;


    const endRecord =
        Math.min(
            currentPage *
            PAGE_SIZE,
            totalRecords
        );


    const info =
        document.createElement(
            "div"
        );


    info.className =
        "pagination-info";


    info.textContent =
        `Showing ${startRecord}-${endRecord} of ${totalRecords} shares`;


    pagination.appendChild(
        info
    );
}


/* =========================================================
   RENDER STOCKS
   ========================================================= */

function renderStocks(
    data,
    investment,
    allData = dividendData
) {

    /*
     * Make sure favorite animation CSS
     * has been loaded.
     */

    ensureFavoriteBurstCSS();


    stockList.innerHTML = "";


    /*
     * No records after filtering/sorting.
     */

    if (
        !Array.isArray(data) ||
        data.length === 0
    ) {

        empty.classList.remove(
            "hidden"
        );


        const pagination =
            document.getElementById(
                "pagination"
            );


        if (pagination) {

            pagination.innerHTML = "";

            pagination.classList.add(
                "hidden"
            );
        }


        if (
            currentSort ===
            "favorites"
        ) {

            resultMessage.textContent =
                "No favorite companies selected.";

        } else {

            resultMessage.textContent =
                "No dividends found for the selected dates.";
        }


        return;
    }


    empty.classList.add(
        "hidden"
    );


    /*
     * IMPORTANT:
     *
     * data:
     *     COMPLETE filtered + sorted dataset.
     *
     * allData:
     *     COMPLETE API dataset.
     *
     * Pagination happens BELOW this point.
     *
     * Highest Expected Dividend is calculated
     * using allData BEFORE pagination.
     */

    const highestExpectedDividend =
        getHighestExpectedDividend(
            allData,
            investment
        );


    /* =====================================================
       PAGINATION CALCULATION
       ===================================================== */

    const totalRecords =
        data.length;


    const totalPages =
        getTotalPages(
            totalRecords
        );


    /*
     * Protect against an invalid current page.
     */

    if (
        currentPage >
        totalPages
    ) {

        currentPage =
            totalPages;
    }


    if (
        currentPage < 1
    ) {

        currentPage =
            1;
    }


    const startIndex =
        (
            currentPage - 1
        ) *
        PAGE_SIZE;


    const endIndex =
        Math.min(
            startIndex +
            PAGE_SIZE,
            totalRecords
        );


    /*
     * ONLY THESE RECORDS ARE RENDERED.
     *
     * data itself remains untouched.
     */

    const pageData =
        data.slice(
            startIndex,
            endIndex
        );


    /* =====================================================
       RENDER CURRENT PAGE
       ===================================================== */

    pageData.forEach(
        function(stock, index) {

            const price =
                Number(
                    stock.current_share_price
                );


            const dividend =
                Number(
                    stock.dividend_amount
                );


            let shares = 1;


            let expectedDividend =
                isNaN(dividend)
                    ? 0
                    : dividend;


            if (
                investment !== null &&
                investment > 0 &&
                !isNaN(price) &&
                price > 0 &&
                !isNaN(dividend) &&
                dividend >= 0
            ) {

                shares =
                    Math.floor(
                        investment /
                        price
                    );


                expectedDividend =
                    shares *
                    dividend;
            }


            const tomorrow =
                isTomorrow(
                    stock.ex_dividend_date
                );


            const isHighestExpectedDividend =
                highestExpectedDividend !== null &&
                shares > 0 &&
                expectedDividend ===
                    highestExpectedDividend;


            const card =
                document.createElement(
                    "div"
                );


            let cardClass =
                "stock-card";


            if (tomorrow) {

                cardClass +=
                    " tomorrow-card";
            }


            if (
                isHighestExpectedDividend
            ) {

                cardClass +=
                    " highest-dividend-card";
            }


            card.className =
                cardClass;


            /*
             * Animation starts from zero for
             * every page.
             */

            card.style.animationDelay =
                `${index * 0.06}s`;


            const favoriteKey =
                getFavoriteKey(stock);


            const isFavorite =
                favorites.has(
                    favoriteKey
                );


            card.innerHTML = `

                <button
                    type="button"
                    class="favorite-button ${
                        isFavorite
                            ? "is-favorite"
                            : ""
                    }"
                    aria-label="${
                        isFavorite
                            ? "Remove favorite"
                            : "Add favorite"
                    }"
                    title="${
                        isFavorite
                            ? "Remove favorite"
                            : "Add favorite"
                    }">

                    ${
                        isFavorite
                            ? "★"
                            : "☆"
                    }

                </button>


                <div class="company-name">

                    <span>

                        ${
                            stock.share_name ||
                            "Unknown Company"
                        }

                    </span>

                </div>


                <div class="symbol">

                    ${
                        stock.symbol ||
                        ""
                    }

                </div>


                ${
                    tomorrow
                        ? `
                            <div class="tomorrow-badge">

                                💵 Last Chance for Dividend

                            </div>
                          `
                        : ""
                }


                ${
                    isHighestExpectedDividend
                        ? `
                            <div class="highest-dividend-badge">

                                🏆 Highest Expected Dividend

                            </div>
                          `
                        : ""
                }


                <div class="main-result">

                    <div class="expected">

                        <div class="expected-value">

                            💰
                            ${
                                formatMoney(
                                    expectedDividend
                                )
                            }

                        </div>


                        <div class="expected-label">

                            Expected Dividend ·
                            ${
                                shares.toLocaleString(
                                    "en-IN"
                                )
                            }
                            ${
                                shares === 1
                                    ? "share"
                                    : "shares"
                            }

                        </div>

                    </div>


                    <div class="ex-date">

                        <div class="ex-date-value">

                            📅
                            ${
                                formatExDividendDate(
                                    stock.ex_dividend_date
                                )
                            }

                        </div>


                        <div class="ex-date-label">

                            Ex-Dividend Date

                        </div>

                    </div>

                </div>


                <div class="metrics">

                    <div class="metric">

                        <div class="metric-value">

                            💵
                            ${
                                formatMoney(
                                    stock.dividend_amount
                                )
                            }

                        </div>


                        <div class="metric-label">

                            Dividend / Share

                        </div>

                    </div>


                    <div class="metric">

                        <div class="metric-value">

                            📈
                            ${
                                formatMoney(
                                    stock.current_share_price
                                )
                            }

                        </div>


                        <div class="metric-label">

                            Current Price

                        </div>

                    </div>

                </div>

            `;


            const favoriteButton =
                card.querySelector(
                    ".favorite-button"
                );


            /*
             * If this company is already
             * in the 8-second cooldown,
             * keep the newly rendered button hidden.
             */

            syncFavoriteButtonCooldown(
                favoriteButton,
                favoriteKey
            );


            favoriteButton.addEventListener(
                "click",
                function(event) {

                    event.preventDefault();

                    event.stopPropagation();


                    /*
                     * Do not allow clicks while
                     * the 8-second cooldown is active.
                     */

                    if (
                        favoriteButton.classList.contains(
                            "favorite-burst-cooldown"
                        )
                    ) {

                        return;
                    }


                    /*
                     * EXISTING FAVORITE LOGIC
                     *
                     * This remains responsible for:
                     *
                     * - adding favorite
                     * - removing favorite
                     * - localStorage
                     * - existing favorite UI behavior
                     */

                    toggleFavorite(
                        stock,
                        favoriteButton
                    );


                    /*
                     * NEW RAPID CLICK DETECTOR
                     */

                    recordFavoriteClick(
                        favoriteKey,
                        favoriteButton
                    );
                }
            );


            stockList.appendChild(
                card
            );
        }
    );


    /*
     * Render pagination AFTER the current
     * page's 50 cards have been rendered.
     */

    renderPagination(
        totalRecords
    );
}
