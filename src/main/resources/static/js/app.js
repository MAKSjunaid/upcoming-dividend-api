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

const PAGE_SIZE = 30;

let currentPage = 1;


/* =========================================================
   VERSIONED AUTOMATIC REFRESH
   ========================================================= */

const AUTO_REFRESH_INTERVAL =
    20 * 1000;

let autoRefreshTimer = null;

let autoRefreshing = false;

let currentDataVersion = null;


/* =========================================================
   INITIAL LOAD STATE
   ========================================================= */

let initialLoad = true;

let initialDataReady = false;


/* =========================================================
   PREVIOUS SUCCESSFUL DATA
   ========================================================= */

const LAST_SUCCESSFUL_DATA_KEY =
    "lastSuccessfulDividendData";

const LAST_SUCCESSFUL_VERSION_KEY =
    "lastSuccessfulDividendVersion";


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
   SAVE LAST SUCCESSFUL DATA
   ========================================================= */

function saveLastSuccessfulDividendData(
    data,
    version
) {

    if (
        !Array.isArray(data) ||
        data.length === 0
    ) {

        return;
    }


    try {

        localStorage.setItem(
            LAST_SUCCESSFUL_DATA_KEY,
            JSON.stringify(data)
        );


        if (
            version !== null &&
            version !== undefined
        ) {

            localStorage.setItem(
                LAST_SUCCESSFUL_VERSION_KEY,
                String(version)
            );
        }

    } catch (e) {

        console.warn(
            "Unable to save previous dividend data:",
            e
        );
    }
}


/* =========================================================
   LOAD LAST SUCCESSFUL DATA
   ========================================================= */

function loadLastSuccessfulDividendData() {

    try {

        const savedData =
            localStorage.getItem(
                LAST_SUCCESSFUL_DATA_KEY
            );


        if (!savedData) {

            return null;
        }


        const parsed =
            JSON.parse(savedData);


        if (
            !Array.isArray(parsed) ||
            parsed.length === 0
        ) {

            return null;
        }


        return parsed;

    } catch (e) {

        console.warn(
            "Unable to load previous dividend data:",
            e
        );


        return null;
    }
}


/* =========================================================
   LOAD LAST SUCCESSFUL VERSION
   ========================================================= */

function loadLastSuccessfulVersion() {

    try {

        const version =
            localStorage.getItem(
                LAST_SUCCESSFUL_VERSION_KEY
            );


        if (
            version === null ||
            version === undefined ||
            version === ""
        ) {

            return null;
        }


        return String(version);

    } catch (e) {

        return null;
    }
}


/* =========================================================
   RESTORE PREVIOUS BROWSER DATA
   ========================================================= */

function restorePreviousDividendData() {

    const previousData =
        loadLastSuccessfulDividendData();


    if (
        !Array.isArray(previousData) ||
        previousData.length === 0
    ) {

        return false;
    }


    /*
     * IMPORTANT:
     *
     * Restore the previous successful data
     * BEFORE making any API request.
     */

    dividendData =
        previousData;


    currentPage = 1;


    /*
     * Restore the version that produced this data.
     *
     * This is important because if the backend has
     * already moved to a newer version, automatic
     * refresh must detect that difference.
     */

    const previousVersion =
        loadLastSuccessfulVersion();


    if (
        previousVersion !== null
    ) {

        currentDataVersion =
            previousVersion;
    }


    /*
     * Immediately render the previous data.
     */

    sortStocks();


    return true;
}


/* =========================================================
   INITIAL WAITING STATE
   =========================================================
 *
 * This is ONLY used when:
 *
 * - Initial page load
 * - No previous browser data exists
 * - We are waiting for the backend to produce records
 *
 * IMPORTANT:
 *
 * We use the user's requested text:
 *
 * "Please wait while fetching the result..."
 */

