package com.toskey.media.rtp4j.ps;

import com.toskey.media.rtp4j.util.ByteUtils;

/**
 * <p>
 *     将H264/HEVC封装为PS格式
 * </p>
 * @author toskey
 * @version 1.0
 */
public class PsEncoder {

    private long scr_base = 9999999;    // 默认时间戳
    private long pts = 9999999;         // 视频PTS
    private long dts = 9999999;         // 视频DTS
    private long audio_pts = 0;         // 音频PTS
    private int frame_rate = 25;

    private final static long MAX_RATE = 16001;
    /**
     * 单个PS包的最大数据长度
     */
    private final static int MAX_LENGTH = 65400;

    /**
     * 打包类型：视频流
     */
    public final static int PACKAGE_TYPE_VIDEO_MPEG = 0x10;
    public final static int PACKAGE_TYPE_VIDEO_AVC = 0x1B;
    public final static int PACKAGE_TYPE_VIDEO_SAVC = 0x80;

    /**
     * 打包类型：音频流
     */
    public final static int PACKAGE_TYPE_AUDIO_G711 = 0x90;
    public final static int PACKAGE_TYPE_AUDIO_G722 = 0X92;
    public final static int PACKAGE_TYPE_AUDIO_G723 = 0X93;
    public final static int PACKAGE_TYPE_AUDIO_G729 = 0X99;
    public final static int PACKAGE_TYPE_AUDIO_SAVC = 0X9B;

    /**
     * 视频PS系统头
     */
    private final static byte[] PS_SYSTEM_HEADER_VIDEO = {
                (byte)0x00, (byte)0x00, (byte)0x01, (byte)0xBB, (byte)0x00, (byte)0x0C, (byte)0x80, (byte)0x1E,
                (byte)0xFF, (byte)0xFE, (byte)0xE1, (byte)0x7F, (byte)0xE0, (byte)0xE0, (byte)0xD8, (byte)0xC0,
                (byte)0xC0, (byte)0x20
    };
    /**
     * 音频PS系统头
     */
    private final static byte[] PS_SYSTEM_HEADER_AUDIO = {
                (byte)0x00, (byte)0x00, (byte)0x01, (byte)0xBB, (byte)0x00, (byte)0x12, (byte)0x80, (byte)0x7D,
                (byte)0x03, (byte)0x04, (byte)0xE1, (byte)0x7F, (byte)0xE0, (byte)0xE0, (byte)0x80, (byte)0xC0,
                (byte)0xC0, (byte)0x08, (byte)0xBD, (byte)0xE0, (byte)0x80, (byte)0xBF, (byte)0xE0, (byte)0x80
    };
    /**
     * 视频PS系统映射头
     */
    private final static byte[] PS_MAP_HEADER = {
                (byte)0x00, (byte)0x00, (byte)0x01, (byte)0xBC, (byte)0x00, (byte)0x18, (byte)0xE1, (byte)0xFF,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x08, (byte)0x1B, (byte)0xE0, (byte)0x00, (byte)0x06,
                (byte)0x0A, (byte)0x04, (byte)0x65, (byte)0x6E, (byte)0x67, (byte)0x00, (byte)0x90, (byte)0xC0,
                (byte)0x00, (byte)0x00, (byte)0x23, (byte)0xB9, (byte)0x0F, (byte)0x3D
    };

    public byte[] packageAudio(byte[] src, int len) {
        byte[] dest = null;
        if (null == src) {
            return null;
        }
        byte[] header = getPsHeader(PACKAGE_TYPE_AUDIO_G711);
        byte[] pes = audioPesPackage(src, len);
        dest = ByteUtils.byteMergerAll(header, pes);
        return dest;
    }


    /**
     * 首帧打包
     * 包含PS头、系统头、节目流映射、PES视频头+数据
     *
     * @param src   视频源数据
     * @param len   源数据长度
     * @param isSPS 该数据是否为SPS
     * @return
     */
    public byte[] packageFrameIDR(byte[] src, int len, boolean isSPS) {
        byte[] dest = null;
        if(null == src) {
            return null;
        }

        byte[] pesPackage = videoPesPackage(src, len);

        if(isSPS) {
            return pesPackage;
        }

        byte[] psHeader = getPsHeader(PACKAGE_TYPE_VIDEO_AVC);
//      byte[] sysHeader = getSystemHeader(PACKAGE_TYPE_VIDEO);
//      byte[] psMapHeader = getPsMapHeader();
//      dest = ByteUtils.byteMergerAll(psHeader, sysHeader, psMapHeader, pesPackage);
        dest = ByteUtils.byteMergerAll(psHeader, PS_SYSTEM_HEADER_VIDEO, PS_MAP_HEADER, pesPackage);
        return dest;
    }



