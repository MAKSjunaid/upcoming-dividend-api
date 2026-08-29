
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
     *
     * Example:
     *
     * User is on page 7.
     *
     * Automatic refresh reduces the data
     * from 327 shares to 215 shares.
     *
     * Page 7 no longer exists.
     *
     * We automatically move to page 5.
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
             *
             * This means page 2 will animate
             * nicely when opened.
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


            favoriteButton.addEventListener(
                "click",
                function(event) {

                    event.preventDefault();

                    event.stopPropagation();


                    toggleFavorite(
                        stock,
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
