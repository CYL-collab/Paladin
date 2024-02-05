package com.sei.modules;

import com.sei.agent.Device;
import com.sei.bean.Collection.Graph.ActivityNode;
import com.sei.bean.Collection.Graph.FragmentNode;
import com.sei.bean.Collection.Graph.GraphAdjustor;
import com.sei.bean.View.Action;
import com.sei.bean.View.ViewTree;
import com.sei.server.component.Decision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sei.util.CommonUtil.log;

public class ModelSearch extends ModelReplay{
    public ModelSearch(GraphAdjustor graphAdjustor, Map<String, Device> devices){
        super(graphAdjustor, devices);
    }

//    @Override
//    public Decision make(String serial, ViewTree currentTree, ViewTree newTree, Decision prev_decision, int response){
//        Device d = devices.get(serial);
//        if (prev_decision.action == null){
//            if (d.getRoute_list().isEmpty()){
//                return new Decision(Decision.CODE.STOP);
//            }
//            Decision decision = replay(newTree, d.serial, d.popFirstRoute());
//            if (decision.code == Decision.CODE.CONTINUE){
//                //即将执行一个操作
//            }
//            return decision;
//        }
//
//
//        //FragmentNode currentNode = graphAdjustor.locate(newTree);
//        String activity = prev_decision.action.target_activity;
//        int hash = prev_decision.action.target_hash;
//        FragmentNode expectNode = graphAdjustor.appGraph.getFragment(activity, hash);
//        List<Action> paths = actionTable.get(serial);
//        //log("current: " + newTree.getTreeStructureHash() + " expect: " + expectNode.getSignature());
//        if (paths.size() < 1){
//            if (newTree.getActivityName().equals(expectNode.getActivity()) &&
//                    expectNode.calc_similarity(newTree.getClickable_list()) > 0.7)
//                d.visits.add(expectNode.getSignature());
//            return new Decision(Decision.CODE.STOP);
//        }
//
//        Action action = paths.remove(paths.size()-1);
//        String xpath;
//        if (action.path.indexOf("#") != -1){
//            int idx = action.path.indexOf("#");
//            xpath = action.path.substring(0, idx);
//        }else{
//            xpath = action.path;
//        }
//
//        if ((newTree.getClickable_list().contains(xpath) || xpath.equals("menu"))
//                && newTree.getActivityName().equals(expectNode.getActivity())){
//            d.visits.add(expectNode.getSignature());
//            // 即将执行一个操作
//
//            return new Decision(Decision.CODE.CONTINUE, action);
//        }else if (currentTree.getTreeStructureHash() != newTree.getTreeStructureHash()){
//            return replay(newTree, d.serial, d.popFirstRoute());
//        }else{
//            // for debug
//            log("xpath: " + xpath);
//            for(String s: newTree.getClickable_list()){
//                log(s);
//            }
//        }
//        return new Decision(Decision.CODE.STOP);
//    }
}
