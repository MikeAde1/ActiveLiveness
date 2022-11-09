package com.luxand.livenessrecognition;

import java.lang.System;

@kotlin.Metadata(mv = {1, 7, 1}, k = 1, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\u0018\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00010\u00032\b\b\u0001\u0010\u0004\u001a\u00020\u0005H\'\u00a8\u0006\u0006"}, d2 = {"Lcom/luxand/livenessrecognition/ApiInterface;", "", "getFaceMatch", "Lretrofit2/Call;", "verifyBody", "Lcom/luxand/livenessrecognition/VerifyBody;", "app_debug"})
public abstract interface ApiInterface {
    
    @org.jetbrains.annotations.NotNull()
    @retrofit2.http.POST(value = "verify-with-profile-picture")
    public abstract retrofit2.Call<java.lang.Object> getFaceMatch(@org.jetbrains.annotations.NotNull()
    @retrofit2.http.Body()
    com.luxand.livenessrecognition.VerifyBody verifyBody);
}