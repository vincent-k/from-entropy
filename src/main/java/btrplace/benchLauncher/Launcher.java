package btrplace.benchLauncher;

import btrplace.json.JSONConverterException;
import btrplace.json.model.InstanceConverter;
import btrplace.model.Attributes;
import btrplace.model.Instance;
import btrplace.model.VM;
import btrplace.plan.ReconfigurationPlan;
import btrplace.plan.event.Action;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ChocoReconfigurationAlgorithm;
import btrplace.solver.choco.DefaultChocoReconfigurationAlgorithm;
import btrplace.solver.choco.transition.KeepRunningVM;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * Created by vkherbac on 16/09/14.
 */
public class Launcher {

    public static void main(String[] args) {

        String src = null, dst = null;
        if (args.length < 3 || args.length > 4 || !args[args.length-2].equals("-o")) {
            usage(1);
        }

        // Create and customize a reconfiguration algorithm
        ChocoReconfigurationAlgorithm cra = new DefaultChocoReconfigurationAlgorithm();
        // TODO: manage with cmdline options
        cra.setTimeLimit(300);
        cra.setVerbosity(0);

        if (args[0].equals("--repair")) {
            cra.doRepair(true);
            src = args[1];
        }
        else {
            cra.doRepair(false);
            src = args[0];
        }
        dst = args[args.length-1];

        // Read the input JSON instance
        JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
        Object obj = null;
        try {
            // Check for gzip extension
            if (src.endsWith(".gz")) {
                obj = parser.parse(new InputStreamReader(new GZIPInputStream(new FileInputStream(src))));
            }
            else {
                obj = parser.parse(new FileReader(src));
            }

        } catch (ParseException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        JSONObject o =  (JSONObject) obj;

        // Convert the json object to an instance
        InstanceConverter conv = new InstanceConverter();
        Instance i = null;
        try {
            i = conv.fromJSON(o);
        } catch (JSONConverterException e) {
            e.printStackTrace();
        }

        //Set custom attributes
        setAttributes(i);

        // Try to solve
        ReconfigurationPlan plan = null;
        try {
            plan = cra.solve(i.getModel(), i.getSatConstraints());
        } catch (SolverException e) {
            e.printStackTrace();
        }

        // Save stats to a CSV file
        try {
            createCSV(dst, plan, cra);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setAttributes(Instance i) {

        Attributes attrs = i.getModel().getAttributes();
        for (VM vm : i.getModel().getMapping().getAllVMs()) {
            // Hypervisor
            //attrs.put(vm, "template", "kvm");
            // Cannot be re-instantiate
            attrs.put(vm, "clone", true);
            // Actions
            //attrs.put(vm, "boot", 5);
            // => halt on entropy
            //attrs.put(vm, "shutdown", 2);
            attrs.put(vm, "forge", 3);
            // Migration duration: Memory/10 => BUG (npe)
            //attrs.put(vm, "migrate", i.getModel().getInteger(vm, "memory") / 10);
            //attrs.put(vm, "suspend", 4);
            //attrs.put(vm, "resume", 5);
            //attrs.put(vm, "allocate", 5);
            attrs.put(vm, "kill", 2);

        }
        /*for (Node n : i.getModel().getMapping().getAllNodes()) {
            attrs.put(n, "boot", 6);
            attrs.put(n, "shutdown", 6);
        }*/
    }

    public static void createCSV(String fileName, ReconfigurationPlan plan, ChocoReconfigurationAlgorithm cra) throws IOException {

        FileWriter writer = new FileWriter(fileName);
        writer.append("metric;value\n");

        // Store plan parameters
        if (plan != null) {
            writer.append("planDuration;" + plan.getDuration() + '\n');
            writer.append("planSize;" + plan.getSize() + '\n');
            writer.append("planActionsSize;" + plan.getActions().size() + '\n');
        }

        for (Action a : plan.getActions()) {
            if (a instanceof KeepRunningVM) {
                //writer.append("planRelocateActions;"+plan.getActions());
            }
        }
        writer.append("craSolutionTime;" + cra.getStatistics().getSolutions().get(0).getTime() + '\n');

        // Store reconf. algo. stats
        writer.append("craCoreRPBuildDuration;"+String.valueOf(cra.getStatistics().getCoreRPBuildDuration())+'\n');
        writer.append("craNbBacktracks;"+String.valueOf(cra.getStatistics().getNbBacktracks())+'\n');
        writer.append("craNbConstraints;"+String.valueOf(cra.getStatistics().getNbConstraints())+'\n');
        writer.append("craNbManagedVMs;"+String.valueOf(cra.getStatistics().getNbManagedVMs())+'\n');
        writer.append("craNbNodes;"+String.valueOf(cra.getStatistics().getNbNodes())+'\n');
        writer.append("craNbSearchNodes;"+String.valueOf(cra.getStatistics().getNbSearchNodes())+'\n');
        writer.append("craNbVMs;"+String.valueOf(cra.getStatistics().getNbVMs())+'\n');
        writer.append("craSolvingDuration;"+String.valueOf(cra.getStatistics().getSolvingDuration())+'\n');
        writer.append("craNbSolutions;"+String.valueOf(cra.getStatistics().getSolutions().size())+'\n');
        writer.append("craSpeRPDuration;"+String.valueOf(cra.getStatistics().getSpeRPDuration())+'\n');
        writer.append("craStart;"+String.valueOf(cra.getStatistics().getStart()));

        writer.flush();
        writer.close();
    }

    public static void usage(int code) {
        System.out.println("Usage: converter [--repair] src -o output");
        System.out.println("\tsrc: the json instance to read");
        System.out.println("\toutput: the output statistics file.");
        System.out.println("\t--repair: option to enable the 'repair' feature");
        System.exit(code);
    }
}
