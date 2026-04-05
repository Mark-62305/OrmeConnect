package com.ormec.myapplication.models;

public class RegisterRequest {
    public long session_id;
    public long user_id;

    public RegisterRequest(long sessionId, long userId) {
        this.session_id = sessionId;
        this.user_id = userId;
    }
}