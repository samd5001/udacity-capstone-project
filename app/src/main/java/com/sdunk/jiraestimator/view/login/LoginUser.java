package com.sdunk.jiraestimator.view.login;

import android.util.Patterns;

import com.sdunk.jiraestimator.model.Project;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import okhttp3.Credentials;

@Getter
public class LoginUser {

    private static final String ATLASSIAN_URL_SUFFIX = "atlassian.net";

    private final String url;
    private final String email;
    private final String token;

    @Setter
    private List<Project> projectList;

    @Setter
    private String apiError;

    public LoginUser(String url, String email, String token) {
        this.url = url;
        this.email = email;
        this.token = token;
    }

    public boolean urlIsValid() {
        return url != null && !url.isEmpty() && Patterns.WEB_URL.matcher(url).matches() && url.trim().endsWith(ATLASSIAN_URL_SUFFIX);
    }

    public boolean emailIsValid() {
        return email != null && !email.trim().isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public boolean tokenIsValid() {
        return token != null && !token.trim().isEmpty();
    }

    public boolean userValid() {
        return urlIsValid() && emailIsValid() && tokenIsValid();
    }

    public String getAuthToken() {
        if (email != null && token != null) {
            return Credentials.basic(email.trim(), token.trim());
        }

        return null;
    }
}
