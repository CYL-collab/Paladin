package com.sei.util;

import com.sei.agent.Device;
import com.sei.bean.View.ViewTree;
import com.sei.util.client.ClientAdaptor;
import jdk.jfr.events.ExceptionThrownEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Created by vector on 16/6/27.
 */
public class CommonUtil {
    public static int DEFAULT_PORT = 5700;
    public static double SIMILARITY = 0.9;
    public static String DIR = "";
    public static String ADB_PATH = "/home/mike/Android/Sdk/platform-tools/";
    public static Boolean SCREENSHORT = false;
    public static Boolean UITree = true;
    public static Boolean INTENT = false;
    public static String SERIAL = "";
    public static Random random = new Random(1100); //trail : 259
    public static int screen_x = 720;
    public static int screen_y = 1280;


    public static void main(String[] argv){
        for(int i=0; i < 15; i++)
            System.out.println(random.nextDouble());
    }

    public static void sleep(int milliseconds){
        try{
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    
    public static int shuffle(List<Integer> foots, int tot){
        int ran = (int)(random.nextDouble() * tot);
        if (foots.size() >= tot)
            return -1;
        while (foots.contains(ran)){
            ran = (int)(random.nextDouble() * tot);
        }
//        log("shuffle " + (ran + 1) + " / " + tot);
        return ran;
    }

    public static String readFromFile(String path){
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while(line != null){
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            String ret = sb.toString();
            return ret;
        }catch(Exception e){
            e.printStackTrace();
            return "";
        }
    }

    public static void getSnapshot(ViewTree tree, Device d){
        if (!SCREENSHORT) return;

        File dir1 = new File("output");
        if (!dir1.exists())
            dir1.mkdir();

        File dir = new File("output/" + ConnectUtil.launch_pkg);
        if (!dir.exists())
            dir.mkdir();

        String picname = tree.getActivityName() + "_" + tree.getTreeStructureHash() + ".png";
        ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial  +" shell screencap -p sdcard/" + picname);
        ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial  +" pull sdcard/" + picname + " " + "output/" + ConnectUtil.launch_pkg + "/");
        ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial  +" shell rm sdcard/" + picname);
        if (UITree) storeTree(tree);
    }

    public static void storeTree(ViewTree tree){
        String treeStr = SerializeUtil.toBase64(tree);

        File dir1 = new File("output");
        if (!dir1.exists())
            dir1.mkdir();

        File dir = new File("output/" + ConnectUtil.launch_pkg);
        if (!dir.exists())
            dir.mkdir();
        try {
            String name = tree.getActivityName() + "_" + tree.getTreeStructureHash() + ".json";
            File file = new File("output/" + ConnectUtil.launch_pkg + "/" + name);
            FileWriter writer = new FileWriter(file);
            writer.write(treeStr);
            writer.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void log(String info) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String S = new SimpleDateFormat("MM-dd HH:mm:ss").format(timestamp);
        System.out.println(S + "\t" + info);
    }

    public static void start_paladin(Device d){
        ClientAdaptor.stopApp(d, "ias.deepsearch.com.helper");
        ClientAdaptor.stopApp(d, ConnectUtil.launch_pkg);
        CommonUtil.sleep(2000);
        ClientAdaptor.startApp(d, "ias.deepsearch.com.helper");
        ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " shell input keyevent KEYCODE_HOME");
        CommonUtil.sleep(2000);
        ClientAdaptor.startApp(d, ConnectUtil.launch_pkg);
        if (d.ip.contains("127.0.0.1"))
            ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " forward tcp:" + d.port + " tcp:6161");
    }

    public static double calc_similarity(List<String> s1, List<String> s2){
        float match = 0f;
        for(String s : s1){
            if (s2.contains(s))
                match += 1;
        }

        int tot = s1.size() + s2.size();
        return 2 * match / tot;
    }
}
