package com.ormec.myapplication.models;

import java.util.List;

public class BenefitListResponse {

    private String status;           // "success" / "error"
    private List<BenefitItem> benefits;

    public String getStatus() {
        return status;
    }

    public List<BenefitItem> getBenefits() {
        return benefits;
    }
}
