package com.sei.modules;

import com.sei.bean.Collection.Graph.FragmentNode;
import com.sei.bean.Collection.Graph.GraphManager;
import com.sei.bean.Collection.Tuple2;
import com.sei.bean.View.Action;
import com.sei.util.ClientUtil;
import com.sei.util.CommonUtil;
import com.sei.util.ConnectUtil;

import java.util.ArrayList;
import java.util.List;

import static com.sei.util.CommonUtil.log;

public class ModelReplay extends Strategy {
    public volatile Boolean verify = false;
    FragmentNode start;
    public GraphManager graphManager;
    List<String> route_list;
    public ModelReplay(GraphManager graphManager){
        super();
        this.graphManager = graphManager;
        verify = true;
    }

    public ModelReplay(GraphManager graphManager, List<String> route_list){
        super();
        this.graphManager = graphManager;
        this.route_list = route_list;
        verify = false;
    }

    public void run(){
        if (graphManager == null || graphManager.appGraph == null) return;
        if (!restart()) return;

        if (!verify)
            replay(false);
        else
            replay(true);
    }



    public void replay(Boolean is_verify){
        List<String> visited = new ArrayList<>();
        List<String> unvisited = new ArrayList<>();
        List<FragmentNode> nodes;
        if (is_verify)
            nodes = graphManager.getAllNodes();
        else
            nodes = graphManager.getRouteNodes(route_list);

        for(FragmentNode node : nodes){
            FragmentNode start = getStartNode();
            if (start == null) return;

            if (start.getStructure_hash() == node.getStructure_hash()){
                visited.add(node.getSignature());
                continue;
            }

            log("To: " + node.getSignature());
            if (visited.contains(node.getSignature()))
                continue;
            List<List<Tuple2<FragmentNode, Action>>> paths = graphManager.findOnePath(start, node);
            for(List<Tuple2<FragmentNode, Action>> path: paths){
                List<String> visited_along_path = execute_path(path);
                for(String n : visited_along_path){
                    if (!visited.contains(n))
                        visited.add(n);
                }

                if (visited_along_path.contains(node.getSignature()))
                    break;
            }

            if(!visited.contains(node.getSignature()))
                unvisited.add(node.getSignature());

            if (is_verify)
                if (!restart()) return;

            //graphManager.resetColor();
        }

        display(visited, unvisited);
    }

    List<String> execute_path(List<Tuple2<FragmentNode, Action>> path){
        List<String> visited_along_path = new ArrayList<>();
        log("path size: " + path.size());
        for(int i = path.size()-1; i >=0; i--){
            Action action = path.get(i).getSecond();
            FragmentNode expect_node = path.get(i).getFirst();
            ClientUtil.checkStatus(ClientUtil.execute_action(Action.action_list.CLICK, action.path));
            if (action.getAction() == Action.action_list.ENTERTEXT)
                ClientUtil.checkStatus(ClientUtil.execute_action(action.getAction(), action.getContent()));

            currentTree = ClientUtil.getCurrentTree();
            if (currentTree == null) return visited_along_path;

            if (!currentTree.getActivityName().equals("null"))
                if (!currentTree.getActivityName().equals(expect_node.getActivity())) {
                    log("Activity not match, expect: " + expect_node.getActivity() +
                            " jump to: " + currentTree.getActivityName());
                    return visited_along_path;
                }

            if (expect_node.getStructure_hash() != currentTree.getTreeStructureHash()){
                double similarity = currentTree.calc_similarity(expect_node.get_Clickable_list());
                if (similarity < CommonUtil.SIMILARITY){
                    log("jumped to unqualified node " + currentTree.getTreeStructureHash() +
                    " compared to " + expect_node.getStructure_hash() + " " + similarity);
                    return visited_along_path;
                }
            }

            visited_along_path.add(expect_node.getSignature());
        }
        return visited_along_path;
    }

    void display(List<String> visited, List<String> unvisited){
        int tot = visited.size() + unvisited.size();
        float coverage = (float) visited.size() / tot;
        log("coverage: " + visited.size() + "/" + tot);
        log("visited: ");
        for(String visit : visited)
            log("*" + visit.replace(ConnectUtil.launch_pkg, ""));

        log("unvisited: ");
        for(String unvisit : unvisited)
            log("-" + unvisit.replace(ConnectUtil.launch_pkg, ""));
    }

    FragmentNode getStartNode(){
        currentTree = ClientUtil.getCurrentTree();
        //log("find entry: " + currentTree.getActivityName() + "_" + currentTree.getTreeStructureHash() + " size: " + currentTree.get_Clickabke_list().size());
        FragmentNode start = graphManager.getFragmentInGraph(currentTree);
        int limits = 0;
        while (start == null && limits < 5) {
            CommonUtil.sleep(2000);
            ClientUtil.initiate();
            currentTree = ClientUtil.getCurrentTree();
            start = graphManager.getFragmentInGraph(currentTree);
            limits += 1;
        }

        if (start == null) {
            log("fail to find entry");
            return null;
        }
        log("current node: " + start.getSignature());
        return start;
    }
}