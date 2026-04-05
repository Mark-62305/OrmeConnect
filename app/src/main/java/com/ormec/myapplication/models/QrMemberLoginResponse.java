package com.ormec.myapplication.models;

public class QrMemberLoginResponse {
    public String status;
    public String message;

    // optional if you return it
    public String accessToken;

    // optional member info
    public Member member;

    public static class Member {
        public Integer id;
        public String member_code;
        public String full_name; // if you have it
    }
}