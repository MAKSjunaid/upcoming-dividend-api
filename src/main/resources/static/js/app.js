const fromDate =
    document.getElementById("fromDate");

const toDate =
    document.getElementById("toDate");

const fromDateDisplay =
    document.getElementById("fromDateDisplay");

const toDateDisplay =
    document.getElementById("toDateDisplay");

const investmentAmount =
    document.getElementById("investmentAmount");

const searchSummary =
    document.getElementById("searchSummary");

const searchForm =
    document.getElementById("searchForm");

const editButton =
    document.getElementById("editButton");

const searchButton =
    document.getElementById("searchButton");

const dateSummary =
    document.getElementById("dateSummary");

const investmentSummary =
    document.getElementById("investmentSummary");

const stockList =
    document.getElementById("stockList");

const loading =
    document.getElementById("loading");

const loadingMessage =
    document.getElementById("loadingMessage");

const filterError =
    document.getElementById("filterError");

const empty =
    document.getElementById("empty");

const resultMessage =
    document.getElementById("resultMessage");

const pullRefresh =
    document.getElementById("pullRefresh");

const pullRefreshIcon =
    document.getElementById("pullRefreshIcon");

const pullRefreshText =
    document.getElementById("pullRefreshText");

const sortFilter =
    document.getElementById("sortFilter");

const stickyHeader =
    document.querySelector(".sticky-header");

const favoriteFlyStar =
    document.getElementById("favoriteFlyStar");


/* =========================================================
   DATA / UI STATE
   ========================================================= */

let dividendData = [];

let currentInvestment = null;

let currentSort = "dividendDesc";

let loadingMessageTimer = null;


/* =========================================================
   PAGINATION
   ========================================================= */

/*
 * Number of shares displayed on one page.
 *
 * IMPORTANT:
 *
 * dividendData still contains ALL shares.
 *
 * Pagination only controls how many cards
 * are rendered on the screen.
 */

const PAGE_SIZE = 30;

let currentPage = 1;


/* =========================================================
   VERSIONED AUTOMATIC REFRESH
   ========================================================= */

const AUTO_REFRESH_INTERVAL =
    60 * 1000;

let autoRefreshTimer = null;

let autoRefreshing = false;

let currentDataVersion = null;


/* =========================================================
   FAVORITES
   ========================================================= */

let favorites = new Set(
    JSON.parse(
        localStorage.getItem(
            "dividendFavorites"
        ) || "[]"
    )
);


function saveFavorites() {

    localStorage.setItem(
        "dividendFavorites",
        JSON.stringify(
            Array.from(favorites)
        )
    );
}


function getFavoriteKey(stock) {

    if (
        stock.symbol &&
        String(stock.symbol).trim()
    ) {

        return String(
            stock.symbol
        ).trim();
    }


    return String(
        stock.share_name ||
        ""
    ).trim();
}


/* =========================================================
   LOADING ANIMATION MESSAGES
   ========================================================= */

function startLoadingAnimation() {

    if (!loadingMessage) {
        return;
    }


    const messages = [

        "Loading… blame the internet...",

        "Asking the database nicely...",

        "Convincing the server this is important...",

        "Loading screen or a meditation session?",

        "Scanning upcoming dividends...",

        "Checking ex-dividend dates...",

        "Finding dividend opportunities...",

        "Calculating potential rewards...",

        "Checking the stock market...",

        "Looking for money-making opportunities...",

        "Preparing your dividend list...",

        "Almost there...",

        "Brain updated, Next time it won’t happen..."

    ];


    let index = 0;


    loadingMessage.textContent =
        messages[index];


    clearInterval(
        loadingMessageTimer
    );


    loadingMessageTimer =
        setInterval(
            function() {

                if (
                    loadingMessage
                ) {

                    loadingMessage.style.opacity =
                        "0";


                    setTimeout(
                        function() {

                            index =
                                (
                                    index + 1
                                ) %
                                messages.length;


                            loadingMessage.textContent =
                                messages[index];


                            loadingMessage.style.opacity =
                                "1";

                        },
                        180
                    );
                }

            },
            1700
        );
}


function stopLoadingAnimation() {

    clearInterval(
        loadingMessageTimer
    );


    loadingMessageTimer =
        null;


    if (loadingMessage) {

        loadingMessage.style.opacity =
            "1";
    }
}


/* =========================================================
   STICKY HEADER
   ========================================================= */

