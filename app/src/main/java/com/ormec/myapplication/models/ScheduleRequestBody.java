package com.ormec.myapplication.models;

public class ScheduleRequestBody {
    public long user_id;
    public String seminar_date;
    public String start_time;
    public String end_time;

    public ScheduleRequestBody(long userId, String seminarDate, String startTime, String endTime) {
        this.user_id = userId;
        this.seminar_date = seminarDate;
        this.start_time = startTime;
        this.end_time = endTime;
    }
}