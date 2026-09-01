package com.example.upcomingdividendapi;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DividendResponse {

    @JsonProperty("share_name")
    private String shareName;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("ex_dividend_date")
    private String exDate;

    @JsonProperty("dividend_amount")
    private Double dividendAmount;

    @JsonProperty("current_share_price")
    private Double currentSharePrice;

    @JsonProperty("previous_share_price")
    private Double previousSharePrice;

    public DividendResponse(
            String shareName,
            String symbol,
            String exDate,
            Double dividendAmount,
            Double currentSharePrice,
            Double previousSharePrice) {

        this.shareName = shareName;
        this.symbol = symbol;
        this.exDate = exDate;
        this.dividendAmount = dividendAmount;
        this.currentSharePrice = currentSharePrice;
        this.previousSharePrice = previousSharePrice;
    }

    public String getShareName() {
        return shareName;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getExDate() {
        return exDate;
    }

    public Double getDividendAmount() {
        return dividendAmount;
    }

    public Double getCurrentSharePrice() {
        return currentSharePrice;
    }

    public Double getPreviousSharePrice() {
        return previousSharePrice;
    }
}

/*
         * Cached dividend records.
         */
