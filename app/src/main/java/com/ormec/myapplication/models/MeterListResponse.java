package com.ormec.myapplication.models;

import java.util.List;

public class MeterListResponse {
    private String status;
    private int count;
    private java.util.List<MeterInfo> meters;

    public String getStatus() { return status; }
    public int getCount() { return count; }
    public java.util.List<MeterInfo> getMeters() { return meters; }
}
