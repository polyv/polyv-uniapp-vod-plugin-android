package com.easefun.plvvod;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.easefun.plvvod.utils.JsonOptionUtil;
import com.easefun.plvvod.utils.PolyvErrorMessageUtils;
import com.easefun.plvvod.vo.VideoInfoVO;
import com.easefun.polyvsdk.PolyvBitRate;
import com.easefun.polyvsdk.PolyvDownloader;
import com.easefun.polyvsdk.PolyvDownloaderErrorReason;
import com.easefun.polyvsdk.PolyvDownloaderManager;
import com.easefun.polyvsdk.PolyvSDKClient;
import com.easefun.polyvsdk.PolyvSDKUtil;
import com.easefun.polyvsdk.download.listener.IPolyvDownloaderProgressListener2;
import com.easefun.polyvsdk.download.listener.IPolyvDownloaderStopListener;
import com.easefun.polyvsdk.download.util.PolyvDownloaderUtils;
import com.easefun.polyvsdk.video.PolyvValidateM3U8VideoReturnType;
import com.easefun.polyvsdk.video.PolyvVideoUtil;
import com.easefun.polyvsdk.vo.PolyvValidateLocalVideoVO;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.common.WXModule;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


public class PolyvDownloadModule extends WXModule {

    private String TAG = "PolyvDownloadModule";

    private static final String SHARED_PREFERENCES_NAME = "download_shared_preferences_key";
    private static final String VIDEO_ID_LIST_KEY = "video_id_list_key";


    /**
     * vid最后回调的下载中时间戳Map
     */
    private Map<String, Long> lastCallbackProgressTimeMap = new HashMap<String, Long>();
    /**
     * vid最后下载进度
     */
    private Map<String, Integer> videoLastDownloadProgress = new HashMap<String, Integer>();
    /**
     * 下载中回调间隔秒
     */
    private int downloadingCallbackIntervalSeconds = 0;


