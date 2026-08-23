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

const pageHeader =
    document.querySelector(".page-header");


let dividendData = [];

let currentInvestment = null;

let currentSort = "expectedDesc";


function updateStickyHeader() {

    const scrolled =
        window.scrollY > 20;


    if (stickyHeader) {

        if (scrolled) {

            stickyHeader.classList.add(
                "scrolled"
            );

        } else {

            stickyHeader.classList.remove(
                "scrolled"
            );
        }
    }


    if (pageHeader) {

        if (scrolled) {

            pageHeader.classList.add(
                "scrolled"
            );

        } else {

            pageHeader.classList.remove(
                "scrolled"
            );
        }
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


function getOrdinal(day) {

    if (day >= 11 && day <= 13) {
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

    const parts = date.split("-");

    if (parts.length !== 3) {
        return date;
    }

    const year = parts[0];

    const month = Number(parts[1]);

    const day = Number(parts[2]);

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

    if (
        month < 1 ||
        month > 12 ||
        day < 1
    ) {
        return date;
    }

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

    const parts = date.split("-");

    if (parts.length !== 3) {
        return date;
    }

    const year = parts[0];

    const month = Number(parts[1]);

    const day = Number(parts[2]);

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

    if (
        month < 1 ||
        month > 12 ||
        day < 1
    ) {
        return date;
    }

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

    const parts = date.split("-");

    if (parts.length !== 3) {
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

    if (parts.length !== 3) {
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


searchSummary.addEventListener(
    "click",
    openSearchPanel
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


function addOneMonth(dateValue) {

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

    date.setMonth(
        date.getMonth() + 1
    );

    return dateToInputValue(date);
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
            addOneMonth(
                selectedFrom
            );
    }

    else if (
        selectedFrom &&
        !selectedTo
    ) {

        selectedTo =
            addOneMonth(
                selectedFrom
            );
    }

    else if (
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

        loading.classList.add(
            "hidden"
        );

        searchButton.disabled =
            false;

        searchButton.textContent =
            "Search";
    }
}


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


    const sortedData =
        [...dividendData];


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


function updateSortMessage() {

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

        resultMessage.textContent =
            "No dividends found for the selected dates.";

        return;
    }


    empty.classList.add(
        "hidden"
    );


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


            const card =
                document.createElement(
                    "div"
                );


            card.className =
                "stock-card";

            card.style.animationDelay =
                `${data.indexOf(stock) * 0.06}s`;


            card.innerHTML = `

                <div class="company-name">

                    <span class="field-icon">
                        🏢
                    </span>

                    <span>
                        ${
                            stock.share_name ||
                            "Unknown Company"
                        }
                    </span>

                </div>


                <div class="symbol">

                    <span class="field-icon">
                        📊
                    </span>

                    <span>
                        ${
                            stock.symbol ||
                            ""
                        }
                    </span>

                </div>


                <div class="main-result">

                    <div class="expected">

                        <div class="expected-value">

                            <span class="field-icon">
                                💰
                            </span>

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

                            <span class="field-icon">
                                📅
                            </span>

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

                            <span class="field-icon">
                                💵
                            </span>

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

                            <span class="field-icon">
                                📈
                            </span>

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


            stockList.appendChild(
                card
            );
        }
    );
}


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


function setDefaultDates() {

    const tomorrow =
        getTomorrow();


    const tomorrowValue =
        dateToInputValue(
            tomorrow
        );


    const oneMonthLater =
        addOneMonth(
            tomorrowValue
        );


    fromDate.value =
        tomorrowValue;


    toDate.value =
        oneMonthLater;
}


setDefaultDates();

updateDateDisplays();


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
            `translate(-50%, ${-100 + (progress * 100)}%)`;


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


searchDividends();
