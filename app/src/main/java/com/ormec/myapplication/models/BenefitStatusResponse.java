// BenefitStatusResponse.java
package com.ormec.myapplication.models;

import java.util.List;

public class BenefitStatusResponse {
    private String status;
    private String current_application_status;   // e.g. "Review", "Processing"
    private List<BenefitHistoryItem> history;

    public String getStatus() { return status; }
    public String getCurrent_application_status() { return current_application_status; }
    public List<BenefitHistoryItem> getHistory() { return history; }
}
