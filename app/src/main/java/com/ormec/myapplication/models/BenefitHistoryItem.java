// BenefitHistoryItem.java
package com.ormec.myapplication.models;

public class BenefitHistoryItem {
    private String benefit_name;
    private String status;      // approved, pending, etc.
    private String date;

    public String getBenefit_name() { return benefit_name; }
    public String getStatus() { return status; }
    public String getDate() { return date; }
}