function updateStickyHeader() {

    const scrollY =
        window.scrollY;

    const SHRINK_POINT =
        30;

    const EXPAND_POINT =
        5;


    if (!stickyHeader) {
        return;
    }


    if (
        !stickyHeader.classList.contains("scrolled") &&
        scrollY > SHRINK_POINT
    ) {

        stickyHeader.classList.add(
            "scrolled"
        );

    } else if (
        stickyHeader.classList.contains("scrolled") &&
        scrollY < EXPAND_POINT
    ) {

        stickyHeader.classList.remove(
            "scrolled"
        );
    }
}


window.addEventListener(
    "scroll",
    updateStickyHeader,
    {
        passive: true
    }
);


updateStickyHeader();


/* =========================================================
   DATE FUNCTIONS
   ========================================================= */

function getOrdinal(day) {

    if (
        day >= 11 &&
        day <= 13
    ) {

        return "th";
    }


    switch (day % 10) {

        case 1:
            return "st";

        case 2:
            return "nd";

        case 3:
            return "rd";

        default:
            return "th";
    }
}


function formatDate(date) {

    if (!date) {
        return "";
    }


    const parts =
        date.split("-");


    if (
        parts.length !== 3
    ) {

        return date;
    }


    const year =
        parts[0];

    const month =
        Number(parts[1]);

    const day =
        Number(parts[2]);


    const months = [

        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec"

    ];


    return (
        day +
        getOrdinal(day) +
        "-" +
        months[month - 1] +
        "-" +
        year
    );
}


function formatFilterDate(date) {

    if (!date) {
        return "";
    }


    const parts =
        date.split("-");


    if (
        parts.length !== 3
    ) {

        return date;
    }


    const year =
        parts[0];

    const month =
        Number(parts[1]);

    const day =
        Number(parts[2]);


    const months = [

        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec"

    ];


    return (
        String(day).padStart(2, "0") +
        "-" +
        months[month - 1] +
        "-" +
        year
    );
}


function formatApiDate(date) {

    if (!date) {
        return "";
    }


    const parts =
        date.split("-");


    if (
        parts.length !== 3
    ) {

        return "";
    }


    return (
        parts[2] +
        "-" +
        parts[1] +
        "-" +
        parts[0]
    );
}


function formatExDividendDate(date) {

    if (!date) {
        return "N/A";
    }


    const parts =
        date.trim().split("-");


    if (
        parts.length !== 3
    ) {

        return date;
    }


    const day =
        Number(parts[0]);

    const month =
        parts[1];

    const year =
        parts[2];


    if (
        isNaN(day) ||
        !month ||
        !year
    ) {

        return date;
    }


    return (
        day +
        getOrdinal(day) +
        "-" +
        month.substring(0, 1).toUpperCase() +
        month.substring(1).toLowerCase() +
        "-" +
        year
    );
}


function formatMoney(value) {

    if (
        value === null ||
        value === undefined ||
        value === "" ||
        isNaN(Number(value))
    ) {

        return "N/A";
    }


    return (
        "₹" +
        Number(value).toLocaleString(
            "en-IN",
            {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            }
        )
    );
}


/* =========================================================
   TOMORROW
   ========================================================= */

function isTomorrow(date) {

    if (!date) {
        return false;
    }


    const exTime =
        getExDividendTime(date);


    if (exTime === null) {
        return false;
    }


    const tomorrow =
        getTomorrow();


    const tomorrowStart =
        new Date(
            tomorrow.getFullYear(),
            tomorrow.getMonth(),
            tomorrow.getDate()
        ).getTime();


    const tomorrowEnd =
        tomorrowStart +
        86400000;


    return (
        exTime >= tomorrowStart &&
        exTime < tomorrowEnd
    );
}


/* =========================================================
   DATE DISPLAY
   ========================================================= */

function updateDateDisplays() {

    fromDateDisplay.textContent =
        formatFilterDate(
            fromDate.value
        );


    toDateDisplay.textContent =
        formatFilterDate(
            toDate.value
        );
}


/* =========================================================
   SEARCH PANEL
   ========================================================= */

function openSearchPanel() {

    searchForm.classList.add(
        "open"
    );
}


function closeSearchPanel() {

    searchForm.classList.remove(
        "open"
    );
}


stockList.addEventListener(
    "click",
    function(event) {

        if (
            event.target.closest(
                ".favorite-button"
            )
        ) {

            return;
        }


        if (
            searchForm.classList.contains(
                "open"
            )
        ) {

            closeSearchPanel();
        }
    }
);


