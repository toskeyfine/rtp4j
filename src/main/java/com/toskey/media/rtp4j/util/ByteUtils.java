package com.toskey.media.rtp4j.util;

public class ByteUtils {


    /**
     * 初始化byte数组
     * @param buf
     * @param value
     * @param size
     */
    public static void memset(byte[] buf, int value, int size) {
        for (int i = 0; i < size; i++) {
            buf[i] = (byte) value;
        }
    }

    /**
     * 将数组填充为NULL
     * @param buf       数据源
     * @param value     填充值
     * @param start     起始位
     * @param end       结束位
     */
    public static void fillNull(byte[] buf, int value, int start, int end) {
        for (int i = start; i < end; i++) {
            buf[i] = (byte) value;
        }
    }

    /**
     * int转byte数组
     * 返回的是4个字节的数组
     * @param number
     * @return
     */
    public static byte[] intToByte(int number) {
        int temp = number;
        byte[] b = new byte[4];
        for (int i = 0; i < b.length; i++) {
            b[i] = new Integer(temp & 0xff).byteValue();
            temp = temp >> 8;
        }
        return b;
    }

    public static byte[] lenToByte(int len) {
        byte[] b = new byte[2];
        b[1] = (byte) ((len >> 8) & 0xFF);
        b[0] = (byte) (len & 0xFF);
        return b;
    }


    /**
     * 合并byte数组
     * @param values
     * @return
     */
    public static byte[] byteMergerAll(byte[]... values) {
        int length_byte = 0;
        for (int i = 0; i < values.length; i++) {
            length_byte += values[i].length;
        }
        byte[] all_byte = new byte[length_byte];
        int countLength = 0;
        for (int i = 0; i < values.length; i++) {
            byte[] b = values[i];
            System.arraycopy(b, 0, all_byte, countLength, b.length);
            countLength += b.length;
        }
        return all_byte;
    }

    public static byte[] margeByteArray(byte[] a, byte[] b) {
        if ( a == null ) {
            return b;
        }
        byte[] buf = new byte[a.length + b.length];
        System.arraycopy(a, 0, buf, 0, a.length);
        System.arraycopy(b, 0, buf, a.length, b.length);

        return buf;
    }

}
