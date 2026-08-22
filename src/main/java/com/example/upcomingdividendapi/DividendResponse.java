package com.example.upcomingdividendapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "from_date",
        "to_date",
        "share_name",
        "symbol",
        "ex_dividend_date",
        "dividend_amount",
        "current_share_price"
})
public class DividendResponse {

    private String fromDate;
    private String toDate;
    private String shareName;
    private String symbol;
    private String exDividendDate;
    private Double dividendAmount;
    private Double currentSharePrice;

    public DividendResponse(
            String fromDate,
            String toDate,
            String shareName,
            String symbol,
            String exDividendDate,
            Double dividendAmount,
            Double currentSharePrice) {

        this.fromDate = fromDate;
        this.toDate = toDate;
        this.shareName = shareName;
        this.symbol = symbol;
        this.exDividendDate = exDividendDate;
        this.dividendAmount = dividendAmount;
        this.currentSharePrice = currentSharePrice;
    }

    @JsonProperty("from_date")
    public String getFromDate() {
        return fromDate;
    }

    @JsonProperty("to_date")
    public String getToDate() {
        return toDate;
    }

    @JsonProperty("share_name")
    public String getShareName() {
        return shareName;
    }

    @JsonProperty("symbol")
    public String getSymbol() {
        return symbol;
    }

    @JsonProperty("ex_dividend_date")
    public String getExDividendDate() {
        return exDividendDate;
    }

    @JsonProperty("dividend_amount")
    public Double getDividendAmount() {
        return dividendAmount;
    }

    @JsonProperty("current_share_price")
    public Double getCurrentSharePrice() {
        return currentSharePrice;
    }
}