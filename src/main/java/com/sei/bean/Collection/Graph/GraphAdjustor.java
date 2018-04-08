package com.sei.bean.Collection.Graph;

import com.sei.bean.Collection.UiTransition;
import com.sei.bean.View.Action;
import com.sei.bean.View.ViewTree;
import com.sei.util.ClientUtil;
import com.sei.util.CommonUtil;
import com.sei.util.ConnectUtil;
import com.sei.util.SerializeUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sei.util.CommonUtil.log;

public class GraphAdjustor extends UiTransition{
    public AppGraph appGraph;

    public GraphAdjustor(){
        File graph = new File( "./graph.json");
        if (graph.exists()) load();

        if (appGraph == null) {
            appGraph = new AppGraph();
            appGraph.setPackage_name(ConnectUtil.launch_pkg);
        }
        registerAllHandlers();
    }

    @Override
    public void registerAllHandlers(){
        registerHandler(UI.NEW_ACT, new Handler() {
            @Override
            public int adjust(Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addInterpath(action);
                ActivityNode activityNode = new ActivityNode(new_tree.getActivityName());
                FragmentNode frag_cur = new FragmentNode(new_tree);
                activityNode.appendFragment(frag_cur);
                appGraph.appendActivity(activityNode);
                return UI.NEW_ACT;
            }
        });

        registerHandler(UI.OLD_ACT_NEW_FRG, new Handler() {
            @Override
            public int adjust(Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addInterpath(action);
                ActivityNode activityNode = appGraph.find_Activity(new_tree.getActivityName());
                FragmentNode frag_cur = new FragmentNode(new_tree);
                activityNode.appendFragment(frag_cur);
                return UI.OLD_ACT_NEW_FRG;
            }
        });

        registerHandler(UI.OLD_ACT_OLD_FRG, new Handler() {
            @Override
            public int adjust(Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addInterpath(action);
                return UI.OLD_ACT_OLD_FRG;
            }
        });

        registerHandler(UI.NEW_FRG, new Handler() {
            @Override
            public int adjust(Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addIntrapath(action);
                ActivityNode activityNode = appGraph.find_Activity(new_tree.getActivityName());
                FragmentNode frag_cur = new FragmentNode(new_tree);
                activityNode.appendFragment(frag_cur);
                return UI.NEW_FRG;
            }
        });

        registerHandler(UI.OLD_FRG, new Handler() {
            @Override
            public int adjust(Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addIntrapath(action);
                return UI.OLD_FRG;
            }
        });
    }


    public int update(int id, Action action, ViewTree currentTree, ViewTree new_tree){
        if (action == null){
            log("device #" + id + "'s first node");
            locate(currentTree);
            return 0;
        }

        if (currentTree.getTreeStructureHash() == new_tree.getTreeStructureHash())
            return UI.OLD_FRG;

        int status = queryGraph(id, currentTree, new_tree);
        Handler handler = handler_table.get(status);
        return handler.adjust(action, currentTree, new_tree);
    }

    public int queryGraph(int id, ViewTree currentTree, ViewTree new_tree){
        ActivityNode actNode = appGraph.getAct(new_tree.getActivityName());

        if(!currentTree.getActivityName().equals(new_tree.getActivityName())){
            if (actNode == null){
                log("device #" + id + ": brand new activity " + new_tree.getActivityName() + "_" + new_tree.getTreeStructureHash());
                return UI.NEW_ACT;
            }else if(actNode.find_Fragment(new_tree) == null){
                log("device #" + id + ": old activity brand new fragment " + actNode.getActivity_name() + "_" + new_tree.getTreeStructureHash());
                return UI.OLD_ACT_NEW_FRG;
            }else{
                log("device #" + id + ": old activity and old fragment " + actNode.getActivity_name() + "_" + new_tree.getTreeStructureHash());
                return UI.OLD_ACT_OLD_FRG;
            }
        }else{
            if(actNode.find_Fragment(new_tree) == null){
                log("device #" + id + ": brand new fragment " + new_tree.getActivityName() + " " +  new_tree.getTreeStructureHash());
                return UI.NEW_FRG;
            }else{
                log("device #" + id + ": old fragment " + new_tree.getTreeStructureHash());
                return UI.OLD_FRG;
            }
        }
    }