searchSummary.addEventListener(
    "click",
    function() {

        if (
            searchForm.classList.contains(
                "open"
            )
        ) {

            closeSearchPanel();

        } else {

            openSearchPanel();
        }
    }
);


editButton.addEventListener(
    "click",
    function(event) {

        event.stopPropagation();

        openSearchPanel();
    }
);


fromDate.addEventListener(
    "change",
    function() {

        clearFilterError();

        updateDateDisplays();
    }
);


toDate.addEventListener(
    "change",
    function() {

        clearFilterError();

        updateDateDisplays();
    }
);


investmentAmount.addEventListener(
    "input",
    clearFilterError
);


searchButton.addEventListener(
    "click",
    searchDividends
);


sortFilter.addEventListener(
    "change",
    function() {

        currentSort =
            sortFilter.value;

        /*
         * Whenever the user changes the
         * sorting/filter, start from page 1.
         */

        currentPage = 1;

        sortStocks();
    }
);


/* =========================================================
   PAGINATION
   ========================================================= */

function goToPage(page) {

    const requestedPage =
        Number(page);


    if (
        isNaN(requestedPage) ||
        requestedPage < 1
    ) {

        return;
    }


    /*
     * We calculate the number of pages
     * from the currently visible dataset.
     *
     * The actual filtered/sorted dataset
     * is prepared by sortStocks().
     *
     * renderStocks() will also protect
     * against an invalid page.
     */

    currentPage =
        Math.floor(
            requestedPage
        );


    sortStocks();


    /*
     * Move the user back to the beginning
     * of the stock list after changing page.
     *
     * We do not force the very top of the
     * entire page because the sticky filter
     * should remain useful.
     */

    if (stockList) {

        const rect =
            stockList.getBoundingClientRect();

        const scrollTop =
            window.scrollY +
            rect.top -
            10;


        window.scrollTo({
            top: Math.max(
                0,
                scrollTop
            ),
            behavior: "smooth"
        });
    }
}


/* =========================================================
   DATE CALCULATION
   ========================================================= */

function getTomorrow() {

    const today =
        new Date();


    const tomorrow =
        new Date(today);


    tomorrow.setDate(
        today.getDate() + 1
    );


    return tomorrow;
}


function dateToInputValue(date) {

    const year =
        date.getFullYear();


    const month =
        String(
            date.getMonth() + 1
        ).padStart(2, "0");


    const day =
        String(
            date.getDate()
        ).padStart(2, "0");


    return (
        year +
        "-" +
        month +
        "-" +
        day
    );
}


/* =========================================================
   ADD 15 DAYS
   ========================================================= */

function addFifteenDays(dateValue) {

    const parts =
        dateValue.split("-");


    const year =
        Number(parts[0]);

    const month =
        Number(parts[1]) - 1;

    const day =
        Number(parts[2]);


    const date =
        new Date(
            year,
            month,
            day
        );


    date.setDate(
        date.getDate() + 15
    );


    return dateToInputValue(
        date
    );
}


function resolveDates() {

    let selectedFrom =
        fromDate.value;


    let selectedTo =
        toDate.value;


    const tomorrow =
        dateToInputValue(
            getTomorrow()
        );


    if (
        !selectedFrom &&
        !selectedTo
    ) {

        selectedFrom =
            tomorrow;

        selectedTo =
            addFifteenDays(
                selectedFrom
            );

    } else if (
        selectedFrom &&
        !selectedTo
    ) {

        selectedTo =
            addFifteenDays(
                selectedFrom
            );

    } else if (
        !selectedFrom &&
        selectedTo
    ) {

        selectedFrom =
            tomorrow;
    }


    if (
        selectedTo <
        selectedFrom
    ) {

        throw new Error(
            "To Date can not be earlier than From Date."
        );
    }


    return {
        from: selectedFrom,
        to: selectedTo
    };
}


/* =========================================================
   FETCH DIVIDEND DATA
   ========================================================= */

async function fetchDividendData(
    fromValue,
    toValue
) {

    const response =
        await fetch(
            "/api/dividends",
            {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/json"
                },

                body:
                    JSON.stringify(
                        {
                            from_date:
                                formatApiDate(
                                    fromValue
                                ),

                            to_date:
                                formatApiDate(
                                    toValue
                                )
                        }
                    )
            }
        );


    if (!response.ok) {

        const message =
            await response.text();


        throw new Error(
            message ||
            "Unable to fetch dividend data."
        );
    }


    const data =
        await response.json();


    if (!Array.isArray(data)) {

        throw new Error(
            "API returned an invalid dividend response."
        );
    }


    return data;
}


