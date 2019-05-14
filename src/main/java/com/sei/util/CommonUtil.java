package com.sei.util;

import com.sei.agent.Device;
import com.sei.bean.Collection.Graph.AppGraph;
import com.sei.bean.View.ViewTree;
import com.sei.util.client.ClientAdaptor;
import jdk.nashorn.tools.Shell;

import java.io.*;
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
    public static Boolean SCREENSHOT = true;
    public static Boolean UITree = true;
    public static Boolean INTENT = false;
    public static String SERIAL = "";
    public static Random random = new Random(233); //trail : 259
    private static Boolean UPLOAD = false;
    public static Boolean WEBVIEW = false;
    public static Boolean DEEPLINK = false;
    public static Boolean SPIDER = false;

    // add bu ycx on 2018/10/10
    // 目前给app赋的权限包括：相机、读取联系人信息、位置、录音、手机状态、打电话、读写外存。
    private static String[] perissiongStrs = {"CAMERA", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "RECORD_AUDIO",
            "READ_PHONE_STATE", "CALL_PHONE", "READ_CONTACTS", "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE"};


    public static void main(String[] argv) {
        AppGraph appGraph = new AppGraph();
        upload(appGraph, "new");
    }

    public static void sleep(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int shuffle(List<Integer> foots, int tot) {
        int ran = (int) (random.nextDouble() * tot);
        if (foots.size() >= tot)
            return -1;
        while (foots.contains(ran)) {
            ran = (int) (random.nextDouble() * tot);
        }
//        log("shuffle " + (ran + 1) + " / " + tot);
        return ran;
    }

    public static String readFromFile(String path) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            String ret = sb.toString();
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void writeToFile(String file, String content) {
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            out.write(content);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void getSnapshot(ViewTree tree, Device d) {
        if (!SCREENSHOT) return;

        String old_launch_pkg = ConnectUtil.launch_pkg;

        if(ConnectUtil.launch_pkg.endsWith(".app")) {
            // 在苹果系统下会无法创建文件夹
            CommonUtil.log("change package name");
            ConnectUtil.launch_pkg = ConnectUtil.launch_pkg.replace(".app", "");
        }

        File dir1 = new File("output");
        if (!dir1.exists())
            dir1.mkdir();

        File dir = new File("output/" + ConnectUtil.launch_pkg);
        if (!dir.exists())
            dir.mkdir();

        if (SPIDER) {
            File dir2 = new File("output/" + ConnectUtil.launch_pkg + "/spider_out_put");
            if (!dir2.exists())
                dir2.mkdir();
        }

        String picname = tree.getActivityName() + "_" + tree.getTreeStructureHash();
        if (SPIDER) {
            picname += "_" + tree.getDeeplink().hashCode();
        }
        picname += ".png";
        ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " shell screencap -p sdcard/" + picname);
        if (SPIDER)
            ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " pull sdcard/" + picname + " " + "output/" + ConnectUtil.launch_pkg + "/spider_out_put/");
        else
            ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " pull sdcard/" + picname + " " + "output/" + ConnectUtil.launch_pkg + "/");
        ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " shell rm sdcard/" + picname);
        if (UITree) storeTree(tree);

        ConnectUtil.launch_pkg = old_launch_pkg;
    }

    public static void storeTree(ViewTree tree) {

        /*
        目前paladin只在spider mode下存储deeplink
        if(DEEPLINK) {
            // save deep link in view tree
            ShellUtils2.execCommand("adb forward tcp:1997 tcp:8080");
            String deeplinks = ConnectUtil.sendHttpGet("http://127.0.0.1:1997/getDeepLinks");
            tree.setDeeplink(deeplinks);
        }*/

        String treeStr = SerializeUtil.toBase64(tree);

        File dir1 = new File("output");
        if (!dir1.exists())
            dir1.mkdir();

        File dir = new File("output/" + ConnectUtil.launch_pkg);
        if (!dir.exists())
            dir.mkdir();
        try {
            String name = tree.getActivityName() + "_" + tree.getTreeStructureHash();
            if (SPIDER) name += "_" + tree.getDeeplink().hashCode();
            name += ".json";
            File file;
            if (SPIDER)
                file = new File("output/" + ConnectUtil.launch_pkg + "/spider_out_put/" + name);
            else
                file = new File("output/" + ConnectUtil.launch_pkg + "/" + name);
            FileWriter writer = new FileWriter(file);
            writer.write(treeStr);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void log(String info) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String S = new SimpleDateFormat("MM-dd HH:mm:ss").format(timestamp);
        System.out.println(S + "\t" + info);
    }

    public static void log(String serial, String info) {
        log("device #" + serial + ": " + info);
    }

    public static void start_paladin(Device d) {
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

    public static double calc_similarity(List<String> s1, List<String> s2) {
        float match = 0f;
        for (String s : s1) {
            if (s2.contains(s))
                match += 1;
        }

        int tot = s1.size() + s2.size();
        return 2 * match / tot;
    }

    public static void setScreenSize(Device d) {
        ShellUtils2.CommandResult result = ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " shell dumpsys window | grep init");
        String info = result.successMsg;
        // format: init=768X1280 320dpi
        int p1 = info.indexOf("=");
        int p2 = info.indexOf("x");
        int p3 = info.indexOf(" ", p1);
        if (p1 == -1 || p2 == -1 || p3 == -1) {
            log("set screen size fail, info: " + info);
            return;
        }
        d.screenWidth = Integer.parseInt(info.substring(p1 + 1, p2));
        d.screenHeight = Integer.parseInt(info.substring(p2 + 1, p3));

    }

    public static void upload(AppGraph appGraph, String current) {
        if (!UPLOAD) return;
        try {
            String content = SerializeUtil.toBase64(appGraph);
            //JSONObject jo = new JSONObject(content);
            com.alibaba.fastjson.JSONObject jo = new com.alibaba.fastjson.JSONObject();
            jo.put("graph", content);
            jo.put("current", current);
            ConnectUtil.postJson("http://127.0.0.1:5000/upload", jo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getDeeplink() {
        ShellUtils2.execCommand("adb forward tcp:1997 tcp:8080");
        String deeplink = ConnectUtil.sendHttpGet("http://127.0.0.1:1997/getDeepLink");
        return deeplink;
    }


    public static void grantPermission(Device d) {
        for (String perisionStr : perissiongStrs) {
            ShellUtils2.execCommand(CommonUtil.ADB_PATH + "adb -s " + d.serial + " shell pm grant " + ConnectUtil.launch_pkg + " android.permission." + perisionStr);
        }
    }
}