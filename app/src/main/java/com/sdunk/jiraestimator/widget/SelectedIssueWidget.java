package com.sdunk.jiraestimator.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import com.sdunk.jiraestimator.BuildConfig;
import com.sdunk.jiraestimator.R;
import com.sdunk.jiraestimator.SplashScreenActivity;
import com.sdunk.jiraestimator.db.issue.IssueDatabase;
import com.sdunk.jiraestimator.model.JiraIssue;

import static android.content.Context.MODE_PRIVATE;

public class SelectedIssueWidget extends AppWidgetProvider {

    public static final String PREF_STORY_KEY = "pref_story_key";

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                        int appWidgetId) {
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_selected_issue);

        SharedPreferences prefs = context.getSharedPreferences(BuildConfig.APPLICATION_ID, MODE_PRIVATE);
        String key = prefs.getString(PREF_STORY_KEY, null);

        JiraIssue issue = key == null ? null :IssueDatabase.getInstance(context).issueDAO().loadIssueByKey(key).getValue();

        if (issue != null) {
            populateIssueWidget(views, issue);
        } else {
            populateEmptyWidget(context, views);
        }

        Intent appIntent = new Intent(context, SplashScreenActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, appIntent, 0);
        views.setOnClickPendingIntent(R.id.widget, pendingIntent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private void populateIssueWidget(RemoteViews views, JiraIssue issue) {
        views.setTextViewText(R.id.widget_story_name, issue.getKey() + " - " + issue.getName());
        views.setTextViewText(R.id.widget_story_description, issue.getDescription());
        views.setTextViewText(R.id.widget_story_points, issue.getStoryPoints().toString());
        views.setViewVisibility(R.id.widget_header, View.VISIBLE);
        views.setViewVisibility(R.id.widget_story_point_container, View.VISIBLE);
    }

    private void populateEmptyWidget(Context context, RemoteViews views) {
        views.setViewVisibility(R.id.widget_header, View.GONE);
        views.setViewVisibility(R.id.widget_story_point_container, View.GONE);
        views.setTextViewText(R.id.widget_story_description, context.getString(R.string.widget_empty_text));
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}