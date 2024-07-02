package com.toskey.media.rtp4j;

import com.toskey.media.rtp4j.util.ByteUtils;
import com.toskey.media.rtp4j.wrapper.RTPWrapper;

/**
 * RTP
 *
 * @author toskey
 * @version 1.0.0
 */
public class RTP {

    public static final int HEADER_SIZE = 12;

    private int version;

    private int padding;

    private int extension;
    
    private int mark;

    private int pt;

    private int seq;

    private int timestamp;

    private int ssrc;

    private byte[] header;

    private byte[] payload;

    public static RTP defaultRTP(int payloadType, int timestamp) {
        RTP defaultRTP = new RTP();
        defaultRTP.setVersion(2);
        defaultRTP.setPadding(0);
        defaultRTP.setExtension(0);
        defaultRTP.setMark(0);
        defaultRTP.setSeq(0);
        defaultRTP.setSsrc(0x55667788);
        defaultRTP.setTimestamp(timestamp);
        defaultRTP.setPt(payloadType);

        byte[] header = new byte[HEADER_SIZE];
        ByteUtils.memset(header, 0x00, HEADER_SIZE);
        header[0] = (byte) ((defaultRTP.getVersion() << 6) + defaultRTP.getPadding() + defaultRTP.getExtension());
        header[1] = (byte) ((defaultRTP.getMark() << 7) + payloadType);
        // timestamp
        byte[] bTimestamp = ByteUtils.intToByte(timestamp);
        System.arraycopy(bTimestamp, 0, header, 4, 4);
        {
            byte tmp = header[4];
            header[4] = header[7];
            header[7] = tmp;
            tmp = header[5];
            header[5] = header[6];
            header[6] = tmp;
        }
        // ssrc
        {
            header[11] = (byte) (defaultRTP.getSsrc() >> 24);
            header[10] = (byte) ((defaultRTP.getSsrc() & 0xFFFFFF) >> 16);
            header[9] = (byte) ((defaultRTP.getSsrc() & 0xFFFF) >> 8);
            header[8] = (byte) ((defaultRTP.getSsrc() & 0xFF));
        }

        defaultRTP.setHeader(header);
        if (payloadType == RTPWrapper.RTP_TYPE_PCM) {
            defaultRTP.setPayload(new byte[320]);
        } else {
            defaultRTP.setPayload(new byte[1400]);
        }
        return defaultRTP;
    }

    public byte[] toByte() {
        byte[] rtp = new byte[this.header.length + this.payload.length];
        System.arraycopy(this.header, 0, rtp, 0, this.header.length);
        System.arraycopy(this.payload, 0, rtp, this.header.length, this.payload.length);
        return rtp;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getPadding() {
        return padding;
    }

    public void setPadding(int padding) {
        this.padding = padding;
    }

    public int getExtension() {
        return extension;
    }

    public void setExtension(int extension) {
        this.extension = extension;
    }

    public int getMark() {
        return mark;
    }

    public void setMark(int mark) {
        this.mark = mark;
        if (this.header != null) {
            if (mark == 1) {
                this.header[1] = (byte) (this.header[1] | 0x80);
            } else if (mark == 0) {
                this.header[1] = (byte) (this.header[1] & 0x7F);
            }
        }
    }

    public int getPt() {
        return pt;
    }

    public void setPt(int pt) {
        this.pt = pt;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
        if (this.header != null) {
            System.arraycopy(ByteUtils.intToByte(seq), 0, this.header, 2, 2);
            {
                byte tmp = this.header[3];
                this.header[3] = this.header[2];
                this.header[2] = tmp;
            }
        }
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getSsrc() {
        return ssrc;
    }

    public void setSsrc(int ssrc) {
        this.ssrc = ssrc;
    }

    public byte[] getHeader() {
        return header;
    }

    public void setHeader(byte[] header) {
        this.header = header;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
