package com.ormec.myapplication.models;

public class MeterInfo {

    private long id;
    private String meter_number;
    private String status;
    private float last_reading_kwh;
    private Float bill_amount; // nullable

    public long getId() { return id; }
    public String getMeter_number() { return meter_number; }
    public String getStatus() { return status; }
    public float getLast_reading_kwh() { return last_reading_kwh; }
    public Float getBill_amount() { return bill_amount; }
}
