package com.ormec.myapplication;

import com.ormec.myapplication.models.BillingRatesResponse;
import com.ormec.myapplication.models.BenefitListResponse;
import com.ormec.myapplication.models.BenefitStatusResponse;
import com.ormec.myapplication.models.IncidentListResponse;
import com.ormec.myapplication.models.MeterHistoryResponse;
import com.ormec.myapplication.models.MeterListResponse;
import com.ormec.myapplication.models.SimpleResponse;
import com.ormec.myapplication.models.UserProfile;
import com.ormec.myapplication.models.QrMemberLoginRequest;
import com.ormec.myapplication.models.QrMemberLoginResponse;
import com.ormec.myapplication.models.RegisterRequest;
import com.ormec.myapplication.models.RegisterResponse;
import com.ormec.myapplication.models.ScheduleRequestBody;
import com.ormec.myapplication.models.ScheduleRequestResponse;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;
import retrofit2.http.Body;

import java.util.List;
public interface ApiServices {

    // ---------------- AUTH ----------------

    // Login with role (must match login.php that expects email, password, role)
    @FormUrlEncoded
    @POST("login")
    Call<ResponseBody> login(
            @Field("email") String email,
            @Field("password") String password
    );

    @FormUrlEncoded
    @POST("signup")
    Call<ResponseBody> signup(
            @Field("account_number") String accountNumber,
            @Field("name") String name,
            @Field("membership_no") String membershipNo,
            @Field("address") String address,
            @Field("contact_number") String contactNumber,
            @Field("password") String password
    );


    // ---------------- PROFILE ----------------

    // Example profile endpoint (if your PHP uses member_id or user_id, keep it consistent)
// ApiServices.java
    @GET("profile")
    Call<UserProfile> getUserProfile(@Query("user_id") long userId);



    // ---------------- METERS & HISTORY ----------------
    @GET("meters")
    Call<MeterListResponse> getMeters(@Query("user_id") long userId);

    @GET("meter_history")
    Call<MeterHistoryResponse> getMeterHistory(
            @Query("meter_id") long meterId,
            @Query("limit") int limit
    );

// ---------------- SEMINARS ----------------

    // GET http://127.0.0.1:4000/api/mobile/seminars
    @POST("seminar-schedule-requests")
    Call<ScheduleRequestResponse> createScheduleRequest(@Body ScheduleRequestBody body);

    @GET("seminar-schedule-requests/latest")
    Call<ScheduleRequestResponse> getLatestScheduleRequest();

    // ---------------- BILLING CALCULATOR ----------------

    // Load all billing rates (residential, commercial, etc.)
    @GET("billing_rates")
    Call<BillingRatesResponse> getBillingRates();

    // ---------------- INCIDENT REPORTS ----------------

    // Submit an incident report (without image upload for now)
    @FormUrlEncoded
    @POST("incidents/report")
    Call<SimpleResponse> reportIncident(
            @Field("user_id") long userId,
            @Field("category") String category,
            @Field("description") String description,
            @Field("location") String location
    );

    // List incidents for the logged-in user
    @GET("incidents")
    Call<IncidentListResponse> getIncidents(@Query("user_id") long userId);

    // ---------------- BENEFITS ----------------

    // Available benefits (Senior Citizen, Insurance, etc.)
    @GET("benefits")
    Call<BenefitListResponse> getBenefits();

    // Apply for a benefit (without files - deprecated, use applyBenefitWithFiles instead)
    @FormUrlEncoded
    @POST("benefits/apply")
    Call<SimpleResponse> applyBenefit(
            @Field("user_id") long userId,
            @Field("benefit_id") int benefitId
    );

    // Apply for a benefit with file upload
    @Multipart
    @POST("benefits/apply")
    Call<SimpleResponse> applyBenefitWithFiles(
            @Part("user_id") long userId,      // ✅ Correct
            @Part("benefit_id") int benefitId, // ✅ Correct
            @Part List<MultipartBody.Part> files
    );

    @POST("qr-login")
    Call<QrMemberLoginResponse> qrMemberLogin(@Body QrMemberLoginRequest body);
    // Status + history of benefit applications for this user
    @GET("benefits/status")
    Call<BenefitStatusResponse> getBenefitStatus(@Query("user_id") long userId);
}