    /**
     * 非首帧打包
     * 包含PS头、PES视频头+数据
     *
     * @param src
     * @param len
     * @return
     */
    public byte[] packageFrameP(byte[] src, int len) {
        byte[] dest = null;
        if(null == src) {
            return dest;
        }
        byte[] psHeader = getPsHeader(PACKAGE_TYPE_VIDEO_AVC);
        byte[] pesPackage = videoPesPackage(src, len);
        dest = ByteUtils.byteMergerAll(psHeader, pesPackage);

        return dest;
    }

    /**
     * 生成PS标题头
     * @param packageType
     * @return
     */
    public byte[] getPsHeader(int packageType) {
        byte[] header = new byte[16];
        // 开始标志
        // 标志位
        header[0] = 0x00;
        header[1] = 0x00;
        header[2] = 0x01;
        header[3] = (byte) 0xBA;
        // 封装SCR
        header[4] = (byte) (((scr_base >> 27) & 0x38)|0x44);
        header[4] = (byte) (((scr_base >> 28) & 0x03) | header[4]);
        header[5] = (byte) ((scr_base >> 20) & 0xFF);
        header[6] = (byte) (((scr_base >> 12) & 0xF8) | 0x04);
        header[6] = (byte) (((scr_base >> 13) & 0x03) | header[6]);
        header[7] = (byte) ((scr_base >> 5) & 0xFF);
        header[8] = (byte) (((scr_base << 3) & 0xF8) | 0x04);
        header[9] = (byte) 0x01;

        // PS流复用速率
        header[10] = (byte) MAX_RATE >> 14;
        header[11] = (byte) MAX_RATE >> 6;
        header[12] = (byte) (((MAX_RATE << 2) & 0xFC) | 0x03);
        header[13] = (byte) 0xFA;

        // 填充字节
        header[14] = (byte) 0xFF;
        header[15] = (byte) 0xFF;
        if(packageType == PACKAGE_TYPE_VIDEO_AVC) {
            scr_base += (90000 / 25);
        } else if(packageType == PACKAGE_TYPE_AUDIO_G711) {
            scr_base += (8000 * 0.02);
        } else {
            return null;
        }
        return header;
    }

    /**
     * 生成系统标题头
     * @param packageType
     * @return
     */
    public byte[] getSystemHeader(int packageType) {
        byte[] header = null;

        if(packageType == PACKAGE_TYPE_VIDEO_AVC) {
            header = new byte[18];
        } else if(packageType == PACKAGE_TYPE_AUDIO_G711) {
            header = new byte[24];
        } else {
            return null;
        }

        // 标志位
        header[0] = 0x00;
        header[1] = 0x00;
        header[2] = 0x01;
        header[3] = (byte) 0xBB;
        if(packageType == PACKAGE_TYPE_VIDEO_AVC) {
            //长度位
            header[4] = 0x00;
            header[5] = 0x0C;
            // rate_bound
            header[6] = (byte) 0x80;
            header[7] = (byte) 0x1E;
            header[8] = (byte) 0xFF;

            //audio_bound
            header[9] = (byte) 0xFE;
            // video_bound
            header[10] = (byte) 0xE1;

            header[11] = (byte) 0x7F;

            header[12] = (byte) 0xE0;

            header[13] = (byte) 0xE0;

            header[14] = (byte) 0xD8;
            header[15] = (byte) 0xC0;
            header[16] = (byte) 0xC0;
            header[17] = (byte) 0x20;
        } else {

            //长度位
            header[4] = 0x00;
            header[5] = 0x12;
            // rate_bound
            header[6] = (byte) 0x80;
            header[7] = (byte) 0x7D;

            header[8] = (byte) 0x03;
            header[9] = (byte) 0x04;

            header[10] = (byte) 0xE1;
            header[11] = (byte) 0x7F;

            header[12] = (byte) 0xE0;
            header[13] = (byte) 0xE0;

            header[14] = (byte) 0x80;
            header[15] = (byte) 0xC0;
            header[16] = (byte) 0xC0;
            header[17] = (byte) 0x08;

            header[18] = (byte) 0xBD;
            header[19] = (byte) 0xE0;

            header[20] = (byte) 0x80;
            header[21] = (byte) 0xBF;

            header[22] = (byte) 0xE0;
            header[23] = (byte) 0x80;
        }

        return header;
    }

