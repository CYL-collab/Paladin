package com.sei.bean.Collection.Graph;

import com.sei.agent.Device;
import com.sei.bean.Collection.Stack.RuntimeFragmentNode;
import com.sei.bean.Collection.Tuple2;
import com.sei.bean.Collection.UiTransition;
import com.sei.bean.View.Action;
import com.sei.bean.View.ViewTree;
import com.sei.util.ClientUtil;
import com.sei.util.CommonUtil;
import com.sei.util.ConnectUtil;
import com.sei.util.SerializeUtil;
import com.sei.util.client.ClientAdaptor;
import com.sun.org.apache.xpath.internal.operations.Bool;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static com.sei.util.CommonUtil.log;

public class GraphAdjustor extends UiTransition{
    public AppGraph appGraph;
    public AppGraph reGraph;
    Boolean REPLAY_MODE = false;
    Boolean SPIDER_MODE = false;
    String RECENT_INTENT_TIMESTAMP = "";
    private Map<String, List<Integer>> similarPathIndex;

    public GraphAdjustor(String argv){
        similarPathIndex = new HashMap<>();
        String n = "graph-" + ConnectUtil.launch_pkg + ".json";
        File graph = new File(n);
        if (graph.exists()) load(argv);

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
            public int adjust(Device d, Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                action.setIntent(getSerIntent(d));
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addInterpath(action);
                ActivityNode activityNode = new ActivityNode(new_tree.getActivityName());
                FragmentNode frag_cur = new FragmentNode(new_tree);
                activityNode.appendFragment(frag_cur);
                if (!REPLAY_MODE) {
                    appGraph.appendActivity(activityNode);
                    CommonUtil.upload(appGraph, new_tree.getActivityName() + "_" + new_tree.getTreeStructureHash());
                }else {
                    appGraph.transfer_actions(frag_cur);
                    reGraph.appendActivity(activityNode);
                }

                if (new_tree.hasWebview && !REPLAY_MODE){
                    appGraph.getWebFragments().add(frag_cur.getSignature());
                }

                return UI.NEW_ACT;
            }
        });

        registerHandler(UI.OLD_ACT_NEW_FRG, new Handler() {
            @Override
            public int adjust(Device d, Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                action.setIntent(getSerIntent(d));
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addInterpath(action);
                AppGraph graph;
                if (!REPLAY_MODE) {
                    graph = appGraph;
                }else
                    graph = reGraph;
                ActivityNode activityNode = graph.find_Activity(new_tree.getActivityName());
                FragmentNode frag_cur = new FragmentNode(new_tree);
                if (REPLAY_MODE) appGraph.transfer_actions(frag_cur);

                activityNode.appendFragment(frag_cur);
                CommonUtil.upload(appGraph, new_tree.getActivityName() + "_" + new_tree.getTreeStructureHash());
                if (new_tree.hasWebview && !REPLAY_MODE){
                    graph.getWebFragments().add(frag_cur.getSignature());
                }
                return UI.OLD_ACT_NEW_FRG;
            }
        });

        registerHandler(UI.OLD_ACT_OLD_FRG, new Handler() {
            @Override
            public int adjust(Device d, Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                action.setIntent(getSerIntent(d));
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addInterpath(action);
                CommonUtil.upload(appGraph, new_tree.getActivityName() + "_" + new_tree.getTreeStructureHash());
                return UI.OLD_ACT_OLD_FRG;
            }
        });

        registerHandler(UI.NEW_FRG, new Handler() {
            @Override
            public int adjust(Device d, Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addIntrapath(action);
                ActivityNode activityNode;
                if (!REPLAY_MODE)
                    activityNode = appGraph.find_Activity(new_tree.getActivityName());
                else
                    activityNode = reGraph.find_Activity(new_tree.getActivityName());
                FragmentNode frag_cur = new FragmentNode(new_tree);
                activityNode.appendFragment(frag_cur);
                if (REPLAY_MODE) appGraph.transfer_actions(frag_cur);
                CommonUtil.upload(appGraph, new_tree.getActivityName() + "_" + new_tree.getTreeStructureHash());
                if (new_tree.hasWebview && !REPLAY_MODE){
                    appGraph.getWebFragments().add(frag_cur.getSignature());
                }
                return UI.NEW_FRG;
            }
        });

        registerHandler(UI.OLD_FRG, new Handler() {
            @Override
            public int adjust(Device d, Action action, ViewTree currentTree, ViewTree new_tree) {
                action.setTarget(new_tree.getActivityName(), new_tree.getTreeStructureHash());
                FragmentNode frag_prev = locate(currentTree);
                frag_prev.addIntrapath(action);
                CommonUtil.upload(appGraph, new_tree.getActivityName() + "_" + new_tree.getTreeStructureHash());
                return UI.OLD_FRG;
            }
        });
    }


    public int update(Device d, Action action, ViewTree currentTree, ViewTree new_tree, int response){
        if (action == null){
            log("device #" + d.serial + "'s first node");
            FragmentNode fragmentNode = locate(currentTree);
            CommonUtil.getSnapshot(currentTree, d);
            return 0;
        }

        if (response == Device.UI.OUT && !ClientUtil.getForeground(d).equals(ConnectUtil.launch_pkg)){
            try {
                String current_app = ClientUtil.getForeground(d);
                String act = ClientUtil.getTopActivityName(d);
                action.setTarget(current_app + "_" + act);
                action.setIntent(getSerIntent(d));
                FragmentNode fragmentNode = locate(currentTree);
                fragmentNode.interAppPaths.add(action);
                d.log("add interAppPath " + current_app + "_" + act);
                return 0;
            } catch (Exception e) {
                CommonUtil.log("something wrong when adding inter app path");
                CommonUtil.log("============stack trace============");
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                CommonUtil.log(sw.toString());
                CommonUtil.log("============stack trace============");
                return 0;
            }

        }else if(response == Device.UI.OUT){
            return 0;
        }

        if (currentTree.getTreeStructureHash() == new_tree.getTreeStructureHash())
            return UI.OLD_FRG;

        AppGraph graph;
        if (!REPLAY_MODE)
            graph = appGraph;
        else
            graph = reGraph;

        Handler handler = handler_table.get(queryGraph(graph, d, currentTree, new_tree));
        return handler.adjust(d, action, currentTree, new_tree);

    }

    public int queryGraph(AppGraph graph, Device d, ViewTree currentTree, ViewTree new_tree){
        ActivityNode actNode = graph.getAct(new_tree.getActivityName());
        String name = new_tree.getActivityName() + "_" + new_tree.getTreeStructureHash();
        if (new_tree.hasWebview && !appGraph.webFragments.contains(name)){
            d.log("detect webview " + new_tree.getActivityName() + "_" + new_tree.getTreeStructureHash());
        }

        if(!currentTree.getActivityName().equals(new_tree.getActivityName())){
            if (actNode == null){
                log("device #" + d.serial + ": brand new activity " + name);
                CommonUtil.getSnapshot(new_tree, d);
                return UI.NEW_ACT;
            }else{
                FragmentNode fragmentNode = actNode.find_Fragment(new_tree, REPLAY_MODE);
                if (fragmentNode == null){
                    log("device #" + d.serial + ": old activity brand new fragment " + name);
                    CommonUtil.getSnapshot(new_tree, d);
                    return UI.OLD_ACT_NEW_FRG;
                }else{
                    if (actNode.getFragment(fragmentNode.structure_hash) == null){
                        actNode.appendFragment(fragmentNode);
                        if (REPLAY_MODE) {
                            appGraph.transfer_actions(fragmentNode);
                        }
                        CommonUtil.getSnapshot(new_tree, d);
                    }
                    log("device #" + d.serial + ": old activity and old fragment " + name);
                    return UI.OLD_ACT_OLD_FRG;
                }

            }
        }else{
            FragmentNode fragmentNode = actNode.find_Fragment(new_tree, REPLAY_MODE);
            if(fragmentNode == null){
                log("device #" + d.serial + ": brand new fragment " + name);
                CommonUtil.getSnapshot(new_tree, d);
                return UI.NEW_FRG;
            }else{
                log("device #" + d.serial + ": old fragment " + name);
                if (!actNode.getFragments().contains(fragmentNode)) {
                    actNode.appendFragment(fragmentNode);
                    d.log("generated old fragment " + name);
                    if (REPLAY_MODE) {
                        appGraph.transfer_actions(fragmentNode);
                    }
                    CommonUtil.getSnapshot(new_tree, d);
                }
                return UI.OLD_FRG;
            }
        }
    }

    @Override
    public void save(){
        try {
            String n = "graph-" + ConnectUtil.launch_pkg + ".json";
            File file = new File(n);
            FileWriter writer = new FileWriter(file);
            String content = SerializeUtil.toBase64(appGraph);
            writer.write(content);
            writer.close();

            if (reGraph != null){
                file = new File("re_graph.json");
                FileWriter writer1 = new FileWriter(file);
                content = SerializeUtil.toBase64(reGraph);
                writer1.write(content);
                writer1.close();
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void load(String argv){
        try{
            if (argv.contains("-r")){
                REPLAY_MODE = true;
                reGraph = new AppGraph();
                reGraph.setPackage_name(ConnectUtil.launch_pkg);
            } else if (argv.contains("-n")) {
                SPIDER_MODE = true;
            }
            String n = "graph-" + ConnectUtil.launch_pkg + ".json";
            String graphStr = CommonUtil.readFromFile(n);
            appGraph = (AppGraph) SerializeUtil.toObject(graphStr, AppGraph.class);
            for(ActivityNode actNode: appGraph.getActivities()){
                for(FragmentNode frgNode: actNode.getFragments()) {
                    frgNode.VISIT = false;
                    frgNode.clicked_edges = new ArrayList<>();
                    frgNode.setColor("white");
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void reset(){
        appGraph = null;
    }

    public void resetColor(){
        for(ActivityNode activityNode: appGraph.activities){
            for(FragmentNode fragmentNode: activityNode.fragments){
                fragmentNode.setColor("white");
            }
        }
    }

    public FragmentNode locate(ViewTree tree){
        AppGraph graph;
        if (!REPLAY_MODE) graph = appGraph;
        else graph = reGraph;

        if(tree == null)
            return null;
        ActivityNode activityNode = graph.getAct(tree.getActivityName());
        FragmentNode fragmentNode = null;
        if (activityNode == null){
            log("1 fail to find " + tree.getActivityName() + "_" + tree.getTreeStructureHash());
            activityNode = new ActivityNode(tree.getActivityName());
            graph.appendActivity(activityNode);
            fragmentNode = new FragmentNode(tree);
            activityNode.appendFragment(fragmentNode);
            if (REPLAY_MODE) appGraph.transfer_actions(fragmentNode);
            return fragmentNode;
        }

        fragmentNode = activityNode.find_Fragment(tree, REPLAY_MODE);

        if (fragmentNode == null){
            log("2 fail to locate " + tree.getActivityName() + "_" + tree.getTreeStructureHash());
            fragmentNode = new FragmentNode(tree);
            activityNode.appendFragment(fragmentNode);
            if (REPLAY_MODE) appGraph.transfer_actions(fragmentNode);
        }

        if (activityNode.getFragment(tree.getTreeStructureHash()) == null){
            if (REPLAY_MODE) appGraph.transfer_actions(fragmentNode);
            activityNode.appendFragment(fragmentNode);
        }

        return fragmentNode;
    }

    public FragmentNode locateForSpider(ViewTree tree){
        AppGraph graph;
        if (!REPLAY_MODE) graph = appGraph;
        else graph = reGraph;

        if(tree == null)
            return null;
        ActivityNode activityNode = graph.getAct(tree.getActivityName());
        FragmentNode fragmentNode = null;
        if (activityNode == null){
            log("3 fail to find " + tree.getActivityName() + "_" + tree.getTreeStructureHash());
            activityNode = new ActivityNode(tree.getActivityName());
            graph.appendActivity(activityNode);
            fragmentNode = new FragmentNode(tree);
            activityNode.appendFragment(fragmentNode);
            if (REPLAY_MODE) appGraph.transfer_actions(fragmentNode);
            if(SPIDER_MODE) return null;
            return fragmentNode;
        }

        fragmentNode = activityNode.find_Fragment(tree, REPLAY_MODE);

        if (fragmentNode == null){
            log("4 fail to locate " + tree.getActivityName() + "_" + tree.getTreeStructureHash());
            fragmentNode = new FragmentNode(tree);
            activityNode.appendFragment(fragmentNode);
            if(SPIDER_MODE) return null;
            if (REPLAY_MODE) appGraph.transfer_actions(fragmentNode);
        }

        if (activityNode.getFragment(tree.getTreeStructureHash()) == null){
            if (REPLAY_MODE) appGraph.transfer_actions(fragmentNode);
            if(SPIDER_MODE) return null;
            activityNode.appendFragment(fragmentNode);
        }

        return fragmentNode;
    }

    public Action getAction(ViewTree currentTree){
        FragmentNode currentNode = locate(currentTree);
        //log("current node: " + currentNode.getSignature());
        Action action = null;
        if (currentNode.path_index.size() < currentNode.path_list.size()) {
            for (int i = 0; i < currentNode.path_list.size(); i++){
                String path = currentNode.path_list.get(i);
                if (!currentNode.path_index.contains(i) && currentNode.edit_fields.contains(path)){
                    currentNode.path_index.add(i);
                    log(currentNode.getSignature() +  " path: " + currentNode.path_index.size() + "/" + currentNode.path_list.size());
                    return new Action(path, Action.action_list.ENTERTEXT);
                }
            }
            int ser = CommonUtil.shuffle(currentNode.path_index, currentNode.path_list.size());

            currentNode.path_index.add(ser);
            log(currentNode.getSignature() +  " path: " + currentNode.path_index.size() + "/" + currentNode.path_list.size());

            String path = currentNode.path_list.get(ser);
            //log("next path to act:" + path);
            if (currentNode.edit_fields.contains(path)) {
                action = new Action(path, Action.action_list.ENTERTEXT);
            }else if (path.equals("menu")){
                action = new Action(path, Action.action_list.MENU);
            }else
                action = new Action(path, Action.action_list.CLICK);
        }else
            currentNode.setTraverse_over(true);
        return action;
    }

    public Action getTextAction(ViewTree currentTree){
        FragmentNode currentNode = locate(currentTree);
        //log("current node: " + currentNode.getSignature());
        Action action = null;
        if(currentNode == null) return null;
        if (currentNode.text_path_index.size() < 30 && currentNode.text_path_index.size() < currentNode.text_path_list.size()) {
            //CommonUtil.log("text path list size:"+currentNode.text_path_list.size());
            int ser = CommonUtil.shuffle(currentNode.text_path_index, currentNode.text_path_list.size());

            currentNode.text_path_index.add(ser);
            log(currentNode.getSignature() +  "text path: " + currentNode.text_path_index.size() + "/" + currentNode.text_path_list.size());

            String path = currentNode.text_path_list.get(ser);
            //log("next path to act:" + path);
            if (currentNode.edit_fields.contains(path)) {
                action = new Action(path, Action.action_list.ENTERTEXT);
            }else if (path.equals("menu")){
                action = new Action(path, Action.action_list.MENU);
            }else
                action = new Action(path, Action.action_list.CLICK);
        }else
            currentNode.setTraverse_over(true);
        return action;
    }


    public Action getEdgetActionInOrder(Device d, ViewTree currentTree){
        FragmentNode currentNode = locate(currentTree);
        Action action = null;
        int ser = currentNode.path_index.size();
        if (ser < currentNode.path_list.size()){
            currentNode.path_index.add(ser);
            String path = currentNode.path_list.get(ser);
            log(currentNode.getSignature() +  " path: " + currentNode.path_index.size() + "/" + currentNode.path_list.size());
            if (currentNode.edit_fields.contains(path))
                action = new Action(path, Action.action_list.ENTERTEXT);
            else if (path.equals("menu")){
                action = new Action(path, Action.action_list.MENU);
            }else
                action = new Action(path, Action.action_list.CLICK);
            action.setTarget(currentNode.targets.get(ser));
            return action;
        }else{
            currentNode.setTraverse_over(true);
            return action;
        }
    }

    public Action getEdgeAction(Device d, ViewTree currentTree){
        FragmentNode currentNode = locate(currentTree);
        Action action = null;
        while (currentNode.path_index.size() < currentNode.path_list.size()) {
            int ser = CommonUtil.shuffle(currentNode.path_index, currentNode.path_list.size());
            currentNode.path_index.add(ser);
            String path = currentNode.path_list.get(ser);
            if (currentNode.edit_fields.contains(path)) {
                action = new Action(path, Action.action_list.ENTERTEXT);
            }else {
                action = new Action(path, Action.action_list.CLICK);
            }

            if (currentNode.targets.size() == 0) break;

            int index = currentNode.targets.get(ser).indexOf("_");
            String target_act = currentNode.targets.get(ser).substring(0, index);
            int target_hash = Integer.parseInt(currentNode.targets.get(ser).substring(index+1));
            FragmentNode node = appGraph.getFragment(target_act, target_hash);
            if (node == null) break;

            int p = d.fragmentStack.getPosition(target_act, target_hash, node.get_Clickable_list());
            if (p == -1) break;
            else{
                //d.log(target_act + "_" + target_hash + " in stack " + p);
                continue;
            }
        }

        if (currentNode.path_index.size() >= currentNode.path_list.size())
            currentNode.setTraverse_over(true);

        return action;

    }

    public String getSerIntent(Device d){
        if (!CommonUtil.INTENT) return null;

        //String record = ConnectUtil.sendHttpGet(d.ip + "/intent");
        String record = ConnectUtil.sendHttpGet("http://127.0.0.1:7008/intent");
        int idx = record.indexOf("$");
        if (idx == -1) return null;
        String timestamp = record.substring(0, idx);

        if (timestamp.equals(RECENT_INTENT_TIMESTAMP))
            return null;
        else{
            RECENT_INTENT_TIMESTAMP = timestamp;
            //d.log(record);
            return record.substring(idx+1);
        }
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
        AppGraph graph;
        if (REPLAY_MODE) graph = reGraph;
        else graph = appGraph;

        ActivityNode actNode = graph.getAct(tree.getActivityName());
        if (actNode == null) return null;
        FragmentNode fragmentNode = actNode.find_Fragment(tree, REPLAY_MODE);
        return fragmentNode;
    }

    public Map<String, List<String>> getAllNodesTag(){
        AppGraph graph;
        if (REPLAY_MODE) graph = reGraph;
        else graph = appGraph;

        Map<String, List<String>> tags = new HashMap<>();
        for(int i=0; i < graph.getActivities().size(); i++){
            ActivityNode act = graph.getActivities().get(i);
            tags.put(act.activity_name, new ArrayList<>());
            for(int j=act.getFragments().size()-1; j>=0; j--){
                FragmentNode frg = act.getFragments().get(j);
                tags.get(act.activity_name).add(String.valueOf(frg.getStructure_hash()));
            }
        }
        return tags;
    }

    public Action getInterPathAction(Device d, ViewTree tree){
        FragmentNode fragmentNode = locate(tree);
        if (fragmentNode != null && fragmentNode.interpaths.size() > 0){
            for (Action action: fragmentNode.interpaths){
                String act = action.target_activity;
                if (d.fragmentStack.contains(act))
                    return action;
            }
            return null;
        }else
            return null;
    }

    public void tie(RuntimeFragmentNode runtimeFragmentNode, ViewTree tree, Action action){
        AppGraph graph;
        if (REPLAY_MODE) graph = reGraph;
        else graph = appGraph;

        ActivityNode actNode = graph.getAct(runtimeFragmentNode.getActivity());
        FragmentNode fragmentNode = actNode.getFragment(runtimeFragmentNode.getStructure_hash());
        action.setTarget(tree.getActivityName(), tree.getTreeStructureHash());
        if (tree.getActivityName().equals(actNode.getActivity_name())){
            fragmentNode.addIntrapath(action);
        }else{
            fragmentNode.addInterpath(action);
        }
    }

    public List<Action> BFS(FragmentNode start, FragmentNode end){
        Queue<FragmentNode> queue = new LinkedList<>();
        queue.add(start);
        start.setColor("gray");
        while(!queue.isEmpty()){
            FragmentNode processing = queue.poll();
            if (loop(processing.getIntrapaths(), end, processing, queue)){
                return buildPath(end);
            } else if (loop(processing.getInterpaths(), end, processing, queue)){
                return buildPath(end);
            }
        }
        return null;
    }

    private List<Action> buildPath(FragmentNode end){
        List<Action> path = new ArrayList<>();
        FragmentNode tmp;
        while(end.getPrevious() != null){
            path.add(end.getAction());
            tmp = end;
            end = end.getPrevious();
            tmp.setPrevious(null);
        }
        return path;
    }

    private boolean loop(List<Action> actions, FragmentNode end, FragmentNode processing, Queue<FragmentNode> queue){
        for(Action action : actions){
            FragmentNode n = appGraph.getFragment(action.target_activity, action.target_hash);
            if (n == null){
                continue;
            }
            if (n.getColor().equals("white")){
                n.setPrevious(processing);
                n.setAction(action);
                n.setColor("gray");
                if (n.getActivity().equals(end.getActivity()) &&
                        n.getStructure_hash() == end.getStructure_hash()){
                    //log("search "  + n.getSignature() + " success!"
                    //+ "previous " + n.getPrevious().getSignature());
                    return true;
                }else{
                    queue.add(n);
                }
            }
        }
        return false;
    }

    public FragmentNode getFragmentInGraph(ViewTree tree){
        ActivityNode actNode = appGraph.getAct(tree.getActivityName());
        if (actNode == null){
            log(tree.getActivityName() + " not found");
            return null;
        }
        return actNode.find_Fragment_in_graph(tree);
    }

    public List<Tuple2<FragmentNode, Action>> getPreviousNodes(FragmentNode start, String activityName){
        List<Tuple2<FragmentNode, Action>> previousNodes = new ArrayList<>();
        ActivityNode targetActivityNode = appGraph.find_Activity(activityName);
        if(targetActivityNode == null) {
            log("can't find activity node:" + activityName);
            return previousNodes;
        }

        List<FragmentNode> targetFragmentNodes = targetActivityNode.fragments;
        if(targetFragmentNodes == null || targetFragmentNodes.size() == 0){
            log("can't find fragment nodes!");
            return previousNodes;
        }


        //宽搜
        Queue<FragmentNode> queue = new LinkedList<>();
        queue.add(start);
        start.setColor("gray");
        while(!queue.isEmpty()){
            //CommonUtil.log("bfs");
            FragmentNode processing = queue.poll();
            //CommonUtil.log("now:" + processing.getSignature());
            for(Action action : processing.getAllPaths()){
                FragmentNode n = appGraph.getFragment(action.target_activity, action.target_hash);
                if (n == null){
                    CommonUtil.log("null???");
                    continue;
                }
                if (n.getColor().equals("white")){
                    n.setPrevious(processing);
                    n.setAction(action);
                    n.setColor("gray");
                    //CommonUtil.log("activity:" + n.activity);
                    if (n.getActivity().equals(activityName)){
                        //log("find one:"  + processing.getSignature());
                        Tuple2<FragmentNode, Action> tmp = new Tuple2<FragmentNode, Action>(processing, action);
                        previousNodes.add(tmp);
                    }
                    queue.add(n);
                }
            }
        }
        return previousNodes;
    }

    public void clearPathIndex() {
        for(ActivityNode an : appGraph.activities){
            for(FragmentNode fn : an.fragments){
                fn.path_index.clear();
                fn.text_path_index.clear();
            }
        }
    }


    //选择5个与string最相近的path，构造action返回。
    public Action getSimilarTextAction(ViewTree currentTree, String string) {
        FragmentNode currentNode = locate(currentTree);
        //log("current node: " + currentNode.getSignature());
        Action action = null;
        if(currentNode == null) return null;

        if(!similarPathIndex.containsKey(currentNode.getSignature() + string)) {
            List<Integer> indexList = new ArrayList<>();
            List<Tuple2<Integer,Integer>> index2similarity = new ArrayList<Tuple2<Integer,Integer>>();
            for(int index = 0; index < currentNode.text_path_list.size(); index++) {
                String path = currentNode.text_path_list.get(index);
                Tuple2<Integer, Integer> tuple = new Tuple2<Integer, Integer> (index, cal_similarity(path, string));
                index2similarity.add(tuple);
            }
            Collections.sort(index2similarity, new Comparator(){
                @Override
                public int compare(Object o1, Object o2) {
                    Tuple2<Integer, Integer> t1 = (Tuple2<Integer, Integer>) o1;
                    Tuple2<Integer, Integer> t2 = (Tuple2<Integer, Integer>) o2;
                    int i = t2.getSecond().compareTo(t1.getSecond());
                    if(i == 0)
                        return t2.getFirst().compareTo(t1.getFirst());
                    return i;
                }
            });
            //CommonUtil.log("string:" + string);
            //CommonUtil.log("current node:" + currentNode.getSignature());
            for(Tuple2<Integer,Integer> t : index2similarity) {
                //CommonUtil.log("index:" + t.getFirst());
                //CommonUtil.log("similarity:" + t.getSecond());
                //CommonUtil.log("path:" + currentNode.text_path_list.get(t.getFirst()));
                indexList.add(t.getFirst());
            }
            similarPathIndex.put(currentNode.getSignature() + string, indexList);
        }
        List<Integer> indexList = similarPathIndex.get(currentNode.getSignature() + string);
        //选择8个最接近的。
        if (currentNode.text_path_index.size() < 8 && currentNode.text_path_index.size() < indexList.size()) {
            int ser = indexList.get(currentNode.text_path_index.size());
            while (ser >= currentNode.text_path_list.size()){
                CommonUtil.log("xxx");
                currentNode.text_path_index.add(ser);
                ser = indexList.get(currentNode.text_path_index.size());
            }
            currentNode.text_path_index.add(ser);

            log(currentNode.getSignature() +  "text path: " + currentNode.text_path_index.size() + "/" + currentNode.text_path_list.size());

            String path = currentNode.text_path_list.get(ser);
            //log("next path to act:" + path);
            if (currentNode.edit_fields.contains(path)) {
                action = new Action(path, Action.action_list.ENTERTEXT);
            }else if (path.equals("menu")){
                action = new Action(path, Action.action_list.MENU);
            }else
                action = new Action(path, Action.action_list.CLICK);
        }else
            currentNode.setTraverse_over(true);
        return action;
    }

    public int cal_similarity(String s1, String s2) {
        int i = 0;
        while(i < s1.length() && i < s2.length() && s1.charAt(i) == s2.charAt(i)) i++;
        return i;
    }
}
