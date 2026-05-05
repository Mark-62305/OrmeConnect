package com.ormec.myapplication.models;

public class ScheduleRequestResponse {
    // POST response fields
    public String status;       // "success" / "fail"
    public String message;
    public long request_id;
    public String approval_status;  // from POST response

    // GET /latest response — result is nested
    public RequestDetail request;

    public static class RequestDetail {
        public long id;
        public String approval_status;
        public String seminar_date;
        public String start_time;
        public String end_time;
        public String created_at;
        public String reviewed_at;
        public String remarks;
    }

    public String getEffectiveStatus() {
        if (request != null && request.approval_status != null) return request.approval_status;
        if (approval_status != null) return approval_status;
        return null;
    }
}