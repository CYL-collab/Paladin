package com.sei.modules.test;

import com.sei.agent.Device;
import com.sei.bean.Collection.Graph.FragmentNode;
import com.sei.bean.Collection.Graph.GraphAdjustor;
import com.sei.bean.View.Action;
import com.sei.bean.View.ViewTree;
import com.sei.server.component.Scheduler;
import com.sei.util.CommonUtil;
import com.sei.util.SerializeUtil;
import com.sei.util.ShellUtils2;
import com.sei.util.client.ClientAdaptor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sei.util.CommonUtil.random;

public class GeneticAlgo{
    private static final int POPULATION_SIZE = 15;
    private static final int MAX_GENERATIONS = 15;
    private static final double MUTATION_RATE = 0.5;
    private static final double CROSSOVER_RATE = 0.2;
    private final int repeatRunTimes = 10;
    private final int savingInterval = 2;
    private final int maxCycleLength = 20;
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
    public void run() {
        long startTime = System.currentTimeMillis();
        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            evaluatePopulation();
            if (generation % savingInterval == 0 || generation == MAX_GENERATIONS - 1) {
                long timePassed = (System.currentTimeMillis() - startTime) / 1000;
                // savePopulation(generation, timePassed);
            }
            population = selectAndReproduce();
            mutate(population);
        }
        evaluatePopulation();
        long timePassed = (System.currentTimeMillis() - startTime) / 1000;
        // savePopulation(MAX_GENERATIONS, timePassed);
    }
    public void runPareto() {
        long startTime = System.currentTimeMillis();
        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            evaluatePopulationPareto();
            if (generation % savingInterval == 0) {
                long timePassed = (System.currentTimeMillis() - startTime) / 1000;
                // savePopulation(generation, timePassed);
            }
            population = selectAndReproducePareto();
            mutate(population);
        }
        evaluatePopulationPareto();
        long timePassed = (System.currentTimeMillis() - startTime) / 1000;
        // savePopulation(MAX_GENERATIONS, timePassed);
    }
    public void runPseudo() {
        long startTime = System.currentTimeMillis();
        String fileName = "population_over_generations.csv";
        try (FileWriter writer = new FileWriter(fileName)) {
            // 写入文件头部
            writer.write("generation,id,cycle,length,fitness_metric\n");
            for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
                evaluatePopulationPseudo();
                double entropy = calculateEntropy();
                CommonUtil.log("Gen:" + generation + " Entropy: " + entropy);
                long timePassed = (System.currentTimeMillis() - startTime) / 1000;
                savePopulation(writer, generation, 0); // 注意这里传递了writer
                population = selectAndReproducePareto();
                mutate(population);
            }
            evaluatePopulationPseudo();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to " + fileName, e);
        }
    }
    private void savePopulation(FileWriter writer, int generation, long timePassed) {
        try {
            for (Individual individual : population) {
                List<String> signatures = individual.getFragmentsInCycle().stream()
                        .map(FragmentNode::getSignature)
                        .collect(Collectors.toList());
                // 注意加入generation作为数据列
                writer.write(generation + "," + individual.hashCode() + "," + "\"" + signatures + "\"" + "," + individual.getFragmentsInCycle().size() + "," + individual.getFitnesses().get(0) + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save population for generation " + generation, e);
        }
    }

    private double calculateEntropy () {
        Map<Individual, Integer> frequencyMap = new HashMap<>();
        // 计算频率
        for (Individual individual : population) {
            frequencyMap.put(individual, frequencyMap.getOrDefault(individual, 0) + 1);
        }

        double entropy = 0.0;
        int size = population.size();
        // 计算概率和熵
        for (int freq : frequencyMap.values()) {
            double probability = (double) freq / size;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        return entropy;
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

    public void initializePopulationWithDiversion() {
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
        if (list.isEmpty()) {
            throw new RuntimeException("Initial Population Empty!");
        }

        Random random = new Random();
        List<GeneticAlgo.Individual> result = new ArrayList<>(list);

        while (result.size() < GeneticAlgo.POPULATION_SIZE) {
            // 随机选取一个元素并复制
            int randomIndex = random.nextInt(result.size());
            result.add(result.get(randomIndex));
        }

        return result;
    }

    private void runIndividual(Individual individual, List<Action> actions, List<FragmentNode> fragments) throws InterruptedException {
        d.searchActions = actions;
        d.searchFragments = fragments;
        d.start();
        // d.start();
        while(true){
            if (d.Exit){
                if (!d.searchRunnable)
                    individual.canExecute = false;
                d = new Device(d.ip, d.port, d.serial, d.current_pkg, d.password, d.mode);
                d.bind(scheduler, graphAdjustor);
                scheduler.bind(d);
                break;
            }
            CommonUtil.sleep(1000);
        }
    }
    private void evaluatePopulation() {
        for (GeneticAlgo.Individual individual : population) {
            if (individual.fitness > 0 && individual.canExecute)
                continue;
            if (!individual.canExecute) {
                individual.fitness = -1.0;
                population.remove(individual);
                initialPopulationCandidate.remove(individual);
                if (!initialPopulationCandidate.isEmpty()) {
                    int addIndex = random.nextInt(initialPopulationCandidate.size());
                    population.add(initialPopulationCandidate.get(addIndex));
                }
            }
            Double fitness = evaluateFitness(individual);
            if (fitness == null) {
                individual.canExecute = false;
                individual.setFitness(-1.0);
            } else {
                individual.canExecute = true;
                individual.setFitness(fitness);
//                for (Individual candidate : initialPopulationCandidate) {
//                    if (individual.equals(candidate))
//                        candidate.setFitness(fitness);
//                }
            }
        }
    }

    private void evaluatePopulationPareto() {
        for (int i = 0; i < population.size() ; i ++) {
            Individual individual = population.get(i);
            if (individual.fitness_metric > 0 && individual.fitness_len > 0 && individual.canExecute)
                continue;
            if (!individual.canExecute) {
                individual.fitness_metric = -1.0;
                individual.fitness_len = -1.0;
                population.remove(individual);
                i--;
                initialPopulationCandidate.remove(individual);
                if (!initialPopulationCandidate.isEmpty()) {
                    int addIndex = random.nextInt(initialPopulationCandidate.size());
                    population.add(initialPopulationCandidate.get(addIndex));
                }
            }
            double[] fitnesses = evaluateFitnessPareto(individual);
            if (fitnesses != null) {
                individual.canExecute = true;
                individual.setFitnesses(fitnesses[0], fitnesses[1]);
            } else {
                individual.canExecute = false;
                individual.setFitnesses(-1.0, -1.0);
            }
        }
    }
    private void evaluatePopulationPseudo() {
        for (int i = 0; i < population.size() ; i ++) {
            Individual individual = population.get(i);
            if (individual.fitness_metric > 0 && individual.fitness_len > 0 && individual.canExecute)
                continue;
            if (!individual.canExecute) {
                individual.fitness_metric = -1.0;
                individual.fitness_len = -1.0;
                population.remove(individual);
                i--;
                initialPopulationCandidate.remove(individual);
                if (!initialPopulationCandidate.isEmpty()) {
                    int addIndex = random.nextInt(initialPopulationCandidate.size());
                    population.add(initialPopulationCandidate.get(addIndex));
                }
            }
            individual.fitness_len = (double) 1 / individual.getActionsInCycle().size();
            if (isLeaked(individual)) {
                individual.fitness_metric = 10000.0;
            } else {
                individual.fitness_metric = 0.0;
            }
            if (individual.getFragmentsInCycle().size() > 10) {
                individual.fitness_metric = -1.0;
            }
        }
    }
    private boolean isLeaked(Individual individual) {
        boolean isLeaked = false;
//        for (FragmentNode fn1 : individual.getFragmentsInCycle()) {
//            if (Objects.equals(fn1.getSignature(), "MainActivity_1360430170")
//                || Objects.equals(fn1.getSignature(), "MainActivity_1495749363")
//            ) {
//                for (FragmentNode fn2 : individual.getFragmentsInCycle()) {
//                    if (Objects.equals(fn2.getSignature(), "MainActivity_-847221117")
//                        || Objects.equals(fn2.getSignature(), "MainActivity_-2075763518")
//                        || Objects.equals(fn2.getSignature(), "MainActivity_1314884666")
//                        || Objects.equals(fn2.getSignature(), "MainActivity_-1696953291")
//                        || Objects.equals(fn2.getSignature(), "MainActivity_1062520850")
//                        || Objects.equals(fn2.getSignature(), "MainActivity_862220217")
//                        || Objects.equals(fn2.getSignature(), "MainActivity_-571006335")
//                    ) {
//                        for (FragmentNode fn3 : individual.getFragmentsInCycle()) {
//                            if (!Objects.equals(fn2.getSignature(), "MainActivity_-847221117")
//                                || !Objects.equals(fn2.getSignature(), "MainActivity_-2075763518")
//                                || !Objects.equals(fn2.getSignature(), "MainActivity_1314884666")
//                                || !Objects.equals(fn2.getSignature(), "MainActivity_-1696953291")
//                                || !Objects.equals(fn2.getSignature(), "MainActivity_1062520850")
//                                || !Objects.equals(fn2.getSignature(), "MainActivity_862220217")
//                                || !Objects.equals(fn2.getSignature(), "MainActivity_-571006335")
//                                || !Objects.equals(fn1.getSignature(), "MainActivity_1360430170")
//                                || !Objects.equals(fn1.getSignature(), "MainActivity_1495749363")) {
//                                CommonUtil.log("Found leaked individual in Category fragment, cycle size: " + individual.getFragmentsInCycle().size());
//                                isLeaked = true;
//                                break;
//                            }
//                        }
//                        if (isLeaked) break;
//                    }
//                }
//            }
//            if (isLeaked) break;
//        }
        for (FragmentNode fn1 : individual.getFragmentsInCycle()) {
            if (Objects.equals(fn1.getSignature(), "MainActivity_-392515493")
                || Objects.equals(fn1.getSignature(), "MainActivity_1180766279")
                || Objects.equals(fn1.getSignature(), "MainActivity_1023513372")
                || Objects.equals(fn1.getSignature(), "MainActivity_-1750633259")
            ) {
                for (FragmentNode fn2 : individual.getFragmentsInCycle()) {
                    if (Objects.equals(fn2.getSignature(), "CreateAndEditRecipeActivity_1977549779")) {
                        CommonUtil.log("Found leaked individual in CreateAndEditRecipe Activity, cycle size: " + individual.getFragmentsInCycle().size());
                        isLeaked = true;
                        break;
                    }
                }
            }
            if (isLeaked) break;
        }
        return isLeaked;
    }
    private Double evaluateFitness(GeneticAlgo.Individual individual) {
        List<Action> actions = new ArrayList<>(individual.getActionsToCycle());
        List<FragmentNode> fragments = new ArrayList<>(individual.getFragmentsToCycle());
        for (int i = 0; i < repeatRunTimes ; i ++) {
            actions.addAll(individual.getActionsInCycle());
            fragments.addAll(individual.getFragmentsInCycle());
        }
        Double rssGrowth = null;
        Double pssGrowth = null;
        try{
            ClientAdaptor.stopApp(d, d.current_pkg);
            ClientAdaptor.startApp(d, d.current_pkg);
            List<Double> metricBefore = collectMetric();
            Double pssBefore = metricBefore.get(0);
            Double rssBefore = metricBefore.get(1);
            runIndividual(individual, actions, fragments);
            List<Double> metricAfter = collectMetric();
            Double pssAfter = metricAfter.get(0);
            Double rssAfter = metricAfter.get(1);
            pssGrowth = pssAfter - pssBefore;
            rssGrowth = rssAfter - rssBefore;
        } catch (Exception e){
            return null;
        }
        if (!individual.canExecute) {
            return null;
        }
        return Math.max(pssGrowth + rssGrowth, 0) + 100000 / (individual.getActionsInCycle().size()+individual.getActionsToCycle().size());
    }

    private double[] evaluateFitnessPareto(GeneticAlgo.Individual individual) {
        List<Action> actions = new ArrayList<>(individual.getActionsToCycle());
        List<FragmentNode> fragments = new ArrayList<>(individual.getFragmentsToCycle());
        for (int i = 0; i < repeatRunTimes ; i ++) {
            actions.addAll(individual.getActionsInCycle());
            fragments.addAll(individual.getFragmentsInCycle());
        }
        Double rssGrowth = null;
        Double pssGrowth = null;
        try{
            ClientAdaptor.stopApp(d, d.current_pkg);
            ClientAdaptor.startApp(d, d.current_pkg);
            List<Double> metricBefore = collectMetric();
            Double pssBefore = metricBefore.get(0);
            Double rssBefore = metricBefore.get(1);
            runIndividual(individual, actions, fragments);
            List<Double> metricAfter = collectMetric();
            Double pssAfter = metricAfter.get(0);
            Double rssAfter = metricAfter.get(1);
            pssGrowth = pssAfter - pssBefore;
            rssGrowth = rssAfter - rssBefore;
        } catch (Exception e){
            return null;
        }
        if (!individual.canExecute) {
            return null;
        }
        return new double[]{Math.max(pssGrowth + rssGrowth, 0), (double) 1 / individual.getActionsInCycle().size()};
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
        List<GeneticAlgo.Individual> chosenAncestors = new ArrayList<>();
        Random random = new Random();

        // 进行POPULATION_SIZE / 2次选择，因为每次选择都会产生两个后代
        for (int i = 0; i < POPULATION_SIZE / 2; i++) {
            GeneticAlgo.Individual parent1 = selectIndividualByRouletteWheel(totalFitness);
            GeneticAlgo.Individual parent2 = selectIndividualByRouletteWheel(totalFitness);
            chosenAncestors.add(parent1);
            chosenAncestors.add(parent2);
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
                boolean isChildrenSameAsParents = true;
                int attempt = 0;
                while (isChildrenSameAsParents && attempt < 3) {
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

                    // 检查子代个体是否与父代个体相同
                    isChildrenSameAsParents = child1.equals(parent1) && child2.equals(parent2);

                    // 如果相同,则切换到另一种交叉方法
                    if (isChildrenSameAsParents) {
                        mutationType = (mutationType + 1) % 3;
                        attempt += 1;
                    }
                }
                newPopulation.add(child1);
                newPopulation.add(child2);
            } else {
                // 如果不进行交叉，直接将父代添加到新的种群中
                newPopulation.add(parent1);
                newPopulation.add(parent2);
            }
        }
        List<Individual> diff = new ArrayList<>(population);
        diff.removeAll(chosenAncestors);
        initialPopulationCandidate.addAll(diff);
        return newPopulation;
    }

    private List<GeneticAlgo.Individual> selectAndReproducePareto() {
//        double totalFitness_metric = 0.0;
//        double totalFitness_len = 0.0;
//        for (Individual individual : population) {
//            List<Double> fitnesses = individual.getFitnesses();
//            totalFitness_metric += fitnesses.get(0);
//            totalFitness_len += fitnesses.get(1);
//        }
        List<GeneticAlgo.Individual> newPopulation = new ArrayList<>();
        List<GeneticAlgo.Individual> chosenAncestors = new ArrayList<>();
        Random random = new Random();

        // 进行POPULATION_SIZE / 2次选择，因为每次选择都会产生两个后代
        for (int i = 0; i < POPULATION_SIZE / 2; i++) {
            GeneticAlgo.Individual parent1 = selectIndividualByRouletteWheelPareto();
            GeneticAlgo.Individual parent2 = selectIndividualByRouletteWheelPareto();
            chosenAncestors.add(parent1);
            chosenAncestors.add(parent2);
            // 确保两个父代不相同
            while (parent1 == parent2) {
                parent2 = selectIndividualByRouletteWheelPareto();
            }

            // 根据交叉率决定是否进行交叉
            if (random.nextDouble() < CROSSOVER_RATE) {
                GeneticAlgo.Individual child1 = null;
                GeneticAlgo.Individual child2 = null;
                int mutationType = random.nextInt(3); // 0, 1, 或 2，分别代表删除、增加、系统事件
                List<Individual> children = null;
                boolean isChildrenSameAsParents = true;
                int attempt = 0;
                while (isChildrenSameAsParents && attempt < 3) {
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

                    if (child1.getActionsToCycle().size() > maxCycleLength) {
                        child1 = parent1;
                    }

                    if (child2.getActionsToCycle().size() > maxCycleLength) {
                        child2 = parent2;
                    }

                    // 检查子代个体是否与父代个体相同
                    isChildrenSameAsParents = child1.equals(parent1) && child2.equals(parent2);

                    // 如果相同,则切换到另一种交叉方法
                    if (isChildrenSameAsParents) {
                        mutationType = (mutationType + 1) % 3;
                        attempt += 1;
                    }
                }
                newPopulation.add(child1);
                newPopulation.add(child2);
            } else {
                // 如果不进行交叉，直接将父代添加到新的种群中
                newPopulation.add(parent1);
                newPopulation.add(parent2);
            }
        }
        List<Individual> diff = new ArrayList<>(population);
        diff.removeAll(chosenAncestors);
        initialPopulationCandidate.addAll(diff);
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

    private GeneticAlgo.Individual selectIndividualByRouletteWheelPareto() {
        double[] ranks = new double[population.size()];
        double totalRank = 0.0;
        for (int i = 0; i < population.size(); i++) {
            Individual ind = population.get(i);
            int rank = getRank(ind, population);
            ranks[i] = Math.sqrt(rank);
            totalRank += ranks[i];
        }

        double r = Math.random() * totalRank;
        double sum = 0.0;
        for (int i = 0; i < ranks.length; i++) {
            sum += ranks[i];
            if (sum > r) {
                return population.get(i);
            }
        }
        // 如果所有层级都为空,则返回最后一个个体
        return population.get(population.size() - 1);
    }

    private boolean dominates(Individual individual1, Individual individual2) {
        double metric1 = individual1.getFitnesses().get(0);
        double metric2 = individual2.getFitnesses().get(0);
        double len1 = individual1.getFitnesses().get(1);
        double len2 = individual2.getFitnesses().get(1);

        // 如果individual1在两个目标上都优于individual2,则individual1支配individual2
        return metric1 >= metric2 && len1 >= len2 && (metric1 > metric2 || len1 > len2);
    }

    public int getRank(Individual ind, List<Individual> population) {
        int rank = 1;
        for (Individual other : population) {
            if (dominates(ind, other)) {
                rank++;
            }
        }
        return rank;
    }

    private List<GeneticAlgo.Individual> mutate(List<GeneticAlgo.Individual> population) {
        List<GeneticAlgo.Individual> newPopulation = new ArrayList<>();
        for (Individual individual : population) {
            Individual newIndividual = individual;
            Random random = new Random();
            if (random.nextDouble() < MUTATION_RATE) {
                // 选择变异类型
                int mutationType = random.nextInt(3); // 0, 1, 或 2，分别代表删除、增加、系统事件
                switch (mutationType) {
                    case 0:
                        newIndividual = mutateByDeletion(individual);
                        break;
                    case 1:
                        newIndividual = mutateByAddition(individual);
                        break;
                    case 2:
                        newIndividual = mutateByDeletion(individual);
                        break;
                }
            }
            newPopulation.add(newIndividual);
            if (newIndividual != individual) {
                initialPopulationCandidate.add(individual);
            }
        }
        return newPopulation;
    }

    private Individual mutateByDeletion(GeneticAlgo.Individual individual) {
        if (individual.getActionsInCycle().size() < 6) {
            return individual;
        }
        List<Integer> deletedIndexCandidate = new ArrayList<>();
        List<Integer> deletedActionNum = new ArrayList<>();
        List<Action> redirectedActionCandidate = new ArrayList<>();
        for (int num = 4 ; num > 0 ; num--){
            for (int i = 1 ; i < individual.getActionsInCycle().size() - num ; i++) {
                FragmentNode fn_after = individual.getFragmentsInCycle().get(i+num);
                FragmentNode fn_before = individual.getFragmentsInCycle().get(i-1);
                for (Action action : fn_before.getAllPaths()) {
                    if (Objects.equals(action.target, fn_after.getSignature())) {
                        deletedIndexCandidate.add(i);
                        deletedActionNum.add(num);
                        redirectedActionCandidate.add(action);
                        break;
                    }
                }
            }
        }
        if (deletedIndexCandidate.isEmpty()) {
            return individual;
        } else {
            Individual newIndividual = new Individual(individual);
            int mutationIndex = random.nextInt(deletedIndexCandidate.size());
            for (int j = 0; j < deletedActionNum.get(mutationIndex); j++) {
                newIndividual.actionsInCycle.remove((int)deletedIndexCandidate.get(mutationIndex));
                newIndividual.fragmentsInCycle.remove((int)deletedIndexCandidate.get(mutationIndex));
            }
            newIndividual.actionsInCycle.set(deletedIndexCandidate.get(mutationIndex) - 1, redirectedActionCandidate.get(mutationIndex));
            newIndividual.setFitness(-1);
            newIndividual.setFitnesses(-1, -1);
            return newIndividual;
        }
    }
    private Individual mutateByAddition(GeneticAlgo.Individual individual) {
        List<Integer> addIndexCandidate = new ArrayList<>();
        List<Action> addActionCandidate = new ArrayList<>();
        List<FragmentNode> addFragmentCandidate = new ArrayList<>();
        List<Action> redirectedActionCandidate = new ArrayList<>();
        for (int i = 0 ; i < individual.getActionsInCycle().size() - 1 ; i++) {
            String fn_after = individual.getActionsInCycle().get(i).target;
            FragmentNode fn_before = individual.getFragmentsInCycle().get(i);
            for (Action action_before : fn_before.getAllPaths()) {
                FragmentNode fn_mid = graphAdjustor.appGraph.getFragment(action_before.target);
                for (Action action_mid : fn_mid.getAllPaths()) {
                    if (Objects.equals(action_mid.target, fn_after)) {
                        addIndexCandidate.add(i);
                        addFragmentCandidate.add(fn_mid);
                        addActionCandidate.add(action_mid);
                        redirectedActionCandidate.add(action_before);
                    }
                }
            }
        }
        if (addActionCandidate.isEmpty()) {
            return individual;
        } else {
            Individual newIndividual = new Individual(individual);
            int mutationIndex = random.nextInt(addActionCandidate.size());
            newIndividual.actionsInCycle.add(addIndexCandidate.get(mutationIndex) + 1, addActionCandidate.get(mutationIndex));
            newIndividual.fragmentsInCycle.add(addIndexCandidate.get(mutationIndex), addFragmentCandidate.get(mutationIndex));
            newIndividual.actionsInCycle.set(addIndexCandidate.get(mutationIndex), redirectedActionCandidate.get(mutationIndex));
            newIndividual.setFitness(-1);
            newIndividual.setFitnesses(-1, -1);
            return newIndividual;
        }
    }
    private Individual mutateBySystemEvent(GeneticAlgo.Individual individual) {
        int mutationIndex = random.nextInt(individual.getActionsInCycle().size());
        int mutationType = random.nextInt(2); // 0, 1, 或 2，分别代表不同系统事件
        FragmentNode fragmentToMutate = individual.getFragmentsInCycle().get(mutationIndex);
        Individual newIndividual = new Individual(individual);
        switch (mutationType) {
            case 0:
                newIndividual.actionsInCycle.add(mutationIndex, new Action(null, Action.action_list.ROWDOWN));
                newIndividual.fragmentsInCycle.add(mutationIndex, fragmentToMutate);
                newIndividual.actionsInCycle.add(mutationIndex, new Action(null, Action.action_list.ROWRIGHT));
                newIndividual.fragmentsInCycle.add(mutationIndex, fragmentToMutate);
                newIndividual.setFitness(-1);
                newIndividual.setFitnesses(-1, -1);
                break;
            case 1:
                newIndividual.actionsInCycle.add(mutationIndex, new Action(null, Action.action_list.DISABLEBT));
                newIndividual.fragmentsInCycle.add(mutationIndex, fragmentToMutate);
                newIndividual.actionsInCycle.add(mutationIndex, new Action(null, Action.action_list.ENABLEBT));
                newIndividual.fragmentsInCycle.add(mutationIndex, fragmentToMutate);
                newIndividual.setFitness(-1);
                newIndividual.setFitnesses(-1, -1);
                break;
        }
        return newIndividual;
    }

    private GeneticAlgo.Individual findBestIndividual() {
        // Find the individual with the highest fitness
        return population.get(0); // Placeholder
    }
    public List<GeneticAlgo.Individual> crossoverInCycle(GeneticAlgo.Individual parent1, GeneticAlgo.Individual parent2) {
        List<Action> parentActionsToCycle1 = new ArrayList<>(parent1.getActionsToCycle());
        List<Action> parentActionsToCycle2 = new ArrayList<>(parent2.getActionsToCycle());
        List<Action> parentActionsInCycle1 = new ArrayList<>(parent1.getActionsInCycle());
        List<Action> parentActionsInCycle2 = new ArrayList<>(parent2.getActionsInCycle());
        List<FragmentNode> parentFragmentsToCycle1 = new ArrayList<>(parent1.getFragmentsToCycle());
        List<FragmentNode> parentFragmentsToCycle2 = new ArrayList<>(parent2.getFragmentsToCycle());
        List<FragmentNode> parentFragmentsInCycle1 = new ArrayList<>(parent1.getFragmentsInCycle());
        List<FragmentNode> parentFragmentsInCycle2 = new ArrayList<>(parent2.getFragmentsInCycle());
        List<FragmentNode> commonFragmentsInCycle = new ArrayList<>(parentFragmentsInCycle1);
        commonFragmentsInCycle.retainAll(parentFragmentsInCycle2);
        GeneticAlgo.Individual child1 = parent1;
        GeneticAlgo.Individual child2 = parent2;
        if (commonFragmentsInCycle.isEmpty()) {
            return Arrays.asList(parent1, parent2);
        }
        int crossoverIndex = random.nextInt(commonFragmentsInCycle.size());
        FragmentNode crossoverPoint = commonFragmentsInCycle.get(crossoverIndex);
        int crossoverIndex1 = parentFragmentsInCycle1.indexOf(crossoverPoint);
        int crossoverIndex2 = parentFragmentsInCycle2.indexOf(crossoverPoint);
        try{
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

            child1 = new Individual(parentActionsToCycle1, childActionsInCycle1, parentFragmentsToCycle1, childFragmentsInCycle1);
            child2 = new Individual(parentActionsToCycle2, childActionsInCycle2, parentFragmentsToCycle2, childFragmentsInCycle2);
        } catch (Exception e) {
            CommonUtil.log(String.valueOf(e));
        }
        return Arrays.asList(child1, child2);
    }
    public List<GeneticAlgo.Individual> crossoverAcrossCycle(GeneticAlgo.Individual parent1, GeneticAlgo.Individual parent2) {
        List<Action> parentActionsToCycle1 = new ArrayList<>(parent1.getActionsToCycle());
        List<Action> parentActionsToCycle2 = new ArrayList<>(parent2.getActionsToCycle());
        List<Action> parentActionsInCycle1 = new ArrayList<>(parent1.getActionsInCycle());
        List<Action> parentActionsInCycle2 = new ArrayList<>(parent2.getActionsInCycle());
        List<FragmentNode> parentFragmentsToCycle1 = new ArrayList<>(parent1.getFragmentsToCycle());
        List<FragmentNode> parentFragmentsToCycle2 = new ArrayList<>(parent2.getFragmentsToCycle());
        List<FragmentNode> parentFragmentsInCycle1 = new ArrayList<>(parent1.getFragmentsInCycle());
        List<FragmentNode> parentFragmentsInCycle2 = new ArrayList<>(parent2.getFragmentsInCycle());
        GeneticAlgo.Individual child1 = parent1;
        GeneticAlgo.Individual child2 = parent2;

        // parent1ToCycle with parent2InCycle
        List<FragmentNode> commonFragmentsAcrossCycle2 = new ArrayList<>(parentFragmentsToCycle1);
        commonFragmentsAcrossCycle2.retainAll(parentFragmentsInCycle2);
        try {
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
        } finally {
        }
        return Arrays.asList(child1, child2);
    }
    public List<GeneticAlgo.Individual> crossoverToCycle(GeneticAlgo.Individual parent1, GeneticAlgo.Individual parent2) {
        List<Action> parentActionsToCycle1 = new ArrayList<>(parent1.getActionsToCycle());
        List<Action> parentActionsToCycle2 = new ArrayList<>(parent2.getActionsToCycle());
        List<Action> parentActionsInCycle1 = new ArrayList<>(parent1.getActionsInCycle());
        List<Action> parentActionsInCycle2 = new ArrayList<>(parent2.getActionsInCycle());
        List<FragmentNode> parentFragmentsToCycle1 = new ArrayList<>(parent1.getFragmentsToCycle());
        List<FragmentNode> parentFragmentsToCycle2 = new ArrayList<>(parent2.getFragmentsToCycle());
        List<FragmentNode> parentFragmentsInCycle1 = new ArrayList<>(parent1.getFragmentsInCycle());
        List<FragmentNode> parentFragmentsInCycle2 = new ArrayList<>(parent2.getFragmentsInCycle());
        if (parentActionsToCycle1.isEmpty() || parentActionsToCycle2.isEmpty()) return Arrays.asList(parent1, parent2);

        List<FragmentNode> commonFragmentsToCycle = new ArrayList<>(parentFragmentsToCycle1);
        commonFragmentsToCycle.retainAll(parentFragmentsToCycle2);
        if (commonFragmentsToCycle.isEmpty()) {
            return Arrays.asList(parent1, parent2);
        }
        int crossoverIndex = random.nextInt(commonFragmentsToCycle.size());
        FragmentNode crossoverPoint = commonFragmentsToCycle.get(crossoverIndex);
        int index1 = parentFragmentsToCycle1.indexOf(crossoverPoint);
        int index2 = parentFragmentsToCycle2.indexOf(crossoverPoint);

        // Create new child lists by swapping the tails at the crossover point
        try {
            List<Action> childActionsToCycle1 = new ArrayList<>(parentActionsToCycle1.subList(0, index1));
            List<FragmentNode> childFragmentsToCycle1 = new ArrayList<>(parentFragmentsToCycle1.subList(0, index1));
            childActionsToCycle1.addAll(parentActionsToCycle2.subList(index2, parentActionsToCycle2.size()));
            childFragmentsToCycle1.addAll(parentFragmentsToCycle2.subList(index2, parentFragmentsToCycle2.size()));

            List<Action> childActionsToCycle2 = new ArrayList<>(parentActionsToCycle2.subList(0, index2));
            List<FragmentNode> childFragmentsToCycle2 = new ArrayList<>(parentFragmentsToCycle2.subList(0, index2));
            childActionsToCycle2.addAll(parentActionsToCycle1.subList(index1, childActionsToCycle1.size()));
            childFragmentsToCycle2.addAll(parentFragmentsToCycle1.subList(index1, parentFragmentsToCycle1.size()));

            GeneticAlgo.Individual child1 = new Individual(childActionsToCycle1, parentActionsInCycle2, childFragmentsToCycle1, parentFragmentsInCycle2);
            GeneticAlgo.Individual child2 = new Individual(childActionsToCycle2, parentActionsInCycle1, childFragmentsToCycle2, parentFragmentsInCycle1);
            return Arrays.asList(child1, child2);
        } catch (Exception e) {
            return Arrays.asList(parent1, parent2);
        }

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
    public static class Individual implements Serializable {
        public List<Action> actionsToCycle;
        public List<FragmentNode> fragmentsToCycle;
        public List<Action> actionsInCycle;
        public List<FragmentNode> fragmentsInCycle;
        public int dominatingCount;
        public List<Individual> dominatedIndividuals;
        private Double fitness;
        private Double fitness_metric;
        private Double fitness_len;
        public Boolean canExecute = true;

        public Individual(List<Action> actionsToCycle, List<Action> actionsInCycle, Set<FragmentNode> fragmentSet) {
            this.actionsToCycle = new ArrayList<>(actionsToCycle); // 创建新的列表,避免共享引用
            this.actionsInCycle = new ArrayList<>(actionsInCycle);
            this.fragmentsToCycle = setFragmentsByActions(fragmentSet, this.actionsToCycle);
            this.fragmentsInCycle = setFragmentsByActions(fragmentSet, this.actionsInCycle);
            this.fitness = -1.0;
            this.fitness_metric = -1.0;
            this.fitness_len = -1.0;
        }
        public Individual(List<Action> actionsToCycle, List<Action> actionsInCycle, List<FragmentNode> fragmentsToCycle, List<FragmentNode> fragmentsInCycle) {
            this.actionsToCycle = new ArrayList<>(actionsToCycle);
            this.actionsInCycle = new ArrayList<>(actionsInCycle);
            this.fragmentsToCycle = new ArrayList<>(fragmentsToCycle);
            this.fragmentsInCycle = new ArrayList<>(fragmentsInCycle);
            this.fitness = -1.0;
            this.fitness_metric = -1.0;
            this.fitness_len = -1.0;
        }
        public Individual(Individual individual) {
            this.actionsToCycle = new ArrayList<>(individual.getActionsToCycle());
            this.actionsInCycle = new ArrayList<>(individual.getActionsInCycle());
            this.fragmentsToCycle = new ArrayList<>(individual.getFragmentsToCycle());
            this.fragmentsInCycle = new ArrayList<>(individual.getFragmentsInCycle());
            this.fitness = individual.getFitness();
            this.fitness_metric = individual.getFitnesses().get(0);
            this.fitness_len = individual.getFitnesses().get(1);
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
        public void setFitnesses(double fitness_metric, double fitness_len) {
            this.fitness_len = fitness_len;
            this.fitness_metric = fitness_metric;
        }
        public List<Double> getFitnesses() {
            return new ArrayList<Double>(Arrays.asList(this.fitness_metric, this.fitness_len));
        }
        public double getFitness() {
            return fitness;
        }
        public boolean validate() {
            for (int i = 0 ; i < actionsToCycle.size() - 1 ; i++) {
                Action action = actionsToCycle.get(i);
                FragmentNode fn_before = fragmentsToCycle.get(i);
                FragmentNode fn_next = fragmentsToCycle.get(i+1);
                if (!Objects.equals(action.target, fn_next.getSignature())) {
                    return false;
                }
            }
            for (int i = 0 ; i < actionsInCycle.size() - 1 ; i++) {
                Action action = actionsInCycle.get(i);
                FragmentNode fn_before = fragmentsInCycle.get(i);
                FragmentNode fn_next = fragmentsInCycle.get(i+1);
                if (!Objects.equals(action.target, fn_next.getSignature())) {
                    return false;
                }
            }
            return true;
        }
        @Override
        public int hashCode() {
            return this.fragmentsToCycle.hashCode() + this.fragmentsInCycle.hashCode();
        }
        @Override
        public boolean equals(Object obj) {
            // 1. 检查非空性
            if (obj == null) {
                return false;
            }

            // 2. 检查是否为同一个对象
            if (this == obj) {
                return true;
            }

            // 3. 检查对象类型
            if (getClass() != obj.getClass()) {
                return false;
            }

            // 4. 转型并比较属性值
            Individual other = (Individual) obj;
            return (this.fragmentsToCycle.equals(other.fragmentsToCycle)) && (this.fragmentsInCycle.equals(other.fragmentsInCycle));
        }
    }
}