    @JSMethod(uiThread = true)
    public void addDownloader(JSONObject options, final JSCallback callback) {
        if (options == null) {
            return;
        }

        JSONArray downloadArr = options.getJSONArray("downloadArr");

        SharedPreferences sharedPreferences = mWXSDKInstance.getContext().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> set = sharedPreferences.getStringSet(VIDEO_ID_LIST_KEY, new TreeSet<String>());

        int length = downloadArr == null ? 0 : downloadArr.size();
        JSONObject jsonObject;

        JSONObject error = null;
        SharedPreferences.Editor editor = null;
        for (int i = 0; i < length; i++) {
            jsonObject = downloadArr.getJSONObject(i);
            if (jsonObject != null) {
                final String vid = JsonOptionUtil.getString(jsonObject, "vid", "");
                if (PolyvSDKUtil.validateVideoId(vid)) {
                    int level = JsonOptionUtil.getInt(jsonObject, "level", PolyvBitRate.liuChang.getNum());

                    PolyvBitRate bitRate = PolyvBitRate.getBitRate(level);
                    if (bitRate == null) {
                        bitRate = PolyvBitRate.liuChang;
                    }

                    if (set.contains(vid) && sharedPreferences.contains(vid)) {
                        int saveLevel = sharedPreferences.getInt(vid, PolyvBitRate.liuChang.getNum());
                        if (bitRate.getNum() != saveLevel) {
                            if (error == null) {
                                error = new JSONObject();
                            }

                            if (callback != null) {
                                JSONObject vidJsonObject = new JSONObject();
                                vidJsonObject.put("errMsg", "有其他码率视频在下载列表，请先删除原有的再添加");
                                Log.d(TAG, "addDownloader: vid=" + vid + " already have other bitrate in download list");
                                error.put(vid, vidJsonObject);
                            }

                        }
                    }
                    Log.d(TAG, "addDownloader: vid=" + vid + " level=" + bitRate.getNum());
                    final PolyvDownloader polyvDownloader = PolyvDownloaderManager.getPolyvDownloader(vid, bitRate.getNum());
                    polyvDownloader.setPolyvDownloadProressListener2(new IPolyvDownloaderProgressListener2() {
                        @Override
                        public void onDownload(long current, long total) {
                            int progress = (int) (current * 100 / total);
                            videoLastDownloadProgress.put(vid, progress);
                            Log.d(TAG, progress + "");

                            long currentTimeMillis = System.currentTimeMillis();
                            if (lastCallbackProgressTimeMap.containsKey(vid)) {
                                if (currentTimeMillis - lastCallbackProgressTimeMap.get(vid) < downloadingCallbackIntervalSeconds * 1000) {
                                    return;
                                }
                            }

                            lastCallbackProgressTimeMap.put(vid, currentTimeMillis);

                            JSONObject result = new JSONObject();
                            JSONObject vidJsonObject = new JSONObject();
                            vidJsonObject.put("downloadStatus", "downloading");
                            vidJsonObject.put("downloadPercentage", progress);
                            result.put(vid, vidJsonObject);
                            if (callback != null) {
                                callback.invokeAndKeepAlive(result);
                            }
                        }

                        @Override
                        public void onDownloadSuccess(int i) {
                            JSONObject result = new JSONObject();
                            JSONObject vidJsonObject = new JSONObject();
                            vidJsonObject.put("downloadStatus", "finished");
                            vidJsonObject.put("downloadPercentage", 100);
                            Log.d(TAG, "DownloadSuccess: " + vid);
                            result.put(vid, vidJsonObject);
                            if (callback != null) {
                                callback.invokeAndKeepAlive(result);
                            }
                        }

                        @Override
                        public void onDownloadFail(@NonNull PolyvDownloaderErrorReason polyvDownloaderErrorReason) {
                            JSONObject result = new JSONObject();
                            JSONObject vidJsonObject = new JSONObject();

                            vidJsonObject.put("downloadStatus", "failed");
                            vidJsonObject.put("downloadPercentage", 0);
                            String message = PolyvErrorMessageUtils.getDownloaderErrorMessage(polyvDownloaderErrorReason.getType());
                            message += "(error code " + polyvDownloaderErrorReason.getType().getCode() + ")";
                            vidJsonObject.put("errMsg", message);
                            Log.d(TAG, "DownloadFail: " + message);

                            result.put(vid, vidJsonObject);

                            if (callback != null) {
                                callback.invokeAndKeepAlive(result);
                            }
                        }
                    });
                    polyvDownloader.setPolyvDownloadStopListener(new IPolyvDownloaderStopListener() {
                        @Override
                        public void onStop() {
                            JSONObject result = new JSONObject();
                            JSONObject vidJsonObject = new JSONObject();
                            vidJsonObject.put("downloadStatus", "stopped");
                            result.put(vid, vidJsonObject);
                            if (callback != null) {
                                callback.invokeAndKeepAlive(result);
                            }
                        }
                    });


                    if (!set.contains(vid)) {
                        set.add(vid);

                        editor = sharedPreferences.edit();
                        editor.putStringSet(VIDEO_ID_LIST_KEY, set);
                        editor.putInt(vid, bitRate.getNum());
                        editor.apply();
                    }

                    JSONObject result = new JSONObject();
                    JSONObject vidJsonObject = new JSONObject();
                    vidJsonObject.put("downloadStatus", "ready");
                    vidJsonObject.put("downloadPercentage", 0);
                    result.put(vid, vidJsonObject);
                    if (callback != null) {
                        callback.invokeAndKeepAlive(result);
                    }
                } else {
                    if (error == null) {
                        error = new JSONObject();
                    }
                    Log.d(TAG, "vid is not correct: " + vid);
                    error.put(vid, "视频id不正确，请设置正确的视频id进行下载");
                }
            }
        }

        if (editor != null) {
            editor.commit();
        }

        if (callback != null && error != null) {
            callback.invokeAndKeepAlive(error);
        }
    }

    @JSMethod(uiThread = true)
    public void getDownloadList(JSONObject options, final JSCallback callback) {
        if (callback == null) {
            return;
        }
        SharedPreferences sharedPreferences = mWXSDKInstance.getContext().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> set = sharedPreferences.getStringSet(VIDEO_ID_LIST_KEY, new TreeSet<String>());

        JSONArray downloadList = new JSONArray();
        JSONObject downloadItem;
        for (String videoId : set) {
            downloadItem = new JSONObject();
            downloadItem.put("vid", videoId);
            downloadItem.put("level", sharedPreferences.getInt(videoId, PolyvBitRate.liuChang.getNum()));
            downloadList.add(downloadItem);
        }
        Log.d(TAG, "getDownloadList: " + downloadList.toString());

        JSONObject result = new JSONObject();
        result.put("downloadList", downloadList);
        callback.invoke(result);
    }


