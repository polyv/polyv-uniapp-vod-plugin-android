package com.easefun.plvvod.vo;

/**
 * Created by tanqu on 2018/8/24.
 */

public class VideoInfoVO {
    private final String videoId;
    private final int bitrate;

    public VideoInfoVO(String videoId, int bitrate) {
        this.videoId = videoId;
        this.bitrate = bitrate;
    }

    public String getVideoId() {
        return videoId;
    }

    public int getBitrate() {
        return bitrate;
    }
}