    /**
     * 生成节目流映射
     * @return
     */
    public byte[] getPsMapHeader() {
        byte[] header = new byte[30];
        // 标志位
        header[0] = (byte) 0x00;
        header[1] = (byte) 0x00;
        header[2] = (byte) 0x01;
        header[3] = (byte) 0xBC;
        // 长度位
        header[4] = (byte) 0x00;
        header[5] = (byte) 0x18;

        header[6] = (byte) 0xE1;
        header[7] = (byte) 0xFF;

        header[8] = (byte) 0x00;
        header[9] = (byte) 0x00;

        header[10] = (byte) 0x00;
        header[11] = (byte) 0x08;

        header[12] = (byte) 0x1B;

        header[13] = (byte) 0xE0;

        header[14] = (byte) 0x00;
        header[15] = (byte) 0x06;

        header[16] = (byte) 0x0A;
        header[17] = (byte) 0x04;
        header[18] = (byte) 0x65;
        header[19] = (byte) 0x6E;
        header[20] = (byte) 0x67;
        header[21] = (byte) 0x00;

        header[22] = (byte) 0x90;

        header[23] = (byte) 0xC0;

        header[24] = (byte) 0x00;

        header[25] = (byte) 0x00;

        header[26] = (byte) 0x23;
        header[27] = (byte) 0xB9;
        header[28] = (byte) 0x0F;
        header[29] = (byte) 0x3D;

        return header;
    }

    /**
     * 视频流PES打包
     * @param src
     * @param len
     * @return
     */
    public byte[] videoPesPackage(byte[] src, int len) {
        byte[] dest = new byte[19 + len];
        dest[0] = 0x00;
        dest[1] = 0x00;
        dest[2] = 0x01;
        dest[3] = (byte) 0xE0;
        // 长度位
        dest[4] = (byte) (((13 + len) >> 8) & 0xFF);
        dest[5] = (byte) ((13 + len) & 0xFF);

        dest[6] = (byte) 0x88;
        dest[7] = (byte) 0xC0;

        dest[8] = (byte) 0x0A;
        // PTS
        dest[9] = (byte) ((0x0E & (pts >> 29)) | 0x31);
        dest[10] = (byte) ((pts >> 22) & 0xFF);
        dest[11] = (byte) (((pts >> 14) & 0xFE) | 0x01);
        dest[12] = (byte) ((pts >> 7) & 0xFF);
        dest[13] = (byte) (((pts << 1) & 0xFE) | 0x01);
        // DTS
        dest[14] = (byte) ((0x0E & (dts >> 29)) | 0x11);/* 0001填充字段 */
        dest[15] = (byte) ((dts >> 22) & 0xFF);
        dest[16] = (byte) (((dts >> 14) & 0xFE) | 0x01);
        dest[17] = (byte) ((dts >> 7) & 0xFF);
        dest[18] = (byte) (((dts << 1) & 0xFE) | 0x01);

        System.arraycopy(src, 0, dest, 19, len);

        dts = (long) (pts + (90000 / 25));
        pts = (long) (pts + (90000 / 25));

        return dest;
    }

    /**
     * 音频流PES打包
     * @param src
     * @param len
     * @return
     */
    public byte[] audioPesPackage(byte[] src, int len) {
        byte[] dest = new byte[14 + len];
        dest[0] = 0x00;
        dest[1] = 0x00;
        dest[2] = 0x01;
        dest[3] = (byte) 0xC0;
        // 长度位
        dest[4] = (byte) (((8 + len) >> 8) & 0xFF);
        dest[5] = (byte) ((8 + len) & 0xFF);
        // PES头扩展字段识别标识
        dest[6] = (byte) 0x88;
        dest[7] = (byte) 0x80;
        // PES头扩展字段长度
        dest[8] = (byte) 0x05;
        // PTS
        if(pts - audio_pts > 500000) {
            audio_pts = pts;
        }
        dest[9] = (byte) ((0x0E & (audio_pts >> 29)) | 0x21);
        dest[10] = (byte) ((audio_pts >> 22) & 0xFF);
        dest[11] = (byte) (((audio_pts >> 14) & 0xFE) | 0x01);
        dest[12] = (byte) ((audio_pts >> 7) & 0xFF);
        dest[13] = (byte) (((audio_pts << 1) & 0xFE) | 0x01);

        System.arraycopy(src, 0, dest, 14, len);

        audio_pts = (long) (audio_pts + 8000 * 0.02);/* 音频PTS时标 */

        return dest;
    }

}

