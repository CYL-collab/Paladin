package com.sei.modules.test;

import com.sei.agent.Device;
import com.sei.bean.Collection.Graph.ActionEdge;
import com.sei.bean.Collection.Graph.ActivityNode;
import com.sei.bean.Collection.Graph.FragmentNode;
import com.sei.bean.Collection.Graph.GraphAdjustor;
import com.sei.bean.View.Action;
import com.sei.bean.View.ViewTree;
import com.sei.server.component.Decision;
import com.sei.server.component.Scheduler;
import com.sei.util.CommonUtil;
import com.sei.util.client.ClientAdaptor;
import com.sei.util.client.ClientAutomator;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.cycle.SzwarcfiterLauerSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;

import javax.management.remote.rmi._RMIConnection_Stub;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.sei.util.CommonUtil.calc_similarity;
import static com.sei.util.CommonUtil.log;

public class SearchTest extends Thread{
    private Scheduler scheduler;
    private GraphAdjustor graphAdjustor;
    private double match = 0.0;
    private Device d;
    private List<String> visits;
    private List<List<FragmentNode>> cycles;
    private FragmentNode sourceNode;
    private GeneticAlgo geneticAlgo;
    public SearchTest(Device d, Scheduler scheduler){
        this.d = d;
        this.scheduler = scheduler;
        this.graphAdjustor = scheduler.graphAdjustor;
        this.geneticAlgo = new GeneticAlgo(d, scheduler);
        visits = new ArrayList<>();
        cycles = new ArrayList<>();
    }

    @Override
    public void run(){
        graphAdjustor.appGraph.buildDirectedGraph();
        DefaultDirectedGraph<FragmentNode, ActionEdge> g = graphAdjustor.appGraph.getDirectedGraph();
        cycles = (new SzwarcfiterLauerSimpleCycles<>(g)).findSimpleCycles();
        // FragmentNode sourceNode = (FragmentNode) (graphAdjustor.appGraph.findSourceFragment().toArray()[0]);
        sourceNode = getSourceFragment();
        // Initial Population
        for(List<FragmentNode> cycle : cycles) {
            List<Action>[] paths = genActionsFromCycle(cycle);
            List<Action> pathInCycle;
            List<Action> pathToCycle;
            if (paths == null) {
                continue;
            } else {
                pathToCycle = paths[0];
                pathInCycle = paths[1];
            }
            GeneticAlgo.Individual ind = new GeneticAlgo.Individual(pathToCycle, pathInCycle);
            geneticAlgo.addToInitialPopulationCandidate(ind);
        }
        geneticAlgo.initializePopulation();
        geneticAlgo.run();
//        try {
//            runByActions(paths);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
    }
    private List<Action>[] genActionsFromCycle(List<FragmentNode> cycle) {
        FragmentNode cycleStartNode = cycle.get(0);
        List<Action> pathToCycle = buildPath(sourceNode, cycleStartNode);
        List<Action> actions;
        if (cycle.size() < 3) return null;
        if (pathToCycle == null ) {
            if (sourceNode != cycleStartNode) {
                return null;
            }else {
                pathToCycle = new ArrayList<>();
            }
        }
        FragmentNode lastNode = cycleStartNode;
        List<Action> pathInCycle = new ArrayList<>();
        for (FragmentNode nextNode : cycle) {
            if (nextNode == cycleStartNode) continue;
            Action nextAction = buildPath(lastNode, nextNode).get(0);
            pathInCycle.add(nextAction);
            lastNode = nextNode;
        }
        pathInCycle.addAll(buildPath(cycle.get(cycle.size() - 1), cycleStartNode));
//        List<Action> pathInCycleManyTimes = new ArrayList<>();
//        for (int i = 0; i < 50 ; i ++) {
//            pathInCycleManyTimes.addAll(pathInCycle);
//        }
//        pathToCycle.addAll(pathInCycleManyTimes);
        return new List[]{pathToCycle, pathInCycle};
    }
    private FragmentNode getSourceFragment() {
        ClientAdaptor.startApp(d, d.current_pkg);
        FragmentNode sourceFragment = null;
        try {
            ViewTree tree = d.getCurrentTree();
            double max_sm = 0.0;
            for (ActivityNode an : graphAdjustor.appGraph.getActivities()) {
                for (FragmentNode fn : an.getFragments()) {
                    double sm = fn.calc_similarity(tree.getClickable_list());
                    if (sm > max_sm) {
                        max_sm = sm;
                        sourceFragment = fn;
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return sourceFragment;
    }
    private void runByActions(List<Action> actions) throws InterruptedException {
        ClientAdaptor.stopApp(d, d.current_pkg);
        d.actions = actions;
        d.start();
        while(true){
            if (d.Exit){
                d = new Device(d.ip, d.port, d.serial, d.current_pkg, d.password, d.mode);
                scheduler.bind(d);
                break;
            }
            CommonUtil.sleep(2000);
        }
    }
    private void runDeviceByRoutes(List<String> routes) {
        d.setRoute_list(routes);
        ClientAdaptor.stopApp(d, d.current_pkg);
        d.start();
        while(true){
            if (d.Exit){
                checkOutcome(d, graphAdjustor.appGraph.getFragment(routes.get(0)));
                d = new Device(d.ip, d.port, d.serial, d.current_pkg, d.password, d.mode);
                scheduler.bind(d);
                break;
            }
            CommonUtil.sleep(2000);
        }
    }

    private List<Action> buildPath(FragmentNode start, FragmentNode end){
        //FragmentNode start = graphAdjustor.locate(tree)
        List<Action> actions = null;
        if (end == null) return null;

        if (start != null) {
            log("start: " + start.getActivity() + "_" + start.getStructure_hash());
            if (start.getStructure_hash() == end.getStructure_hash()) {
                return null;
            }
            actions = graphAdjustor.BFS(start, end);
            graphAdjustor.resetColor();
            if (actions != null) {
                log("search path success!");
                d.visits.add(start.getSignature());
                return actions;
            }else{
                log("search fail");
            }
        }
        return null;
    }

    private void checkOutcome(Device d, FragmentNode fn){
        try {
            ViewTree tree = d.getCurrentTree();
            double sm = fn.calc_similarity(tree.getClickable_list());
            String s = tree.getActivityName() + "_" + tree.getTreeStructureHash();
            d.log(s + "-" + fn.getSignature() + " size: " + d.visits.size() + " rate: " + sm);

            for(String n: d.visits){
                if (!visits.contains(n)){
                    visits.add(n);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
