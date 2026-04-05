package com.ormec.myapplication.models;

public class BillingRateItem {
    private String type;
    private Float rate_per_kwh;

    public String getType() {
        return type;
    }

    public Float getRate_per_kwh() {
        return rate_per_kwh;
    }
}