function showInitialWaitingState() {

    if (!loading) {
        return;
    }


    stopLoadingAnimation();


    loading.classList.remove(
        "hidden"
    );


    if (loadingMessage) {

        loadingMessage.textContent =
            "Please wait while fetching the result...";


        loadingMessage.style.opacity =
            "1";
    }
}


/* =========================================================
   HIDE INITIAL WAITING STATE
   ========================================================= */

function hideInitialWaitingState() {

    stopLoadingAnimation();


    if (loadingMessage) {

        /*
         * Explicitly clear the waiting text.
         *
         * This prevents the old
         * "Please wait while fetching the result..."
         * message from remaining visible after data
         * has already been restored/rendered.
         */

        loadingMessage.textContent =
            "";


        loadingMessage.style.opacity =
            "1";
    }


    if (loading) {

        loading.classList.add(
            "hidden"
        );
    }
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


    currentPage =
        Math.floor(
            requestedPage
        );


    sortStocks();


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
   INITIAL LOAD
   ========================================================= */

async function initialLoadDividends() {

    /*
     * -----------------------------------------------------
     * STEP 1
     * -----------------------------------------------------
     *
     * Restore browser data immediately.
     */

    const hasPreviousData =
        restorePreviousDividendData();


    let dates;


    try {

        dates =
            resolveDates();

    } catch (e) {

        /*
         * If previous data exists, keep it visible.
         */

        if (
            hasPreviousData
        ) {

            hideInitialWaitingState();

        } else {

            showInitialWaitingState();
        }


        initialLoad =
            false;


        return;
    }


    /*
     * Keep date inputs synchronized.
     */

    fromDate.value =
        dates.from;


    toDate.value =
        dates.to;


    updateDateDisplays();


    /*
     * -----------------------------------------------------
     * IMPORTANT FIX
     * -----------------------------------------------------
     *
     * If browser data already exists:
     *
     * - Render it
     * - Hide loading immediately
     * - Do NOT show:
     *   "Please wait while fetching the result..."
     *
     * The API request below is completely silent.
     */

    if (
        hasPreviousData
    ) {

        initialDataReady =
            true;


        clearMessages();


        /*
         * Make sure the top filter summary is also
         * updated immediately, just like after Search.
         */

        updateSummary(
            currentInvestment,
            dates
        );


        /*
         * Explicitly hide the loading element.
         *
         * This is the important part that prevents
         * the stale loading text from remaining visible.
         */

        hideInitialWaitingState();


        /*
         * Render again after the summary is updated.
         */

        sortStocks();

    } else {

        /*
         * No previous browser data.
         *
         * Only now show:
         *
         * "Please wait while fetching the result..."
         */

        initialDataReady =
            false;


        currentDataVersion =
            null;


        showInitialWaitingState();
    }


    /*
     * -----------------------------------------------------
     * STEP 2
     * -----------------------------------------------------
     *
     * Fetch backend data silently.
     */

    try {

        let versionBefore =
            null;


        try {

            versionBefore =
                await getBackendVersion();

        } catch (versionError) {

            console.warn(
                "Unable to get backend version during initial load:",
                versionError
            );
        }


        /*
         * IMPORTANT:
         *
         * We DO NOT clear dividendData here.
         *
         * Therefore existing browser data remains visible
         * while this request is running.
         */

        const data =
            await fetchDividendData(
                dates.from,
                dates.to
            );


        /* =================================================
           INITIAL + RECORDS
           ================================================= */

        if (
            Array.isArray(data) &&
            data.length > 0
        ) {

            /*
             * New successful data replaces old browser data.
             */

            dividendData =
                data;


            initialDataReady =
                true;


            /*
             * Confirm the version AFTER records are received.
             */

            let versionAfter =
                versionBefore;


            try {

                versionAfter =
                    await getBackendVersion();

            } catch (versionError) {

                console.warn(
                    "Unable to get backend version after initial load:",
                    versionError
                );
            }


            /*
             * Only save successful records.
             */

            saveLastSuccessfulDividendData(
                data,
                versionAfter
            );


            /*
             * Use the latest confirmed version.
             */

            if (
                versionAfter !== null &&
                versionAfter !== undefined
            ) {

                currentDataVersion =
                    String(versionAfter);
            }


            /*
             * Update the top filter summary.
             */

            updateSummary(
                currentInvestment,
                dates
            );


            /*
             * Render the new records.
             */

            sortStocks();


            /*
             * IMPORTANT:
             *
             * Explicitly remove the initial waiting message.
             */

            hideInitialWaitingState();


            return;
        }


        /* =================================================
           INITIAL + []
           ================================================= */

        if (
            Array.isArray(data) &&
            data.length === 0
        ) {

            console.log(
                "Initial /api/dividends returned []."
            );


            /*
             * -------------------------------------------------
             * Previous browser data exists
             * -------------------------------------------------
             */

            if (
                hasPreviousData
            ) {

                /*
                 * KEEP existing dividendData.
                 *
                 * Do NOT:
                 *
                 * dividendData = [];
                 */

                initialDataReady =
                    true;


                /*
                 * IMPORTANT:
                 *
                 * Do NOT replace currentDataVersion with
                 * versionBefore here.
                 *
                 * We must preserve the version belonging
                 * to the browser data.
                 *
                 * If backend version is newer, the 5-second
                 * automatic refresh will detect the change
                 * and retry /api/dividends.
                 */

                clearMessages();


                updateSummary(
                    currentInvestment,
                    dates
                );


                sortStocks();


                /*
                 * Most important:
                 *
                 * Keep loading text hidden.
                 */

                hideInitialWaitingState();


            } else {

                /*
                 * -------------------------------------------------
                 * No browser data exists
                 * -------------------------------------------------
                 *
                 * API returned [].
                 *
                 * Keep waiting.
                 */

                initialDataReady =
                    false;


                currentDataVersion =
                    null;


                showInitialWaitingState();
            }


            return;
        }

    } catch (e) {

        /*
         * Initial API error.
         *
         * NEVER destroy existing browser data.
         */

        console.warn(
            "Initial dividend load failed:",
            e
        );


        if (
            hasPreviousData
        ) {

            /*
             * Existing data stays visible.
             */

            initialDataReady =
                true;


            clearMessages();


            updateSummary(
                currentInvestment,
                dates
            );


            sortStocks();


            /*
             * Never leave the initial waiting message
             * visible when we already have usable data.
             */

            hideInitialWaitingState();

        } else {

            /*
             * Nothing to display yet.
             */

            initialDataReady =
                false;


            currentDataVersion =
                null;


            showInitialWaitingState();
        }

    } finally {

        initialLoad =
            false;
    }
}


/* =========================================================
   SEARCH
   ========================================================= */

async function searchDividends() {

    clearMessages();


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

        let versionBeforeSearch =
            null;


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


        let versionAfterSearch =
            null;


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


            const latestData =
                await fetchDividendData(
                    dates.from,
                    dates.to
                );


            if (
                Array.isArray(latestData)
            ) {

                finalData =
                    latestData;
            }


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


        /*
         * Manual Search intentionally replaces the
         * currently displayed data.
         */

        dividendData =
            finalData;


        currentInvestment =
            investment;


        currentSort =
            "dividendDesc";


        sortFilter.value =
            "dividendDesc";


        currentPage = 1;


        sortStocks();


        /*
         * Save only successful non-empty data.
         */

        if (
            Array.isArray(finalData) &&
            finalData.length > 0
        ) {

            saveLastSuccessfulDividendData(
                finalData,
                versionAfterSearch
            );
        }


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


        initialDataReady =
            true;


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


    autoRefreshing =
        true;


    try {

        /*
         * =================================================
         * INITIAL DATA NOT READY
         * =================================================
         *
         * If we have no successful data yet, keep trying
         * /api/dividends every 5 seconds.
         */

        if (
            !initialDataReady
        ) {

            let backendVersion =
                null;


            try {

                backendVersion =
                    await getBackendVersion();

            } catch (versionError) {

                console.warn(
                    "Unable to get version while preparing initial data:",
                    versionError
                );
            }


            const initialData =
                await fetchDividendData(
                    fromDate.value,
                    toDate.value
                );


            if (
                Array.isArray(initialData) &&
                initialData.length > 0
            ) {

                dividendData =
                    initialData;


                initialDataReady =
                    true;


                if (
                    backendVersion !== null
                ) {

                    currentDataVersion =
                        backendVersion;

                } else {

                    try {

                        currentDataVersion =
                            await getBackendVersion();

                    } catch (versionError) {

                        console.warn(
                            "Unable to confirm initial dividend version:",
                            versionError
                        );
                    }
                }


                saveLastSuccessfulDividendData(
                    initialData,
                    currentDataVersion
                );


                updateSummary(
                    currentInvestment,
                    {
                        from:
                            fromDate.value,

                        to:
                            toDate.value
                    }
                );


                sortStocks();


                hideInitialWaitingState();

            } else {

                /*
                 * Still no records.
                 *
                 * Keep waiting.
                 */

                showInitialWaitingState();
            }


            return;
        }


        /* =================================================
           GET CURRENT BACKEND VERSION
           ================================================= */

        const backendVersion =
            await getBackendVersion();


        /* =================================================
           SAME VERSION
           =================================================
         *
         * EXACT RULE:
         *
         * Same + Any
         *     -> Do nothing
         */

        if (
            currentDataVersion ===
            backendVersion
        ) {

            return;
        }


        /* =================================================
           VERSION CHANGED
           =================================================
         *
         * Backend version changed.
         *
         * Now request the actual dividend data.
         *
         * Existing cards remain untouched while the
         * request is loading.
         */

        const data =
            await fetchDividendData(
                fromDate.value,
                toDate.value
            );


        /* =================================================
           CHANGED + []
           =================================================
         *
         * EXACT RULE:
         *
         * Changed + []
         *     -> Ignore
         *
         * Do NOT:
         *
         * - clear dividendData
         * - render empty
         * - update currentDataVersion
         */

        if (
            !Array.isArray(data) ||
            data.length === 0
        ) {

            console.log(
                "Backend version changed but API returned []. Keeping existing data."
            );


            return;
        }


        /* =================================================
           CHANGED + RECORDS
           =================================================
         *
         * EXACT RULE:
         *
         * Changed + Records
         *     -> Replace data
         */

        dividendData =
            data;


        /*
         * Confirm version after receiving records.
         */

        let confirmedVersion =
            backendVersion;


        try {

            confirmedVersion =
                await getBackendVersion();

        } catch (versionError) {

            console.warn(
                "Unable to confirm dividend version:",
                versionError
            );
        }


        currentDataVersion =
            confirmedVersion;


        /*
         * Save successful data.
         */

        saveLastSuccessfulDividendData(
            data,
            confirmedVersion
        );


        /*
         * Preserve:
         *
         * - currentSort
         * - currentInvestment
         * - favorites
         * - currentPage
         */

        sortStocks();


    } catch (e) {

        /*
         * Automatic refresh errors remain silent.
         *
         * Existing data remains visible.
         */

        console.warn(
            "Automatic dividend version check failed:",
            e
        );

    } finally {

        autoRefreshing =
            false;
    }
}


/* =========================================================
   START AUTOMATIC VERSION CHECK
   ========================================================= */

function startAutomaticRefresh() {

    if (
        autoRefreshTimer !== null
    ) {

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

        sortedData =
            sortedData.filter(
                function(stock) {

                    return favorites.has(
                        getFavoriteKey(stock)
                    );
                }
            );


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
   INITIAL PAGE LOAD
   ========================================================= */

initialLoadDividends();