/* =========================================================
   GET BACKEND VERSION
   ========================================================= */

async function getBackendVersion() {

    const response =
        await fetch(
            "/api/dividends/version",
            {
                method: "GET",

                cache: "no-store"
            }
        );


    if (!response.ok) {

        throw new Error(
            "Unable to check dividend version."
        );
    }


    const versionData =
        await response.json();


    if (
        !versionData ||
        versionData.version === undefined ||
        versionData.version === null
    ) {

        throw new Error(
            "Backend returned an invalid dividend version."
        );
    }


    return String(
        versionData.version
    );
}


/* =========================================================
   SEARCH
   ========================================================= */

async function searchDividends() {

    clearMessages();


    /*
     * A new search represents a new result set.
     *
     * Always start from page 1.
     */

    currentPage = 1;


    let dates;


    try {

        dates =
            resolveDates();

    } catch (e) {

        showError(
            e.message
        );

        return;
    }


    const investmentValue =
        investmentAmount.value.trim();


    const investment =
        investmentValue
            ? Number(investmentValue)
            : null;


    if (
        investment !== null &&
        (
            isNaN(investment) ||
            investment <= 0
        )
    ) {

        showError(
            "Please enter a valid investment amount."
        );

        return;
    }


    loading.classList.remove(
        "hidden"
    );


    startLoadingAnimation();


    searchButton.disabled =
        true;


    searchButton.textContent =
        "Searching...";


    try {

        let versionBeforeSearch = null;


        try {

            versionBeforeSearch =
                await getBackendVersion();

        } catch (versionError) {

            console.warn(
                "Unable to get version before search:",
                versionError
            );
        }


        const data =
            await fetchDividendData(
                dates.from,
                dates.to
            );


        let versionAfterSearch = null;


        try {

            versionAfterSearch =
                await getBackendVersion();

        } catch (versionError) {

            console.warn(
                "Unable to get version after search:",
                versionError
            );
        }


        let finalData =
            data;


        if (
            versionBeforeSearch !== null &&
            versionAfterSearch !== null &&
            versionBeforeSearch !==
                versionAfterSearch
        ) {

            console.log(
                "Dividend data changed during search. Fetching latest data..."
            );


            finalData =
                await fetchDividendData(
                    dates.from,
                    dates.to
                );


            try {

                versionAfterSearch =
                    await getBackendVersion();

            } catch (versionError) {

                console.warn(
                    "Unable to get final dividend version:",
                    versionError
                );
            }
        }


        fromDate.value =
            dates.from;


        toDate.value =
            dates.to;


        updateDateDisplays();


        updateSummary(
            investment,
            dates
        );


        dividendData =
            finalData;


        currentInvestment =
            investment;


        currentSort =
            "dividendDesc";


        sortFilter.value =
            "dividendDesc";


        /*
         * currentPage is already 1 because
         * this is a new search.
         */

        currentPage = 1;


        sortStocks();


        clearFilterError();


        closeSearchPanel();


        window.scrollTo({
            top: 0,
            behavior: "smooth"
        });


        if (
            versionAfterSearch !== null
        ) {

            currentDataVersion =
                versionAfterSearch;
        }


    } catch (e) {

        showError(
            e.message ||
            "Unable to fetch dividend data."
        );

    } finally {

        stopLoadingAnimation();


        loading.classList.add(
            "hidden"
        );


        searchButton.disabled =
            false;


        searchButton.textContent =
            "Search";
    }
}


/* =========================================================
   VERSION-CHECK AUTOMATIC REFRESH
   ========================================================= */

async function autoRefreshDividends() {

    if (autoRefreshing) {
        return;
    }


    if (
        refreshing ||
        (
            searchButton &&
            searchButton.disabled
        )
    ) {

        return;
    }


    if (
        !fromDate.value ||
        !toDate.value
    ) {

        return;
    }


    autoRefreshing = true;


    try {

        const backendVersion =
            await getBackendVersion();


        if (
            currentDataVersion === null
        ) {

            currentDataVersion =
                backendVersion;

            return;
        }


        if (
            backendVersion ===
            currentDataVersion
        ) {

            return;
        }


        const data =
            await fetchDividendData(
                fromDate.value,
                toDate.value
            );


        let confirmedVersion =
            backendVersion;


        try {

            confirmedVersion =
                await getBackendVersion();

        } catch (versionError) {

            console.warn(
                "Unable to confirm dividend version after automatic refresh:",
                versionError
            );
        }


        dividendData =
            data;


        currentDataVersion =
            confirmedVersion;


        /*
         * IMPORTANT:
         *
         * Do not reset:
         *
         * currentPage
         * currentSort
         * currentInvestment
         * favorites
         * sortFilter.value
         *
         * Existing UI state remains intact.
         *
         * If the new data has fewer pages,
         * renderStocks() will automatically
         * move currentPage to the last valid page.
         */

        sortStocks();


    } catch (e) {

        console.warn(
            "Automatic dividend version check failed:",
            e
        );

    } finally {

        autoRefreshing = false;
    }
}