    @JSMethod(uiThread = true)
    public void startDownloader(JSONObject options, final JSCallback callback) {
        if (options == null)
            return;

        String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!PolyvSDKUtil.validateVideoId(vid)) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                Log.d(TAG, "startDownloader: " + "vid is not correct: " + vid);
                error.put("errMsg", "视频id不正确，请设置正确的视频id进行下载");
                callback.invoke(error);
            }
            return;
        }

        SharedPreferences sharedPreferences = mWXSDKInstance.getContext().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> set = sharedPreferences.getStringSet(VIDEO_ID_LIST_KEY, new TreeSet<String>());

        if (!set.contains(vid)) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                Log.d(TAG, "startDownloader: " + "download list no this vid: " + vid);
                error.put("errMsg", "下载列表没有此视频，请删除视频后重新下载");
                callback.invoke(error);
            }
            return;
        }

        int level = sharedPreferences.getInt(vid, PolyvBitRate.liuChang.getNum());
        Log.d(TAG, "startDownloader: vid=" + vid + " level=" + level);
        PolyvDownloader polyvDownloader = PolyvDownloaderManager.getPolyvDownloader(vid, level);
        polyvDownloader.start(mWXSDKInstance.getContext());
    }

    @JSMethod(uiThread = true)
    public synchronized void startAllDownloader() {
        Log.d(TAG, "startAllDownloader");
        PolyvDownloaderManager.startAll(mWXSDKInstance.getContext());
    }

    @JSMethod(uiThread = true)
    public void stopDownloader(JSONObject options, final JSCallback callback) {
        if (options == null)
            return;

        String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!PolyvSDKUtil.validateVideoId(vid)) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                Log.d(TAG, "stopDownloader" + "download list no this vid: " + vid);
                error.put("errMsg", "视频id不正确，请设置正确的视频id进行下载");
                callback.invoke(error);
            }
            return;
        }

        SharedPreferences sharedPreferences = mWXSDKInstance.getContext().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> set = sharedPreferences.getStringSet(VIDEO_ID_LIST_KEY, new TreeSet<String>());
        if (!set.contains(vid)) {
            return;
        }

        int level = sharedPreferences.getInt(vid, PolyvBitRate.liuChang.getNum());
        PolyvDownloader polyvDownloader = PolyvDownloaderManager.getPolyvDownloader(vid, level);
        polyvDownloader.stop();
        Log.d(TAG, "stopDownloader" + " vid: " + vid + " level:" + level);
        if (callback != null) {
            JSONObject result = new JSONObject();
            JSONObject vidJsonObject = new JSONObject();
            int progress = 0;
            if (videoLastDownloadProgress.containsKey(vid)) {
                progress = videoLastDownloadProgress.get(vid);
            }

            vidJsonObject.put("downloadStatus", "stopped");
            vidJsonObject.put("downloadPercentage", progress);
            result.put(vid, vidJsonObject);
            callback.invoke(result);
        }
    }


    @JSMethod(uiThread = true)
    public synchronized void stopAllDownloader(JSONObject options, final JSCallback callback) {
        Log.d(TAG, "stopAllDownloader");
        PolyvDownloaderManager.stopAll();
        SharedPreferences sharedPreferences = mWXSDKInstance.getContext().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> set = sharedPreferences.getStringSet(VIDEO_ID_LIST_KEY, new TreeSet<String>());
        if (callback != null) {
            for (String vid : set) {
                JSONObject result = new JSONObject();
                JSONObject vidJsonObject = new JSONObject();
                int progress = 0;
                if (videoLastDownloadProgress.containsKey(vid)) {
                    progress = videoLastDownloadProgress.get(vid);
                }

                vidJsonObject.put("downloadStatus", "stopped");
                vidJsonObject.put("downloadPercentage", progress);
                result.put(vid, vidJsonObject);
                callback.invokeAndKeepAlive(result);
            }
        }
    }

    @JSMethod(uiThread = false)
    public JSONObject isVideoExist(JSONObject options) {
        JSONObject error = new JSONObject();
        if (options == null)
            return null;

        final String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!PolyvSDKUtil.validateVideoId(vid)) {
            error.put("errMsg", "视频id不正确，请设置正确的视频id进行下载");
            Log.e(TAG, "downloadedVideoExist: vid is incorrect by " + vid);
            return error;
        }

        int level = JsonOptionUtil.getInt(options, "level", -99);
        PolyvBitRate bitRate = PolyvBitRate.getBitRate(level);
        if (bitRate == null) {
            error.put("errMsg", "码率错误，请设置正确的码率进行下载");
            Log.e(TAG, "downloadedVideoExist: bitrate is incorrect by " + level);
            return error;
        }

        final File downloadDir = PolyvSDKClient.getInstance().getDownloadDir();
        if (downloadDir == null) {
            error.put("errMsg", "下载目录未设置，请重启手机再次下载或者向管理员反馈");
            Log.e(TAG, "downloadedVideoExist: download dir not set ");
            return error;
        }

        PolyvValidateLocalVideoVO localVideo = PolyvVideoUtil.validateLocalVideo(vid, level);

        JSONObject result = new JSONObject();
        result.put("exist", localVideo.hasLocalVideo());
        Log.d(TAG, "downloadedVideoExist: " + localVideo.hasLocalVideo());
        return result;

    }

    @JSMethod(uiThread = true)
    public void deleteVideo(JSONObject options, final JSCallback callback) {
        if (options == null)
            return;

        String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!PolyvSDKUtil.validateVideoId(vid)) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                error.put("errMsg", "视频id不正确，请设置正确的视频id进行下载");
                callback.invoke(error);
            }
            return;
        }

        Log.d(TAG, "deleteVideo: " + vid);

        SharedPreferences sharedPreferences = mWXSDKInstance.getContext().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> set = sharedPreferences.getStringSet(VIDEO_ID_LIST_KEY, new TreeSet<String>());
        if (!set.contains(vid)) {
            PolyvDownloaderUtils.deleteVideo(vid);
            return;
        }

        int level = sharedPreferences.getInt(vid, PolyvBitRate.liuChang.getNum());
        PolyvDownloader polyvDownloader = PolyvDownloaderManager.clearPolyvDownload(vid, level);
        polyvDownloader.deleteVideo(vid, level);

        lastCallbackProgressTimeMap.remove(vid);
        videoLastDownloadProgress.remove(vid);

        set.remove(vid);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(VIDEO_ID_LIST_KEY, set);
        editor.remove(vid);
        editor.apply();
        editor.commit();
    }

    @JSMethod(uiThread = true)
    public void deleteAllVideo() {
        Log.d(TAG, "deleteAllVideo");
        SharedPreferences sharedPreferences = mWXSDKInstance.getContext().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> set = sharedPreferences.getStringSet(VIDEO_ID_LIST_KEY, new TreeSet<String>());
        PolyvDownloader polyvDownloader;
        for (String vid : set) {
            int level = sharedPreferences.getInt(vid, PolyvBitRate.liuChang.getNum());
            polyvDownloader = PolyvDownloaderManager.clearPolyvDownload(vid, level);
            polyvDownloader.deleteVideo(vid, level);

            lastCallbackProgressTimeMap.remove(vid);
            videoLastDownloadProgress.remove(vid);
        }

        set.clear();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        editor.commit();
        PolyvDownloaderUtils.deleteDownloaderDir();
    }

    @JSMethod(uiThread = true)
    public void setDownloadCallbackInterval(JSONObject options, JSCallback callback) {
        if (options == null)
            return;

        int seconds = JsonOptionUtil.getInt(options, "seconds", 1);
        if (seconds < 0) {
            seconds = 0;
        }
        Log.d(TAG, "setDownloadCallbackInterval: " + seconds);
        downloadingCallbackIntervalSeconds = seconds;

    }

    private boolean validateVideoExist(String vid, ArrayList<File> dirList) {
        String fileName, vpbid;
        VideoInfoVO videoInfo;
        for (File dir : dirList) {
            if (!dir.isDirectory()) {
                continue;
            }

            if (!dir.exists()) {
                continue;
            }

            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                continue;
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    //2.0 m3u8的存储结构vpbid/vpbid.m3u8
                    fileName = file.getName();
                    //判断目录名称是否是videoPoolBitrate的结构
                    if (PolyvSDKUtil.validateVideoPoolBitrateId(fileName)) {
                        vpbid = fileName;
                        videoInfo = getVideoInfo(vpbid);
                        if (!videoInfo.getVideoId().equals(vid)) {
                            continue;
                        }

                        int validateResult = PolyvVideoUtil.validateM3U8Video(videoInfo.getVideoId(), videoInfo.getBitrate());
                        switch (validateResult) {
                            case PolyvValidateM3U8VideoReturnType.M3U8_CORRECT:
                                return true;
                            case PolyvValidateM3U8VideoReturnType.M3U8_FILE_NOT_FOUND:
                                break;
                            case PolyvValidateM3U8VideoReturnType.M3U8_FILE_CONTENT_EMPTY:
                            case PolyvValidateM3U8VideoReturnType.M3U8_KEY_FILE_NOT_FOUND:
                            case PolyvValidateM3U8VideoReturnType.M3U8_TS_FILE_NOT_FOUND:
                            case PolyvValidateM3U8VideoReturnType.M3U8_TS_LIST_EMPTY:
                                return false;
                        }
                    }

                    continue;
                }

                //过滤key,json文件
                fileName = file.getName();
                if (fileName.endsWith(".key") || fileName.endsWith(".json")) {
                    continue;
                }

                //获取文件名前缀
                int index = fileName.lastIndexOf(".");
                if (index != -1) {
                    vpbid = fileName.substring(0, index);
                } else if (fileName.matches(".+_\\d_.+")) {
                    vpbid = fileName.substring(0, fileName.lastIndexOf("_"));
                } else {
                    vpbid = fileName;
                }

                //判断是否符合videoPoolBitrateId规范
                if (!PolyvSDKUtil.validateVideoPoolBitrateId(vpbid)) {
                    continue;
                }

                //判断是否是没有后缀的文件，是下载视频的临时文件，表示没有下载完整的视频文件。
                videoInfo = getVideoInfo(vpbid);
                if (!videoInfo.getVideoId().equals(vid)) {
                    continue;
                }

                if (!fileName.contains(".") && !fileName.matches(".+_\\d_.+")) {
                    File tmpVideo = PolyvVideoUtil.validateTmpVideo(videoInfo.getVideoId(), videoInfo.getBitrate());
                    if (tmpVideo != null) {
                        return false;
                    }
                }

                //正常有后缀的视频文件
                if (fileName.endsWith(".m3u8")) {
                    //1.0 m3u8存储结构vpbid.m3u8
                    int validateResult = PolyvVideoUtil.validateM3U8Video(videoInfo.getVideoId(), videoInfo.getBitrate());
                    switch (validateResult) {
                        case PolyvValidateM3U8VideoReturnType.M3U8_CORRECT:
                            return true;
                        case PolyvValidateM3U8VideoReturnType.M3U8_FILE_NOT_FOUND:
                            break;
                        case PolyvValidateM3U8VideoReturnType.M3U8_FILE_CONTENT_EMPTY:
                        case PolyvValidateM3U8VideoReturnType.M3U8_KEY_FILE_NOT_FOUND:
                        case PolyvValidateM3U8VideoReturnType.M3U8_TS_FILE_NOT_FOUND:
                        case PolyvValidateM3U8VideoReturnType.M3U8_TS_LIST_EMPTY:
                            return false;
                    }
                } else if (fileName.endsWith(".mp4") || fileName.endsWith("_mp4")) {
                    File mp4Video = PolyvVideoUtil.validateMP4Video(videoInfo.getVideoId(), videoInfo.getBitrate());
                    if (mp4Video != null) {
                        return true;
                    }
                } else {
                    File video = PolyvVideoUtil.validateVideo(videoInfo.getVideoId(), videoInfo.getBitrate());
                    if (video != null) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private VideoInfoVO getVideoInfo(String vpbid) {
        String vid = vpbid.substring(0, vpbid.lastIndexOf("_") + 1) + vpbid.substring(0, 1);
        int bitrate = Integer.parseInt(vpbid.substring(vpbid.length() - 1));
        return new VideoInfoVO(vid, bitrate);
    }

}
