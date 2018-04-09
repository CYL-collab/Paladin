package com.sei.bean.Collection.Graph;

import java.util.ArrayList;
import java.util.List;

public class AppGraph {
    String package_name;
    String home_activity;
    List<ActivityNode> activities;

    public AppGraph(){
        activities = new ArrayList<>();
    }

    public ActivityNode getAct(String act){
        for (ActivityNode ac: activities)
            if (ac.getActivity_name().equals(act))
                return ac;
        return null;
    }

    public ActivityNode find_Activity(String activity_name){
        return getAct(activity_name);
    }

    public String getHome_activity() {
        return home_activity;
    }


    public void setHome_activity(String home_activity) {
        this.home_activity = home_activity;
    }

    public String getPackage_name() {
        return package_name;
    }

    public void setPackage_name(String package_name) {
        this.package_name = package_name;
    }


    public List<ActivityNode> getActivities() {
        return activities;
    }

    public void setActivities(List<ActivityNode> activities) {
        this.activities = activities;
    }

    public void appendActivity(ActivityNode node){
        activities.add(node);
    }

    public FragmentNode getFragment(String activity, int hash){
        ActivityNode activityNode = getAct(activity);
        if (activityNode == null) return null;
        return activityNode.getFragment(hash);
    }
}
