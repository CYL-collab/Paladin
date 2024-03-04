package com.sei.modules.test;

import com.sei.agent.Device;
import com.sei.bean.Collection.Graph.FragmentNode;
import com.sei.bean.Collection.Graph.GraphAdjustor;
import com.sei.bean.View.Action;
import com.sei.bean.View.ViewTree;
import com.sei.server.component.Scheduler;
import com.sei.util.CommonUtil;
import com.sei.util.ShellUtils2;
import com.sei.util.client.ClientAdaptor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sei.util.CommonUtil.random;

public class GeneticAlgo {
    private static final int POPULATION_SIZE = 50;
    private static final int MAX_GENERATIONS = 50;
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.8;
    private int repeatRunTimes = 50;
    private List<GeneticAlgo.Individual> population;
    private Device d;
    private final Scheduler scheduler;
    private final GraphAdjustor graphAdjustor;
    private ViewTree currentViewTree;
    private List<GeneticAlgo.Individual> initialPopulationCandidate;

    public GeneticAlgo(Device device, Scheduler scheduler) {
        this.d = device;
        this.scheduler = scheduler;
        this.graphAdjustor = scheduler.graphAdjustor;
        this.initialPopulationCandidate = new ArrayList<GeneticAlgo.Individual>();
    }

    public GeneticAlgo.Individual run() {
        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            evaluatePopulation();
            List<GeneticAlgo.Individual> newPopulation = selectAndReproduce();
            newPopulation = mutate(newPopulation);
            population = newPopulation;
        }
        return findBestIndividual();
    }
    public void addToInitialPopulationCandidate(GeneticAlgo.Individual individual) {
        initialPopulationCandidate.add(individual);
    }
    public void initializePopulation() {
        Collections.shuffle(initialPopulationCandidate);
        List<GeneticAlgo.Individual> initialPopulation;
        if (GeneticAlgo.POPULATION_SIZE <= initialPopulationCandidate.size()) {
            initialPopulation = initialPopulationCandidate.subList(0, GeneticAlgo.POPULATION_SIZE);
        } else {
            initialPopulation = extendListToSize(initialPopulationCandidate);
        }
        this.population = initialPopulation;
    }

    private static List<GeneticAlgo.Individual> extendListToSize(List<Individual> list) {
        Random random = new Random();
        List<GeneticAlgo.Individual> result = new ArrayList<>(list);

        while (result.size() < GeneticAlgo.POPULATION_SIZE) {
            // 随机选取一个元素并复制
            int randomIndex = random.nextInt(result.size());
            result.add(result.get(randomIndex));
        }

        return result;
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
            CommonUtil.sleep(1000);
        }
    }
    private void evaluatePopulation() {
        for (GeneticAlgo.Individual individual : population) {
            if (individual.fitness > 0) {
                individual.canExecute = true;
                continue;
            }
            Double fitness = evaluateFitness(individual);
            if (fitness == null) {
                individual.canExecute = false;
                individual.setFitness(0);
            } else {
                individual.canExecute = true;
                individual.setFitness(fitness);
            }
        }
    }

    private Double evaluateFitness(GeneticAlgo.Individual individual) {
        List<Action> actions = individual.getActionsToCycle();
        for (int i = 0; i < repeatRunTimes ; i ++) {
            actions.addAll(individual.getActionsInCycle());
        }
        Double rssGrowth = null;
        Double pssGrowth = null;
        try{
            List<Double> metricBefore = collectMetric();
            Double pssBefore = metricBefore.get(0);
            Double rssBefore = metricBefore.get(1);
            runByActions(actions);
            List<Double> metricAfter = collectMetric();
            Double pssAfter = metricAfter.get(0);
            Double rssAfter = metricAfter.get(1);
            pssGrowth = pssAfter - pssBefore;
            rssGrowth = rssAfter - rssBefore;
        } catch (Exception e){
            return null;
        }
        return Math.max(pssGrowth + rssGrowth, 0) + 10 / individual.getActionsInCycle().size();
    }

    private List<Double> collectMetric() {
        String memoryInfo = Objects.requireNonNull(ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " shell dumpsys meminfo " + d.current_pkg)).successMsg;
        Pattern pssPattern = Pattern.compile("TOTAL PSS:\\s*(\\d+)");
        Pattern rssPattern = Pattern.compile("TOTAL RSS:\\s*(\\d+)");

        Matcher pssMatcher = pssPattern.matcher(memoryInfo);
        Matcher rssMatcher = rssPattern.matcher(memoryInfo);
        Double pss = null;
        Double rss = null;

        if (pssMatcher.find()) {
            pss = Double.parseDouble(pssMatcher.group(1));
        }

        if (rssMatcher.find()) {
            rss = Double.parseDouble(rssMatcher.group(1));
        }

        return Arrays.asList(pss, rss);
    }

    private List<GeneticAlgo.Individual> selectAndReproduce() {
        double totalFitness = population.stream().mapToDouble(GeneticAlgo.Individual::getFitness).sum();
        List<GeneticAlgo.Individual> newPopulation = new ArrayList<>();
        Random random = new Random();

        // 进行POPULATION_SIZE / 2次选择，因为每次选择都会产生两个后代
        for (int i = 0; i < POPULATION_SIZE / 2; i++) {
            GeneticAlgo.Individual parent1 = selectIndividualByRouletteWheel(totalFitness);
            GeneticAlgo.Individual parent2 = selectIndividualByRouletteWheel(totalFitness);

            // 确保两个父代不相同
            while (parent1 == parent2) {
                parent2 = selectIndividualByRouletteWheel(totalFitness);
            }

            // 根据交叉率决定是否进行交叉
            if (random.nextDouble() < CROSSOVER_RATE) {
                GeneticAlgo.Individual child1 = null;
                GeneticAlgo.Individual child2 = null;
                int mutationType = random.nextInt(3); // 0, 1, 或 2，分别代表删除、增加、系统事件
                List<Individual> children = null;
                switch (mutationType) {
                    case 0:
                        children = crossoverToCycle(parent1, parent2);
                        break;
                    case 1:
                        children = crossoverInCycle(parent1, parent2);
                        break;
                    case 2:
                        children = crossoverAcrossCycle(parent1, parent2);
                        break;
                }
                child1 = children.get(0);
                child2 = children.get(1);
                newPopulation.add(child1);
                newPopulation.add(child2);
            } else {
                // 如果不进行交叉，直接将父代添加到新的种群中
                newPopulation.add(parent1);
                newPopulation.add(parent2);
            }
        }

        return newPopulation;
    }

    private GeneticAlgo.Individual selectIndividualByRouletteWheel(double totalFitness) {
        double slice = Math.random() * totalFitness;
        double total = 0;
        for (GeneticAlgo.Individual individual : population) {
            total += individual.getFitness();
            if (total >= slice) {
                return individual;
            }
        }
        return population.get(population.size() - 1); // 防止未选择到个体
    }


    private List<GeneticAlgo.Individual> mutate(List<GeneticAlgo.Individual> newPopulation) {
        for (Individual individual : newPopulation) {
            Random random = new Random();
            if (random.nextDouble() < MUTATION_RATE) {
                // 选择变异类型
                int mutationType = random.nextInt(3); // 0, 1, 或 2，分别代表删除、增加、系统事件
                switch (mutationType) {
                    case 0:
                        mutateByDeletion(individual);
                        break;
                    case 1:
                        mutateByAddition(individual);
                        break;
                    case 2:
                        mutateBySystemEvent(individual);
                        break;
                }
            }
        }
        return newPopulation;
    }

    private void mutateByDeletion(GeneticAlgo.Individual individual) {
        List<Action> deletedActionCandidate = new ArrayList<>();
        List<Action> redirectedActionCandidate = new ArrayList<>();
        for (int i = 1 ; i < individual.getActionsInCycle().size() - 1 ; i++) {
            String fn_after = individual.getActionsInCycle().get(i).target;
            FragmentNode fn_before = individual.getActionsInCycle().get(i-1).findFragmentByAction(graphAdjustor.appGraph.getDirectedGraph().vertexSet());
            for (Action action : fn_before.getAllPaths()) {
                if (Objects.equals(action.target, fn_after)) {
                    deletedActionCandidate.add(individual.getActionsInCycle().get(i));
                    redirectedActionCandidate.add(action);
                    break;
                }
            }
        }
        if (deletedActionCandidate.isEmpty()) {
            return;
        } else {
            int mutationIndex = random.nextInt(deletedActionCandidate.size());
            individual.actionsInCycle.remove(deletedActionCandidate.get(mutationIndex));
            individual.actionsInCycle.set(mutationIndex - 1, redirectedActionCandidate.get(mutationIndex));
        }
    }
    private void mutateByAddition(GeneticAlgo.Individual individual) {
        List<Action> addActionCandidate = new ArrayList<>();
        List<Action> redirectedActionCandidate = new ArrayList<>();
        for (int i = 0 ; i < individual.getActionsInCycle().size() - 1 ; i++) {
            String fn_after = individual.getActionsInCycle().get(i).target;
            FragmentNode fn_before = individual.getActionsInCycle().get(i).findFragmentByAction(graphAdjustor.appGraph.getDirectedGraph().vertexSet());
            for (Action action_before : fn_before.getAllPaths()) {
                FragmentNode fn_mid = graphAdjustor.appGraph.getFragment(action_before.target);
                for (Action action_mid : fn_mid.getAllPaths()) {
                    if (Objects.equals(action_mid.target, fn_after)) {
                        addActionCandidate.add(action_mid);
                        redirectedActionCandidate.add(action_before);
                    }
                }
            }
        }
        if (addActionCandidate.isEmpty()) {
            return;
        } else {
            int mutationIndex = random.nextInt(addActionCandidate.size());
            individual.actionsInCycle.add(addActionCandidate.get(mutationIndex));
            individual.actionsInCycle.set(mutationIndex - 1, redirectedActionCandidate.get(mutationIndex));
        }
    }
    private void mutateBySystemEvent(GeneticAlgo.Individual individual) {
        int mutationIndex = random.nextInt(individual.getActionsInCycle().size());
        int mutationType = random.nextInt(2); // 0, 1, 或 2，分别代表不同系统事件
        switch (mutationType) {
            case 0:
                individual.actionsInCycle.add(mutationIndex, new Action(null, Action.action_list.ROWDOWN));
                individual.actionsInCycle.add(mutationIndex, new Action(null, Action.action_list.ROWRIGHT));
                break;
            case 1:
                individual.actionsInCycle.add(mutationIndex, new Action(null, Action.action_list.DISABLEBT));
                individual.actionsInCycle.add(mutationIndex, new Action(null, Action.action_list.ENABLEBT));
                break;
        }
    }

    private GeneticAlgo.Individual findBestIndividual() {
        // Find the individual with the highest fitness
        return population.get(0); // Placeholder
    }
    public List<GeneticAlgo.Individual> crossoverInCycle(GeneticAlgo.Individual parent1, GeneticAlgo.Individual parent2) {
        List<Action> parentActionsToCycle1 = parent1.getActionsToCycle();
        List<Action> parentActionsToCycle2 = parent2.getActionsToCycle();
        List<Action> parentActionsInCycle1 = parent1.getActionsInCycle();
        List<Action> parentActionsInCycle2 = parent2.getActionsInCycle();
        List<FragmentNode> parentFragmentsToCycle1 = parent1.getFragmentsToCycle();
        List<FragmentNode> parentFragmentsToCycle2 = parent2.getFragmentsToCycle();
        List<FragmentNode> parentFragmentsInCycle1 = parent1.getFragmentsInCycle();
        List<FragmentNode> parentFragmentsInCycle2 = parent2.getFragmentsInCycle();

        List<FragmentNode> commonFragmentsInCycle = new ArrayList<>(parentFragmentsInCycle1);
        commonFragmentsInCycle.retainAll(parentFragmentsInCycle2);
        if (commonFragmentsInCycle.isEmpty()) {
            return Arrays.asList(parent1, parent2);
        }
        int crossoverIndex = random.nextInt(commonFragmentsInCycle.size());
        FragmentNode crossoverPoint = commonFragmentsInCycle.get(crossoverIndex);
        int crossoverIndex1 = parentFragmentsInCycle1.indexOf(crossoverPoint);
        int crossoverIndex2 = parentFragmentsInCycle2.indexOf(crossoverPoint);

        List<Action> childActionsInCycle1 = new ArrayList<>();
        childActionsInCycle1.addAll(parentActionsInCycle1.subList(0, crossoverIndex1));
        childActionsInCycle1.addAll(parentActionsInCycle2.subList(crossoverIndex2, parentActionsInCycle2.size()));
        childActionsInCycle1.addAll(parentActionsInCycle2.subList(0, crossoverIndex2));
        childActionsInCycle1.addAll(parentActionsInCycle1.subList(crossoverIndex1, parentActionsInCycle1.size()));
        List<FragmentNode> childFragmentsInCycle1 = new ArrayList<>();
        childFragmentsInCycle1.addAll(parentFragmentsInCycle1.subList(0, crossoverIndex1));
        childFragmentsInCycle1.addAll(parentFragmentsInCycle2.subList(crossoverIndex2, parentFragmentsInCycle2.size()));
        childFragmentsInCycle1.addAll(parentFragmentsInCycle2.subList(0, crossoverIndex2));
        childFragmentsInCycle1.addAll(parentFragmentsInCycle1.subList(crossoverIndex1, parentFragmentsInCycle1.size()));

        List<Action> childActionsInCycle2 = new ArrayList<>();
        childActionsInCycle2.addAll(parentActionsInCycle2.subList(0, crossoverIndex2));
        childActionsInCycle2.addAll(parentActionsInCycle1.subList(crossoverIndex1, parentActionsInCycle1.size()));
        childActionsInCycle2.addAll(parentActionsInCycle1.subList(0, crossoverIndex1));
        childActionsInCycle2.addAll(parentActionsInCycle2.subList(crossoverIndex2, parentActionsInCycle2.size()));
        List<FragmentNode> childFragmentsInCycle2 = new ArrayList<>();
        childFragmentsInCycle2.addAll(parentFragmentsInCycle2.subList(0, crossoverIndex2));
        childFragmentsInCycle2.addAll(parentFragmentsInCycle1.subList(crossoverIndex1, parentFragmentsInCycle1.size()));
        childFragmentsInCycle2.addAll(parentFragmentsInCycle1.subList(0, crossoverIndex1));
        childFragmentsInCycle2.addAll(parentFragmentsInCycle2.subList(crossoverIndex2, parentFragmentsInCycle2.size()));

        GeneticAlgo.Individual child1 = new Individual(parentActionsToCycle1, childActionsInCycle1, parentFragmentsToCycle1, childFragmentsInCycle1);
        GeneticAlgo.Individual child2 = new Individual(parentActionsToCycle2, childActionsInCycle2, parentFragmentsToCycle2, childFragmentsInCycle2);
        return Arrays.asList(child1, child2);
    }
    public List<GeneticAlgo.Individual> crossoverAcrossCycle(GeneticAlgo.Individual parent1, GeneticAlgo.Individual parent2) {
        List<Action> parentActionsToCycle1 = parent1.getActionsToCycle();
        List<Action> parentActionsToCycle2 = parent2.getActionsToCycle();
        List<Action> parentActionsInCycle1 = parent1.getActionsInCycle();
        List<Action> parentActionsInCycle2 = parent2.getActionsInCycle();
        List<FragmentNode> parentFragmentsToCycle1 = parent1.getFragmentsToCycle();
        List<FragmentNode> parentFragmentsToCycle2 = parent2.getFragmentsToCycle();
        List<FragmentNode> parentFragmentsInCycle1 = parent1.getFragmentsInCycle();
        List<FragmentNode> parentFragmentsInCycle2 = parent2.getFragmentsInCycle();
        GeneticAlgo.Individual child1 = parent1;
        GeneticAlgo.Individual child2 = parent2;

        // parent1ToCycle with parent2InCycle
        List<FragmentNode> commonFragmentsAcrossCycle2 = new ArrayList<>(parentFragmentsToCycle1);
        commonFragmentsAcrossCycle2.retainAll(parentFragmentsInCycle2);
        if (!commonFragmentsAcrossCycle2.isEmpty()) {
            FragmentNode crossoverPoint = commonFragmentsAcrossCycle2.get(random.nextInt(commonFragmentsAcrossCycle2.size()));
            int crossoverIndex1 = parentFragmentsToCycle1.indexOf(crossoverPoint);
            int crossoverIndex2 = parentFragmentsInCycle2.indexOf(crossoverPoint);
            List<Action> childActionsToCycle = new ArrayList<>();
            List<FragmentNode> childFragmentsToCycle = new ArrayList<>();
            childActionsToCycle.addAll(parentActionsToCycle1.subList(0, crossoverIndex1));
            childActionsToCycle.addAll(parentActionsInCycle2.subList(crossoverIndex2, parentActionsInCycle2.size()));
            childFragmentsToCycle.addAll(parentFragmentsToCycle1.subList(0, crossoverIndex1));
            childFragmentsToCycle.addAll(parentFragmentsInCycle2.subList(crossoverIndex2, parentFragmentsInCycle2.size()));
            child1 = new Individual(childActionsToCycle, parentActionsInCycle2, childFragmentsToCycle, parentFragmentsInCycle2);
        }

        // parent2ToCycle with parent1InCycle
        List<FragmentNode> commonFragmentsAcrossCycle1 = new ArrayList<>(parentFragmentsToCycle2);
        commonFragmentsAcrossCycle1.retainAll(parentFragmentsInCycle1);
        if (!commonFragmentsAcrossCycle1.isEmpty()) {
            FragmentNode crossoverPoint = commonFragmentsAcrossCycle1.get(random.nextInt(commonFragmentsAcrossCycle1.size()));
            int crossoverIndex1 = parentFragmentsToCycle2.indexOf(crossoverPoint);
            int crossoverIndex2 = parentFragmentsInCycle1.indexOf(crossoverPoint);
            List<Action> childActionsToCycle = new ArrayList<>();
            List<FragmentNode> childFragmentsToCycle = new ArrayList<>();
            childActionsToCycle.addAll(parentActionsToCycle2.subList(0, crossoverIndex1));
            childActionsToCycle.addAll(parentActionsInCycle1.subList(crossoverIndex2, parentActionsInCycle1.size()));
            childFragmentsToCycle.addAll(parentFragmentsToCycle2.subList(0, crossoverIndex1));
            childFragmentsToCycle.addAll(parentFragmentsInCycle1.subList(crossoverIndex2, parentFragmentsInCycle1.size()));
            child2 = new Individual(childActionsToCycle, parentActionsInCycle1, childFragmentsToCycle, parentFragmentsInCycle1);
        }

        return Arrays.asList(child1, child2);
    }
    public List<GeneticAlgo.Individual> crossoverToCycle(GeneticAlgo.Individual parent1, GeneticAlgo.Individual parent2) {
        List<Action> parentActionsToCycle1 = parent1.getActionsToCycle();
        List<Action> parentActionsToCycle2 = parent2.getActionsToCycle();
        if (parentActionsToCycle1.isEmpty() || parentActionsToCycle2.isEmpty()) return null;

        Optional<String> crossoverPoint = findCommonTarget(parentActionsToCycle1, parentActionsToCycle2);
        if (!crossoverPoint.isPresent()) {
            // No common target found, cannot perform crossover
            return Arrays.asList(parent1, parent2);
        }

        String commonTarget = crossoverPoint.get();
        int index1 = findFirstOccurrence(parentActionsToCycle1, commonTarget);
        int index2 = findFirstOccurrence(parentActionsToCycle2, commonTarget);

        // Create new child lists by swapping the tails at the crossover point
        List<Action> childActionsToCycle1 = new ArrayList<>(parentActionsToCycle1.subList(0, index1 + 1));
        childActionsToCycle1.addAll(parentActionsToCycle2.subList(index2 + 1, parentActionsToCycle2.size()));

        List<Action> childActionsToCycle2 = new ArrayList<>(parentActionsToCycle2.subList(0, index2 + 1));
        childActionsToCycle2.addAll(childActionsToCycle1.subList(index1 + 1, childActionsToCycle1.size()));

        GeneticAlgo.Individual child1 = new Individual(childActionsToCycle1, parent2.getActionsInCycle(), graphAdjustor.appGraph.getDirectedGraph().vertexSet());
        GeneticAlgo.Individual child2 = new Individual(childActionsToCycle2, parent1.getActionsInCycle(), graphAdjustor.appGraph.getDirectedGraph().vertexSet());
        return Arrays.asList(child1, child2);
    }

    private static Optional<String> findCommonTarget(List<Action> parent1, List<Action> parent2) {
        for (Action action1 : parent1) {
            for (Action action2 : parent2) {
                if (action1.target.equals(action2.target)) {
                    return Optional.of(action1.target);
                }
            }
        }
        return Optional.empty();
    }

    private static int findFirstOccurrence(List<Action> actions, String target) {
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).target.equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private static List<Action> validateAndAdjust(List<Action> child) {
        // 实现验证和调整逻辑，确保子代的操作序列合法
        // 这可能包括删除不合法的操作，或者在不改变程序逻辑的前提下调整操作顺序
        return null;
    }
    // Inner class to represent an individual in the population
    public static class Individual {
        public List<Action> actionsToCycle;
        public List<FragmentNode> fragmentsToCycle;
        public List<Action> actionsInCycle;
        public List<FragmentNode> fragmentsInCycle;
        private Double fitness;
        private List<Double> fitnesses;
        public Boolean canExecute;

        public Individual(List<Action> actionsToCycle, List<Action> actionsInCycle, Set<FragmentNode> fragmentSet) {
            this.actionsToCycle = actionsToCycle;
            this.actionsInCycle = actionsInCycle;
            this.fragmentsToCycle = setFragmentsByActions(fragmentSet, actionsToCycle);
            this.fragmentsInCycle = setFragmentsByActions(fragmentSet, actionsInCycle);
            this.fitness = -1.0;
        }
        public Individual(List<Action> actionsToCycle, List<Action> actionsInCycle, List<FragmentNode> fragmentsToCycle, List<FragmentNode> fragmentsInCycle) {
            this.actionsToCycle = actionsToCycle;
            this.actionsInCycle = actionsInCycle;
            this.fragmentsToCycle = fragmentsToCycle;
            this.fragmentsInCycle = fragmentsInCycle;
            this.fitness = -1.0;
        }
        private List<FragmentNode> setFragmentsByActions(Set<FragmentNode> fragmentSet, List<Action> actions) {
            List<FragmentNode> fragments = new ArrayList<>();
            for (Action action : actions) {
                fragments.add(fragments.size(), action.findFragmentByAction(fragmentSet));
            }
            return fragments;
        }
        public List<Action> getActionsToCycle() {
            return actionsToCycle;
        }

        public List<Action> getActionsInCycle() {
            return actionsInCycle;
        }

        public List<FragmentNode> getFragmentsToCycle() {
            return fragmentsToCycle;
        }

        public List<FragmentNode> getFragmentsInCycle() {
            return fragmentsInCycle;
        }

        public void setFitness(double fitness) {
            this.fitness = fitness;
        }

        public double getFitness() {
            return fitness;
        }

        // Other methods for individual management
    }
}
