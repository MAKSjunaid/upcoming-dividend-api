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


let dividendData = [];

let currentInvestment = null;

let currentSort = "expectedDesc";

let loadingMessageTimer = null;


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

        "Scanning upcoming dividends...",

        "Checking ex-dividend dates...",

        "Finding dividend opportunities...",

        "Calculating potential rewards...",

        "Checking the stock market...",

        "Looking for money-making opportunities...",

        "Almost there...",

        "Is this a loading screen or a meditation session?",

        "Preparing your dividend list...",

        "Loading… blame the internet...",

        "Asking the database nicely...",

        "Convincing the server this is important..."

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

        sortStocks();
    }
);


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
            "To Date cannot be earlier than From Date."
        );
    }


    return {
        from: selectedFrom,
        to: selectedTo
    };
}


/* =========================================================
   SEARCH
   ========================================================= */

async function searchDividends() {

    clearMessages();


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
                                        dates.from
                                    ),

                                to_date:
                                    formatApiDate(
                                        dates.to
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
            data;


        currentInvestment =
            investment;


        currentSort =
            "expectedDesc";


        sortFilter.value =
            "expectedDesc";


        sortStocks();


        clearFilterError();


        closeSearchPanel();


        window.scrollTo({
            top: 0,
            behavior: "smooth"
        });


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
   SORTING
   ========================================================= */

function sortStocks() {

    if (
        !Array.isArray(dividendData) ||
        dividendData.length === 0
    ) {

        renderStocks(
            [],
            currentInvestment
        );

        return;
    }


    let sortedData =
        [...dividendData];


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

    } else {

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

                    return (
                        getExpectedDividend(b) -
                        getExpectedDividend(a)
                    );
                }


                if (
                    currentSort ===
                    "dividendAsc"
                ) {

                    return (
                        getExpectedDividend(a) -
                        getExpectedDividend(b)
                    );
                }


                return (
                    getExpectedDividend(b) -
                    getExpectedDividend(a)
                );
            }
        );
    }


    renderStocks(
        sortedData,
        currentInvestment
    );


    updateSortMessage();
}


function getExpectedDividend(stock) {

    const price =
        Number(
            stock.current_share_price
        );


    const dividend =
        Number(
            stock.dividend_amount
        );


    if (
        isNaN(dividend)
    ) {

        return 0;
    }


    if (
        currentInvestment === null ||
        currentInvestment <= 0
    ) {

        return dividend;
    }


    if (
        isNaN(price) ||
        price <= 0
    ) {

        return 0;
    }


    const shares =
        Math.floor(
            currentInvestment /
            price
        );


    return (
        shares *
        dividend
    );
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
            "Sort by Dividend: High to Low";

        return;
    }


    if (
        currentSort ===
        "dividendAsc"
    ) {

        resultMessage.textContent =
            "Sort by Dividend: Low to High";

        return;
    }


    resultMessage.textContent =
        "Sort by Dividend: High to Low";
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


    /* =====================================================
       UNFAVORITE
       ===================================================== */

    if (wasFavorite) {

        favorites.delete(key);

        saveFavorites();


        /*
         * IMPORTANT:
         * Remove yellow immediately.
         */

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


    /* =====================================================
       FAVORITE
       ===================================================== */

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
   RENDER STOCKS
   ========================================================= */

function renderStocks(
    data,
    investment
) {

    stockList.innerHTML = "";


    if (
        !Array.isArray(data) ||
        data.length === 0
    ) {

        empty.classList.remove(
            "hidden"
        );


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


    data.forEach(
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


            const card =
                document.createElement(
                    "div"
                );


            card.className =
                tomorrow
                    ? "stock-card tomorrow-card"
                    : "stock-card";


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
