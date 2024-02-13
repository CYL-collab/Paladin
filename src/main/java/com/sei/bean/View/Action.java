package com.sei.bean.View;
import com.sei.bean.Collection.Graph.FragmentNode;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * Created by vector on 16/5/15.
 */
public class Action implements Serializable{

    public String path;
    //Action的path统一为xpath和序号
    public String target;
    public int target_hash;
    public String target_activity;
    public String content;
    public String intent;
    boolean list;
    int index;

    int scroll;
    //TODO action is enum type
    int action;

    public static String[] filterwords = {"视频","登陆","注册","login","刷新", "登录","搜索","设置","setting"};

    public void setTarget(String activity, int hash){
        target_activity = activity;
        target_hash = hash;
        target = activity + "_" + hash;
    }

    public void setTarget(String target){
        this.target = target;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
    public Action() {
        list = false;
        scroll = 0;
    }

    public void setIntent(String intent){
        this.intent = intent;
    }

    public interface action_list {
        int CLICK   = 0;
        int LONGCLICK = 1;
        int ROWDOWN = 2;
        int ROWUP   = -2;
        int ROWRIGHT = 3;
        int ROWLEFT = -3;
        int MENU = 4;
        int INPUT = -4;
        int BACK    = 5;
        int SCROLLUP = 6;
        int SCROLLDOWN = -6;
        int ENTERTEXT = 7;
        int SCROLLLEFT = 8;
        int SCROLLRIGHT = -8;
        int ENABLEBT = 9;
        int DISABLEBT = -9;
    }

    public Action(String path, int action) {
        this.path = path;
        this.action = action;
        this.content = "test";
        list = false;
        scroll = 0;
    }

    public Action(String path, int action, String content){
        this(path, action);
        this.content = content;
    }

    public String getContent(){return content;}
    public FragmentNode findFragmentByAction(Set<FragmentNode> fragments) {
        for (FragmentNode fragment : fragments) {
            List<Action> actions = fragment.getAllPaths();
            // 检查这个Fragment的allPaths是否包含给定的Action
            if (actions.contains(this)) {
                return fragment; // 找到了，返回这个Fragment
            }
        }
        return null; // 如果没有找到，返回null
    }
}
