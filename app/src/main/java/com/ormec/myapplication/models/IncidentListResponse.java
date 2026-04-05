package com.ormec.myapplication.models;

import java.util.List;

public class IncidentListResponse {
    private String status;
    private int count;
    private java.util.List<IncidentItem> incidents;

    public String getStatus() { return status; }
    public int getCount() { return count; }
    public java.util.List<IncidentItem> getIncidents() { return incidents; }
}
