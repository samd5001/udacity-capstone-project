package com.sdunk.jiraestimator.db.user;

import com.sdunk.jiraestimator.model.User;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface UserDAO {

    @Query("SELECT * from users")
    User getLoggedInUser();

    @Insert
    void loginUser(User user);

    @Update
    void updateUser(User user);

    @Query("DELETE FROM users")
    void logoutUser();
}