/* =========================================================
   START AUTOMATIC VERSION CHECK
   ========================================================= */

function startAutomaticRefresh() {

    if (autoRefreshTimer !== null) {

        clearInterval(
            autoRefreshTimer
        );
    }


    autoRefreshTimer =
        setInterval(
            autoRefreshDividends,
            AUTO_REFRESH_INTERVAL
        );
}


startAutomaticRefresh();


/* =========================================================
   SUMMARY
   ========================================================= */

function updateSummary(
    investment,
    dates
) {

    dateSummary.textContent =
        formatDate(
            dates.from
        ) +
        " → " +
        formatDate(
            dates.to
        );


    if (
        investment !== null &&
        investment > 0
    ) {

        investmentSummary.textContent =
            "Investment Amount: " +
            formatMoney(
                investment
            );


        investmentSummary.classList.remove(
            "hidden"
        );

    } else {

        investmentSummary.textContent =
            "";


        investmentSummary.classList.add(
            "hidden"
        );
    }
}


/* =========================================================
   INVESTMENT SHARES
   ========================================================= */

function getInvestmentShares(stock) {

    if (
        currentInvestment === null ||
        currentInvestment <= 0
    ) {

        return 1;
    }


    const price =
        Number(
            stock.current_share_price
        );


    if (
        isNaN(price) ||
        price <= 0
    ) {

        return 0;
    }


    return Math.floor(
        currentInvestment /
        price
    );
}


/* =========================================================
   DIVIDEND YIELD
   ========================================================= */

function getDividendYield(stock) {

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

        return 0;
    }


    return (
        dividend /
        price
    ) * 100;
}


/* =========================================================
   EXPECTED DIVIDEND
   ========================================================= */

function getExpectedDividend(stock) {

    const dividend =
        Number(
            stock.dividend_amount
        );


    if (
        isNaN(dividend) ||
        dividend < 0
    ) {

        return 0;
    }


    if (
        currentInvestment === null ||
        currentInvestment <= 0
    ) {

        return dividend;
    }


    const shares =
        getInvestmentShares(stock);


    if (shares <= 0) {
        return 0;
    }


    return (
        shares *
        dividend
    );
}


/* =========================================================
   DIVIDEND HIGH SORT
   ========================================================= */

function sortByDividendHigh(a, b) {

    if (
        currentInvestment === null ||
        currentInvestment <= 0
    ) {

        const yieldA =
            getDividendYield(a);

        const yieldB =
            getDividendYield(b);


        if (
            yieldB !== yieldA
        ) {

            return (
                yieldB -
                yieldA
            );
        }


        return (
            Number(b.dividend_amount || 0) -
            Number(a.dividend_amount || 0)
        );
    }


    const sharesA =
        getInvestmentShares(a);

    const sharesB =
        getInvestmentShares(b);


    if (
        sharesA === 0 &&
        sharesB > 0
    ) {

        return 1;
    }


    if (
        sharesA > 0 &&
        sharesB === 0
    ) {

        return -1;
    }


    const expectedA =
        getExpectedDividend(a);

    const expectedB =
        getExpectedDividend(b);


    if (
        expectedB !== expectedA
    ) {

        return (
            expectedB -
            expectedA
        );
    }


    const yieldA =
        getDividendYield(a);

    const yieldB =
        getDividendYield(b);


    if (
        yieldB !== yieldA
    ) {

        return (
            yieldB -
            yieldA
        );
    }


    return (
        Number(b.dividend_amount || 0) -
        Number(a.dividend_amount || 0)
    );
}


/* =========================================================
   DIVIDEND LOW SORT
   ========================================================= */

