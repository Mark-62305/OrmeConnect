package com.ormec.myapplication.models;

import java.util.List;

public class MeterHistoryResponse {

    private String status;
    private int count;
    private List<MeterReading> values;

    public String getStatus() {
        return status;
    }

    public int getCount() {
        return count;
    }

    public List<MeterReading> getValues() {
        return values;
    }
}
