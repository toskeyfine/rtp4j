package com.toskey.media.rtp4j.wrapper;

import com.toskey.media.rtp4j.RTP;
import com.toskey.media.rtp4j.exception.UnknownNalUTypeException;
import com.toskey.media.rtp4j.listener.IWrapperListener;
import com.toskey.media.rtp4j.ps.PsEncoder;
import com.toskey.media.rtp4j.util.ByteUtils;

/**
 * RTP
 *
 * @author toskey
 * @version 1.0.0
 */
public final class RTPWrapper {

    private int sequence = 0;

    private IWrapperListener wrapperListener;

    int RTP_PAYLOAD_POSITION_START = 12;

    int RTP_PAYLOAD_MAX_LENGTH = 1400;

    public static final int RTP_TYPE_PCM = 0x08;

    public static final int RTP_TYPE_PS = 0x60;

    public static final int RTP_TYPE_HEVC = 0x64;

    public static final int RTP_TYPE_H264 = 0x66;

    public void wrap(byte[] src, int payloadType, int timestamp) {
        wrapperListener.onBefore(src);
        RTP rtp = RTP.defaultRTP(payloadType, timestamp);
        switch (payloadType) {
            case RTP_TYPE_PCM -> {
                rtp.setSeq(this.sequence++);
                rtp.setPayload(src);
                wrapperListener.onSuccess(rtp);
            }
            case RTP_TYPE_H264, RTP_TYPE_HEVC, RTP_TYPE_PS -> {
                byte[] payload = removeNalUIdentification(src);
                int nalUType = payload[0] & 0x1F;
                if (nalUType < 1 || nalUType > 12) {
                    wrapperListener.onError(new UnknownNalUTypeException(nalUType));
                }
                if (payloadType == RTP_TYPE_PS) {
                    PsEncoder psEncoder = new PsEncoder();
                    payload = switch (nalUType) {
                        case 5 ->     // I帧
                                psEncoder.packageFrameIDR(payload, payload.length, false);
                        case 7 ->     // SPS
                                psEncoder.packageFrameIDR(payload, payload.length, true);
                        default ->
                            // 其他按P帧处理
                                psEncoder.packageFrameP(payload, payload.length);
                    };
                }
                if (payload.length < RTP_PAYLOAD_MAX_LENGTH) {
                    rtp.setSeq(this.sequence++);
                    rtp.setPayload(payload);
                    wrapperListener.onSuccess(rtp);
                } else {
                    switch (payloadType) {
                        case RTP_TYPE_H264 -> h264Wrapper(rtp, src);
                        case RTP_TYPE_HEVC -> hevcWrapper(rtp, src);
                        case RTP_TYPE_PS -> psWrapper(rtp, src);
                    }
                }
            }
        }

    }

    private byte[] removeNalUIdentification(byte[] src) {
        byte[] result = null;
        if (src[0] == 0x00 && src[1] == 0x00 && src[2] == 0x00 && src[3] == 0x01) {
            result = new byte[src.length - 4];
            System.arraycopy(src, 4, result, 0, src.length - 4);
        } else if (src[0] == 0x00 && src[1] == 0x00 && src[2] == 0x01) {
            result = new byte[src.length - 3];
            System.arraycopy(src, 3, result, 0, src.length - 3);
        } else {
            return src;
        }
        return result;
    }

    private void h264Wrapper(RTP rtp, byte[] payload) {
        int total = payload.length / (RTP_PAYLOAD_MAX_LENGTH - 2);
        int last = payload.length % (RTP_PAYLOAD_MAX_LENGTH - 2);
        int current = 0;
        byte[] chunk = null;
        /**
         *      FU identifier
         *       0 1 2 3 4 5 6 7
         *      +-+-+-+-+-+-+-+-+
         *      |F|NRI|  Type   |
         *      +-------------+-----------------+
         *      F
         *      NRI         值越大表示此NAL越重要
         *      Type        28：FU-A/29:FU-B
         *
         *      FU Header
         *       0 1 2 3 4 5 6 7
         *      +-+-+-+-+-+-+-+-+
         *      |S|E|R|  Type   |
         *      +---------------+
         *      S           = Start Variable
         *      E           = End Variable
         *      R           = 0
         *      Type        = NAL Unit Type（1~12）
         *
         */
        byte[] fua_header = new byte[2];
        fua_header[0] = (byte) ((payload[0] & 0x60) | 20);
        fua_header[1] = (byte) (payload[0] & 0x1F);
        while (current <= total) {
            rtp.setSeq(this.sequence++);
            if (current == total) {
                // 包大小应等于余量+2字节的FU-A头
                chunk = new byte[last + 1];
                ByteUtils.fillNull(chunk, 0x00, RTP_PAYLOAD_POSITION_START, chunk.length);
                // FU-A header E -> 1
                fua_header[1] = (byte) (fua_header[1] | (1 << 6));
                System.arraycopy(fua_header, 0, chunk, 0, 2);
                // rtp mark
                rtp.setMark(1);
                // fill payload
                System.arraycopy(payload, current * (RTP_PAYLOAD_MAX_LENGTH - 2) + 1, chunk, 2, last - 1);
            } else {
                chunk = new byte[RTP_PAYLOAD_MAX_LENGTH];
                if (0 == current) {
                    fua_header[1] = (byte) (fua_header[1] | (1 << 7));
                    System.arraycopy(fua_header, 0, chunk, 0, 2);
                    rtp.setMark(0);
                    System.arraycopy(payload, 1, chunk, 2, RTP_PAYLOAD_MAX_LENGTH - 2);
                } else {
                    if (0 == last) {
                        fua_header[1] = (byte) (fua_header[1] | (1 << 6));
                        rtp.setMark(1);
                    }
                    System.arraycopy(fua_header, 0, chunk, 0, 2);
                    System.arraycopy(payload, current * (RTP_PAYLOAD_MAX_LENGTH - 2) + 1, chunk, 2, RTP_PAYLOAD_MAX_LENGTH - 2);
                }
            }
            rtp.setPayload(chunk);
            wrapperListener.onSuccess(rtp);
            current++;
        }
    }