function sortByDividendLow(a, b) {

    if (
        currentInvestment !== null &&
        currentInvestment > 0
    ) {

        const sharesA =
            getInvestmentShares(a);

        const sharesB =
            getInvestmentShares(b);


        if (
            sharesA === 0 &&
            sharesB > 0
        ) {

            return 1;
        }


        if (
            sharesA > 0 &&
            sharesB === 0
        ) {

            return -1;
        }


        const expectedA =
            getExpectedDividend(a);

        const expectedB =
            getExpectedDividend(b);


        if (
            expectedA !== expectedB
        ) {

            return (
                expectedA -
                expectedB
            );
        }


        return (
            getDividendYield(a) -
            getDividendYield(b)
        );
    }


    return (
        getDividendYield(a) -
        getDividendYield(b)
    );
}


/* =========================================================
   SORT STOCKS
   ========================================================= */

function sortStocks() {

    if (
        !Array.isArray(dividendData) ||
        dividendData.length === 0
    ) {

        renderStocks(
            [],
            currentInvestment,
            dividendData
        );

        return;
    }


    let sortedData =
        [...dividendData];


    /* =====================================================
       FAVORITES
       ===================================================== */

    if (
        currentSort ===
        "favorites"
    ) {

        /*
         * First keep only favorites.
         */

        sortedData =
            sortedData.filter(
                function(stock) {

                    return favorites.has(
                        getFavoriteKey(stock)
                    );
                }
            );


        /*
         * IMPORTANT:
         *
         * Favorites ALWAYS use
         * Expected Dividend HIGH -> LOW.
         *
         * This intentionally ignores
         * dividendAsc/dateAsc.
         */

        sortedData.sort(
            function(a, b) {

                return sortByDividendHigh(
                    a,
                    b
                );
            }
        );

    } else {

        /* =================================================
           NORMAL SORTING
           ================================================= */

        sortedData.sort(
            function(a, b) {

                if (
                    currentSort ===
                    "dateAsc"
                ) {

                    return (
                        getDateDistanceFromFromDate(a) -
                        getDateDistanceFromFromDate(b)
                    );
                }


                if (
                    currentSort ===
                    "dividendDesc"
                ) {

                    return sortByDividendHigh(
                        a,
                        b
                    );
                }


                if (
                    currentSort ===
                    "dividendAsc"
                ) {

                    return sortByDividendLow(
                        a,
                        b
                    );
                }


                return sortByDividendHigh(
                    a,
                    b
                );
            }
        );
    }


    /*
     * IMPORTANT:
     *
     * sortedData = COMPLETE list
     * after filtering and sorting.
     *
     * renderStocks() is responsible for
     * displaying only the current 50 records.
     */

    renderStocks(
        sortedData,
        currentInvestment,
        dividendData
    );


    updateSortMessage();
}


/* =========================================================
   DATE SORT
   ========================================================= */

function getFromDateTime() {

    if (!fromDate.value) {
        return null;
    }


    const parts =
        fromDate.value.split("-");


    if (
        parts.length !== 3
    ) {

        return null;
    }


    const date =
        new Date(
            Number(parts[0]),
            Number(parts[1]) - 1,
            Number(parts[2])
        );


    if (
        isNaN(date.getTime())
    ) {

        return null;
    }


    return date.getTime();
}


function getExDividendTime(date) {

    if (!date) {
        return null;
    }


    const parts =
        date.trim().split("-");


    if (
        parts.length !== 3
    ) {

        return null;
    }


    const day =
        Number(parts[0]);


    const months = {

        JAN: 0,
        FEB: 1,
        MAR: 2,
        APR: 3,
        MAY: 4,
        JUN: 5,
        JUL: 6,
        AUG: 7,
        SEP: 8,
        OCT: 9,
        NOV: 10,
        DEC: 11

    };


    const monthName =
        parts[1]
            .trim()
            .substring(0, 3)
            .toUpperCase();


    const month =
        months[monthName];


    const year =
        Number(parts[2]);


    if (
        isNaN(day) ||
        month === undefined ||
        isNaN(year)
    ) {

        return null;
    }


    const result =
        new Date(
            year,
            month,
            day
        );


    if (
        isNaN(result.getTime())
    ) {

        return null;
    }


    return result.getTime();
}


function getDateDistanceFromFromDate(stock) {

    const fromTime =
        getFromDateTime();


    const exDateTime =
        getExDividendTime(
            stock.ex_dividend_date
        );


    if (
        fromTime === null ||
        exDateTime === null
    ) {

        return Number.MAX_SAFE_INTEGER;
    }


    const difference =
        exDateTime -
        fromTime;


    if (difference >= 0) {
        return difference;
    }


    return (
        Math.abs(difference) +
        864000000000
    );
}


/* =========================================================
   SORT MESSAGE
   ========================================================= */

