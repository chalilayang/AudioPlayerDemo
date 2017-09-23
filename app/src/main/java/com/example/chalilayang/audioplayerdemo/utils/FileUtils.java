package com.example.chalilayang.audioplayerdemo.utils;

import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by chalilayang on 2016/11/16.
 */

public class FileUtils {
    public static String generateNameByDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH:mm:ss");
        return sdf.format(new Date(System.currentTimeMillis()));
    }

    public static String generateRandomFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return sdf.format(new Date(System.currentTimeMillis()));
    }

    public static String parseFileName(String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        int index = path.lastIndexOf(File.separatorChar);
        if (index < 0 && index > path.length() - 2) {
            return path;
        } else {
            return path.substring(index + 1);
        }
    }

    public static String replaceFileName(String path) {
        String result = null;
        String filepath = parseFilePath(path);
        String filename = parseFileName(path);
        if (!TextUtils.isEmpty(filepath) && !TextUtils.isEmpty(filename)) {
            StringBuilder sb = new StringBuilder();
            String tmp = filename.toLowerCase().replace(".mp4", "");
            sb.append(filepath).append(File.separatorChar)
                    .append(tmp).append(generateNameByDate()).append(".mp4");
            result = sb.toString();
        }
        return result;
    }

    public static String generateFileName(String path) {
        String result = null;
        String filename = parseFileName(path);
        if (!TextUtils.isEmpty(filename)) {
            StringBuilder sb = new StringBuilder();
            String tmp = filename.toLowerCase().replace(".mp4", "");
            sb.append(tmp).append(generateNameByDate()).append(".mp4");
            result = sb.toString();
        }
        return result;
    }

    public static String parseFilePath(String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        int index = path.lastIndexOf(File.separatorChar);
        if (index < 0 && index < path.length()-1) {
            return null;
        } else {
            return path.substring(0, index);
        }
    }

    public static boolean renameFile(String oldFilePath, String newFilePath) {
        if (FileUtils.isValid(oldFilePath)) {
            File oldFile = new File(oldFilePath);
            File newFile = new File(newFilePath);
            if (newFile.exists()) {
                newFile.delete();
            }
            boolean result = false;
            if (oldFile.getParent().equals(newFile.getParent())) {
                result = oldFile.renameTo(newFile);
            } else {
                result = copyFileUsingFileChannels(oldFile, newFile);
                if (result) {
                    oldFile.delete();
                }
            }
            return result;
        } else {
            return false;
        }
    }

    public static boolean copyFile(String oldFilePath, String newFilePath) {
        if (FileUtils.isValid(oldFilePath)) {
            File oldFile = new File(oldFilePath);
            File newFile = new File(newFilePath);
            if (newFile.exists()) {
                newFile.delete();
            }
            boolean result = copyFileUsingFileChannels(oldFile, newFile);
            return result;
        } else {
            return false;
        }
    }

    private static boolean copyFileUsingFileChannels(File source, File dest) {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;
        try {
            inputChannel = new FileInputStream(source).getChannel();
            outputChannel = new FileOutputStream(dest).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                inputChannel.close();
                outputChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isValid(String file) {
        if (TextUtils.isEmpty(file)) {
            return false;
        }
        File mFile = new File(file);
        if (!mFile.exists() || mFile.length() <= 0) {
            return false;
        }
        return true;
    }

    public static void deleteFile(String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return;
        }
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
    }
}