    @Override
    public void save(){
        try {
            File file = new File("graph.json");
            FileWriter writer = new FileWriter(file);
            String content = SerializeUtil.toBase64(appGraph);
            writer.write(content);
            writer.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void load(){
        try{
            String graphStr = CommonUtil.readFromFile("graph.json");
            appGraph = (AppGraph) SerializeUtil.toObject(graphStr, AppGraph.class);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void reset(){
        appGraph = null;
    }

    public FragmentNode locate(ViewTree tree){
        ActivityNode activityNode = appGraph.getAct(tree.getActivityName());
        FragmentNode fragmentNode = null;
        if (activityNode == null){
            log("fail to find " + tree.getActivityName() + "_" + tree.getTreeStructureHash());
            activityNode = new ActivityNode(tree.getActivityName());
            appGraph.appendActivity(activityNode);
            fragmentNode = new FragmentNode(tree);
            activityNode.appendFragment(fragmentNode);
            return fragmentNode;
        }

        fragmentNode = activityNode.find_Fragment(tree);
        if (fragmentNode == null){
            log("fail to locate " + tree.getActivityName() + "_" + tree.getTreeStructureHash());
            fragmentNode = new FragmentNode(tree);
            activityNode.appendFragment(fragmentNode);
        }

        return fragmentNode;
    }

    public Action getAction(ViewTree currentTree){
        FragmentNode currentNode = locate(currentTree);
        Action action = null;
        if (currentNode.path_index.size() < currentNode.path_list.size()) {
            int ser = CommonUtil.shuffle(currentNode.path_index, currentNode.path_list.size());
            currentNode.path_index.add(ser);
            String path = currentNode.path_list.get(ser);
            if (currentNode.edit_fields.contains(path))
                action = new Action(path, Action.action_list.ENTERTEXT);
            else
                action = new Action(path, Action.action_list.CLICK);
        }else
            currentNode.setTraverse_over(true);
        return action;
    }

    public Boolean hasAction(String activity, int hash){
        ActivityNode activityNode = appGraph.getAct(activity);
        if (activityNode == null) return false;
        FragmentNode fragmentNode = activityNode.getFragment(hash);
        if (fragmentNode == null) return false;
        if (fragmentNode.path_index.size() < fragmentNode.path_list.size()) return true;

        return false;
    }

    public Boolean match(ViewTree tree, String activity, int hash){
        ActivityNode activityNode = appGraph.getAct(activity);
        if (activityNode == null) return false;
        FragmentNode fragmentNode = activityNode.getFragment(hash);
        if (fragmentNode == null) return false;
        if (fragmentNode.calc_similarity(tree.getClickable_list()) > 0.8)
            return true;
        else
            return false;
    }

    public FragmentNode searchFragment(ViewTree tree){
        ActivityNode actNode = appGraph.getAct(tree.getActivityName());
        if (actNode == null) return null;
        return actNode.find_Fragment(tree);
    }

    public Map<String, List<String>> getAllNodesTag(){
        Map<String, List<String>> tags = new HashMap<>();
        for(int i=0; i < appGraph.getActivities().size(); i++){
            ActivityNode act = appGraph.getActivities().get(i);
            tags.put(act.activity_name, new ArrayList<>());
            for(int j=act.getFragments().size()-1; j>=0; j--){
                FragmentNode frg = act.getFragments().get(j);
                tags.get(act.activity_name).add(String.valueOf(frg.getStructure_hash()));
            }
        }
        return tags;
    }
}
