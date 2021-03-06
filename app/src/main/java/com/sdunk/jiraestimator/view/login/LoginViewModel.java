package com.sdunk.jiraestimator.view.login;

import com.sdunk.jiraestimator.api.APIUtils;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class LoginViewModel extends ViewModel {

    private final MutableLiveData<String> url = new MutableLiveData<>();

    private final MutableLiveData<String> email = new MutableLiveData<>();

    private final MutableLiveData<String> token = new MutableLiveData<>();

    private final MutableLiveData<LoginUser> user = new MutableLiveData<>();

    public void login() {

        // Process the url string to ensure it begins with https://
        String urlString = url.getValue() != null ? "https://" + url.getValue().replace("https://", "").replace("http://", "") : null;

        LoginUser loginUser = new LoginUser(urlString, email.getValue(), token.getValue());

        if (loginUser.urlIsValid() && loginUser.emailIsValid() && loginUser.tokenIsValid()) {
            APIUtils.getUserProjects(loginUser, user);
        } else {
            user.setValue(loginUser);
        }
    }


}