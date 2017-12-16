package com.sei.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by vector on 16/6/27.
 */
public class CommonUtil {
    public static String HOST = "http://127.0.0.1:6161";
    public static String SERVER = "http://172.20.66.202:5600";
    public static int DEFAULT_PORT = 5700;
    public static int SLEEPTIME = 1000;
    public static double SIMILARITY = 0.9;
    public static String DIR = "";
    public static String ADB_PATH = "/home/mike/Android/Sdk/platform-tools/";
    public static int SCREEN_X = 768;


    public static void sleep(int milliseconds){
        try{
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static int shuffle(List<Integer> foots, int tot){
//        int ran = (int)(Math.random()*tot);
//        while (foots.contains(ran)){
//            ran = (int)(Math.random()*tot);
//        }
        int ran = -1;
        for(int i=0; i < tot; i++){
            ran = i;
            if (!foots.contains(i)) {
                break;
            }
        }
//        log("shuffle " + (ran + 1) + " / " + tot);
        return ran;
    }


    public static int shuffle_random(List<Integer> foots, int tot){
        int ran = (int)(Math.random()*tot);
        if (foots.size() >= tot)
            return -1;
        while (foots.contains(ran)){
            ran = (int)(Math.random()*tot);
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

    public static void log(String info) {
        System.out.println(info);
    }
}
