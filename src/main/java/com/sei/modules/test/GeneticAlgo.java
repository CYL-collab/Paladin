package com.sei.modules.test;

import com.sei.agent.Device;
import com.sei.bean.View.Action;
import com.sei.bean.View.ViewTree;
import com.sei.server.component.Decision;
import com.sei.server.component.Scheduler;
import com.sei.util.CommonUtil;
import com.sei.util.ShellUtils2;
import com.sei.util.client.ClientAdaptor;

import java.util.*;

public class GeneticAlgo {
    private static final int POPULATION_SIZE = 20;
    private static final int MAX_GENERATIONS = 50;
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.8;
    private int repeatRunTimes = 50;
    private List<GeneticAlgo.Individual> population;
    private Device d;
    private final Scheduler scheduler;
    private ViewTree currentViewTree;
    private List<GeneticAlgo.Individual> initialPopulationCandidate;

    public GeneticAlgo(Device device, Scheduler scheduler) {
        this.d = device;
        this.scheduler = scheduler;
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
            initialPopulation = extendListToSize(initialPopulationCandidate, GeneticAlgo.POPULATION_SIZE);
        }
        this.population = initialPopulation;
    }

    private static List<GeneticAlgo.Individual> extendListToSize(List<GeneticAlgo.Individual> list, int targetSize) {
        Random random = new Random();
        List<GeneticAlgo.Individual> result = new ArrayList<>(list);

        while (result.size() < targetSize) {
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
        List<Action> actions = individual.getActionsInCycle();
        for (int i = 0; i < repeatRunTimes ; i ++) {
            actions.addAll(individual.getActionsInCycle());
        }
        try{
            runByActions(actions);
        } catch (Exception e){
            return null;
        }
        return individual.metricGrowth + individual.getActionsInCycle().size();
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
                List<Action> childActionsInCycle1 = new ArrayList<>(parent1.getActionsInCycle());
                List<Action> childActionsInCycle2 = new ArrayList<>(parent2.getActionsInCycle());
                List<Action> childActionsToCycle1 = new ArrayList<>(parent1.getActionsToCycle());
                List<Action> childActionsToCycle2 = new ArrayList<>(parent2.getActionsToCycle());

                // 执行交叉操作
                childActionsInCycle1 = crossoverInCycle(childActionsInCycle1, childActionsInCycle2).get(0);
                childActionsInCycle2 = crossoverInCycle(childActionsInCycle1, childActionsInCycle2).get(1);
                GeneticAlgo.Individual child1 = new GeneticAlgo.Individual(childActionsToCycle1, childActionsInCycle1);
                GeneticAlgo.Individual child2 = new GeneticAlgo.Individual(childActionsToCycle2, childActionsInCycle2);

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
        // Implement mutation logic
        return newPopulation;
    }

    private GeneticAlgo.Individual findBestIndividual() {
        // Find the individual with the highest fitness
        return population.get(0); // Placeholder
    }
    public static List<List<Action>> crossoverToCycle(List<Action> parent1, List<Action> parent2) {
        return Arrays.asList(new ArrayList<>(parent1), new ArrayList<>(parent2));
    }
    public static List<List<Action>> crossoverInCycle(List<Action> parent1, List<Action> parent2) {
        if (parent1.isEmpty() || parent2.isEmpty()) return null;

        Optional<String> crossoverPoint = findCommonTarget(parent1, parent2);
        if (!crossoverPoint.isPresent()) {
            // No common target found, cannot perform crossover
            return Arrays.asList(new ArrayList<>(parent1), new ArrayList<>(parent2));
        }

        String commonTarget = crossoverPoint.get();
        int index1 = findFirstOccurrence(parent1, commonTarget);
        int index2 = findFirstOccurrence(parent2, commonTarget);

        // Create new child lists by swapping the tails at the crossover point
        List<Action> child1 = new ArrayList<>(parent1.subList(0, index1 + 1));
        child1.addAll(parent2.subList(index2 + 1, parent2.size()));

        List<Action> child2 = new ArrayList<>(parent2.subList(0, index2 + 1));
        child2.addAll(parent1.subList(index1 + 1, parent1.size()));

        return Arrays.asList(new ArrayList<>(child1), new ArrayList<>(child2));
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
        private List<Action> actionsToCycle;
        private List<Action> actionsInCycle;
        private Double fitness;
        private double metricGrowth;
        public Boolean canExecute;

        public Individual(List<Action> actionsToCycle, List<Action> actionsInCycle) {
            this.actionsToCycle = actionsToCycle;
            this.actionsInCycle = actionsInCycle;
        }

        public List<Action> getActionsToCycle() {
            return actionsToCycle;
        }

        public List<Action> getActionsInCycle() {
            return actionsInCycle;
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