function updateSortMessage() {

    if (
        currentSort ===
        "favorites"
    ) {

        resultMessage.textContent =
            "Showing Favorite Companies";

        return;
    }


    if (
        currentSort ===
        "dateAsc"
    ) {

        resultMessage.textContent =
            "Sort by Date: Latest";

        return;
    }


    if (
        currentSort ===
        "dividendDesc"
    ) {

        resultMessage.textContent =
            "Sort by Expected Dividend: High to Low";

        return;
    }


    if (
        currentSort ===
        "dividendAsc"
    ) {

        resultMessage.textContent =
            "Sort by Expected Dividend: Low to High";

        return;
    }


    resultMessage.textContent =
        "Sort by Expected Dividend: High to Low";
}


/* =========================================================
   FAVORITE ANIMATION
   ========================================================= */

function animateFavoriteStar(
    button,
    flyingAway
) {

    if (!button || !favoriteFlyStar) {
        return;
    }


    const buttonRect =
        button.getBoundingClientRect();


    const startX =
        buttonRect.left +
        buttonRect.width / 2;


    const startY =
        buttonRect.top +
        buttonRect.height / 2;


    const outsideLeft =
        window.innerWidth +
        40;


    const outsideTop =
        -40;


    let endX;

    let endY;


    if (!flyingAway) {

        endX =
            startX;

        endY =
            startY;


        favoriteFlyStar.textContent =
            "★";


        favoriteFlyStar.style.left =
            `${outsideLeft - 14}px`;


        favoriteFlyStar.style.top =
            `${outsideTop - 14}px`;

    } else {

        endX =
            outsideLeft;

        endY =
            outsideTop;


        favoriteFlyStar.textContent =
            "★";


        favoriteFlyStar.style.left =
            `${startX - 14}px`;


        favoriteFlyStar.style.top =
            `${startY - 14}px`;
    }


    favoriteFlyStar.classList.remove(
        "fly"
    );


    void favoriteFlyStar.offsetWidth;


    favoriteFlyStar.classList.add(
        "fly"
    );


    const startCenterX =
        flyingAway
            ? startX
            : outsideLeft;


    const startCenterY =
        flyingAway
            ? startY
            : outsideTop;


    const deltaX =
        endX -
        startCenterX;


    const deltaY =
        endY -
        startCenterY;


    const animation =
        favoriteFlyStar.animate(
            [

                {
                    transform:
                        "translate(0, 0) scale(0.45) rotate(-25deg)",

                    opacity: 0
                },


                {
                    transform:
                        `translate(
                            ${deltaX * 0.25}px,
                            ${deltaY * 0.25}px
                        )
                        scale(0.85)
                        rotate(-10deg)`,

                    opacity: 0.75,

                    offset: 0.20
                },


                {
                    transform:
                        `translate(
                            ${deltaX * 0.55}px,
                            ${deltaY * 0.55}px
                        )
                        scale(1.15)
                        rotate(8deg)`,

                    opacity: 1,

                    offset: 0.60
                },


                {
                    transform:
                        `translate(
                            ${deltaX}px,
                            ${deltaY}px
                        )
                        scale(1)
                        rotate(0deg)`,

                    opacity: 1
                }

            ],

            {
                duration: 1200,

                easing:
                    "cubic-bezier(0.22, 1, 0.36, 1)",

                fill: "forwards"
            }
        );


    animation.onfinish =
        function() {

            favoriteFlyStar.classList.remove(
                "fly"
            );


            favoriteFlyStar.style.left =
                "0px";


            favoriteFlyStar.style.top =
                "0px";
        };
}


/* =========================================================
   FAVORITE CLICK
   ========================================================= */

function toggleFavorite(
    stock,
    button
) {

    const key =
        getFavoriteKey(stock);


    if (!key) {
        return;
    }


    const wasFavorite =
        favorites.has(key);


    if (wasFavorite) {

        favorites.delete(key);

        saveFavorites();


        button.classList.remove(
            "is-favorite"
        );


        button.textContent =
            "☆";


        button.setAttribute(
            "aria-label",
            "Add favorite"
        );


        button.setAttribute(
            "title",
            "Add favorite"
        );


        animateFavoriteStar(
            button,
            true
        );


        button.classList.remove(
            "favorite-changing"
        );


        void button.offsetWidth;


        button.classList.add(
            "favorite-changing"
        );


        setTimeout(
            function() {

                button.classList.remove(
                    "favorite-changing"
                );

            },
            700
        );


        if (
            currentSort ===
            "favorites"
        ) {

            setTimeout(
                function() {

                    sortStocks();

                },
                350
            );
        }


        return;
    }


    favorites.add(key);

    saveFavorites();


    button.classList.add(
        "is-favorite"
    );


    button.textContent =
        "★";


    button.setAttribute(
        "aria-label",
        "Remove favorite"
    );


    button.setAttribute(
        "title",
        "Remove favorite"
    );


    animateFavoriteStar(
        button,
        false
    );


    button.classList.remove(
        "favorite-changing"
    );


    void button.offsetWidth;


    button.classList.add(
        "favorite-changing"
    );


    setTimeout(
        function() {

            button.classList.remove(
                "favorite-changing"
            );

        },
        700
    );
}


