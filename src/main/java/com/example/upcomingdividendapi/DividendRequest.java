package com.example.upcomingdividendapi;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DividendRequest {

    @JsonProperty("from_date")
    private String fromDate;

    @JsonProperty("to_date")
    private String toDate;

    public String getFromDate() {
        return fromDate;
    }

    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public void setToDate(String toDate) {
        this.toDate = toDate;
    }
}
/*
         * Cached dividend records.
         */