    private void hevcWrapper(RTP rtp, byte[] payload) {
        int total = payload.length / (RTP_PAYLOAD_MAX_LENGTH - 3);
        int last = payload.length % (RTP_PAYLOAD_MAX_LENGTH - 3);
        int current = 0;
        byte[] chunk = null;
        /**
         *      HEVC Payload Header
         *       0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
         *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *      |F|   Type    |  LayerID |  TID |
         *      +-------------+-----------------+
         *      F           = 0
         *      Type        = 49 (fragmentation unit (FU) )
         *      LayerID     = 0
         *      TID         = 1
         *
         *      FU Header
         *       0 1 2 3 4 5 6 7
         *      +-+-+-+-+-+-+-+-+
         *      |S|E|   FuType  |
         *      +---------------+
         *      S           = Start Variable
         *      E           = End Variable
         *      FuType      = NAL Unit Type
         */
        byte[] fua_hdr = new byte[3];
        fua_hdr[0] = 49 << 1;
        fua_hdr[1] = 1;
        fua_hdr[2] = (byte) ((payload[0] >> 1) & 0x3F);
        while (current <= total) {
            rtp.setSeq(this.sequence++);
            if (current == total) {
                if (last != 0) {
                    fua_hdr[2] |= 1 << 6;// FU-A结束标志
                    chunk = new byte[last + 1];
                    System.arraycopy(fua_hdr, 0, chunk, 0, 3);
                    rtp.setMark(1);
                    // 防止尾包有空数据
                    byte[] temp = new byte[12 + last];
                    System.arraycopy(payload, current * (RTP_PAYLOAD_MAX_LENGTH - 3) + 2, chunk, 2, last - 2);
                }
            } else {
                chunk = new byte[RTP_PAYLOAD_MAX_LENGTH];
                if (current == 0) {
                    fua_hdr[2] |= 1 << 7; // FU-A起始标志
                    System.arraycopy(fua_hdr, 0, chunk, 0, 3);
                    rtp.setMark(0);
                    System.arraycopy(payload, 2, chunk, 3, RTP_PAYLOAD_MAX_LENGTH - 3);
                } else {
                    // 清空RTP数据
                    if (0 == last) {
                        // 此时认为本次切片为最后一个
                        fua_hdr[2] |= 1 << 6;// FU-A结束标志
                        rtp.setMark(1);
                    }
                    System.arraycopy(fua_hdr, 0, chunk, 0, 3);
                    System.arraycopy(payload, current * (RTP_PAYLOAD_MAX_LENGTH - 3) + 2, chunk, 3, RTP_PAYLOAD_MAX_LENGTH - 3);
                }
            }
            rtp.setPayload(chunk);
            wrapperListener.onSuccess(rtp);
            current++;
        }
    }

    private void psWrapper(RTP rtp, byte[] payload) {
        int total = payload.length / RTP_PAYLOAD_MAX_LENGTH;
        int last = payload.length % RTP_PAYLOAD_MAX_LENGTH;
        int current = 0;
        byte[] chunk = null;
        while (current <= total) {
            rtp.setSeq(this.sequence++);
            if (current == total) {
                // 最后一包
                if (last != 0) {
                    // 有余量
                    rtp.setMark(1);
                    chunk = new byte[last];
                    System.arraycopy(payload, RTP_PAYLOAD_MAX_LENGTH * current, chunk, 0, last);
                }
            } else {
                // 非最后一包
                rtp.setMark(0);
                chunk = new byte[RTP_PAYLOAD_MAX_LENGTH];
                System.arraycopy(payload, RTP_PAYLOAD_MAX_LENGTH * current, chunk, 0, RTP_PAYLOAD_MAX_LENGTH);
            }
            // 将分包数据填充至RTP数据包中
            rtp.setPayload(chunk);
            wrapperListener.onSuccess(rtp);
            current++;
        }

    }

    public IWrapperListener getWrapperListener() {
        return wrapperListener;
    }

    public void setWrapperListener(IWrapperListener wrapperListener) {
        this.wrapperListener = wrapperListener;
    }
}