/* =========================================================
   ERROR HANDLING
   ========================================================= */

function showError(message) {

    filterError.textContent =
        message;


    filterError.classList.remove(
        "hidden"
    );


    openSearchPanel();
}


function clearFilterError() {

    filterError.textContent =
        "";


    filterError.classList.add(
        "hidden"
    );
}


function clearMessages() {

    clearFilterError();

    empty.classList.add(
        "hidden"
    );
}


/* =========================================================
   DEFAULT DATES
   ========================================================= */

function setDefaultDates() {

    const tomorrow =
        getTomorrow();


    const tomorrowValue =
        dateToInputValue(
            tomorrow
        );


    const fifteenDaysLater =
        addFifteenDays(
            tomorrowValue
        );


    fromDate.value =
        tomorrowValue;


    toDate.value =
        fifteenDaysLater;
}


setDefaultDates();

updateDateDisplays();


/* =========================================================
   PULL TO REFRESH
   ========================================================= */

let touchStartY = 0;

let pulling = false;

let refreshing = false;

const PULL_DISTANCE = 75;


document.addEventListener(
    "touchstart",
    function(event) {

        if (
            refreshing ||
            window.scrollY !== 0
        ) {

            return;
        }


        touchStartY =
            event.touches[0].clientY;


        pulling = true;
    },
    {
        passive: true
    }
);


document.addEventListener(
    "touchmove",
    function(event) {

        if (
            !pulling ||
            refreshing ||
            window.scrollY !== 0
        ) {

            return;
        }


        const currentY =
            event.touches[0].clientY;


        const distance =
            currentY -
            touchStartY;


        if (distance <= 0) {
            return;
        }


        const progress =
            Math.min(
                distance /
                PULL_DISTANCE,
                1
            );


        pullRefresh.style.transform =
            `translate(
                -50%,
                ${-100 + (progress * 100)}%
            )`;


        if (
            distance >=
            PULL_DISTANCE
        ) {

            pullRefreshText.textContent =
                "Release to refresh";


            pullRefreshIcon.textContent =
                "↻";

        } else {

            pullRefreshText.textContent =
                "Pull to refresh";


            pullRefreshIcon.textContent =
                "↓";
        }
    },
    {
        passive: true
    }
);


document.addEventListener(
    "touchend",
    async function(event) {

        if (
            !pulling ||
            refreshing
        ) {

            return;
        }


        const distance =
            event.changedTouches[0].clientY -
            touchStartY;


        pulling = false;


        if (
            distance <
            PULL_DISTANCE
        ) {

            pullRefresh.style.transform =
                "translate(-50%, -100%)";

            return;
        }


        await refreshDividends();
    },
    {
        passive: true
    }
);


async function refreshDividends() {

    if (refreshing) {
        return;
    }


    refreshing = true;


    pullRefresh.classList.add(
        "visible",
        "refreshing"
    );


    pullRefreshIcon.textContent =
        "↻";


    pullRefreshText.textContent =
        "Refreshing...";


    try {

        /*
         * Pull-to-refresh remains a MANUAL refresh.
         *
         * It intentionally uses the existing search
         * behavior, including its sorting reset.
         *
         * searchDividends() also resets pagination
         * to page 1 because this is a fresh search.
         */

        await searchDividends();

    } finally {

        setTimeout(
            function() {

                pullRefresh.classList.remove(
                    "visible",
                    "refreshing"
                );


                pullRefresh.style.transform =
                    "translate(-50%, -100%)";


                pullRefreshText.textContent =
                    "Pull to refresh";


                pullRefreshIcon.textContent =
                    "↓";


                refreshing = false;

            },
            500
        );
    }
}


/* =========================================================
   INITIAL SEARCH
   ========================================================= */

searchDividends();
