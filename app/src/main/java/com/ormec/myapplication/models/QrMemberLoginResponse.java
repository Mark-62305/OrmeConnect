package com.ormec.myapplication.models;

public class QrMemberLoginResponse {
    public String status;
    public String message;
    public String accessToken;
    public Member member;
    public User user; // ← add this

    public static class User {
        public int id;
        public String role;
    }

    public static class Member {
        public Integer id;
        public String member_code;
        public String full_name;
    }
}