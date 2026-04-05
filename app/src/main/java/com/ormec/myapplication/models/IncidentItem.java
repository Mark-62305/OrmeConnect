package com.ormec.myapplication.models;

public class IncidentItem {
    private long id;
    private String category;
    private String description;
    private String status;
    private String reported_at;
    private String meter_id; // nullable

    public long getId() { return id; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getReported_at() { return reported_at; }
    public String getMeter_id() { return meter_id; }
}
